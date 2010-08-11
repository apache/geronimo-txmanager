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

import java.util.TimerTask;

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;

/**
 * @version $Rev$ $Date$
 */
public class RecoverTask implements Runnable {

    private final RetryScheduler retryScheduler;
    private final NamedXAResourceFactory namedXAResourceFactory;
    private final Recovery recovery;
    private final RecoverableTransactionManager recoverableTransactionManager;
    private int count = 0;

    public RecoverTask(RetryScheduler retryScheduler, NamedXAResourceFactory namedXAResourceFactory, Recovery recovery, RecoverableTransactionManager recoverableTransactionManager) {
        this.retryScheduler = retryScheduler;
        this.namedXAResourceFactory = namedXAResourceFactory;
        this.recovery = recovery;
        this.recoverableTransactionManager = recoverableTransactionManager;
    }

//    @Override
    public void run() {
        try {
            NamedXAResource namedXAResource = namedXAResourceFactory.getNamedXAResource();
            try {
                recovery.recoverResourceManager(namedXAResource);
            } finally {
                namedXAResourceFactory.returnNamedXAResource(namedXAResource);
            }
            return;
        } catch (XAException e) {
            recoverableTransactionManager.recoveryError(e);
        } catch (SystemException e) {
            recoverableTransactionManager.recoveryError(e);
        }
        retryScheduler.retry(this, count++);
    }
}
