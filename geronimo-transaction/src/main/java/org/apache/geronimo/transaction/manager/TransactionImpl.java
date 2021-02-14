/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.geronimo.transaction.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Basic local transaction with support for multiple resources.
 *
 * @version $Rev$ $Date$
 */
public class TransactionImpl implements Transaction {
    private static final Logger log = LoggerFactory.getLogger("Transaction");

    private final TransactionManagerImpl txManager;
    private final Xid xid;
    private final CurrentTimeMsProvider timeProvider;
    private final long timeout;
    private final List<Synchronization> syncList = new ArrayList<Synchronization>(5);
    private final List<Synchronization> interposedSyncList = new ArrayList<Synchronization>(3);
    private final LinkedList<TransactionBranch> resourceManagers = new LinkedList<TransactionBranch>();
    private final IdentityHashMap<XAResource, TransactionBranch> activeXaResources = new IdentityHashMap<XAResource, TransactionBranch>(3);
    private final IdentityHashMap<XAResource, TransactionBranch> suspendedXaResources = new IdentityHashMap<XAResource, TransactionBranch>(3);
    private int status = Status.STATUS_NO_TRANSACTION;
    private Throwable markRollbackCause;
    private Object logMark;

    private final Map<Object, Object> resources = new HashMap<Object, Object>();

    TransactionImpl(TransactionManagerImpl txManager, long transactionTimeoutMilliseconds) throws SystemException {
        this(txManager.getXidFactory().createXid(), txManager, transactionTimeoutMilliseconds);
    }

    TransactionImpl(TransactionManagerImpl txManager, long transactionTimeoutMilliseconds, CurrentTimeMsProvider timeProvider) throws SystemException {
        this(txManager.getXidFactory().createXid(), txManager, transactionTimeoutMilliseconds, timeProvider);
    }

    TransactionImpl(Xid xid, TransactionManagerImpl txManager, long transactionTimeoutMilliseconds) throws SystemException {
        this(xid, txManager, transactionTimeoutMilliseconds, null);
    }

    TransactionImpl(Xid xid, TransactionManagerImpl txManager, long transactionTimeoutMilliseconds, CurrentTimeMsProvider timeProvider) throws SystemException {
        this.txManager = txManager;
        this.xid = xid;
        this.timeProvider = timeProvider == null ? SystemCurrentTime.INSTANCE : timeProvider;
        this.timeout = transactionTimeoutMilliseconds + this.timeProvider.now();
        try {
            txManager.getTransactionLog().begin(xid);
        } catch (LogException e) {
            markRollbackCause(e);
            status = Status.STATUS_MARKED_ROLLBACK;
            SystemException ex = new SystemException("Error logging begin; transaction marked for roll back)");
            ex.initCause(e);
            throw ex;
        }
        status = Status.STATUS_ACTIVE;
    }

    //reconstruct a tx for an external tx found in recovery
    public TransactionImpl(Xid xid, TransactionManagerImpl txManager) {
        this.txManager = txManager;
        this.xid = xid;
        status = Status.STATUS_PREPARED;
        timeProvider = SystemCurrentTime.INSTANCE;
        //TODO is this a good idea?
        this.timeout = Long.MAX_VALUE;
    }

    public synchronized int getStatus() {
        return status;
    }

    public Object getResource(Object key) {
        return resources.get(key);
    }

    public boolean getRollbackOnly() {
        return status == Status.STATUS_MARKED_ROLLBACK;
    }

    public Object getTransactionKey() {
        return xid;
    }

    public int getTransactionStatus() {
        return status;
    }

    public void putResource(Object key, Object value) {
        if (key == null) {
            throw new NullPointerException("You must supply a non-null key for putResource");
        }
        resources.put(key, value);
    }

    public void registerInterposedSynchronization(Synchronization synchronization) {
        interposedSyncList.add(synchronization);
    }

    public synchronized void setRollbackOnly() throws IllegalStateException {
        setRollbackOnly(new SetRollbackOnlyException());
    }

