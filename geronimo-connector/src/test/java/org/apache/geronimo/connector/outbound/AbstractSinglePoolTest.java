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

import java.util.List;
import java.util.ArrayList;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnectionFactory;

import junit.framework.TestCase;
import org.apache.geronimo.connector.mock.MockManagedConnectionFactory;

/**
 * @version $Rev:$ $Date:$
 */
public class AbstractSinglePoolTest extends TestCase {

    private ManagedConnectionFactory mcf = new MockManagedConnectionFactory();
    protected SwitchableInterceptor switchableInterceptor;
    protected AbstractSinglePoolConnectionInterceptor interceptor;
    protected int maxSize = 10;

    protected void setUp() throws Exception {
        ConnectionInterceptor interceptor = new MCFConnectionInterceptor();
        switchableInterceptor = new SwitchableInterceptor(interceptor);
    }

    /**
     * Check that you can only get maxSize connections out, whether you return or destroy them.
     * Check the pool state after each round.
     * @throws Exception if test fails
     */
    public void testInitialMaxSize() throws Exception {
        getConnections(ConnectionReturnAction.RETURN_HANDLE);
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
        getConnections(ConnectionReturnAction.DESTROY);
        assertEquals(0, interceptor.getConnectionCount());
        assertEquals(0, interceptor.getIdleConnectionCount());
        getConnections(ConnectionReturnAction.RETURN_HANDLE);
    }

    /**
     * Check that if connections cannot be created, no permits are expended.
     * @throws Exception if test fails
     */
    public void testConnectionsUnavailable() throws Exception {
        switchableInterceptor.setOn(false);
        for (int i = 0; i < maxSize; i++) {
            try {
                getConnection();
                fail("Connections should be unavailable");
            } catch (ResourceException e) {
                //pass
            }
        }
        switchableInterceptor.setOn(true);
        getConnections(ConnectionReturnAction.DESTROY);
        switchableInterceptor.setOn(false);
        for (int i = 0; i < maxSize; i++) {
            try {
                getConnection();
                fail("Connections should be unavailable");
            } catch (ResourceException e) {
                //pass
            }
        }
    }

    /**
     * Test that increasing the size of the pool has the expected effects on the connection count
     * and ability to get connections.
     * @throws Exception if test fails
     */
    public void testResizeUp() throws Exception {
        getConnections(ConnectionReturnAction.RETURN_HANDLE);
        int maxSize = this.maxSize;
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
        this.maxSize = 20;
        interceptor.setPartitionMaxSize(this.maxSize);
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
        getConnections(ConnectionReturnAction.RETURN_HANDLE);
        assertEquals(this.maxSize, interceptor.getConnectionCount());
        assertEquals(this.maxSize, interceptor.getIdleConnectionCount());
    }

    /**
     * Check that decreasing the pool size while the pool is full (no connections checked out)
     * immediately destroys the expected number of excess connections and leaves the pool full with
     * the new max size
     * @throws Exception if test fails
     */
    public void testResizeDown() throws Exception {
        getConnections(ConnectionReturnAction.RETURN_HANDLE);
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
        this.maxSize = 5;
        interceptor.setPartitionMaxSize(this.maxSize);
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
        getConnections(ConnectionReturnAction.RETURN_HANDLE);
        assertEquals(this.maxSize, interceptor.getConnectionCount());
        assertEquals(this.maxSize, interceptor.getIdleConnectionCount());
    }

