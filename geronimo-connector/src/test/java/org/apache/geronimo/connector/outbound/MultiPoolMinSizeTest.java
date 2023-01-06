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


package org.apache.geronimo.connector.outbound;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ManagedConnectionFactory;

import junit.framework.TestCase;

import org.apache.geronimo.connector.mock.MockManagedConnectionFactory;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PoolingSupport;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.SinglePool;

/**
 * @version $Rev$ $Date$
 */
public class MultiPoolMinSizeTest extends TestCase {

    private ManagedConnectionFactory mcf = new MockManagedConnectionFactory();
    protected MultiPoolConnectionInterceptor interceptor;
    protected int maxSize = 10;
    protected int minSize = 2;
    protected boolean matchOne = true;
    protected boolean matchAll = true;
    protected boolean selectOneAssumeMatch = false;

    protected void setUp() throws Exception {
        super.setUp();
        PoolingSupport singlePool = new SinglePool(maxSize, minSize, 100, 1, matchOne, matchAll, selectOneAssumeMatch);
        MCFConnectionInterceptor baseInterceptor = new MCFConnectionInterceptor();
        interceptor = new MultiPoolConnectionInterceptor(baseInterceptor, singlePool, false, false);
    }

    /**
     * Check that connection from the pre-filled pool can be returned ok.
     * @throws Exception if test fails
     */
    public void testInitialFill() throws Exception {
        // get first connection, which then fills pool
        ConnectionInfo ci1 = getConnection();
        if (minSize > 0) {
            Thread.sleep(500); // wait for FillTask Timer set at 10ms to run
            assertEquals(minSize, interceptor.getConnectionCount());
        }
        // get second connection from filled pool
        ConnectionInfo ci2 = getConnection();
        // return second connection (which needs to have gotten a proper pool interceptor)
        interceptor.returnConnection(ci2, ConnectionReturnAction.RETURN_HANDLE);
        // return first connection
        interceptor.returnConnection(ci1, ConnectionReturnAction.RETURN_HANDLE);
    }

    private ConnectionInfo getConnection() throws ResourceException {
        ManagedConnectionInfo mci = new ManagedConnectionInfo(mcf, null);
        ConnectionInfo ci = new ConnectionInfo(mci);
        interceptor.getConnection(ci);
        return ci;
    }

}