    public synchronized void setRollbackOnly(Throwable reason) {
        switch (status) {
            case Status.STATUS_ACTIVE:
            case Status.STATUS_PREPARING:
                status = Status.STATUS_MARKED_ROLLBACK;
                markRollbackCause(reason);
                break;
            case Status.STATUS_MARKED_ROLLBACK:
            case Status.STATUS_ROLLING_BACK:
                // nothing to do
                break;
            default:
                throw new IllegalStateException("Cannot set rollback only, status is " + getStateString(status));
        }
    }

    public synchronized void registerSynchronization(Synchronization synch) throws IllegalStateException, RollbackException, SystemException {
        if (synch == null) {
            throw new IllegalArgumentException("Synchronization is null");
        }
        switch (status) {
            case Status.STATUS_ACTIVE:
            case Status.STATUS_PREPARING:
                break;
            case Status.STATUS_MARKED_ROLLBACK:
                throw new RollbackException("Transaction is marked for rollback");
            default:
                throw new IllegalStateException("Status is " + getStateString(status));
        }
        syncList.add(synch);
    }

    public synchronized boolean enlistResource(XAResource xaRes) throws IllegalStateException, RollbackException, SystemException {
        if (xaRes == null) {
            throw new IllegalArgumentException("XAResource is null");
        }
        switch (status) {
            case Status.STATUS_ACTIVE:
                break;
            case Status.STATUS_MARKED_ROLLBACK:
                break;
            default:
                throw new IllegalStateException("Status is " + getStateString(status));
        }

        if (activeXaResources.containsKey(xaRes)) {
            throw new IllegalStateException("xaresource: " + xaRes + " is already enlisted!");
        }

        try {
            TransactionBranch manager = suspendedXaResources.remove(xaRes);
            if (manager != null) {
                //we know about this one, it was suspended
                xaRes.start(manager.getBranchId(), XAResource.TMRESUME);
                activeXaResources.put(xaRes, manager);
                return true;
            }
            //it is not suspended.
            for (Iterator i = resourceManagers.iterator(); i.hasNext();) {
                manager = (TransactionBranch) i.next();
                boolean sameRM;
                //if the xares is already known, we must be resuming after a suspend.
                if (xaRes == manager.getCommitter()) {
                    throw new IllegalStateException("xaRes " + xaRes + " is a committer but is not active or suspended");
                }
                //Otherwise, see if this is a new xares for the same resource manager
                try {
                    sameRM = xaRes.isSameRM(manager.getCommitter());
                } catch (XAException e) {
                    log.warn("Unexpected error checking for same RM", e);
                    continue;
                }
                if (sameRM) {
                    xaRes.start(manager.getBranchId(), XAResource.TMJOIN);
                    activeXaResources.put(xaRes, manager);
                    return true;
                }
            }
            //we know nothing about this XAResource or resource manager
            Xid branchId = txManager.getXidFactory().createBranch(xid, resourceManagers.size() + 1);
            xaRes.start(branchId, XAResource.TMNOFLAGS);
            //Set the xaresource timeout in seconds to match the time left in this tx.
            xaRes.setTransactionTimeout((int)(timeout - timeProvider.now())/1000);
            activeXaResources.put(xaRes, addBranchXid(xaRes, branchId));
            return true;
        } catch (XAException e) {
            log.warn("Unable to enlist XAResource " + xaRes + ", errorCode: " + e.errorCode, e);
            // mark status as rollback only because enlist resource failed
            setRollbackOnly(e);
            return false;
        }
    }

