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

import javax.resource.spi.UnavailableException;
import javax.resource.spi.XATerminator;
import javax.resource.spi.work.WorkManager;

/**
 * GBean BootstrapContext implementation that refers to externally configured WorkManager
 * and XATerminator gbeans.
 *
 * @version $Rev$ $Date$
 */
public class GeronimoBootstrapContext implements javax.resource.spi.BootstrapContext {
    private final WorkManager workManager;
    private final XATerminator xATerminator;

    /**
     * Default constructor for use as a GBean Endpoint.
     */
    public GeronimoBootstrapContext() {
        workManager = null;
        xATerminator = null;
    }

    /**
     * Normal constructor for use as a GBean.
     * @param workManager
     * @param xaTerminator
     */
    public GeronimoBootstrapContext(WorkManager workManager, XATerminator xaTerminator) {
        this.workManager = workManager;
        this.xATerminator = xaTerminator;
    }


    /**
     * @see javax.resource.spi.BootstrapContext#getWorkManager()
     */
    public WorkManager getWorkManager() {
        return workManager;
    }

    /**
     * @see javax.resource.spi.BootstrapContext#getXATerminator()
     */
    public XATerminator getXATerminator() {
        return xATerminator;
    }

    /**
     * @see javax.resource.spi.BootstrapContext#createTimer()
     */
    public Timer createTimer() throws UnavailableException {
        return new Timer("BootStrapTimer", true);
    }

}
