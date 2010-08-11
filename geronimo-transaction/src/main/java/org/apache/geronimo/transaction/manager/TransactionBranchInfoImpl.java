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


import javax.transaction.xa.Xid;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class TransactionBranchInfoImpl implements TransactionBranchInfo {

    private final Xid branchXid;
    private final String resourceName;

    public TransactionBranchInfoImpl(Xid branchXid, String resourceName) {
        if (resourceName == null) throw new NullPointerException("resourceName");
        if (branchXid == null) throw new NullPointerException("branchXid");
        this.branchXid = branchXid;
        this.resourceName = resourceName;
    }

    public Xid getBranchXid() {
        return branchXid;
    }

    public String getResourceName() {
        return resourceName;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("[Transaction branch:\n");
        b.append(" name:").append(resourceName);
        b.append("\n branchId: ");
        for (byte i : branchXid.getBranchQualifier()) {
            b.append(Integer.toHexString(i));
        }
        b.append("\n]\n");
        return b.toString();
    }
}
