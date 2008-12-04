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

package org.apache.geronimo.connector.work;

import java.util.concurrent.CountDownLatch;

import javax.resource.spi.work.ExecutionContext;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkAdapter;
import javax.resource.spi.work.WorkCompletedException;
import javax.resource.spi.work.WorkEvent;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkListener;
import javax.resource.spi.work.WorkManager;
import javax.resource.spi.work.WorkRejectedException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;

import org.apache.geronimo.transaction.manager.ImportedTransactionActiveException;
import org.apache.geronimo.transaction.manager.XAWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Work wrapper providing an execution context to a Work instance.
 *
 * @version $Rev$ $Date$
 */
public class WorkerContext implements Work {

    private static final Logger log = LoggerFactory.getLogger(WorkerContext.class);

    /**
     * Null WorkListener used as the default WorkListener.
     */
    private static final WorkListener NULL_WORK_LISTENER = new WorkAdapter() {
        public void workRejected(WorkEvent event) {
            if (event.getException() != null) {
                if (event.getException() instanceof WorkCompletedException && event.getException().getCause() != null) {
                    log.error(event.getWork().toString(), event.getException().getCause());
                } else {
                    log.error(event.getWork().toString(), event.getException());
                }
            }
        }
    };

    /**
     * Priority of the thread, which will execute this work.
     */
    private int threadPriority;

    /**
     * Actual work to be executed.
     */
    private Work adaptee;

    /**
     * Indicates if this work has been accepted.
     */
    private boolean isAccepted;

    /**
     * System.currentTimeMillis() when the wrapped Work has been accepted.
     */
    private long acceptedTime;

    /**
     * Number of times that the execution of this work has been tried.
     */
    private int nbRetry;

    /**
     * Time duration (in milliseconds) within which the execution of the Work
     * instance must start.
     */
    private long startTimeOut;

    /**
     * Execution context of the actual work to be executed.
     */
    private final ExecutionContext executionContext;

    private final XAWork xaWork;

    /**
     * Listener to be notified during the life-cycle of the work treatment.
     */
    private WorkListener workListener = NULL_WORK_LISTENER;

    /**
     * Work exception, if any.
     */
    private WorkException workException;

    /**
     * A latch, which is released when the work is started.
     */
    private CountDownLatch startLatch = new CountDownLatch(1);

    /**
     * A latch, which is released when the work is completed.
     */
    private CountDownLatch endLatch = new CountDownLatch(1);

    /**
     * Create a WorkWrapper.
     *
     * @param work                      Work to be wrapped.
     * @param xaWork
     */
    public WorkerContext(Work work, XAWork xaWork) {
        adaptee = work;
        executionContext = null;
        this.xaWork = xaWork;
    }

    /**
     * Create a WorkWrapper with the specified execution context.
     *
     * @param aWork         Work to be wrapped.
     * @param aStartTimeout a time duration (in milliseconds) within which the
     *                      execution of the Work instance must start.
     * @param execContext   an object containing the execution context with which
     *                      the submitted Work instance must be executed.
     * @param workListener  an object which would be notified when the various
     *                      Work processing events (work accepted, work rejected, work started,
     */
    public WorkerContext(Work aWork,
                         long aStartTimeout,
                         ExecutionContext execContext,
                         XAWork xaWork,
                         WorkListener workListener) {
        adaptee = aWork;
        startTimeOut = aStartTimeout;
        executionContext = execContext;
        this.xaWork = xaWork;
        if (null != workListener) {
            this.workListener = workListener;
        }
    }

    /* (non-Javadoc)
     * @see javax.resource.spi.work.Work#release()
     */
    public void release() {
        adaptee.release();
    }

    /**
     * Defines the thread priority level of the thread, which will be dispatched
     * to process this work. This priority level must be the same one for a
     * given resource adapter.
     *
     * @param aPriority Priority of the thread to be used to process the wrapped
     *                  Work instance.
     */
    public void setThreadPriority(int aPriority) {
        threadPriority = aPriority;
    }

    /**
     * Gets the priority level of the thread, which will be dispatched
     * to process this work. This priority level must be the same one for a
     * given resource adapter.
     *
     * @return The priority level of the thread to be dispatched to
     *         process the wrapped Work instance.
     */
    public int getThreadPriority() {
        return threadPriority;
    }

    /**
     * Call-back method used by a Work executor in order to notify this
     * instance that the wrapped Work instance has been accepted.
     *
     * @param anObject Object on which the event initially occurred. It should
     *                 be the work executor.
     */
    public synchronized void workAccepted(Object anObject) {
        isAccepted = true;
        acceptedTime = System.currentTimeMillis();
        workListener.workAccepted(new WorkEvent(anObject,
                WorkEvent.WORK_ACCEPTED, adaptee, null));
    }

