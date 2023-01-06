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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collection;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class RecoveryImpl implements Recovery {
    private static final Logger log = LoggerFactory.getLogger("Recovery");


    private final TransactionManagerImpl txManager;

    private final Map<Xid, TransactionImpl> externalXids = new HashMap<Xid, TransactionImpl>();
    private final Map<ByteArrayWrapper, XidBranchesPair> ourXids = new HashMap<ByteArrayWrapper, XidBranchesPair>();
    private final Map<String, Set<XidBranchesPair>> nameToOurTxMap = new HashMap<String, Set<XidBranchesPair>>();
    private final Map<byte[], TransactionImpl> externalGlobalIdMap = new HashMap<byte[], TransactionImpl>();

    private final List<Exception> recoveryErrors = new ArrayList<Exception>();

    public RecoveryImpl(TransactionManagerImpl txManager) {
        this.txManager = txManager;
    }

    public synchronized void recoverLog() throws XAException {
        Collection<XidBranchesPair> preparedXids;
        try {
            preparedXids = txManager.getTransactionLog().recover(txManager.getXidFactory());
        } catch (LogException e) {
            throw (XAException) new XAException(XAException.XAER_RMERR).initCause(e);
        }
        for (XidBranchesPair xidBranchesPair : preparedXids) {
            Xid xid = xidBranchesPair.getXid();
            log.trace("read prepared global xid from log: " + toString(xid));
            if (txManager.getXidFactory().matchesGlobalId(xid.getGlobalTransactionId())) {
                ourXids.put(new ByteArrayWrapper(xid.getGlobalTransactionId()), xidBranchesPair);
                for (TransactionBranchInfo transactionBranchInfo : xidBranchesPair.getBranches()) {
                    log.trace("read branch from log: " + transactionBranchInfo.toString());
                    String name = transactionBranchInfo.getResourceName();
                    Set<XidBranchesPair> transactionsForName = nameToOurTxMap.get(name);
                    if (transactionsForName == null) {
                        transactionsForName = new HashSet<XidBranchesPair>();
                        nameToOurTxMap.put(name, transactionsForName);
                    }
                    transactionsForName.add(xidBranchesPair);
                }
            } else {
                log.trace("read external prepared xid from log: " + toString(xid));
                TransactionImpl externalTx = new ExternalTransaction(xid, txManager, xidBranchesPair.getBranches());
                externalXids.put(xid, externalTx);
                externalGlobalIdMap.put(xid.getGlobalTransactionId(), externalTx);
            }
        }
    }


    public synchronized void recoverResourceManager(NamedXAResource xaResource) throws XAException {
        String name = xaResource.getName();
        Xid[] prepared = xaResource.recover(XAResource.TMSTARTRSCAN + XAResource.TMENDRSCAN);
        for (int i = 0; prepared != null && i < prepared.length; i++) {
            Xid xid = prepared[i];
            if (xid.getGlobalTransactionId() == null || xid.getBranchQualifier() == null) {
                log.warn("Ignoring bad xid from\n name: " + xaResource.getName() + "\n " + toString(xid));
                continue;
            }
            log.trace("Considering recovered xid from\n name: " + xaResource.getName() + "\n " + toString(xid));
            ByteArrayWrapper globalIdWrapper = new ByteArrayWrapper(xid.getGlobalTransactionId());
            XidBranchesPair xidNamesPair = ourXids.get(globalIdWrapper);
            
            if (xidNamesPair != null) {
                
                // Only commit if this NamedXAResource was the XAResource for the transaction.
                // Otherwise, wait for recoverResourceManager to be called for the actual XAResource 
                // This is a bit wasteful, but given our management of XAResources by "name", is about the best we can do.
                if (isNameInTransaction(xidNamesPair, name, xid)) {
                    log.trace("This xid was prepared from this XAResource: committing");
                    commit(xaResource, xid);
                    removeNameFromTransaction(xidNamesPair, name, true);
                } else {
                    log.trace("This xid was prepared from another XAResource, ignoring");
                }
            } else if (txManager.getXidFactory().matchesGlobalId(xid.getGlobalTransactionId())) {
                //ours, but prepare not logged
                log.trace("this xid was initiated from this tm but not prepared: rolling back");
                rollback(xaResource, xid);
            } else if (txManager.getXidFactory().matchesBranchId(xid.getBranchQualifier())) {
                //our branch, but we did not start this tx.
                TransactionImpl externalTx = externalGlobalIdMap.get(xid.getGlobalTransactionId());
                if (externalTx == null) {
                    //we did not prepare this branch, rollback.
                    log.trace("this xid is from an external transaction and was not prepared: rolling back");
                    rollback(xaResource, xid);
                } else {
                    log.trace("this xid is from an external transaction and was prepared in this tm.  Waiting for instructions from transaction originator");
                    //we prepared this branch, must wait for commit/rollback command.
                    externalTx.addBranchXid(xaResource, xid);
                }
            }
            //else we had nothing to do with this xid.
        }
        Set<XidBranchesPair> transactionsForName = nameToOurTxMap.get(name);
        if (transactionsForName != null) {
            for (XidBranchesPair xidBranchesPair : transactionsForName) {
                removeNameFromTransaction(xidBranchesPair, name, false);
            }
        }
    }

    private void commit(NamedXAResource xaResource, Xid xid) {
        doCommitOrRollback(xaResource, xid, true);
    }

    private void rollback(NamedXAResource xaResource, Xid xid) {
        doCommitOrRollback(xaResource, xid, false);
    }

    private void doCommitOrRollback(NamedXAResource xaResource, Xid xid, boolean commit) {
        try {
            if (commit) {
                xaResource.commit(xid, false);
            } else {
                xaResource.rollback(xid);
            }
        } catch (XAException e) {
            try {
                if (e.errorCode == XAException.XA_HEURRB) {
                    log.info("Transaction has been heuristically rolled back");
                    xaResource.forget(xid);
                } else if (e.errorCode == XAException.XA_HEURMIX) {
                    log.info("Transaction has been heuristically committed and rolled back");
                    xaResource.forget(xid);
                } else if (e.errorCode == XAException.XA_HEURCOM) {
                    log.info("Transaction has been heuristically committed");
                    xaResource.forget(xid);
                } else {
                    recoveryErrors.add(e);
                    log.error("Could not roll back", e);
                }
            } catch (XAException e2) {
                if (e2.errorCode == XAException.XAER_NOTA) {
                    // NOTA in response to forget, means the resource already forgot the transaction
                    // ignore
                } else {
                    recoveryErrors.add(e);
                    log.error("Could not roll back", e);
                }
            }
        }
    }

    private boolean isNameInTransaction(XidBranchesPair xidBranchesPair, String name, Xid xid) {
        for (TransactionBranchInfo transactionBranchInfo : xidBranchesPair.getBranches()) {
            if (name.equals(transactionBranchInfo.getResourceName()) && equals(xid, transactionBranchInfo.getBranchXid())) {
                return true;
            }
        }
        return false;
    }

    private boolean equals(Xid xid1, Xid xid2) {
        return xid1.getFormatId() == xid1.getFormatId()
                && Arrays.equals(xid1.getBranchQualifier(), xid2.getBranchQualifier())
                && Arrays.equals(xid1.getGlobalTransactionId(), xid2.getGlobalTransactionId());
    }

    private void removeNameFromTransaction(XidBranchesPair xidBranchesPair, String name, boolean warn) {
        int removed = 0;
        for (Iterator branches = xidBranchesPair.getBranches().iterator(); branches.hasNext();) {
            TransactionBranchInfo transactionBranchInfo = (TransactionBranchInfo) branches.next();
            if (name.equals(transactionBranchInfo.getResourceName())) {
                branches.remove();
                removed++;
            }
        }
        if (warn && removed == 0) {
            log.error("XAResource named: " + name + " returned branch xid for xid: " + xidBranchesPair.getXid() + " but was not registered with that transaction!");
        }
        if (xidBranchesPair.getBranches().isEmpty() && 0 != removed ) {
            try {
                ourXids.remove(new ByteArrayWrapper(xidBranchesPair.getXid().getGlobalTransactionId()));
                txManager.getTransactionLog().commit(xidBranchesPair.getXid(), xidBranchesPair.getMark());
            } catch (LogException e) {
                recoveryErrors.add(e);
                log.error("Could not commit", e);
            }
        }
    }

    public synchronized boolean hasRecoveryErrors() {
        return !recoveryErrors.isEmpty();
    }

    public synchronized List<Exception> getRecoveryErrors() {
        return Collections.unmodifiableList(recoveryErrors);
    }

    public synchronized boolean localRecoveryComplete() {
        return ourXids.isEmpty();
    }

    public synchronized int localUnrecoveredCount() {
        return ourXids.size();
    }

    //hard to implement.. needs ExternalTransaction to have a reference to externalXids.
//    public boolean remoteRecoveryComplete() {
//    }

    public synchronized Map<Xid, TransactionImpl> getExternalXids() {
        return new HashMap<Xid, TransactionImpl>(externalXids);
    }

    private static String toString(Xid xid) {
        if (xid instanceof XidImpl) {
            return xid.toString();
        }
        StringBuilder s = new StringBuilder();
        s.append("[Xid:class=").append(xid.getClass().getSimpleName()).append(":globalId=");
        byte[] globalId = xid.getGlobalTransactionId();
        if (globalId == null) {
            s.append("null");
        } else {
            for (int i = 0; i < globalId.length; i++) {
                s.append(Integer.toHexString(globalId[i]));
            }
            s.append(",length=").append(globalId.length);
        }
        s.append(",branchId=");
        byte[] branchId = xid.getBranchQualifier();
        if (branchId == null) {
            s.append("null");
        } else {
            for (int i = 0; i < branchId.length; i++) {
                s.append(Integer.toHexString(branchId[i]));
            }
            s.append(",length=").append(branchId.length);
        }
        s.append("]");
        return s.toString();
    }

    private static class ByteArrayWrapper {
        private final byte[] bytes;
        private final int hashCode;

        public ByteArrayWrapper(final byte[] bytes) {
            assert bytes != null;
            this.bytes = bytes;
            int hash = 0;
            for (byte aByte : bytes) {
                hash += 37 * aByte;
            }
            hashCode = hash;
        }

        public boolean equals(Object other) {
            if (other instanceof ByteArrayWrapper) {
                return Arrays.equals(bytes, ((ByteArrayWrapper)other).bytes);
            }
            return false;
        }

        public int hashCode() {
            return hashCode;
        }
    }

    private static class ExternalTransaction extends TransactionImpl {
        private final Set<String> resourceNames = new HashSet<String>();

        public ExternalTransaction(Xid xid, TransactionManagerImpl txManager, Set<TransactionBranchInfo> resourceNames) {
            super(xid, txManager);
            for (TransactionBranchInfo info: resourceNames) {
                this.resourceNames.add(info.getResourceName());
            }
        }

        public boolean hasName(String name) {
            return resourceNames.contains(name);
        }

        public void removeName(String name) {
            resourceNames.remove(name);
        }

        public void preparedCommit() throws HeuristicRollbackException, HeuristicMixedException, SystemException {
            if (!resourceNames.isEmpty()) {
                throw new SystemException("This tx does not have all resource managers online, commit not allowed yet");
            }
            super.preparedCommit();
        }

        public void rollback() throws SystemException {
            if (!resourceNames.isEmpty()) {
                throw new SystemException("This tx does not have all resource managers online, rollback not allowed yet");
            }
            super.rollback();

        }
    }
}
