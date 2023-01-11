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

import jakarta.resource.spi.work.ExecutionContext;
import jakarta.resource.spi.work.Work;
import jakarta.resource.spi.work.WorkException;
import jakarta.resource.spi.work.WorkListener;
import jakarta.resource.spi.work.WorkManager;

/**
 * Dummy implementation of WorkManager interface for use in
 * {@link BootstrapContextTest}
 * @version $Rev$ $Date$
 */
public class MockWorkManager
        implements WorkManager {

    private String id = null;

    /** Creates a new instance of MockWorkManager */
    public MockWorkManager(String id) {
        this.id = id;
    }

    public void doWork(Work work) throws WorkException {
    }

    public void doWork(Work work,
            long startTimeout,
            ExecutionContext execContext,
            WorkListener workListener)
            throws WorkException {
    }

    public void scheduleWork(Work work) throws WorkException {
    }

    public void scheduleWork(Work work,
            long startTimeout,
            ExecutionContext execContext,
            WorkListener workListener)
            throws WorkException {
    }

    public long startWork(Work work) throws WorkException {
        return -1;
    }

    public long startWork(Work work,
            long startTimeout,
            ExecutionContext execContext,
            WorkListener workListener)
            throws WorkException {
        return -1;
    }

    public String getId() {
        return id;
    }

    public boolean equals(WorkManager wm) {
        if (!(wm instanceof MockWorkManager)) {
            return false;
        }

        return ((MockWorkManager) wm).getId() != null &&
                ((MockWorkManager) wm).getId().equals(getId());
    }

}
