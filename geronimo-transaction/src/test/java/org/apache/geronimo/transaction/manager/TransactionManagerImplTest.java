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

package org.apache.geronimo.transaction.manager;

import java.util.Map;

import javax.transaction.InvalidTransactionException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import junit.framework.TestCase;

/**
 * @version $Rev$ $Date$
 */
public class TransactionManagerImplTest extends TestCase {

    MockResourceManager rm1 = new MockResourceManager(true);
    MockResource r1_1 = rm1.getResource("rm1_1");
    MockResource r1_2 = rm1.getResource("rm1_2");
    MockResourceManager rm2 = new MockResourceManager(true);
    MockResource r2_1 = rm2.getResource("rm2_1");
    MockResource r2_2 = rm2.getResource("rm2_2");

    TransactionLog transactionLog = new MockLog();

    TransactionManagerImpl tm;

    protected void setUp() throws Exception {
        tm = new TransactionManagerImpl(10,
                new XidFactoryImpl("WHAT DO WE CALL IT?".getBytes()), transactionLog);
    }

    protected void tearDown() throws Exception {
        tm = null;
    }

    public void testNoResourcesCommit() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        tm.commit();
        assertNull(tm.getTransaction());
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        Transaction tx = tm.getTransaction();
        assertNotNull(tx);
        tx.commit();
        assertNotNull(tm.getTransaction());
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
    }

    public void testNoResourcesMarkRollbackOnly() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        tm.getTransaction().setRollbackOnly();
        assertEquals(Status.STATUS_MARKED_ROLLBACK, tm.getStatus());
        try {
            tm.commit();
            fail("tx should not commit");
        } catch (RollbackException e) {
            //expected
        }
        assertNull(tm.getTransaction());
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        Transaction tx = tm.getTransaction();
        assertNotNull(tx);
        tx.setRollbackOnly();
        assertEquals(Status.STATUS_MARKED_ROLLBACK, tx.getStatus());
        try {
            tx.commit();
            fail("tx should not commit");
        } catch (RollbackException e) {
            //expected
        }
        assertNotNull(tm.getTransaction());
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
    }

    public void testNoResourcesRollback() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        tm.rollback();
        assertNull(tm.getTransaction());
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        Transaction tx = tm.getTransaction();
        assertNotNull(tx);
        tx.rollback();
        assertNotNull(tm.getTransaction());
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());

        //check rollback when marked rollback only
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        tm.getTransaction().setRollbackOnly();
        assertEquals(Status.STATUS_MARKED_ROLLBACK, tm.getStatus());
        tm.rollback();
        assertNull(tm.getTransaction());
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
    }

    public void testOneResourceCommit() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        Transaction tx = tm.getTransaction();
        tx.enlistResource(r1_1);
        tx.delistResource(r1_1, XAResource.TMSUCCESS);
        tx.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertTrue(r1_1.isCommitted());
        assertTrue(!r1_1.isPrepared());
        assertTrue(!r1_1.isRolledback());
    }

    public void testOneResourceMarkedRollback() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        Transaction tx = tm.getTransaction();
        tx.enlistResource(r1_1);
        tx.setRollbackOnly();
        tx.delistResource(r1_1, XAResource.TMSUCCESS);
        try {
            tx.commit();
            fail("tx should roll back");
        } catch (RollbackException e) {
            //expected
        }
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertTrue(!r1_1.isCommitted());
        assertTrue(!r1_1.isPrepared());
        assertTrue(r1_1.isRolledback());
    }

    public void testOneResourceRollback() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        Transaction tx = tm.getTransaction();
        tx.enlistResource(r1_1);
        tx.delistResource(r1_1, XAResource.TMSUCCESS);
        tx.rollback();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertTrue(!r1_1.isCommitted());
        assertTrue(!r1_1.isPrepared());
        assertTrue(r1_1.isRolledback());
    }

    public void testTwoResourceOneRMCommit() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        Transaction tx = tm.getTransaction();
        tx.enlistResource(r1_1);
        tx.delistResource(r1_1, XAResource.TMSUCCESS);
        tx.enlistResource(r1_2);
        tx.delistResource(r1_2, XAResource.TMSUCCESS);
        tx.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertTrue(r1_1.isCommitted() ^ r1_2.isCommitted());
        assertTrue(!r1_1.isPrepared() & !r1_2.isPrepared());
        assertTrue(!r1_1.isRolledback() & !r1_2.isRolledback());
    }

    public void testTwoResourceOneRMMarkRollback() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        Transaction tx = tm.getTransaction();
        tx.enlistResource(r1_1);
        tx.delistResource(r1_1, XAResource.TMSUCCESS);
        tx.enlistResource(r1_2);
        tx.delistResource(r1_2, XAResource.TMSUCCESS);
        tx.setRollbackOnly();
        try {
            tx.commit();
            fail("tx should roll back");
        } catch (RollbackException e) {
            //expected
        }
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertTrue(!r1_1.isCommitted() & !r1_2.isCommitted());
        assertTrue(!r1_1.isPrepared() & !r1_2.isPrepared());
        assertTrue(r1_1.isRolledback() ^ r1_2.isRolledback());
    }

    public void testTwoResourcesOneRMRollback() throws Exception {
        tm.begin();
        Transaction tx = tm.getTransaction();
        tx.enlistResource(r1_1);
        tx.delistResource(r1_1, XAResource.TMSUCCESS);
        tx.enlistResource(r1_2);
        tx.delistResource(r1_2, XAResource.TMSUCCESS);
        tx.setRollbackOnly();
        tx.rollback();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertTrue(!r1_1.isCommitted() & !r1_2.isCommitted());
        assertTrue(!r1_1.isPrepared() & !r1_2.isPrepared());
        assertTrue(r1_1.isRolledback() ^ r1_2.isRolledback());
    }

    public void testFourResourceTwoRMCommit() throws Exception {
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        Transaction tx = tm.getTransaction();
        tx.enlistResource(r1_1);
        tx.enlistResource(r1_2);
        tx.enlistResource(r2_1);
        tx.enlistResource(r2_2);
        tx.delistResource(r1_1, XAResource.TMSUCCESS);
        tx.delistResource(r1_2, XAResource.TMSUCCESS);
        tx.delistResource(r2_1, XAResource.TMSUCCESS);
        tx.delistResource(r2_2, XAResource.TMSUCCESS);
        tx.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertTrue((r1_1.isCommitted() & r1_1.isPrepared()) ^ (r1_2.isCommitted() & r1_2.isPrepared()));
        assertTrue(!r1_1.isRolledback() & !r1_2.isRolledback());
        assertTrue((r2_1.isCommitted() & r2_1.isPrepared()) ^ (r2_2.isCommitted() & r2_2.isPrepared()));
        assertTrue(!r2_1.isRolledback() & !r2_2.isRolledback());
    }

    //BE VERY CAREFUL!! the ResourceManager only "recovers" the LAST resource it creates.
    //This test depends on using the resource that will be recovered by the resource manager.
    public void testSimpleRecovery() throws Exception {
        //create a transaction in our own transaction manager
        Xid xid = tm.getXidFactory().createXid();
        Transaction tx = tm.importXid(xid, 0);
        tm.resume(tx);
        assertSame(tx, tm.getTransaction());
        tx.enlistResource(r1_2);
        tx.enlistResource(r2_2);
        tx.delistResource(r1_2, XAResource.TMSUCCESS);
        tx.delistResource(r2_2, XAResource.TMSUCCESS);
        tm.suspend();
        tm.prepare(tx);
        //recover
        tm.recovery.recoverLog();
        recover(r1_1);
        recover(r1_2);
        assertTrue(r1_2.isCommitted());
        assertTrue(!r2_2.isCommitted());
        recover(r2_1);
        recover(r2_2);
        assertTrue(r2_2.isCommitted());
        assertTrue(tm.recovery.localRecoveryComplete());
    }

    private void recover(MockResource mr) {
        tm.registerNamedXAResourceFactory(mr);
        tm.unregisterNamedXAResourceFactory(mr.getName());
    }

    public void testImportedXidRecovery() throws Exception {
        //create a transaction from an external transaction manager.
        XidFactory xidFactory2 = new XidFactoryImpl("tm2".getBytes());
        Xid xid = xidFactory2.createXid();
        Transaction tx = tm.importXid(xid, 0);
        tm.resume(tx);
        assertSame(tx, tm.getTransaction());
        tx.enlistResource(r1_2);
        tx.enlistResource(r2_2);
        tx.delistResource(r1_2, XAResource.TMSUCCESS);
        tx.delistResource(r2_2, XAResource.TMSUCCESS);
        tm.suspend();
        tm.prepare(tx);
        //recover
        tm.recovery.recoverLog();
        recover(r1_1);
        recover(r1_2);
        assertTrue(!r1_2.isCommitted());
        assertTrue(!r2_2.isCommitted());
        recover(r2_1);
        recover(r2_2);
        assertTrue(!r2_2.isCommitted());
        //there are no transactions started here, so local recovery is complete
        assertTrue(tm.recovery.localRecoveryComplete());
        Map recovered = tm.getExternalXids();
        assertEquals(1, recovered.size());
        assertEquals(xid, recovered.keySet().iterator().next());
    }

    public void testTimeout() throws Exception
    {
        long timeout = tm.getTransactionTimeoutMilliseconds(0L);
        tm.setTransactionTimeout((int)timeout/4000);
        tm.begin();
        System.out.println("Test to sleep for " + timeout + " millisecs");
        Thread.sleep(timeout);
        try
        {
            tm.commit();
            fail("Tx Should get Rollback exception");
        }catch(RollbackException rex)
        {
            // Caught expected exception
        }

        // Now test if the default timeout is active
        tm.begin();
        System.out.println("Test to sleep for " + (timeout/2) + " millisecs");
        Thread.sleep((timeout/2));
        tm.commit();
        // Its a failure if exception occurs.
    }    
      
    // resume throws InvalidTransactionException on completed tx (via commit)
    public void testResume1() throws Exception {
        Transaction tx;
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();   
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        tx = tm.getTransaction();
        assertNotNull(tx);
        assertEquals(Status.STATUS_ACTIVE, tx.getStatus());
        
        tm.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertNull(tm.getTransaction());
        
        try {
            tm.resume(tx);
            fail();
        } catch (InvalidTransactionException e) {
            // expected
        }        
    }
    
    // resume throws InvalidTransactionException on completed tx (via rollback)
    public void testResume2() throws Exception {
        Transaction tx;
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        tx = tm.getTransaction();
        assertNotNull(tx);
        assertEquals(Status.STATUS_ACTIVE, tx.getStatus());

        tx = tm.suspend();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertNull(tm.getTransaction());

        tm.resume(tx);
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        assertEquals(tx, tm.getTransaction());

        tm.rollback();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertNull(tm.getTransaction());     
        
        try {
            tm.resume(tx);
            fail();
        } catch (InvalidTransactionException e) {
            // expected
        }   
    }
    
    // resume works on null tx
    public void testResume3() throws Exception {
        Transaction tx;
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        tx = tm.getTransaction();
        assertNotNull(tx);
        assertEquals(Status.STATUS_ACTIVE, tx.getStatus());

        tm.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertNull(tm.getTransaction());

        // tx should be null
        tx = tm.suspend();
        assertNull(tx);
        
        try {
            tm.resume(tx);
        } catch (InvalidTransactionException e) {
            // null is considered valid so we don't expect InvalidTransactionException here
            e.printStackTrace();
            fail();
        }   
    }
    
    // resume works on any valid tx
    public void testResume4() throws Exception {
        Transaction tx;
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
        tx = tm.getTransaction();
        assertNotNull(tx);
        assertEquals(Status.STATUS_ACTIVE, tx.getStatus());

        try {
            tm.resume(tx);
            assertNotNull(tx);
            assertEquals(Status.STATUS_ACTIVE, tx.getStatus());
        } catch (InvalidTransactionException e) {
            // tx is considered valid so we don't expect InvalidTransactionException here
            e.printStackTrace();
            fail();
        }   

        tm.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertNull(tm.getTransaction()); 
    }
}