    public synchronized boolean delistResource(XAResource xaRes, int flag) throws IllegalStateException, SystemException {
        if (!(flag == XAResource.TMFAIL || flag == XAResource.TMSUCCESS || flag == XAResource.TMSUSPEND)) {
            throw new IllegalStateException("invalid flag for delistResource: " + flag);
        }
        if (xaRes == null) {
            throw new IllegalArgumentException("XAResource is null");
        }
        switch (status) {
            case Status.STATUS_ACTIVE:
            case Status.STATUS_MARKED_ROLLBACK:
                break;
            default:
                throw new IllegalStateException("Status is " + getStateString(status));
        }
        TransactionBranch manager = activeXaResources.remove(xaRes);
        if (manager == null) {
            if (flag == XAResource.TMSUSPEND) {
                throw new IllegalStateException("trying to suspend an inactive xaresource: " + xaRes);
            }
            //not active, and we are not trying to suspend.  We must be ending tx.
            manager = suspendedXaResources.remove(xaRes);
            if (manager == null) {
                throw new IllegalStateException("Resource not known to transaction: " + xaRes);
            }
        }

        try {
            xaRes.end(manager.getBranchId(), flag);
            if (flag == XAResource.TMSUSPEND) {
                suspendedXaResources.put(xaRes, manager);
            }
            return true;
        } catch (XAException e) {
            log.warn("Unable to delist XAResource " + xaRes + ", error code: " + e.errorCode, e);
            return false;
        }
    }

    //Transaction method, does 2pc
    public void commit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException, SecurityException, SystemException {
        beforePrepare();

        try {
            if (timeProvider.now() > timeout) {
                markRollbackCause(new Exception("Transaction has timed out"));
                status = Status.STATUS_MARKED_ROLLBACK;
            }

            if (status == Status.STATUS_MARKED_ROLLBACK) {
                rollbackResources(resourceManagers, false);
                RollbackException rollbackException = new RollbackException("Unable to commit: transaction marked for rollback");
                if (markRollbackCause != null) {
                    rollbackException.initCause(markRollbackCause);
                }
                throw rollbackException;
            }
            synchronized (this) {
                if (status == Status.STATUS_ACTIVE) {
                    if (this.resourceManagers.size() == 0) {
                        // nothing to commit
                        status = Status.STATUS_COMMITTED;
                    } else if (this.resourceManagers.size() == 1) {
                        // one-phase commit decision
                        status = Status.STATUS_COMMITTING;
                    } else {
                        // start prepare part of two-phase
                        status = Status.STATUS_PREPARING;
                    }
                }
                // resourceManagers is now immutable
            }

            // no-phase
            if (resourceManagers.size() == 0) {
                synchronized (this) {
                    status = Status.STATUS_COMMITTED;
                }
                return;
            }

            // one-phase
            if (resourceManagers.size() == 1) {
                TransactionBranch manager = resourceManagers.getFirst();
                commitResource(manager);
                return;
            }

            boolean willCommit = false;
            try {
                // two-phase
                willCommit = internalPrepare();
            } catch (SystemException e) {
                rollbackResources(resourceManagers, false);
                throw e;
            }

            // notify the RMs
            if (willCommit) {
                //Re-check whether there are still left resourceMangers, as we might remove those Read-Only Resource in the voting process
                if (resourceManagers.size() == 0) {
                    synchronized (this) {
                        status = Status.STATUS_COMMITTED;
                    }
                    return;
                }
                commitResources(resourceManagers);
            } else {
                // set everRollback to true here because the rollback here is caused by
                // XAException during the above internalPrepare
                rollbackResources(resourceManagers, true);
                throw new RollbackException("transaction rolled back due to problems in prepare");
            }
        } finally {
            afterCompletion();
            synchronized (this) {
                status = Status.STATUS_NO_TRANSACTION;
            }
        }
    }

