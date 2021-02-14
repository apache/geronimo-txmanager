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
import java.util.List;
import java.util.Map;

import javax.transaction.*;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.geronimo.transaction.log.UnrecoverableLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple implementation of a transaction manager.
 *
 * @version $Rev$ $Date$
 */
public class TransactionManagerImpl implements TransactionManager, UserTransaction, TransactionSynchronizationRegistry, XidImporter, MonitorableTransactionManager, RecoverableTransactionManager {
    private static final Logger log = LoggerFactory.getLogger(TransactionManagerImpl.class);
    private static final Logger recoveryLog = LoggerFactory.getLogger("RecoveryController");

    protected static final int DEFAULT_TIMEOUT = 600;
    protected static final byte[] DEFAULT_TM_ID = new byte[] {71,84,77,73,68};

    private final TransactionLog transactionLog;
    private final XidFactory xidFactory;
    private final int defaultTransactionTimeoutMilliseconds;
    private final CurrentTimeMsProvider timeProvider;
    private final ThreadLocal<Long> transactionTimeoutMilliseconds = new ThreadLocal<Long>();
    private final ThreadLocal<Transaction> threadTx = new ThreadLocal<Transaction>();
    private final ConcurrentHashMap<Transaction, Thread> associatedTransactions = new ConcurrentHashMap<Transaction, Thread>();
    final Recovery recovery;
    private final Map<String, NamedXAResourceFactory> namedXAResourceFactories = new ConcurrentHashMap<String, NamedXAResourceFactory>();
    private final CopyOnWriteArrayList<TransactionManagerMonitor> transactionAssociationListeners = new CopyOnWriteArrayList<TransactionManagerMonitor>();
    private final List<Exception> recoveryErrors = new ArrayList<Exception>();
    private final RetryScheduler retryScheduler = new ExponentialtIntervalRetryScheduler();
    // statistics
    private AtomicLong totalCommits = new AtomicLong(0);
    private AtomicLong totalRollBacks = new AtomicLong(0);
    private AtomicLong activeCount = new AtomicLong(0);

    public TransactionManagerImpl() throws XAException {
        this(DEFAULT_TIMEOUT,
                null,
                null
        );
    }

    public TransactionManagerImpl(int defaultTransactionTimeoutSeconds) throws XAException {
        this(defaultTransactionTimeoutSeconds,
                null,
                null
        );
    }

    public TransactionManagerImpl(int defaultTransactionTimeoutSeconds, TransactionLog transactionLog) throws XAException {
        this(defaultTransactionTimeoutSeconds,
                null,
                transactionLog
        );
    }

    public TransactionManagerImpl(int defaultTransactionTimeoutSeconds, XidFactory xidFactory, TransactionLog transactionLog) throws XAException {
        this(defaultTransactionTimeoutSeconds, xidFactory, transactionLog, null);
    }

    public TransactionManagerImpl(int defaultTransactionTimeoutSeconds, XidFactory xidFactory,
            TransactionLog transactionLog, CurrentTimeMsProvider timeProvider) throws XAException {
        if (defaultTransactionTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("defaultTransactionTimeoutSeconds must be positive: attempted value: " + defaultTransactionTimeoutSeconds);
        }
        this.defaultTransactionTimeoutMilliseconds = defaultTransactionTimeoutSeconds * 1000;
        this.timeProvider = timeProvider;

        if (transactionLog == null) {
            this.transactionLog = new UnrecoverableLog();
        } else {
            this.transactionLog = transactionLog;
        }

        if (xidFactory != null) {
            this.xidFactory = xidFactory;
        } else {
            this.xidFactory = new XidFactoryImpl(DEFAULT_TM_ID);
        }

        recovery = new RecoveryImpl(this);
        recovery.recoverLog();
    }

    public Transaction getTransaction() {
        return threadTx.get();
    }

    private void associate(TransactionImpl tx) throws InvalidTransactionException {
        if (tx.getStatus() == Status.STATUS_NO_TRANSACTION) {
            throw new InvalidTransactionException("Cannot resume invalid transaction: " + tx);
        } else {
            Object existingAssociation = associatedTransactions.putIfAbsent(tx, Thread.currentThread());
            if (existingAssociation != null) {
                throw new InvalidTransactionException("Specified transaction is already associated with another thread");
            }
            threadTx.set(tx);
            fireThreadAssociated(tx);
            activeCount.getAndIncrement();
        }
    }

    private void unassociate() {
        Transaction tx = getTransaction();
        if (tx != null) {
            associatedTransactions.remove(tx);
            threadTx.set(null);
            fireThreadUnassociated(tx);
            activeCount.getAndDecrement();
        }
    }

    public void setTransactionTimeout(int seconds) throws SystemException {
        if (seconds < 0) {
            throw new SystemException("transaction timeout must be positive or 0 to reset to default");
        }
        if (seconds == 0) {
            transactionTimeoutMilliseconds.set(null);
        } else {
            transactionTimeoutMilliseconds.set((long) seconds * 1000);
        }
    }

