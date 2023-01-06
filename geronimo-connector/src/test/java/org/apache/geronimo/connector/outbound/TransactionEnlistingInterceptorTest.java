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
import jakarta.transaction.TransactionManager;

import org.apache.geronimo.transaction.manager.NamedXAResource;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.apache.geronimo.transaction.manager.XidFactoryImpl;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class TransactionEnlistingInterceptorTest extends ConnectionInterceptorTestUtils implements NamedXAResource {
    private TransactionEnlistingInterceptor transactionEnlistingInterceptor;
    private boolean started;
    private boolean ended;
    private boolean returned;
    private boolean committed;
    private TransactionManager transactionManager;

    protected void setUp() throws Exception {
        super.setUp();
        transactionManager = new TransactionManagerImpl();
        transactionEnlistingInterceptor = new TransactionEnlistingInterceptor(this, this.transactionManager);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        transactionEnlistingInterceptor = null;
        started = false;
        ended = false;
        returned = false;
        committed = false;
    }

    public void testNoTransaction() throws Exception {
        ConnectionInfo connectionInfo = makeConnectionInfo();
        transactionEnlistingInterceptor.getConnection(connectionInfo);
        assertTrue("Expected not started", !started);
        assertTrue("Expected not ended", !ended);
        transactionEnlistingInterceptor.returnConnection(connectionInfo, ConnectionReturnAction.RETURN_HANDLE);
        assertTrue("Expected returned", returned);
        assertTrue("Expected not committed", !committed);
    }

    public void testTransactionShareableConnection() throws Exception {
        transactionManager.begin();
        ConnectionInfo connectionInfo = makeConnectionInfo();
        transactionEnlistingInterceptor.getConnection(connectionInfo);
        assertTrue("Expected started", started);
        assertTrue("Expected not ended", !ended);
        started = false;
        transactionEnlistingInterceptor.returnConnection(connectionInfo, ConnectionReturnAction.RETURN_HANDLE);
        assertTrue("Expected not started", !started);
        assertTrue("Expected ended", ended);
        assertTrue("Expected returned", returned);
        transactionManager.commit();
        assertTrue("Expected committed", committed);
    }

    public void testTransactionUnshareableConnection() throws Exception {
        transactionManager.begin();
        ConnectionInfo connectionInfo = makeConnectionInfo();
        connectionInfo.setUnshareable(true);
        transactionEnlistingInterceptor.getConnection(connectionInfo);
        assertTrue("Expected started", started);
        assertTrue("Expected not ended", !ended);
        started = false;
        transactionEnlistingInterceptor.returnConnection(connectionInfo, ConnectionReturnAction.RETURN_HANDLE);
        assertTrue("Expected not started", !started);
        assertTrue("Expected ended", ended);
        assertTrue("Expected returned", returned);
        transactionManager.commit();
        assertTrue("Expected committed", committed);
    }

    //ConnectionInterceptor
    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        ManagedConnectionInfo managedConnectionInfo = connectionInfo.getManagedConnectionInfo();
        managedConnectionInfo.setXAResource(this);
    }

    public void returnConnection(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
        returned = true;
    }

    //XAResource
    public void commit(Xid xid, boolean onePhase) throws XAException {
        committed = true;
    }

    public void end(Xid xid, int flags) throws XAException {
        ended = true;
    }

    public void forget(Xid xid) throws XAException {
    }

    public int getTransactionTimeout() throws XAException {
        return 0;
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
        return false;
    }

    public void start(Xid xid, int flags) throws XAException {
        started = true;
    }


}
