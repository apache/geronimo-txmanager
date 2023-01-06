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
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Status;

import org.apache.geronimo.connector.ConnectorTransactionContext;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class TransactionCachingInterceptorTest extends ConnectionInterceptorTestUtils {
    private TransactionManager transactionManager;
    private TransactionCachingInterceptor transactionCachingInterceptor;

    protected void setUp() throws Exception {
        super.setUp();
        transactionManager = new TransactionManagerImpl();
        transactionCachingInterceptor = new TransactionCachingInterceptor(this, transactionManager);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        transactionManager = null;
        transactionCachingInterceptor = null;
    }

    public void testGetConnectionInTransaction() throws Exception {
        transactionManager.begin();
        ConnectionInfo connectionInfo1 = makeConnectionInfo();
        transactionCachingInterceptor.getConnection(connectionInfo1);
        assertTrue("Expected to get an initial connection", obtainedConnectionInfo != null);
        assertTrue("Expected nothing returned yet", returnedConnectionInfo == null);
        assertTrue("Expected the same ManagedConnectionInfo in the TransactionContext as was returned",
                connectionInfo1.getManagedConnectionInfo()
                == getSharedManagedConnectionInfo(transactionManager.getTransaction()));
        obtainedConnectionInfo = null;
        ConnectionInfo connectionInfo2 = new ConnectionInfo();
        transactionCachingInterceptor.getConnection(connectionInfo2);
        assertTrue("Expected to not get a second connection", obtainedConnectionInfo == null);
        assertTrue("Expected nothing returned yet", returnedConnectionInfo == null);
        assertTrue("Expected the same ManagedConnectionInfo in both ConnectionInfos",
                connectionInfo1.getManagedConnectionInfo() == connectionInfo2.getManagedConnectionInfo());
        assertTrue("Expected the same ManagedConnectionInfo in the TransactionContext as was returned",
                connectionInfo1.getManagedConnectionInfo() == getSharedManagedConnectionInfo(transactionManager.getTransaction()));
        //commit, see if connection returned.
        //we didn't create any handles, so the "ManagedConnection" should be returned.
        assertTrue("Expected TransactionContext to report active", TxUtil.isTransactionActive(transactionManager));
        transactionManager.commit();
        assertTrue("Expected connection to be returned", returnedConnectionInfo != null);
        assertTrue("Expected TransactionContext to report inactive", !TxUtil.isTransactionActive(transactionManager));

    }

    public void testGetUnshareableConnectionsInTransaction() throws Exception {
        transactionManager.begin();
        ConnectionInfo connectionInfo1 = makeConnectionInfo();
        connectionInfo1.setUnshareable(true);
        transactionCachingInterceptor.getConnection(connectionInfo1);
        assertTrue("Expected to get an initial connection", obtainedConnectionInfo != null);
        assertTrue("Expected nothing returned yet", returnedConnectionInfo == null);

        //2nd is shared, modelling a call into another ejb
        obtainedConnectionInfo = null;
        ConnectionInfo connectionInfo2 = makeConnectionInfo();
        transactionCachingInterceptor.getConnection(connectionInfo2);
        assertTrue("Expected to get a second connection", obtainedConnectionInfo != null);
        assertTrue("Expected nothing returned yet", returnedConnectionInfo == null);
        assertTrue("Expected the same ManagedConnectionInfo in both ConnectionInfos",
                connectionInfo1.getManagedConnectionInfo() != connectionInfo2.getManagedConnectionInfo());

        //3rd is unshared, modelling a call into a third ejb
        obtainedConnectionInfo = null;
        ConnectionInfo connectionInfo3 = makeConnectionInfo();
        connectionInfo3.setUnshareable(true);
        transactionCachingInterceptor.getConnection(connectionInfo3);
        assertTrue("Expected to get a third connection", obtainedConnectionInfo != null);
        assertTrue("Expected nothing returned yet", returnedConnectionInfo == null);
        assertTrue("Expected different ManagedConnectionInfo in both unshared ConnectionInfos",
                connectionInfo1.getManagedConnectionInfo() != connectionInfo3.getManagedConnectionInfo());

        //commit, see if connection returned.
        //we didn't create any handles, so the "ManagedConnection" should be returned.
        assertTrue("Expected TransactionContext to report active", transactionManager.getStatus() == Status.STATUS_ACTIVE);
        transactionManager.commit();
        assertTrue("Expected connection to be returned", returnedConnectionInfo != null);
    }

    private ManagedConnectionInfo getSharedManagedConnectionInfo(Transaction transaction) {
        if (transaction == null) return null;
        return ConnectorTransactionContext.get(transaction, transactionCachingInterceptor).getShared();
    }

    public void testGetConnectionOutsideTransaction() throws Exception {
        ConnectionInfo connectionInfo1 = makeConnectionInfo();
        transactionCachingInterceptor.getConnection(connectionInfo1);
        assertTrue("Expected to get an initial connection", obtainedConnectionInfo != null);
        assertTrue("Expected nothing returned yet", returnedConnectionInfo == null);
        obtainedConnectionInfo = null;
        ConnectionInfo connectionInfo2 = makeConnectionInfo();
        transactionCachingInterceptor.getConnection(connectionInfo2);
        assertTrue("Expected to get a second connection", obtainedConnectionInfo != null);
        assertTrue("Expected nothing returned yet", returnedConnectionInfo == null);
        assertTrue("Expected different ManagedConnectionInfo in both ConnectionInfos",
                connectionInfo1.getManagedConnectionInfo() != connectionInfo2.getManagedConnectionInfo());
        //we didn't create any handles, so the "ManagedConnection" should be returned.
        transactionCachingInterceptor.returnConnection(connectionInfo1, ConnectionReturnAction.RETURN_HANDLE);
        assertTrue("Expected connection to be returned", returnedConnectionInfo != null);
        returnedConnectionInfo = null;
        transactionCachingInterceptor.returnConnection(connectionInfo2, ConnectionReturnAction.RETURN_HANDLE);
        assertTrue("Expected connection to be returned", returnedConnectionInfo != null);
    }

    public void testTransactionIndependence() throws Exception {
        transactionManager.begin();
        ConnectionInfo connectionInfo1 = makeConnectionInfo();
        transactionCachingInterceptor.getConnection(connectionInfo1);
        obtainedConnectionInfo = null;

        //start a second transaction
        Transaction suspendedTransaction = transactionManager.suspend();
        transactionManager.begin();
        ConnectionInfo connectionInfo2 = makeConnectionInfo();
        transactionCachingInterceptor.getConnection(connectionInfo2);
        assertTrue("Expected to get a second connection", obtainedConnectionInfo != null);
        assertTrue("Expected nothing returned yet", returnedConnectionInfo == null);
        assertTrue("Expected different ManagedConnectionInfo in each ConnectionInfos",
                connectionInfo1.getManagedConnectionInfo() != connectionInfo2.getManagedConnectionInfo());
        assertTrue("Expected the same ManagedConnectionInfo in the TransactionContext as was returned",
                connectionInfo2.getManagedConnectionInfo() == getSharedManagedConnectionInfo(transactionManager.getTransaction()));
        //commit 2nd transaction, see if connection returned.
        //we didn't create any handles, so the "ManagedConnection" should be returned.
        assertTrue("Expected TransactionContext to report active", transactionManager.getStatus() == Status.STATUS_ACTIVE);
        transactionManager.commit();
        assertTrue("Expected connection to be returned", returnedConnectionInfo != null);
        assertTrue("Expected TransactionContext to report inactive", transactionManager.getStatus() == Status.STATUS_NO_TRANSACTION);
        returnedConnectionInfo = null;
        //resume first transaction
        transactionManager.resume(suspendedTransaction);
        transactionManager.commit();
        assertTrue("Expected connection to be returned", returnedConnectionInfo != null);
        assertTrue("Expected TransactionContext to report inactive", transactionManager.getStatus() == Status.STATUS_NO_TRANSACTION);
    }

//interface implementations
    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        super.getConnection(connectionInfo);
        ManagedConnectionInfo managedConnectionInfo = connectionInfo.getManagedConnectionInfo();
        managedConnectionInfo.setConnectionEventListener(new GeronimoConnectionEventListener(null, managedConnectionInfo));
    }


    public void handleObtained(
            ConnectionTrackingInterceptor connectionTrackingInterceptor,
            ConnectionInfo connectionInfo) {
    }

    public void handleReleased(
            ConnectionTrackingInterceptor connectionTrackingInterceptor,
            ConnectionInfo connectionInfo) {
    }

}
