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

import java.util.concurrent.Executor;
import java.util.List;
import java.util.Collections;

import javax.resource.spi.work.ExecutionContext;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkCompletedException;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkListener;
import javax.resource.spi.work.WorkManager;

import org.apache.geronimo.connector.work.pool.ScheduleWorkExecutor;
import org.apache.geronimo.connector.work.pool.StartWorkExecutor;
import org.apache.geronimo.connector.work.pool.SyncWorkExecutor;
import org.apache.geronimo.connector.work.pool.WorkExecutor;

/**
 * WorkManager implementation which uses under the cover three WorkExecutorPool
 * - one for each synchronization policy - in order to dispatch the submitted
 * Work instances.
 * <P>
 * A WorkManager is a component of the JCA specifications, which allows a
 * Resource Adapter to submit tasks to an Application Server for execution.
 *
 * @version $Rev$ $Date$
 */
public class GeronimoWorkManager implements WorkManager {

//    private final static int DEFAULT_POOL_SIZE = 10;

    /**
     * Pool of threads used by this WorkManager in order to process
     * the Work instances submitted via the doWork methods.
     */
    private Executor syncWorkExecutorPool;

    /**
     * Pool of threads used by this WorkManager in order to process
     * the Work instances submitted via the startWork methods.
     */
    private Executor startWorkExecutorPool;

    /**
     * Pool of threads used by this WorkManager in order to process
     * the Work instances submitted via the scheduleWork methods.
     */
    private Executor scheduledWorkExecutorPool;

    private final List<InflowContextHandler> inflowContextHandlers;


    private final WorkExecutor scheduleWorkExecutor = new ScheduleWorkExecutor();
    private final WorkExecutor startWorkExecutor = new StartWorkExecutor();
    private final WorkExecutor syncWorkExecutor = new SyncWorkExecutor();

    /**
     * Create a WorkManager.
     */
    public GeronimoWorkManager() {
        this(null, null, null, null);
    }

    public GeronimoWorkManager(Executor sync, Executor start, Executor sched, List<InflowContextHandler> inflowContextHandlers) {
        syncWorkExecutorPool = sync;
        startWorkExecutorPool = start;
        scheduledWorkExecutorPool = sched;
        this.inflowContextHandlers = inflowContextHandlers == null? Collections.<InflowContextHandler>emptyList(): inflowContextHandlers;
    }

    public void doStart() throws Exception {
    }

    public void doStop() throws Exception {
    }

    public void doFail() {
        try {
            doStop();
        } catch (Exception e) {
            //TODO what to do?
        }
    }

    public Executor getSyncWorkExecutorPool() {
        return syncWorkExecutorPool;
    }

    public Executor getStartWorkExecutorPool() {
        return startWorkExecutorPool;
    }

    public Executor getScheduledWorkExecutorPool() {
        return scheduledWorkExecutorPool;
    }

    /* (non-Javadoc)
    * @see javax.resource.spi.work.WorkManager#doWork(javax.resource.spi.work.Work)
    */
    public void doWork(Work work) throws WorkException {
        executeWork(new WorkerContext(work, inflowContextHandlers), syncWorkExecutor, syncWorkExecutorPool);
    }

    /* (non-Javadoc)
     * @see javax.resource.spi.work.WorkManager#doWork(javax.resource.spi.work.Work, long, javax.resource.spi.work.ExecutionContext, javax.resource.spi.work.WorkListener)
     */
    public void doWork(
            Work work,
            long startTimeout,
            ExecutionContext execContext,
            WorkListener workListener)
            throws WorkException {
        WorkerContext workWrapper =
                new WorkerContext(work, startTimeout, execContext, workListener, inflowContextHandlers);
        workWrapper.setThreadPriority(Thread.currentThread().getPriority());
        executeWork(workWrapper, syncWorkExecutor, syncWorkExecutorPool);
    }

    /* (non-Javadoc)
     * @see javax.resource.spi.work.WorkManager#startWork(javax.resource.spi.work.Work)
     */
    public long startWork(Work work) throws WorkException {
        WorkerContext workWrapper = new WorkerContext(work, inflowContextHandlers);
        workWrapper.setThreadPriority(Thread.currentThread().getPriority());
        executeWork(workWrapper, startWorkExecutor, startWorkExecutorPool);
        return System.currentTimeMillis() - workWrapper.getAcceptedTime();
    }

    /* (non-Javadoc)
     * @see javax.resource.spi.work.WorkManager#startWork(javax.resource.spi.work.Work, long, javax.resource.spi.work.ExecutionContext, javax.resource.spi.work.WorkListener)
     */
    public long startWork(
            Work work,
            long startTimeout,
            ExecutionContext execContext,
            WorkListener workListener)
            throws WorkException {
        WorkerContext workWrapper =
                new WorkerContext(work, startTimeout, execContext, workListener, inflowContextHandlers);
        workWrapper.setThreadPriority(Thread.currentThread().getPriority());
        executeWork(workWrapper, startWorkExecutor, startWorkExecutorPool);
        return System.currentTimeMillis() - workWrapper.getAcceptedTime();
    }

    /* (non-Javadoc)
     * @see javax.resource.spi.work.WorkManager#scheduleWork(javax.resource.spi.work.Work)
     */
    public void scheduleWork(Work work) throws WorkException {
        WorkerContext workWrapper = new WorkerContext(work, inflowContextHandlers);
        workWrapper.setThreadPriority(Thread.currentThread().getPriority());
        executeWork(workWrapper, scheduleWorkExecutor, scheduledWorkExecutorPool);
    }

    /* (non-Javadoc)
     * @see javax.resource.spi.work.WorkManager#scheduleWork(javax.resource.spi.work.Work, long, javax.resource.spi.work.ExecutionContext, javax.resource.spi.work.WorkListener)
     */
    public void scheduleWork(
            Work work,
            long startTimeout,
            ExecutionContext execContext,
            WorkListener workListener)
            throws WorkException {
        WorkerContext workWrapper =
                new WorkerContext(work, startTimeout, execContext, workListener, inflowContextHandlers);
        workWrapper.setThreadPriority(Thread.currentThread().getPriority());
        executeWork(workWrapper, scheduleWorkExecutor, scheduledWorkExecutorPool);
    }

    /**
     * Execute the specified Work.
     *
     * @param work Work to be executed.
     *
     * @exception WorkException Indicates that the Work execution has been
     * unsuccessful.
     */
    private void executeWork(WorkerContext work, WorkExecutor workExecutor, Executor pooledExecutor) throws WorkException {
        work.workAccepted(this);
        try {
            workExecutor.doExecute(work, pooledExecutor);
            WorkException exception = work.getWorkException();
            if (null != exception) {
                throw exception;
            }
        } catch (InterruptedException e) {
            WorkCompletedException wcj = new WorkCompletedException(
                    "The execution has been interrupted.", e);
            wcj.setErrorCode(WorkException.INTERNAL);
            throw wcj;
        }
    }

}