    public int getStatus() throws SystemException {
        Transaction tx = getTransaction();
        return (tx != null) ? tx.getStatus() : Status.STATUS_NO_TRANSACTION;
    }

    public void begin() throws NotSupportedException, SystemException {
        begin(getTransactionTimeoutMilliseconds(0L));
    }

    public Transaction begin(long transactionTimeoutMilliseconds) throws NotSupportedException, SystemException {
        if (getStatus() != Status.STATUS_NO_TRANSACTION) {
            throw new NotSupportedException("Nested Transactions are not supported");
        }
        TransactionImpl tx = new TransactionImpl(this, getTransactionTimeoutMilliseconds(transactionTimeoutMilliseconds), timeProvider);
//        timeoutTimer.schedule(tx, getTransactionTimeoutMilliseconds(transactionTimeoutMilliseconds));
        try {
            associate(tx);
        } catch (InvalidTransactionException e) {
            // should not be possible since we just created that transaction and no one has a reference yet
            throw (SystemException)new SystemException("Internal error: associate threw an InvalidTransactionException for a newly created transaction").initCause(e);
        }
        // Todo: Verify if this is correct thing to do. Use default timeout for next transaction.
        this.transactionTimeoutMilliseconds.set(null);
        return tx;
    }

    public Transaction suspend() throws SystemException {
        Transaction tx = getTransaction();
        if (tx != null) {
            unassociate();
        }
        return tx;
    }

    public void resume(Transaction tx) throws IllegalStateException, InvalidTransactionException, SystemException {
        if (getTransaction() != null && tx != getTransaction()) {
            throw new IllegalStateException("Thread already associated with another transaction");
        }
        if (tx != null && tx != getTransaction()) {
            if (!(tx instanceof TransactionImpl)) {
                throw new InvalidTransactionException("Cannot resume foreign transaction: " + tx);
            }

            associate((TransactionImpl) tx);
        }
    }

    public Object getResource(Object key) {
        TransactionImpl tx = getActiveTransactionImpl();
        return tx.getResource(key);
    }

    private TransactionImpl getActiveTransactionImpl() {
        TransactionImpl tx = (TransactionImpl)threadTx.get();
        if (tx == null) {
            throw new IllegalStateException("No tx on thread");
        }
        if (tx.getStatus() != Status.STATUS_ACTIVE && tx.getStatus() != Status.STATUS_MARKED_ROLLBACK) {
            throw new IllegalStateException("Transaction " + tx + " is not active");
        }
        return tx;
    }

    public boolean getRollbackOnly() {
        TransactionImpl tx = getActiveTransactionImpl();
        return tx.getRollbackOnly();
    }

    public Object getTransactionKey() {
        TransactionImpl tx = (TransactionImpl) getTransaction();
        return tx == null ? null: tx.getTransactionKey();
    }

    public int getTransactionStatus() {
        TransactionImpl tx = (TransactionImpl) getTransaction();
        return tx == null? Status.STATUS_NO_TRANSACTION: tx.getTransactionStatus();
    }

    public void putResource(Object key, Object value) {
        TransactionImpl tx = getActiveTransactionImpl();
        tx.putResource(key, value);
    }

    /**
     * jta 1.1 method so the jpa implementations can be told to flush their caches.
     * @param synchronization interposed synchronization
     */
    public void registerInterposedSynchronization(Synchronization synchronization) {
        TransactionImpl tx = getActiveTransactionImpl();
        tx.registerInterposedSynchronization(synchronization);
    }

    public void setRollbackOnly() throws IllegalStateException {
        TransactionImpl tx = (TransactionImpl) threadTx.get();
        if (tx == null) {
            throw new IllegalStateException("No transaction associated with current thread");
        }
        tx.setRollbackOnly();
    }

    public void commit() throws HeuristicMixedException, HeuristicRollbackException, IllegalStateException, RollbackException, SecurityException, SystemException {
        Transaction tx = getTransaction();
        if (tx == null) {
            throw new IllegalStateException("No transaction associated with current thread");
        }
        try {
            tx.commit();
        } finally {
            unassociate();
        }
        totalCommits.getAndIncrement();
    }

    public void rollback() throws IllegalStateException, SecurityException, SystemException {
        Transaction tx = getTransaction();
        if (tx == null) {
            throw new IllegalStateException("No transaction associated with current thread");
        }
        try {
            tx.rollback();
        } finally {
            unassociate();
        }
        totalRollBacks.getAndIncrement();
    }

    //XidImporter implementation
    public Transaction importXid(Xid xid, long transactionTimeoutMilliseconds) throws XAException, SystemException {
        if (transactionTimeoutMilliseconds < 0) {
            throw new SystemException("transaction timeout must be positive or 0 to reset to default");
        }
        return new TransactionImpl(xid, this, getTransactionTimeoutMilliseconds(transactionTimeoutMilliseconds), timeProvider);
    }

