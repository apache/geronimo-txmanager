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

import java.util.Iterator;
import java.util.List;

import javax.transaction.Status;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

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
    private final RetryScheduler retryScheduler;
    private final TransactionLog txLog;
    private int count = 0;
    private int status;
    private XAException cause;
    private boolean evercommit;

    public CommitTask(Xid xid, List<TransactionBranch> rms, Object logMark, RetryScheduler retryScheduler, TransactionLog txLog) {
        this.xid = xid;
        this.rms = rms;
        this.logMark = logMark;
        this.retryScheduler = retryScheduler;
        this.txLog = txLog;
    }

    public void run() {
        synchronized (this) {
            status = Status.STATUS_COMMITTING;
        }
        for (Iterator<TransactionBranch> i = rms.iterator(); i.hasNext();) {
            TransactionBranch manager = i.next();
            try {
                try {
                    manager.getCommitter().commit(manager.getBranchId(), false);
                    i.remove();
                } catch (XAException e) {
                    log.error("Unexpected exception committing " + manager.getCommitter() + "; continuing to commit other RMs", e);

                    if (e.errorCode == XAException.XA_HEURRB) {
                        i.remove();
                        log.info("Transaction has been heuristically rolled back");
                        cause = e;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_HEURMIX) {
                        i.remove();
                        log.info("Transaction has been heuristically committed and rolled back");
                        cause = e;
                        evercommit = true;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_HEURCOM) {
                        i.remove();
                        // let's not throw an exception as the transaction has been committed
                        log.info("Transaction has been heuristically committed");
                        evercommit = true;
                        manager.getCommitter().forget(manager.getBranchId());
                    } else if (e.errorCode == XAException.XA_RETRY) {
                        // do nothing, retry later
                    } else {
                        //nothing we can do about it.... keep trying
                        i.remove();
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
        //if all resources were read only, we didn't write a prepare record.
        if (rms.isEmpty() && status == Status.STATUS_COMMITTING) {
            try {
                txLog.commit(xid, logMark);
                synchronized (this) {
                    status = Status.STATUS_COMMITTED;
                }
            } catch (LogException e) {
                log.error("Unexpected exception logging commit completion for xid " + xid, e);
                cause = (XAException) new XAException("Unexpected error logging commit completion for xid " + xid).initCause(e);
            }
        } else {
            retryScheduler.retry(this, count++);
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
