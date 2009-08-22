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

package org.apache.geronimo.connector;

import java.util.Timer;
import java.util.Collections;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.resource.spi.XATerminator;
import javax.resource.spi.work.WorkManager;

import junit.framework.TestCase;
import org.apache.geronimo.connector.work.GeronimoWorkManager;
import org.apache.geronimo.connector.work.TransactionContextHandler;
import org.apache.geronimo.connector.work.WorkContextHandler;
import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.apache.geronimo.transaction.manager.XAWork;

/**
 * Unit tests for {@link GeronimoBootstrapContext}
 * @version $Rev$ $Date$
 */
public class BootstrapContextTest extends TestCase {
    ThreadPoolExecutor pool;

    protected void setUp() throws Exception {
        super.setUp();

        XAWork xaWork = new GeronimoTransactionManager();
        int poolSize = 1;
        int keepAliveTime = 30000;
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            poolSize, // core size
            poolSize, // max size
            keepAliveTime, TimeUnit.MILLISECONDS,
            new SynchronousQueue());

        pool.setRejectedExecutionHandler(new WaitWhenBlockedPolicy());
        pool.setThreadFactory(new ThreadPoolThreadFactory("Connector Test", getClass().getClassLoader()));
    }

    private static class WaitWhenBlockedPolicy
        implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) throws RejectedExecutionException {
            try {
                executor.getQueue().put(r);
            }
            catch (InterruptedException e) {
                throw new RejectedExecutionException(e);
            }
        }
    }
    private static final class ThreadPoolThreadFactory implements ThreadFactory {
        private final String poolName;
        private final ClassLoader classLoader;

        private int nextWorkerID = 0;

        public ThreadPoolThreadFactory(String poolName, ClassLoader classLoader) {
            this.poolName = poolName;
            this.classLoader = classLoader;
        }

        public Thread newThread(Runnable arg0) {
            Thread thread = new Thread(arg0, poolName + " " + getNextWorkerID());
            thread.setContextClassLoader(classLoader);
            return thread;
        }

        private synchronized int getNextWorkerID() {
            return nextWorkerID++;
        }
    }

    /**
     * Tests get and set work manager
     */
    public void testGetSetWorkManager() throws Exception {
        GeronimoTransactionManager transactionManager = new GeronimoTransactionManager();
        TransactionContextHandler txWorkContextHandler = new TransactionContextHandler(transactionManager);
        GeronimoWorkManager manager = new GeronimoWorkManager(pool, pool, pool, Collections.<WorkContextHandler>singletonList(txWorkContextHandler));
        GeronimoBootstrapContext context = new GeronimoBootstrapContext(manager, transactionManager);
        WorkManager wm = context.getWorkManager();

        assertSame("Make sure it is the same object", manager, wm);
    }

    /**
     * Tests get and set XATerminator
     */
    public void testGetSetXATerminator() throws Exception {
        GeronimoTransactionManager transactionManager = new GeronimoTransactionManager();
        TransactionContextHandler txWorkContextHandler = new TransactionContextHandler(transactionManager);
        GeronimoWorkManager manager = new GeronimoWorkManager(pool, pool, pool, Collections.<WorkContextHandler>singletonList(txWorkContextHandler));
        GeronimoBootstrapContext context = new GeronimoBootstrapContext(manager, transactionManager);
        XATerminator xat = context.getXATerminator();

        assertSame("Make sure it is the same object", transactionManager, xat);
    }

    /**
     * Tests getTimer
     */
    public void testGetTimer() throws Exception {
        GeronimoBootstrapContext context = new GeronimoBootstrapContext(null, null);
        Timer t = context.createTimer();
        assertNotNull("Object is not null", t);
    }

}