    public void commit(Transaction tx, boolean onePhase) throws XAException {
        if (onePhase) {
            try {
                tx.commit();
            } catch (HeuristicMixedException e) {
                throw (XAException) new XAException().initCause(e);
            } catch (HeuristicRollbackException e) {
                throw (XAException) new XAException().initCause(e);
            } catch (RollbackException e) {
                throw (XAException) new XAException().initCause(e);
            } catch (SecurityException e) {
                throw (XAException) new XAException().initCause(e);
            } catch (SystemException e) {
                throw (XAException) new XAException().initCause(e);
            }
        } else {
            try {
                ((TransactionImpl) tx).preparedCommit();
            } catch (HeuristicMixedException e) {
                throw (XAException) new XAException().initCause(e);
            } catch (HeuristicRollbackException e) {
                throw (XAException) new XAException().initCause(e);
            } catch (SystemException e) {
                throw (XAException) new XAException().initCause(e);
            }
        }
        totalCommits.getAndIncrement();
    }

    public void forget(Transaction tx) throws XAException {
        //TODO implement this!
    }

    public int prepare(Transaction tx) throws XAException {
        try {
            return ((TransactionImpl) tx).prepare();
        } catch (SystemException e) {
            throw (XAException) new XAException().initCause(e);
        } catch (RollbackException e) {
            throw (XAException) new XAException().initCause(e);
        }
    }

    public void rollback(Transaction tx) throws XAException {
        try {
            tx.rollback();
        } catch (IllegalStateException e) {
            throw (XAException) new XAException().initCause(e);
        } catch (SystemException e) {
            throw (XAException) new XAException().initCause(e);
        }
        totalRollBacks.getAndIncrement();
    }

    long getTransactionTimeoutMilliseconds(long transactionTimeoutMilliseconds) {
        if (transactionTimeoutMilliseconds != 0) {
            return transactionTimeoutMilliseconds;
        }
        Long timeout = this.transactionTimeoutMilliseconds.get();
        if (timeout != null) {
            return timeout;
        }
        return defaultTransactionTimeoutMilliseconds;
    }

    //Recovery
    public void recoveryError(Exception e) {
        recoveryLog.error("Recovery error: {}", e.getMessage());
        recoveryErrors.add(e);
    }

    public void registerNamedXAResourceFactory(NamedXAResourceFactory namedXAResourceFactory) {
        namedXAResourceFactories.put(namedXAResourceFactory.getName(), namedXAResourceFactory);
        new RecoverTask(retryScheduler, namedXAResourceFactory, recovery, this).run();
    }

    public void unregisterNamedXAResourceFactory(String namedXAResourceFactoryName) {
        namedXAResourceFactories.remove(namedXAResourceFactoryName);
    }

    NamedXAResourceFactory getNamedXAResourceFactory(String xaResourceName) {
        return namedXAResourceFactories.get(xaResourceName);
    }

    XidFactory getXidFactory() {
        return xidFactory;
    }

    TransactionLog getTransactionLog() {
        return transactionLog;
    }

    RetryScheduler getRetryScheduler() {
        return retryScheduler;
    }

    public Map<Xid, TransactionImpl> getExternalXids() {
        return new HashMap<Xid, TransactionImpl>(recovery.getExternalXids());
    }

    public void addTransactionAssociationListener(TransactionManagerMonitor listener) {
        transactionAssociationListeners.addIfAbsent(listener);
    }

    public void removeTransactionAssociationListener(TransactionManagerMonitor listener) {
        transactionAssociationListeners.remove(listener);
    }

    protected void fireThreadAssociated(Transaction tx) {
        for (TransactionManagerMonitor listener : transactionAssociationListeners) {
            try {
                listener.threadAssociated(tx);
            } catch (Exception e) {
                log.warn("Error calling transaction association listener", e);
            }
        }
    }

    protected void fireThreadUnassociated(Transaction tx) {
        for (TransactionManagerMonitor listener : transactionAssociationListeners) {
            try {
                listener.threadUnassociated(tx);
            } catch (Exception e) {
                log.warn("Error calling transaction association listener", e);
            }
        }
    }

    /**
     * Returns the number of active transactions.
     * @return the count of active transactions
     */
    public long getActiveCount() {
        return activeCount.longValue();
    }

    /**
     * Return the number of total commits
     * @return the number of commits since statistics were reset
     */
    public long getTotalCommits() {
        return totalCommits.longValue();
    }

    /**
     * Returns the number of total rollbacks
     * @return the number of rollbacks since statistics were reset
     */
    public long getTotalRollbacks() {
        return totalRollBacks.longValue();
    }

    /**
     * Reset statistics
     */
    public void resetStatistics() {
        totalCommits.getAndSet(0);
        totalRollBacks.getAndSet(0);
    }
}
