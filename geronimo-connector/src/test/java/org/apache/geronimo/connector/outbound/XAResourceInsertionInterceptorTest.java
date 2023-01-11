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
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class XAResourceInsertionInterceptorTest extends ConnectionInterceptorTestUtils {

    private XAResourceInsertionInterceptor xaResourceInsertionInterceptor;
    private XAResource xaResource;
    private String name = "XAResource";

    protected void setUp() throws Exception {
        super.setUp();
        xaResourceInsertionInterceptor = new XAResourceInsertionInterceptor(this, name);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        xaResourceInsertionInterceptor = null;
    }

    public void testInsertXAResource() throws Exception {
        ConnectionInfo connectionInfo = makeConnectionInfo();
        xaResource = new TestXAResource();
        managedConnection = new TestManagedConnection(xaResource);
        xaResourceInsertionInterceptor.getConnection(connectionInfo);
        xaResource.setTransactionTimeout(200);
        //xaresource is wrapped, so we do something to ours to make it distinguishable.
        assertEquals("Expected the same XAResource", 200, connectionInfo.getManagedConnectionInfo().getXAResource().getTransactionTimeout());
    }


    private static class TestXAResource implements XAResource {
        private int txTimeout;

        public void commit(Xid xid, boolean onePhase) throws XAException {
        }

        public void end(Xid xid, int flags) throws XAException {
        }

        public void forget(Xid xid) throws XAException {
        }

        public int getTransactionTimeout() throws XAException {
            return txTimeout;
        }

        public boolean isSameRM(XAResource xaResource) throws XAException {
            return false;
        }

        public int prepare(Xid xid) throws XAException {
            return 0;
        }

        public Xid[] recover(int flag) throws XAException {
            return new Xid[0];
        }

        public void rollback(Xid xid) throws XAException {
        }

        public boolean setTransactionTimeout(int seconds) throws XAException {
            this.txTimeout = seconds;
            return false;
        }

        public void start(Xid xid, int flags) throws XAException {
        }

    }

    private static class TestManagedConnection extends TestPlainManagedConnection {

        private final XAResource xaResource;

        public TestManagedConnection(XAResource xaResource) {
            this.xaResource = xaResource;
        }

        public XAResource getXAResource() throws ResourceException {
            return xaResource;
        }


    }
}
