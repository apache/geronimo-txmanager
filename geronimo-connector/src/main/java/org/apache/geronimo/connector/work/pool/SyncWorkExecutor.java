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

package org.apache.geronimo.connector.work.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import jakarta.resource.spi.work.WorkException;

import org.apache.geronimo.connector.work.WorkerContext;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class SyncWorkExecutor implements WorkExecutor {

    public void doExecute(WorkerContext work, Executor executor)
            throws WorkException, InterruptedException {
        CountDownLatch latch = work.provideEndLatch();
        executor.execute(new NamedRunnable("A J2EE Connector", work));
        latch.await();
    }

}