    //Used from XATerminator for first phase in a remotely controlled tx.
    int prepare() throws SystemException, RollbackException {
        beforePrepare();
        int result = XAResource.XA_RDONLY;
        try {
            LinkedList rms;
            synchronized (this) {
                if (status == Status.STATUS_ACTIVE) {
                    if (resourceManagers.size() == 0) {
                        // nothing to commit
                        status = Status.STATUS_COMMITTED;
                        return result;
                    } else {
                        // start prepare part of two-phase
                        status = Status.STATUS_PREPARING;
                    }
                }
                // resourceManagers is now immutable
                rms = resourceManagers;
            }

            boolean willCommit = internalPrepare();

            // notify the RMs
            if (willCommit) {
                if (!rms.isEmpty()) {
                    result = XAResource.XA_OK;
                }
            } else {
                try {
                    rollbackResources(rms, false);
                } catch (HeuristicMixedException e) {
                    throw (SystemException)new SystemException("Unable to commit and heuristic exception during rollback").initCause(e);
                }
                throw new RollbackException("Unable to commit");
            }
        } finally {
            if (result == XAResource.XA_RDONLY) {
                afterCompletion();
                synchronized (this) {
                    status = Status.STATUS_NO_TRANSACTION;
                }
            }
        }
        return result;
    }

    //used from XATerminator for commit phase of non-readonly remotely controlled tx.
    void preparedCommit() throws HeuristicRollbackException, HeuristicMixedException, SystemException {
        try {
            commitResources(resourceManagers);
        } finally {
            afterCompletion();
            synchronized (this) {
                status = Status.STATUS_NO_TRANSACTION;
            }
        }
    }

    //helper method used by Transaction.commit and XATerminator prepare.
    private void beforePrepare() {
        synchronized (this) {
            switch (status) {
                case Status.STATUS_ACTIVE:
                case Status.STATUS_MARKED_ROLLBACK:
                    break;
                default:
                    throw new IllegalStateException("Status is " + getStateString(status));
            }
        }

        beforeCompletion();
        endResources();
    }


    //helper method used by Transaction.commit and XATerminator prepare.
    private boolean internalPrepare() throws SystemException {
        for (Iterator rms = resourceManagers.iterator(); rms.hasNext();) {
            synchronized (this) {
                if (status != Status.STATUS_PREPARING) {
                    // we were marked for rollback
                    break;
                }
            }
            TransactionBranch manager = (TransactionBranch) rms.next();
            try {
                int vote = manager.getCommitter().prepare(manager.getBranchId());
                if (vote == XAResource.XA_RDONLY) {
                    // we don't need to consider this RM any more
                    rms.remove();
                }
            } catch (XAException e) {
                if (e.errorCode == XAException.XAER_RMERR
                        || e.errorCode == XAException.XAER_PROTO
                        || e.errorCode == XAException.XAER_INVAL) {
                    throw (SystemException) new SystemException("Error during prepare; transaction was rolled back").initCause(e);
                }
                synchronized (this) {
                    markRollbackCause(e);
                    status = Status.STATUS_MARKED_ROLLBACK;
                    /* Per JTA spec,  If the resource manager wants to roll back the transaction,
                    it should do so by throwing an appropriate XAException in the prepare method.
                    Also per OTS spec:
                    The resource can return VoteRollback under any circumstances, including not having
                    any knowledge about the transaction (which might happen after a crash). If this
                    response is returned, the transaction must be rolled back. Furthermore, the Transaction
                    Service is not required to perform any additional operations on this resource.*/
                    //rms.remove();
                    break;
                }
            }
        }

        // decision time...
        boolean willCommit;
        synchronized (this) {
            willCommit = (status != Status.STATUS_MARKED_ROLLBACK);
            if (willCommit) {
                status = Status.STATUS_PREPARED;
            }
        }
        // log our decision
        if (willCommit && !resourceManagers.isEmpty()) {
            try {
                logMark = txManager.getTransactionLog().prepare(xid, resourceManagers);
            } catch (LogException e) {
                try {
                    rollbackResources(resourceManagers, false);
                } catch (Exception se) {
                    log.error("Unable to rollback after failure to log prepare", se.getCause());
                }
                throw (SystemException) new SystemException("Error logging prepare; transaction was rolled back)").initCause(e);
            }
        }
        return willCommit;
    }

