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


package org.apache.geronimo.transaction.manager;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import javax.transaction.xa.XAException;
import junit.framework.TestCase;

/**
 * Test all combinations of heuristic error codes except XA_HEURHAZ
 * @version $Rev$ $Date$
 */
public class HeuristicTest extends TestCase {

    TransactionLog transactionLog = new MockLog();

    TransactionManagerImpl tm;

    protected void setUp() throws Exception {
        tm = new TransactionManagerImpl(10,
                new XidFactoryImpl("WHAT DO WE CALL IT?".getBytes()), transactionLog);
    }

    protected void tearDown() throws Exception {
        tm = null;
    }

    public void test_C_C() throws Exception {
        expectSuccess(0,0);
    }
    public void test_C_HC() throws Exception {
        expectSuccess(0,XAException.XA_HEURCOM);
    }
    public void test_C_HM() throws Exception {
        expectHeuristicMixedException(0, XAException.XA_HEURMIX);
    }
    public void test_C_HR() throws Exception {
        expectHeuristicMixedException(0, XAException.XA_HEURRB);
    }

    public void test_HC_C() throws Exception {
        expectSuccess(XAException.XA_HEURCOM,0);
    }
    public void test_HC_HC() throws Exception {
        expectSuccess(XAException.XA_HEURCOM, XAException.XA_HEURCOM);
    }
    public void test_HC_HM() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURCOM, XAException.XA_HEURMIX);
    }
    public void test_HC_HR() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURCOM, XAException.XA_HEURRB);
    }

    public void test_HM_C() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURMIX, 0);
    }
    public void test_HM_HC() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURMIX, XAException.XA_HEURCOM);
    }
    public void test_HM_HM() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURMIX, XAException.XA_HEURMIX);
    }
    public void test_HM_HR() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURMIX, XAException.XA_HEURRB);
    }


    public void test_HR_C() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURRB, 0);
    }
    public void test_HR_HC() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURRB, XAException.XA_HEURCOM);
    }
    public void test_HR_HM() throws Exception {
        expectHeuristicMixedException(XAException.XA_HEURRB, XAException.XA_HEURMIX);
    }
    public void test_HR_HR() throws Exception {
        expectHeuristicRollbackException(XAException.XA_HEURRB, XAException.XA_HEURRB);
    }


    public void expectSuccess(int first, int second) throws Exception {
        tm.begin();
        tm.getTransaction().enlistResource(new MockResource("1", first));
        tm.getTransaction().enlistResource(new MockResource("2", second));
        tm.commit();
    }
    public void expectHeuristicMixedException(int first, int second) throws Exception {
        tm.begin();
        tm.getTransaction().enlistResource(new MockResource("1", first));
        tm.getTransaction().enlistResource(new MockResource("2", second));
        try {
            tm.commit();
            fail();
        } catch (HeuristicMixedException e) {
            //expected
        }
    }
    public void expectHeuristicRollbackException(int first, int second) throws Exception {
        tm.begin();
        tm.getTransaction().enlistResource(new MockResource("1", first));
        tm.getTransaction().enlistResource(new MockResource("2", second));
        try {
            tm.commit();
            fail();
        } catch (HeuristicRollbackException e) {
            //expected
        }
    }

}
