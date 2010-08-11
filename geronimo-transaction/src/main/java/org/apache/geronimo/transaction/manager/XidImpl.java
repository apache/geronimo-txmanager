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

import java.io.Serializable;
import java.util.Arrays;
import javax.transaction.xa.Xid;

/**
 * Unique id for a transaction.
 *
 * @version $Rev$ $Date$
 */
public class XidImpl implements Xid, Serializable {
    private static int FORMAT_ID = 0x4765526f;  // Gero
    private final int formatId;
    private final byte[] globalId;
    private final byte[] branchId;
    private int hash;   //apparently never used by our code, so don't compute it.

    /**
     * Constructor taking a global id (for the main transaction)
     * @param globalId the global transaction id
     */
    public XidImpl(byte[] globalId) {
        this.formatId = FORMAT_ID;
        this.globalId = globalId;
        //this.hash = hash(0, globalId);
        branchId = new byte[Xid.MAXBQUALSIZE];
        check();
    }

    private void check() {
        if (globalId.length > Xid.MAXGTRIDSIZE) {
            throw new IllegalStateException("Global id is too long: " + toString());
        }
        if (branchId.length > Xid.MAXBQUALSIZE) {
            throw new IllegalStateException("Branch id is too long: " + toString());
        }
    }

    /**
     * Constructor for a branch id
     * @param global the xid of the global transaction this branch belongs to
     * @param branch the branch id
     */
    public XidImpl(Xid global, byte[] branch) {
        this.formatId = FORMAT_ID;
        //int hash;
        if (global instanceof XidImpl) {
            globalId = ((XidImpl) global).globalId;
            //hash = ((XidImpl) global).hash;
        } else {
            globalId = global.getGlobalTransactionId();
            //hash = hash(0, globalId);
        }
        branchId = branch;
        //this.hash = hash(hash, branchId);
        check();
    }

    public XidImpl(int formatId, byte[] globalId, byte[] branchId) {
        this.formatId = formatId;
        this.globalId = globalId;
        this.branchId = branchId;
        check();
    }

    private int hash(int hash, byte[] id) {
        for (int i = 0; i < id.length; i++) {
            hash = (hash * 37) + id[i];
        }
        return hash;
    }

    public int getFormatId() {
        return formatId;
    }

    public byte[] getGlobalTransactionId() {
        return (byte[]) globalId.clone();
    }

    public byte[] getBranchQualifier() {
        return (byte[]) branchId.clone();
    }

    public boolean equals(Object obj) {
        if (obj instanceof XidImpl == false) {
            return false;
        }
        XidImpl other = (XidImpl) obj;
        return formatId == other.formatId
                && Arrays.equals(globalId, other.globalId)
                && Arrays.equals(branchId, other.branchId);
    }

    public int hashCode() {
        if (hash == 0) {
            hash = hash(hash(0, globalId), branchId);
        }
        return hash;
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("[Xid:globalId=");
        for (int i = 0; i < globalId.length; i++) {
            s.append(Integer.toHexString(globalId[i]));
        }
        s.append(",length=").append(globalId.length);
        s.append(",branchId=");
        for (int i = 0; i < branchId.length; i++) {
            s.append(Integer.toHexString(branchId[i]));
        }
        s.append(",length=");
        s.append(branchId.length);
        s.append("]");
        return s.toString();
    }
}
