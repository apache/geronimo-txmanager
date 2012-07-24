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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import junit.framework.TestCase;

/**
 * This is just a unit test for recovery, depending on proper behavior of the log(s) it uses.
 *
 * @version $Rev$ $Date$
 *
 * */
public abstract class AbstractRecoveryTest extends TestCase {

    protected TransactionManagerImpl txManager;
    private static final String RM1 = "rm1";
    private static final String RM2 = "rm2";
    private static final String RM3 = "rm3";
    private static final int XID_COUNT = 3;
    private int branchCounter;

    @Override
    protected void setUp() throws Exception {
        txManager = createTransactionManager();
    }

    protected TransactionManagerImpl createTransactionManager() throws Exception {
        return new TransactionManagerImpl(1, new XidFactoryImpl("hi".getBytes()), null);
    }

    public void test2ResOnlineAfterRecoveryStart() throws Exception {
        Xid[] xids = getXidArray(XID_COUNT);
        MockXAResource xares1 = new MockXAResource(RM1);
        MockXAResource xares2 = new MockXAResource(RM2);
        MockTransactionInfo[] txInfos = makeTxInfos(xids);
        addBranch(txInfos, xares1);
        addBranch(txInfos, xares2);
        prepareLog(txManager.getTransactionLog(), txInfos);
        prepareForReplay();
        Recovery recovery = txManager.recovery;
        assertTrue(!recovery.hasRecoveryErrors());
        assertTrue(recovery.getExternalXids().isEmpty());
        assertTrue(!recovery.localRecoveryComplete());
        recovery.recoverResourceManager(xares1);
        assertTrue(!recovery.localRecoveryComplete());
        assertEquals(XID_COUNT, xares1.committed.size());
        recovery.recoverResourceManager(xares2);
        assertEquals(XID_COUNT, xares2.committed.size());
        assertTrue(recovery.localRecoveryComplete());
    }

    public void test3ResOnlineAfterRecoveryStart() throws Exception {
        Xid[] xids12 = getXidArray(XID_COUNT);
        List xids12List = Arrays.asList(xids12);
        Xid[] xids13 = getXidArray(XID_COUNT);
        List xids13List = Arrays.asList(xids13);
        Xid[] xids23 = getXidArray(XID_COUNT);
        List xids23List = Arrays.asList(xids23);
        ArrayList tmp = new ArrayList();
        tmp.addAll(xids12List);
        tmp.addAll(xids13List);
        Xid[] xids1 = (Xid[]) tmp.toArray(new Xid[6]);
        tmp.clear();
        tmp.addAll(xids12List);
        tmp.addAll(xids23List);
        Xid[] xids2 = (Xid[]) tmp.toArray(new Xid[6]);
        tmp.clear();
        tmp.addAll(xids13List);
        tmp.addAll(xids23List);
        Xid[] xids3 = (Xid[]) tmp.toArray(new Xid[6]);

        MockXAResource xares1 = new MockXAResource(RM1);
        MockXAResource xares2 = new MockXAResource(RM2);
        MockXAResource xares3 = new MockXAResource(RM3);
        MockTransactionInfo[] txInfos12 = makeTxInfos(xids12);
        addBranch(txInfos12, xares1);
        addBranch(txInfos12, xares2);
        prepareLog(txManager.getTransactionLog(), txInfos12);
        MockTransactionInfo[] txInfos13 = makeTxInfos(xids13);
        addBranch(txInfos13, xares1);
        addBranch(txInfos13, xares3);
        prepareLog(txManager.getTransactionLog(), txInfos13);
        MockTransactionInfo[] txInfos23 = makeTxInfos(xids23);
        addBranch(txInfos23, xares2);
        addBranch(txInfos23, xares3);
        prepareLog(txManager.getTransactionLog(), txInfos23);
        prepareForReplay();
        Recovery recovery = txManager.recovery;
        assertTrue(!recovery.hasRecoveryErrors());
        assertTrue(recovery.getExternalXids().isEmpty());
        assertEquals(XID_COUNT * 3, recovery.localUnrecoveredCount());
        recovery.recoverResourceManager(xares1);
        assertEquals(XID_COUNT * 3, recovery.localUnrecoveredCount());
        assertEquals(XID_COUNT * 2, xares1.committed.size());
        recovery.recoverResourceManager(xares2);
        assertEquals(XID_COUNT * 2, recovery.localUnrecoveredCount());
        assertEquals(XID_COUNT * 2, xares2.committed.size());
        recovery.recoverResourceManager(xares3);
        assertEquals(0, recovery.localUnrecoveredCount());
        assertEquals(XID_COUNT * 2, xares3.committed.size());

    }

    protected abstract void prepareForReplay() throws Exception;

    private void prepareLog(TransactionLog txLog, MockTransactionInfo[] txInfos) throws LogException {
        for (int i = 0; i < txInfos.length; i++) {
            MockTransactionInfo txInfo = txInfos[i];
            txLog.prepare(txInfo.globalXid, txInfo.branches);
        }
    }


    private Xid[] getXidArray(int i) {
        Xid[] xids = new Xid[i];
        for (int j = 0; j < xids.length; j++) {
            xids[j] = txManager.getXidFactory().createXid();
        }
        return xids;
    }

    private void addBranch(MockTransactionInfo[] txInfos, MockXAResource xaRes) throws XAException {
        for (int i = 0; i < txInfos.length; i++) {
            MockTransactionInfo txInfo = txInfos[i];
            Xid globalXid = txInfo.globalXid;
            Xid branchXid = txManager.getXidFactory().createBranch(globalXid, branchCounter++);
            xaRes.start(branchXid, 0);
            txInfo.branches.add(new TransactionBranchInfoImpl(branchXid, xaRes.getName()));
        }
    }

    private MockTransactionInfo[] makeTxInfos(Xid[] xids) {
        MockTransactionInfo[] txInfos = new MockTransactionInfo[xids.length];
        for (int i = 0; i < xids.length; i++) {
            Xid xid = xids[i];
            txInfos[i] = new MockTransactionInfo(xid, new ArrayList());
        }
        return txInfos;
    }

    private static class MockXAResource implements NamedXAResource {

        private final String name;
        private final List<Xid> xids = new ArrayList<Xid>();
        private final List<Xid> committed = new ArrayList<Xid>();
        private final List<Xid> rolledBack = new ArrayList<Xid>();

        public MockXAResource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void commit(Xid xid, boolean onePhase) throws XAException {
            committed.add(xid);
        }

        public void end(Xid xid, int flags) throws XAException {
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
            return xids.toArray(new Xid[xids.size()]);
        }

        public void rollback(Xid xid) throws XAException {
            rolledBack.add(xid);
        }

        public boolean setTransactionTimeout(int seconds) throws XAException {
            return false;
        }

        public void start(Xid xid, int flags) throws XAException {
            xids.add(xid);
        }

        public List getCommitted() {
            return committed;
        }

        public List getRolledBack() {
            return rolledBack;
        }


    }

    private static class MockTransactionInfo {
        private Xid globalXid;
        private List branches;

        public MockTransactionInfo(Xid globalXid, List branches) {
            this.globalXid = globalXid;
            this.branches = branches;
        }
    }

}