    /**
     * System.currentTimeMillis() when the Work has been accepted. This method
     * can be used to compute the duration of a work.
     *
     * @return When the work has been accepted.
     */
    public synchronized long getAcceptedTime() {
        return acceptedTime;
    }

    /**
     * Gets the time duration (in milliseconds) within which the execution of
     * the Work instance must start.
     *
     * @return Time out duration.
     */
    public long getStartTimeout() {
        return startTimeOut;
    }

    /**
     * Used by a Work executor in order to know if this work, which should be
     * accepted but not started has timed out. This method MUST be called prior
     * to retry the execution of a Work.
     *
     * @return true if the Work has timed out and false otherwise.
     */
    public synchronized boolean isTimedOut() {
        assert isAccepted: "The work is not accepted.";
        // A value of 0 means that the work never times out.
        //??? really?
        if (0 == startTimeOut || startTimeOut == WorkManager.INDEFINITE) {
            return false;
        }
        boolean isTimeout = acceptedTime + startTimeOut > 0 &&
                System.currentTimeMillis() > acceptedTime + startTimeOut;
        if (log.isDebugEnabled()) {
            log.debug(this
                    + " accepted at "
                    + acceptedTime
                    + (isTimeout ? " has timed out." : " has not timed out. ")
                    + nbRetry
                    + " retries have been performed.");
        }
        if (isTimeout) {
            workException = new WorkRejectedException(this + " has timed out.",
                    WorkException.START_TIMED_OUT);
            workListener.workRejected(new WorkEvent(this,
                    WorkEvent.WORK_REJECTED,
                    adaptee,
                    workException));
            return true;
        }
        nbRetry++;
        return isTimeout;
    }

    /**
     * Gets the WorkException, if any, thrown during the execution.
     *
     * @return WorkException, if any.
     */
    public synchronized WorkException getWorkException() {
        return workException;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
        if (isTimedOut()) {
            // In case of a time out, one releases the start and end latches
            // to prevent a dead-lock.
            startLatch.countDown();
            endLatch.countDown();
            return;
        }
        // Implementation note: the work listener is notified prior to release
        // the start lock. This behavior is intentional and seems to be the
        // more conservative.
        workListener.workStarted(new WorkEvent(this, WorkEvent.WORK_STARTED, adaptee, null));
        startLatch.countDown();
        //Implementation note: we assume this is being called without an interesting TransactionContext,
        //and ignore/replace whatever is associated with the current thread.
        try {
            if (executionContext == null || executionContext.getXid() == null) {
                adaptee.run();
            } else {
                try {
                    long transactionTimeout = executionContext.getTransactionTimeout();
                    //translate -1 value to 0 to indicate default transaction timeout.
                    xaWork.begin(executionContext.getXid(), transactionTimeout < 0 ? 0 : transactionTimeout);
                } catch (XAException e) {
                    throw new WorkCompletedException("Transaction import failed for xid " + executionContext.getXid(), WorkCompletedException.TX_RECREATE_FAILED).initCause(e);
                } catch (InvalidTransactionException e) {
                    throw new WorkCompletedException("Transaction import failed for xid " + executionContext.getXid(), WorkCompletedException.TX_RECREATE_FAILED).initCause(e);
                } catch (SystemException e) {
                    throw new WorkCompletedException("Transaction import failed for xid " + executionContext.getXid(), WorkCompletedException.TX_RECREATE_FAILED).initCause(e);
                } catch (ImportedTransactionActiveException e) {
                    throw new WorkCompletedException("Transaction already active for xid " + executionContext.getXid(), WorkCompletedException.TX_CONCURRENT_WORK_DISALLOWED).initCause(e);
                }
                try {
                    adaptee.run();
                } finally {
                    xaWork.end(executionContext.getXid());
                }

            }
            workListener.workCompleted(new WorkEvent(this, WorkEvent.WORK_COMPLETED, adaptee, null));
        } catch (Throwable e) {
            workException = (WorkException) (e instanceof WorkCompletedException ? e : new WorkCompletedException("Unknown error", WorkCompletedException.UNDEFINED).initCause(e));
            workListener.workCompleted(new WorkEvent(this, WorkEvent.WORK_REJECTED, adaptee,
                    workException));
        } finally {
            endLatch.countDown();
        }
    }

    /**
     * Provides a latch, which can be used to wait the start of a work
     * execution.
     *
     * @return Latch that a caller can acquire to wait for the start of a
     *         work execution.
     */
    public synchronized CountDownLatch provideStartLatch() {
        return startLatch;
    }

    /**
     * Provides a latch, which can be used to wait the end of a work
     * execution.
     *
     * @return Latch that a caller can acquire to wait for the end of a
     *         work execution.
     */
    public synchronized CountDownLatch provideEndLatch() {
        return endLatch;
    }

    public String toString() {
        return "Work :" + adaptee;
    }

}
