/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.geronimo.transaction.manager;

import java.util.List;

import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;
import org.apache.geronimo.transaction.manager.TransactionImpl.ReturnableTransactionBranch;
import org.apache.geronimo.transaction.manager.TransactionImpl.TransactionBranch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Rev$ $Date$
 */
public class RollbackTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RollbackTask.class);
    private final Xid xid;
    private final List<TransactionBranch> rms;
    private final Object logMark;
    private final TransactionManagerImpl txManager;
    private int count = 0;
    private int status;
    private XAException cause;
    private boolean everRolledBack;

    public RollbackTask(Xid xid, List<TransactionBranch> rms, Object logMark, TransactionManagerImpl txManager) {
        this.xid = xid;
        this.rms = rms;
        this.logMark = logMark;
        this.txManager = txManager;
    }

    public void run() {
        synchronized (this) {
            status = Status.STATUS_ROLLING_BACK;
        }
        for (int index = 0; index < rms.size(); ) {
            TransactionBranch manager = rms.get(index);
            try {
                try {
                    manager.getCommitter().rollback(manager.getBranchId());
                    remove(index);
                    everRolledBack = true;
                } catch (XAException e) {
                    log.error("Unexpected exception committing " + manager.getCommitter() + "; continuing to commit other RMs", e);

                    if (e.errorCode == XAException.XA_HEURRB) {
                        remove(index);
                        // let's not throw an exception as the transaction has been rolled back
                        log.info("Transaction has been heuristically rolled back");
                        everRolledBack = true;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_HEURMIX) {
                        remove(index);
                        log.info("Transaction has been heuristically committed and rolled back");
                        everRolledBack = true;
                        cause = e;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_HEURCOM) {
                        remove(index);
                        log.info("Transaction has been heuristically committed");
                        cause = e;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_RETRY) {
                        // do nothing, retry later
                        index++;
                    } else if (e.errorCode == XAException.XAER_RMFAIL) {
                        //refresh the xa resource from the NamedXAResourceFactory
                        if (manager.getCommitter() instanceof NamedXAResource) {
                            String xaResourceName = ((NamedXAResource)manager.getCommitter()).getName();
                            NamedXAResourceFactory namedXAResourceFactory = txManager.getNamedXAResourceFactory(xaResourceName);
                            if (namedXAResourceFactory != null) {
                                try {
                                    TransactionBranch newManager = new ReturnableTransactionBranch(manager.getBranchXid(), namedXAResourceFactory);
                                    remove(index);
                                    rms.add(index, newManager);
                                    //loop will try this one again immediately.
                                } catch (SystemException e1) {
                                    //try again later
                                    index++;
                                }
                            } else {
                                //else hope NamedXAResourceFactory reappears soon.
                                index++;
                            }
                        } else {
                            //no hope
                            remove(index);
                            cause = e;
                        }
                    } else {
                        //at least these error codes:
                        // XAException.XA_RMERR
                        // XAException.XA_RBROLLBACK
                        // XAException.XAER_NOTA
                        //nothing we can do about it
                        remove(index);
                        cause = e;
                    }
                }
            } catch (XAException e) {
                if (e.errorCode == XAException.XAER_NOTA) {
                    // NOTA in response to forget, means the resource already forgot the transaction
                    // ignore
                } else {
                    cause = e;
                }
            }
        }

        if (rms.isEmpty()) {
            try {
                if (logMark != null) {
                    txManager.getTransactionLog().rollback(xid, logMark);
                }
                synchronized (this) {
                    status = Status.STATUS_ROLLEDBACK;
                }
            } catch (LogException e) {
                log.error("Unexpected exception logging commit completion for xid " + xid, e);
                cause = (XAException) new XAException("Unexpected error logging commit completion for xid " + xid).initCause(e);
            }
        } else {
            synchronized (this) {
                status = Status.STATUS_UNKNOWN;
            }
            txManager.getRetryScheduler().retry(this, count++);
        }
    }

    private void remove(int index) {
        TransactionBranch manager = rms.remove(index);
        if (manager instanceof ReturnableTransactionBranch) {
            ((ReturnableTransactionBranch)manager).returnXAResource();
        }
    }

    public XAException getCause() {
        return cause;
    }

    public boolean isEverRolledBack() {
        return everRolledBack;
    }

    public int getStatus() {
        return status;
    }
}
