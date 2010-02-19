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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.xa.Xid;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class MockLog implements TransactionLog {

    final Map prepared = new HashMap();
    final List committed = new ArrayList();
    final List rolledBack = new ArrayList();

    public void begin(Xid xid) throws LogException {
    }

    public Object prepare(Xid xid, List<TransactionBranchInfo> branches) throws LogException {
        Object mark = new Object();
        Recovery.XidBranchesPair xidBranchesPair = new Recovery.XidBranchesPair(xid, mark);
        xidBranchesPair.getBranches().addAll(branches);
        prepared.put(xid, xidBranchesPair);
        return mark;
    }

    public void commit(Xid xid, Object logMark) throws LogException {
        committed.add(xid);
    }

    public void rollback(Xid xid, Object logMark) throws LogException {
        rolledBack.add(xid);
    }

    public Collection recover(XidFactory xidFactory) throws LogException {
        Map copy = new HashMap(prepared);
        copy.keySet().removeAll(committed);
        copy.keySet().removeAll(rolledBack);
        return copy.values();
    }

    public String getXMLStats() {
        return null;
    }

    public int getAverageForceTime() {
        return 0;
    }

    public int getAverageBytesPerForce() {
        return 0;
    }
}
