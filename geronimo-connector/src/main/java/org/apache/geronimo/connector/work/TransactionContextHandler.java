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


package org.apache.geronimo.connector.work;

import javax.resource.spi.work.TransactionContext;
import javax.resource.spi.work.WorkCompletedException;
import javax.resource.spi.work.WorkContext;
import javax.transaction.xa.XAException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.SystemException;

import org.apache.geronimo.transaction.manager.XAWork;
import org.apache.geronimo.transaction.manager.ImportedTransactionActiveException;

/**
 * @version $Rev$ $Date$
 */
public class TransactionContextHandler implements WorkContextHandler<TransactionContext>{

    private final XAWork xaWork;

    public TransactionContextHandler(XAWork xaWork) {
        this.xaWork = xaWork;
    }

    public void before(TransactionContext workContext) throws WorkCompletedException {
        if (workContext.getXid() != null) {
            try {
                long transactionTimeout = workContext.getTransactionTimeout();
                //translate -1 value to 0 to indicate default transaction timeout.
                xaWork.begin(workContext.getXid(), transactionTimeout < 0 ? 0 : transactionTimeout);
            } catch (XAException e) {
                throw (WorkCompletedException)new WorkCompletedException("Transaction import failed for xid " + workContext.getXid(), WorkCompletedException.TX_RECREATE_FAILED).initCause(e);
            } catch (InvalidTransactionException e) {
                throw (WorkCompletedException)new WorkCompletedException("Transaction import failed for xid " + workContext.getXid(), WorkCompletedException.TX_RECREATE_FAILED).initCause(e);
            } catch (SystemException e) {
                throw (WorkCompletedException)new WorkCompletedException("Transaction import failed for xid " + workContext.getXid(), WorkCompletedException.TX_RECREATE_FAILED).initCause(e);
            } catch (ImportedTransactionActiveException e) {
                throw (WorkCompletedException)new WorkCompletedException("Transaction already active for xid " + workContext.getXid(), WorkCompletedException.TX_CONCURRENT_WORK_DISALLOWED).initCause(e);
            }
        }
    }

    public void after(TransactionContext workContext) throws WorkCompletedException {
        if (workContext.getXid() != null) {
            try {
                xaWork.end(workContext.getXid());
            } catch (XAException e) {
                throw (WorkCompletedException)new WorkCompletedException("Transaction end failed for xid " + workContext.getXid(), WorkCompletedException.TX_RECREATE_FAILED).initCause(e);
            } catch (SystemException e) {
                throw (WorkCompletedException)new WorkCompletedException("Transaction end failed for xid " + workContext.getXid(), WorkCompletedException.TX_RECREATE_FAILED).initCause(e);
            }
        }
    }

    public boolean supports(Class<? extends WorkContext> clazz) {
        return TransactionContext.class.isAssignableFrom(clazz);
    }

    public boolean required() {
        return false;
    }
}
