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

import jakarta.resource.ResourceException;
import jakarta.resource.spi.LocalTransaction;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class LocalXAResourceInsertionInterceptorTest extends ConnectionInterceptorTestUtils {

    private LocalXAResourceInsertionInterceptor localXAResourceInsertionInterceptor;
    private LocalTransaction localTransaction;
    private String name = "LocalXAResource";

    protected void setUp() throws Exception {
        super.setUp();
        localXAResourceInsertionInterceptor = new LocalXAResourceInsertionInterceptor(this, name);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        localXAResourceInsertionInterceptor = null;
    }

    public void testInsertLocalXAResource() throws Exception {
        ConnectionInfo connectionInfo = makeConnectionInfo();
        localXAResourceInsertionInterceptor.getConnection(connectionInfo);
        LocalXAResource returnedLocalXAResource = (LocalXAResource) connectionInfo.getManagedConnectionInfo().getXAResource();
        assertTrue("Expected the same LocalTransaction", localTransaction == returnedLocalXAResource.localTransaction);
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        super.getConnection(connectionInfo);
        localTransaction = new TestLocalTransaction();
        TestManagedConnection managedConnection = new TestManagedConnection(localTransaction);
        ManagedConnectionInfo managedConnectionInfo = connectionInfo.getManagedConnectionInfo();
        managedConnectionInfo.setManagedConnection(managedConnection);
    }

    private static class TestLocalTransaction implements LocalTransaction {
        public void begin() throws ResourceException {
        }

        public void commit() throws ResourceException {
        }

        public void rollback() throws ResourceException {
        }

    }

    private static class TestManagedConnection extends TestPlainManagedConnection {

        private final LocalTransaction localTransaction;

        public TestManagedConnection(LocalTransaction localTransaction) {
            this.localTransaction = localTransaction;
        }

        public LocalTransaction getLocalTransaction() throws ResourceException {
            return localTransaction;
        }
    }
}