    public void rollback() throws IllegalStateException, SystemException {
        List rms;
        synchronized (this) {
            switch (status) {
                case Status.STATUS_ACTIVE:
                    status = Status.STATUS_MARKED_ROLLBACK;
                    break;
                case Status.STATUS_MARKED_ROLLBACK:
                    break;
                default:
                    throw new IllegalStateException("Status is " + getStateString(status));
            }
            rms = resourceManagers;
        }

        endResources();
        try {
            try {
                rollbackResources(rms, false);
            } catch (HeuristicMixedException e) {
                throw (SystemException)new SystemException("Unable to roll back due to heuristics").initCause(e);
            }
        } finally {
            afterCompletion();
            synchronized (this) {
                status = Status.STATUS_NO_TRANSACTION;
            }
        }
    }

    private void beforeCompletion() {
        beforeCompletion(syncList);
        beforeCompletion(interposedSyncList);
    }

    private void beforeCompletion(List syncs) {
        int i = 0;
        while (true) {
            Synchronization synch;
            synchronized (this) {
                if (i == syncs.size()) {
                    return;
                } else {
                    synch = (Synchronization) syncs.get(i++);
                }
            }
            try {
                synch.beforeCompletion();
            } catch (Exception e) {
                log.warn("Unexpected exception from beforeCompletion; transaction will roll back", e);
                synchronized (this) {
                    markRollbackCause(e);
                    status = Status.STATUS_MARKED_ROLLBACK;
                }
            }
        }
    }

    private void markRollbackCause(Throwable e) {
        if (markRollbackCause == null) {
            markRollbackCause = e;
        } else if (markRollbackCause instanceof SetRollbackOnlyException) {
            Throwable cause = markRollbackCause.getCause();
            if (cause == null) {
                markRollbackCause.initCause(e);
            }
        }
    }

    private void afterCompletion() {
        // this does not synchronize because nothing can modify our state at this time
        afterCompletion(interposedSyncList);
        afterCompletion(syncList);
    }

    private void afterCompletion(List syncs) {
        for (Iterator i = syncs.iterator(); i.hasNext();) {
            Synchronization synch = (Synchronization) i.next();
            try {
                synch.afterCompletion(status);
            } catch (Exception e) {
                log.warn("Unexpected exception from afterCompletion; continuing", e);
            }
        }
    }

    private void endResources() {
        endResources(activeXaResources);
        endResources(suspendedXaResources);
    }

    private void endResources(IdentityHashMap<XAResource, TransactionBranch> resourceMap) {
        while (true) {
            XAResource xaRes;
            TransactionBranch manager;
            int flags;
            synchronized (this) {
                Set entrySet = resourceMap.entrySet();
                if (entrySet.isEmpty()) {
                    return;
                }
                Map.Entry entry = (Map.Entry) entrySet.iterator().next();
                xaRes = (XAResource) entry.getKey();
                manager = (TransactionBranch) entry.getValue();
                flags = (status == Status.STATUS_MARKED_ROLLBACK) ? XAResource.TMFAIL : XAResource.TMSUCCESS;
                resourceMap.remove(xaRes);
            }
            try {
                xaRes.end(manager.getBranchId(), flags);
            } catch (XAException e) {
                log.warn("Error ending association for XAResource " + xaRes + "; transaction will roll back. XA error code: " + e.errorCode, e);
                synchronized (this) {
                    markRollbackCause(e);
                    status = Status.STATUS_MARKED_ROLLBACK;
                }
            }
        }
    }

