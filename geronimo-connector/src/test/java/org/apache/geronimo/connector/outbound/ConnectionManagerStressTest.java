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

package org.apache.geronimo.connector.outbound;

import java.util.HashSet;

import org.apache.geronimo.connector.outbound.connectiontracking.ConnectorInstanceContextImpl;

/**
 * ???
 *
 * @version $Rev$ $Date$
 */
public class ConnectionManagerStressTest extends ConnectionManagerTestUtils {

    protected int repeatCount = 200;
    protected int threadCount = 10;
    private Object startBarrier = new Object();
    private Object stopBarrier = new Object();
    private int startedThreads = 0;
    private int stoppedThreads = 0;
    private long totalDuration = 0;
    private int slowCount = 0;
    private Object mutex = new Object();

    private Exception e = null;

    public void testNoTransactionCallOneThread() throws Throwable {
        for (int i = 0; i < repeatCount; i++) {
            defaultComponentInterceptor.invoke(connectorInstanceContext);
        }
    }

    public void testNoTransactionCallMultiThread() throws Throwable {
        startedThreads = 0;
        stoppedThreads = 0;
        for (int t = 0; t < threadCount; t++) {
            new Thread() {
                public void run() {
                    long localStartTime = 0;
                    int localSlowCount = 0;
                    try {
                        synchronized (startBarrier) {
                            ++startedThreads;
                            startBarrier.notifyAll();
                            while (startedThreads < (threadCount + 1)) {
                                startBarrier.wait();
                            }
                        }
                        localStartTime = System.currentTimeMillis();
                        for (int i = 0; i < repeatCount; i++) {
                            try {
                                long start = System.currentTimeMillis();
                                defaultComponentInterceptor.invoke(new ConnectorInstanceContextImpl(new HashSet<String>(), new HashSet<String>()));
                                long duration = System.currentTimeMillis() - start;
                                if (duration > 100) {
                                    localSlowCount++;
                                    log.debug("got a cx: " + i + ", time: " + (duration));
                                }
                            } catch (Throwable throwable) {
                                log.debug(throwable.getMessage(), throwable);
                            }
                        }
                    } catch (Exception e) {
                        log.info(e.getMessage(), e);
                        ConnectionManagerStressTest.this.e = e;
                    } finally {
                        synchronized (stopBarrier) {
                            ++stoppedThreads;
                            stopBarrier.notifyAll();
                        }
                        long localDuration = System.currentTimeMillis() - localStartTime;
                        synchronized (mutex) {
                            totalDuration += localDuration;
                            slowCount += localSlowCount;
                        }
                    }
                }
            }.start();
        }
        // Wait for all the workers to be ready..
        long startTime = 0;
        synchronized (startBarrier) {
            while (startedThreads < threadCount) startBarrier.wait();
            ++startedThreads;
            startBarrier.notifyAll();
            startTime = System.currentTimeMillis();
        }

        // Wait for all the workers to finish.
        synchronized (stopBarrier) {
            while (stoppedThreads < threadCount) stopBarrier.wait();
        }
        long duration = System.currentTimeMillis() - startTime;
        log.debug("no tx run, thread count: " + threadCount + ", connection count: " + repeatCount + ", duration: " + duration + ", total duration: " + totalDuration + ", ms per cx request: " + (totalDuration / (threadCount * repeatCount)) + ", slow cx request count: " + slowCount);
        //return startTime;
        if (e != null) {
            throw e;
        }
    }
}
