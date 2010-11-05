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
public class CommitTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(CommitTask.class);
    private final Xid xid;
    private final List<TransactionBranch> rms;
    private final Object logMark;
    private final TransactionManagerImpl txManager;
    private int count = 0;
    private int status;
    private XAException cause;
    private boolean evercommit;

    public CommitTask(Xid xid, List<TransactionBranch> rms, Object logMark, TransactionManagerImpl txManager) {
        this.xid = xid;
        this.rms = rms;
        this.logMark = logMark;
        this.txManager = txManager;
    }

    public void run() {
        synchronized (this) {
            status = Status.STATUS_COMMITTING;
        }
        for (int index = 0; index < rms.size(); ) {
            TransactionBranch manager = rms.get(index);
            try {
                try {
                    manager.getCommitter().commit(manager.getBranchId(), false);
                    remove(index);
                    evercommit = true;
                } catch (XAException e) {
                    log.error("Unexpected exception committing " + manager.getCommitter() + "; continuing to commit other RMs", e);

                    if (e.errorCode == XAException.XA_HEURRB) {
                        remove(index);
                        log.info("Transaction has been heuristically rolled back");
                        cause = e;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_HEURMIX) {
                        remove(index);
                        log.info("Transaction has been heuristically committed and rolled back");
                        cause = e;
                        evercommit = true;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_HEURCOM) {
                        remove(index);
                        // let's not throw an exception as the transaction has been committed
                        log.info("Transaction has been heuristically committed");
                        evercommit = true;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_RETRY) {
                        // do nothing, retry later
                        index++;
                    } else if (e.errorCode == XAException.XAER_RMFAIL) {
                        //refresh the xa resource from the NamedXAResourceFactory
                        if (manager.getCommitter() instanceof NamedXAResource) {
                            String xaResourceName = manager.getResourceName();
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
                        //at least RMERR, which we can do nothing about
                        //nothing we can do about it.... keep trying
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
                txManager.getTransactionLog().commit(xid, logMark);
                synchronized (this) {
                    status = Status.STATUS_COMMITTED;
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

    public boolean isEvercommit() {
        return evercommit;
    }

    public int getStatus() {
        return status;
    }

}