    /**
     * Check that, with all the connections checked out, shrinking the pool results in the
     * expected number of connections being destroyed when they are returned.
     * @throws Exception if test fails
     */
    public void testShrinkLater() throws Exception {
        List<ConnectionInfo> cis = new ArrayList<ConnectionInfo>();
        for (int i = 0; i < maxSize; i++) {
            cis.add(getConnection());
        }
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(0, interceptor.getIdleConnectionCount());
        int oldMaxSize = maxSize;
        maxSize = 5;
        interceptor.setPartitionMaxSize(maxSize);
        try {
            getConnection();
            fail("Pool should be exhausted");
        } catch (ResourceException e) {
            //pass
        }
        for (int i = 0; i< oldMaxSize - maxSize; i++) {
            interceptor.returnConnection(cis.remove(0), ConnectionReturnAction.RETURN_HANDLE);
        }
        try {
            getConnection();
            fail("Pool should be exhausted");
        } catch (ResourceException e) {
            //pass
        }
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(0, interceptor.getIdleConnectionCount());
        for (int i = 0; i< maxSize; i++) {
            interceptor.returnConnection(cis.remove(0), ConnectionReturnAction.RETURN_HANDLE);
        }
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
        getConnections(ConnectionReturnAction.RETURN_HANDLE);
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
    }

    /**
     * Check that the calculation of "shrinkLater" is correct when the pool is shrunk twice before
     * all the "shrinkLater" connections are returned. 
     * @throws Exception if test fails
     */
    public void testShrinkLaterTwice() throws Exception {
        List<ConnectionInfo> cis = new ArrayList<ConnectionInfo>();
        for (int i = 0; i < maxSize; i++) {
            cis.add(getConnection());
        }
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(0, interceptor.getIdleConnectionCount());
        int oldMaxSize = maxSize;
        maxSize = 7;
        interceptor.setPartitionMaxSize(maxSize);
        try {
            getConnection();
            fail("Pool should be exhausted");
        } catch (ResourceException e) {
            //pass
        }
        interceptor.returnConnection(cis.remove(0), ConnectionReturnAction.RETURN_HANDLE);
        oldMaxSize--;
        maxSize = 5;
        interceptor.setPartitionMaxSize(maxSize);
        try {
            getConnection();
            fail("Pool should be exhausted");
        } catch (ResourceException e) {
            //pass
        }
        for (int i = 0; i< oldMaxSize - maxSize; i++) {
            interceptor.returnConnection(cis.remove(0), ConnectionReturnAction.RETURN_HANDLE);
        }
        try {
            getConnection();
            fail("Pool should be exhausted");
        } catch (ResourceException e) {
            //pass
        }
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(0, interceptor.getIdleConnectionCount());
        for (int i = 0; i< maxSize; i++) {
            interceptor.returnConnection(cis.remove(0), ConnectionReturnAction.RETURN_HANDLE);
        }
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
        getConnections(ConnectionReturnAction.RETURN_HANDLE);
        assertEquals(maxSize, interceptor.getConnectionCount());
        assertEquals(maxSize, interceptor.getIdleConnectionCount());
    }

    private void getConnections(ConnectionReturnAction connectionReturnAction) throws ResourceException {
        List<ConnectionInfo> cis = new ArrayList<ConnectionInfo>();
        for (int i = 0; i < maxSize; i++) {
            cis.add(getConnection());
        }
        try {
            getConnection();
            fail("Pool should be exhausted");
        } catch (ResourceException e) {
            //pass
        }
        for (ConnectionInfo ci: cis) {
            interceptor.returnConnection(ci, connectionReturnAction);
        }
    }

    private ConnectionInfo getConnection() throws ResourceException {
        ManagedConnectionInfo mci = new ManagedConnectionInfo(mcf, null);
        ConnectionInfo ci = new ConnectionInfo(mci);
        interceptor.getConnection(ci);
        return ci;
    }

    private static class SwitchableInterceptor implements ConnectionInterceptor {
        private final ConnectionInterceptor next;
        private boolean on = true;

        private SwitchableInterceptor(ConnectionInterceptor next) {
            this.next = next;
        }

        public void setOn(boolean on) {
            this.on = on;
        }

        public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
            if (on) {
                next.getConnection(connectionInfo);
            } else {
                throw new ResourceException();
            }
        }

        public void returnConnection(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
            next.returnConnection(connectionInfo, connectionReturnAction);
        }

        public void destroy() {
            next.destroy();
        }
    }
}