    private void rollbackResources(List<TransactionBranch> rms, boolean everRb) throws HeuristicMixedException, SystemException {
        RollbackTask rollbackTask = new RollbackTask(xid, rms, logMark, txManager);
        synchronized (this) {
            status = Status.STATUS_ROLLING_BACK;
        }
        rollbackTask.run();
        synchronized (this) {
            status = rollbackTask.getStatus();
        }
        XAException cause = rollbackTask.getCause();
        boolean everRolledback = everRb || rollbackTask.isEverRolledBack();

        if (cause != null) {
            if (cause.errorCode == XAException.XA_HEURCOM && everRolledback) {
                throw (HeuristicMixedException) new HeuristicMixedException("HeuristicMixed error during commit/rolling back").initCause(cause);
            } else if (cause.errorCode == XAException.XA_HEURMIX) {
                throw (HeuristicMixedException) new HeuristicMixedException("HeuristicMixed error during commit/rolling back").initCause(cause);
            } else {
                throw (SystemException) new SystemException("System Error during commit/rolling back").initCause(cause);
            }
        }
    }

    private void commitResource(TransactionBranch manager) throws RollbackException, HeuristicRollbackException, HeuristicMixedException, SystemException{
        XAException cause = null;
        try {
            try {

                manager.getCommitter().commit(manager.getBranchId(), true);
                synchronized (this) {
                    status = Status.STATUS_COMMITTED;
                }
                return;
            } catch (XAException e) {
                synchronized (this) {
                    status = Status.STATUS_ROLLEDBACK;
                }

                if (e.errorCode == XAException.XA_HEURRB) {
                    cause = e;
                    manager.getCommitter().forget(manager.getBranchId());
                    //throw (HeuristicRollbackException) new HeuristicRollbackException("Error during one-phase commit").initCause(e);
                } else if (e.errorCode == XAException.XA_HEURMIX) {
                    cause = e;
                    manager.getCommitter().forget(manager.getBranchId());
                    throw (HeuristicMixedException) new HeuristicMixedException("Error during one-phase commit").initCause(e);
                } else if (e.errorCode == XAException.XA_HEURCOM) {
                    // let's not throw an exception as the transaction has been committed
                    log.info("Transaction has been heuristically committed");
                    manager.getCommitter().forget(manager.getBranchId());
                } else if (e.errorCode == XAException.XA_RBROLLBACK
                        || e.errorCode == XAException.XAER_RMERR
                        || e.errorCode == XAException.XAER_NOTA) {
                    // Per XA spec, XAException.XAER_RMERR from commit means An error occurred in
                    // committing the work performed on behalf of the transaction branch
                    // and the branch's work has been rolled back.
                    // XAException.XAER_NOTA:  assume the DB took a unilateral rollback decision and forgot the transaction
                    log.info("Transaction has been rolled back");
                    cause = e;
                    // throw (RollbackException) new RollbackException("Error during one-phase commit").initCause(e);
                } else {
                    cause = e;
                    //throw (SystemException) new SystemException("Error during one-phase commit").initCause(e);
                }
            }
        } catch (XAException e) {
            if (e.errorCode == XAException.XAER_NOTA) {
                // NOTA in response to forget, means the resource already forgot the transaction
                // ignore
            } else {
                throw (SystemException) new SystemException("Error during one phase commit").initCause(e);
            }
        }

        if (cause != null) {
            if (cause.errorCode == XAException.XA_HEURRB) {
                throw (HeuristicRollbackException) new HeuristicRollbackException("Error during two phase commit").initCause(cause);
            } else if (cause.errorCode == XAException.XA_HEURMIX) {
                throw (HeuristicMixedException) new HeuristicMixedException("Error during two phase commit").initCause(cause);
            } else if (cause.errorCode == XAException.XA_RBROLLBACK
                    || cause.errorCode == XAException.XAER_RMERR
                    || cause.errorCode == XAException.XAER_NOTA) {
                throw (RollbackException) new RollbackException("Error during two phase commit").initCause(cause);
            } else {
                throw (SystemException) new SystemException("Error during two phase commit").initCause(cause);
            }
        }
    }

