/**
 *   Licensed to the Apache Software Foundation (ASF) under one or more
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

package org.apache.geronimo.connector.work;

import java.lang.reflect.Constructor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.resource.spi.work.ExecutionContext;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkEvent;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkListener;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.apache.geronimo.transaction.manager.XAWork;

/**
 * Timing is crucial for this test case, which focuses on the synchronization
 * specificities of the doWork, startWork and scheduleWork.
 *
 * @version $Rev$ $Date$
 */
public class PooledWorkManagerTest extends TestCase {

    private GeronimoWorkManager workManager;

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


        workManager = new GeronimoWorkManager(pool, pool, pool, xaWork);
        workManager.doStart();
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

    public void testDoWork() throws Exception {
        int nbThreads = 2;
        AbstractDummyWork threads[] = helperTest(DummyDoWork.class, nbThreads, 500, 600);
        int nbStopped = 0;
        int nbTimeout = 0;
        for (int i = 0; i < threads.length; i++) {
            if ( null != threads[i].listener.completedEvent ) {
                nbStopped++;
            } else if ( null != threads[i].listener.rejectedEvent ) {
                assertTrue("Should be a time out exception.",
                    threads[i].listener.rejectedEvent.getException().
                    getErrorCode() == WorkException.START_TIMED_OUT);
                nbTimeout++;
            } else {
                fail("WORK_COMPLETED or WORK_REJECTED expected");
            }
        }
        assertEquals("Wrong number of works in the WORK_COMPLETED state", 1, nbStopped);
        assertEquals("Wrong number of works in the START_TIMED_OUT state", 1, nbTimeout);
    }

    public void testStartWork() throws Exception {
        AbstractDummyWork threads[] = helperTest(DummyStartWork.class, 2, 10000, 100);
        int nbStopped = 0;
        int nbStarted = 0;
        for (int i = 0; i < threads.length; i++) {
            if ( null != threads[i].listener.completedEvent ) {
                nbStopped++;
            } else if ( null != threads[i].listener.startedEvent ) {
                nbStarted++;
            } else {
                fail("WORK_COMPLETED or WORK_STARTED expected");
            }
        }
        assertEquals("At least one work should be in the WORK_COMPLETED state.", 1, nbStopped);
        assertEquals("At least one work should be in the WORK_STARTED state.", 1, nbStarted);
    }

    public void testScheduleWork() throws Exception {
        AbstractDummyWork threads[] =
            helperTest(DummyScheduleWork.class, 3, 10000, 100);
        int nbAccepted = 0;
        int nbStarted = 0;

        for (int i = 0; i < threads.length; i++) {
            if ( null != threads[i].listener.acceptedEvent ) {
                nbAccepted++;
            } else if ( null != threads[i].listener.startedEvent ) {
                nbStarted++;
            } else {
                fail("WORK_ACCEPTED or WORK_STARTED expected");
            }
        }

        assertTrue("At least one work should be in the WORK_ACCEPTED state.", nbAccepted > 0);
    }

    public void testLifecycle() throws Exception {
        testDoWork();
        workManager.doStop();
        workManager.doStart();
        testDoWork();
    }

    private AbstractDummyWork[] helperTest(Class aWork, int nbThreads,
                                           int aTimeOut, int aTempo)
        throws Exception {
        Constructor constructor = aWork.getConstructor(
            new Class[]{PooledWorkManagerTest.class, String.class,
                int.class, int.class});
        AbstractDummyWork rarThreads[] = new AbstractDummyWork[nbThreads];
        for (int i = 0; i < nbThreads; i++) {
            rarThreads[i] = (AbstractDummyWork)
                constructor.newInstance(
                    new Object[]{this, "Work" + i,
                        new Integer(aTimeOut), new Integer(aTempo)});
        }
        for (int i = 0; i < nbThreads; i++) {
            rarThreads[i].start();
        }
        for (int i = 0; i < nbThreads; i++) {
            rarThreads[i].join();
        }
        return rarThreads;
    }

    public abstract class AbstractDummyWork extends Thread {
        public final DummyWorkListener listener;
        protected final  String name;
        private final int timeout;
        private final int tempo;
        public AbstractDummyWork(String aName, int aTimeOut, int aTempo) {
            listener = new DummyWorkListener();
            timeout = aTimeOut;
            tempo = aTempo;
            name = aName;
        }
        public void run() {
            try {
                perform(new DummyWork(name, tempo), timeout, null, listener);
            } catch (Exception e) {
//                log.debug(e.getMessage(), e);
            }
        }

        protected abstract void perform(Work work,
                                        long startTimeout,
                                        ExecutionContext execContext,
                                        WorkListener workListener) throws Exception;
    }

    public class DummyDoWork extends AbstractDummyWork {
        public DummyDoWork(String aName, int aTimeOut, int aTempo) {
            super(aName, aTimeOut, aTempo);
        }

        protected void perform(Work work,
                               long startTimeout,
                               ExecutionContext execContext,
                               WorkListener workListener) throws Exception {
            workManager.doWork(work, startTimeout, execContext, workListener);
        }
    }

    public class DummyStartWork extends AbstractDummyWork {
        public DummyStartWork(String aName, int aTimeOut, int aTempo) {
            super(aName, aTimeOut, aTempo);
        }

        protected void perform(Work work,
                               long startTimeout,
                               ExecutionContext execContext,
                               WorkListener workListener) throws Exception {
            workManager.startWork(work, startTimeout, execContext, workListener);
        }
    }

    public class DummyScheduleWork extends AbstractDummyWork {
        public DummyScheduleWork(String aName, int aTimeOut, int aTempo) {
            super(aName, aTimeOut, aTempo);
        }

        protected void perform(Work work,
                               long startTimeout,
                               ExecutionContext execContext,
                               WorkListener workListener) throws Exception {
            workManager.scheduleWork(work, startTimeout, execContext, workListener);
        }
    }

    public static class DummyWork implements Work {
        private Log log = LogFactory.getLog(getClass());

        private final String name;
        private final int tempo;

        public DummyWork(String aName, int aTempo) {
            name = aName;
            tempo = aTempo;
        }

        public void release() {
        }

        public void run() {
            try {
                Thread.sleep(tempo);
            } catch (InterruptedException e) {
                log.debug(e.getMessage(), e);
            }
        }

        public String toString() {
            return name;
        }
    }

    public static class DummyWorkListener implements WorkListener {
        private Log log = LogFactory.getLog(getClass());

        public WorkEvent acceptedEvent;
        public WorkEvent rejectedEvent;
        public WorkEvent startedEvent;
        public WorkEvent completedEvent;

        public void workAccepted(WorkEvent e) {
            acceptedEvent = e;
            log.debug("accepted: " + e);
        }

        public void workRejected(WorkEvent e) {
            rejectedEvent = e;
            log.debug("rejected: " + e);
        }

        public void workStarted(WorkEvent e) {
            startedEvent = e;
            log.debug("started: " + e);
        }

        public void workCompleted(WorkEvent e) {
            completedEvent = e;
            log.debug("completed: " + e);
        }
    }
}
