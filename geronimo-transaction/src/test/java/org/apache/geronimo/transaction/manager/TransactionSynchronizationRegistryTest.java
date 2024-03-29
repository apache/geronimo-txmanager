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

import jakarta.transaction.Synchronization;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.NotSupportedException;

import junit.framework.TestCase;

/**
 * @version $Rev$ $Date$
 */
public class TransactionSynchronizationRegistryTest extends TestCase {

    private static int beforeCounter = 0;
    private static int afterCounter = 0;



    private GeronimoTransactionManager tm;

    private CountingSync interposedSync;
    private CountingSync normalSync;

    protected void setUp() throws Exception {
        tm  = new GeronimoTransactionManager();
    }

    private void setUpInterposedSync() throws NotSupportedException, SystemException {
        interposedSync = new CountingSync();
        tm.begin();
        tm.registerInterposedSynchronization(interposedSync);
    }

    private void setUpSyncs() throws Exception {
        normalSync = new CountingSync();
        setUpInterposedSync();
        tm.getTransaction().registerSynchronization(normalSync);
    }

    public void testTransactionKey() throws Exception {
    	normalSync = new CountingSync();
    	assertNull(tm.getTransactionKey());
    	setUpInterposedSync();
    	tm.getTransaction().registerSynchronization(normalSync);
    	assertNotNull(tm.getTransactionKey());
    	tm.commit();
    	assertNull(tm.getTransactionKey());
    }

    public void testInterposedSynchIsCalledOnCommit() throws Exception {
        setUpInterposedSync();
        tm.commit();
        checkInterposedSyncCalled();
    }

    private void checkInterposedSyncCalled() {
        assertTrue("interposedSync beforeCompletion was not called", interposedSync.getBeforeCount() != -1);
        assertTrue("interposedSync afterCompletion was not called", interposedSync.getAfterCount() != -1);
    }

    private void checkInterposedSyncCalledOnRollback() {
        assertTrue("interposedSync afterCompletion was not called", interposedSync.getAfterCount() != -1);
    }
    
    public void testInterposedSynchIsCalledOnRollback() throws Exception {
        setUpInterposedSync();
        tm.rollback();
        checkInterposedSyncCalledOnRollback();
    }
    
    // check normal synch before completion is not called on rollback
    public void testNormalSynchBeforeCompletion() throws Exception {
    	normalSync = new CountingSync();
    	tm.begin();
    	tm.getTransaction().registerSynchronization(normalSync);
        tm.rollback();
        assertFalse(normalSync.beforeCompletionCalled());
        assertTrue(normalSync.afterCompletionCalled());
    }

    public void testInterposedSynchIsCalledOnMarkRollback() throws Exception {
        setUpInterposedSync();
        tm.setRollbackOnly();
        try {
            tm.commit();
            fail("expected a RollbackException");
        } catch (HeuristicMixedException e) {
            fail("expected a RollbackException not " + e.getClass());
        } catch (HeuristicRollbackException e) {
            fail("expected a RollbackException not " + e.getClass());
        } catch (IllegalStateException e) {
            fail("expected a RollbackException not " + e.getClass());
        } catch (RollbackException e) {

        } catch (SecurityException e) {
            fail("expected a RollbackException not " + e.getClass());
        } catch (SystemException e) {
            fail("expected a RollbackException not " + e.getClass());
        }
        checkInterposedSyncCalled();
    }

    public void testSynchCallOrderOnCommit() throws Exception {
        setUpSyncs();
        tm.commit();
        checkSyncCallOrder();
    }

    private void checkSyncCallOrder() {
        checkInterposedSyncCalled();
        assertTrue("interposedSync beforeCompletion was not called after normalSync beforeCompletion", interposedSync.getBeforeCount() > normalSync.getBeforeCount());
        assertTrue("interposedSync afterCompletion was not called before normalSync beforeCompletion", interposedSync.getAfterCount() < normalSync.getAfterCount());
    }
    
    private void checkSyncCallOrderOnRollback() {
        checkInterposedSyncCalledOnRollback();
        assertTrue("interposedSync afterCompletion was not called before normalSync beforeCompletion", interposedSync.getAfterCount() < normalSync.getAfterCount());
    }

    public void testSynchCallOrderOnRollback() throws Exception {
        setUpSyncs();
        tm.rollback();
        checkSyncCallOrderOnRollback();
    }

    public void testSynchCallOrderOnMarkRollback() throws Exception {
        setUpSyncs();
        tm.setRollbackOnly();
        try {
            tm.commit();
            fail("expected a RollbackException");
        } catch (HeuristicMixedException e) {
            fail("expected a RollbackException not " + e.getClass());
        } catch (HeuristicRollbackException e) {
            fail("expected a RollbackException not " + e.getClass());
        } catch (IllegalStateException e) {
            fail("expected a RollbackException not " + e.getClass());
        } catch (RollbackException e) {

        } catch (SecurityException e) {
            fail("expected a RollbackException not " + e.getClass());
        } catch (SystemException e) {
            fail("expected a RollbackException not " + e.getClass());
        }
        checkSyncCallOrder();
    }

    private class CountingSync implements Synchronization {

        private int beforeCount = -1;
        private int afterCount = -1;
        private boolean beforeCalled = false;
        private boolean afterCalled = false;

        public void beforeCompletion() {
            beforeCalled = true;
            beforeCount = beforeCounter++;
        }

        public void afterCompletion(int i) {
            afterCalled = true;
            afterCount = afterCounter++;
        }

        public int getBeforeCount() {
            return beforeCount;
        }

        public int getAfterCount() {
            return afterCount;
        }
        
        public boolean beforeCompletionCalled() {
        	return beforeCalled;
        }
        
        public boolean afterCompletionCalled() {
        	return afterCalled;
        }
    }

}
