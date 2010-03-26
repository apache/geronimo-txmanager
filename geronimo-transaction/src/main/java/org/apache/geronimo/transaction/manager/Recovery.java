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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public interface Recovery {

    void recoverLog() throws XAException;

    void recoverResourceManager(NamedXAResource xaResource) throws XAException;

    boolean hasRecoveryErrors();

    List getRecoveryErrors();

    boolean localRecoveryComplete();

    int localUnrecoveredCount();

    //hard to implement.. needs ExternalTransaction to have a reference to externalXids.
//    boolean remoteRecoveryComplete();

    Map<Xid, TransactionImpl> getExternalXids();

    public static class XidBranchesPair {
        private final Xid xid;

        //set of TransactionBranchInfo
        private final Set<TransactionBranchInfo> branches = new HashSet<TransactionBranchInfo>();

        private final Object mark;

        public XidBranchesPair(Xid xid, Object mark) {
            this.xid = xid;
            this.mark = mark;
        }

        public Xid getXid() {
            return xid;
        }

        public Set<TransactionBranchInfo> getBranches() {
            return branches;
        }

        public Object getMark() {
            return mark;
        }

        public void addBranch(TransactionBranchInfo branchInfo) {
            branches.add(branchInfo);
        }
    }

}
