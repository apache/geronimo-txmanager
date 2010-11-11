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

import java.util.Timer;
import java.util.TimerTask;

/**
 * @version $Rev$ $Date$
 */
public class ExponentialtIntervalRetryScheduler implements RetryScheduler{

    private final Timer timer = new Timer("RetryTimer", true);

    private final int base = 2;

    public void retry(Runnable task, int count) {
        long interval = Math.round(Math.pow(base, count)) * 1000;
        timer.schedule(new TaskWrapper(task), interval);
    }

    private static class TaskWrapper extends TimerTask {

        private final Runnable delegate;

        private TaskWrapper(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            delegate.run();
        }
    }
}