    private void commitResources(List<TransactionBranch> rms) throws HeuristicRollbackException, HeuristicMixedException, SystemException {
        CommitTask commitTask = new CommitTask(xid, rms, logMark, txManager);
        synchronized (this) {
            status = Status.STATUS_COMMITTING;
        }
        commitTask.run();
        synchronized (this) {
            status = commitTask.getStatus();
        }
        XAException cause = commitTask.getCause();
        boolean evercommit = commitTask.isEvercommit();
        if (cause != null) {
            if (cause.errorCode == XAException.XA_HEURRB && !evercommit) {
                throw (HeuristicRollbackException) new HeuristicRollbackException("Error during two phase commit").initCause(cause);
            } else if (cause.errorCode == XAException.XA_HEURRB && evercommit) {
                throw (HeuristicMixedException) new HeuristicMixedException("Error during two phase commit").initCause(cause);
            } else if (cause.errorCode == XAException.XA_HEURMIX) {
                throw (HeuristicMixedException) new HeuristicMixedException("Error during two phase commit").initCause(cause);
            } else {
                throw (SystemException) new SystemException("Error during two phase commit").initCause(cause);
            }
        }


    }

    private static String getStateString(int status) {
        switch (status) {
            case Status.STATUS_ACTIVE:
                return "STATUS_ACTIVE";
            case Status.STATUS_PREPARING:
                return "STATUS_PREPARING";
            case Status.STATUS_PREPARED:
                return "STATUS_PREPARED";
            case Status.STATUS_MARKED_ROLLBACK:
                return "STATUS_MARKED_ROLLBACK";
            case Status.STATUS_ROLLING_BACK:
                return "STATUS_ROLLING_BACK";
            case Status.STATUS_COMMITTING:
                return "STATUS_COMMITTING";
            case Status.STATUS_COMMITTED:
                return "STATUS_COMMITTED";
            case Status.STATUS_ROLLEDBACK:
                return "STATUS_ROLLEDBACK";
            case Status.STATUS_NO_TRANSACTION:
                return "STATUS_NO_TRANSACTION";
            case Status.STATUS_UNKNOWN:
                return "STATUS_UNKNOWN";
            default:
                throw new AssertionError();
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof TransactionImpl) {
            TransactionImpl other = (TransactionImpl) obj;
            return xid.equals(other.xid);
        } else {
            return false;
        }
    }

    //when used from recovery, do not add manager to active or suspended resource maps.
    // The xaresources have already been ended with TMSUCCESS.
    public TransactionBranch addBranchXid(XAResource xaRes, Xid branchId) {
        TransactionBranch manager = new TransactionBranch(xaRes, branchId);
        resourceManagers.add(manager);
        return manager;
    }

    static class TransactionBranch implements TransactionBranchInfo {
        private final XAResource committer;
        private final Xid branchId;

        public TransactionBranch(XAResource xaRes, Xid branchId) {
            committer = xaRes;
            this.branchId = branchId;
        }

        public XAResource getCommitter() {
            return committer;
        }

        public Xid getBranchId() {
            return branchId;
        }

        public String getResourceName() {
            if (committer instanceof NamedXAResource) {
                return ((NamedXAResource) committer).getName();
            } else {
                // if it isn't a named resource should we really stop all processing here!
                // Maybe this would be better to handle else where and do we really want to prevent all processing of transactions?
                log.error("Please correct the integration and supply a NamedXAResource", new IllegalStateException("Cannot log transactions as " + committer + " is not a NamedXAResource."));
                return committer.toString();
            }
        }

        public Xid getBranchXid() {
            return branchId;
        }
    }

    static class ReturnableTransactionBranch extends TransactionBranch {
        private final NamedXAResourceFactory namedXAResourceFactory;

        ReturnableTransactionBranch(Xid branchId, NamedXAResourceFactory namedXAResourceFactory) throws SystemException {
            super(namedXAResourceFactory.getNamedXAResource(), branchId);
            this.namedXAResourceFactory = namedXAResourceFactory;
        }

        public void returnXAResource() {
            namedXAResourceFactory.returnNamedXAResource((NamedXAResource) getCommitter());
        }
    }
}
