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

import java.util.Set;
import java.util.HashSet;

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * @version $Rev$ $Date$
 */
public class MockResource implements NamedXAResource, NamedXAResourceFactory {
    private String xaResourceName = "mockResource";
    private Xid currentXid;
    private MockResourceManager manager;
    private int timeout = 0;
    private boolean prepared;
    private boolean committed;
    private boolean rolledback;
    private Set<Xid> preparedXids = new HashSet<Xid>();
    private Set<Xid> knownXids = new HashSet<Xid>();
    private Set<Xid> finishedXids = new HashSet<Xid>();//end was called with TMSUCCESS or TMFAIL
    private int errorStatus;

    public MockResource(MockResourceManager manager, String xaResourceName) {
        this.manager = manager;
        this.xaResourceName = xaResourceName;
    }

    public MockResource(String xaResourceName, int errorStatus) {
        this.manager = new MockResourceManager();
        this.xaResourceName = xaResourceName;
        this.errorStatus = errorStatus;
    }

    public int getTransactionTimeout() throws XAException {
        return timeout;
    }

    public boolean setTransactionTimeout(int seconds) throws XAException {
        return false;
    }

    public Xid getCurrentXid() {
        return currentXid;
    }

    public void start(Xid xid, int flags) throws XAException {
        if (this.currentXid != null) {
            throw new XAException(XAException.XAER_PROTO);
        }
        if (flags == XAResource.TMRESUME && !knownXids.contains(xid)) {
            throw new XAException(XAException.XAER_PROTO);
        }
        if (finishedXids.contains(xid)) {
            throw new XAException(XAException.XAER_PROTO);
        }
        if ((flags & XAResource.TMJOIN) != 0) {
            manager.join(xid, this);
        } else {
            manager.newTx(xid, this);
        }
        this.currentXid = xid;
        if (!knownXids.contains(xid)) {
            knownXids.add(xid);
        }
    }

    public void end(Xid xid, int flags) throws XAException {
        if (!knownXids.contains(xid)) {
            throw new XAException(XAException.XAER_PROTO);
        }
        if (flags == XAResource.TMSUSPEND) {
            if (currentXid == null) {
                throw new XAException(XAException.XAER_PROTO);
            } else if (this.currentXid != xid) {
                throw new XAException(XAException.XAER_PROTO);
            }
        } else if (flags == XAResource.TMFAIL || flags == XAResource.TMSUCCESS) {
            if (finishedXids.contains(xid)) {
                throw new XAException(XAException.XAER_PROTO);
            }
            finishedXids.add(xid);
        }
        this.currentXid = null;
    }

    public int prepare(Xid xid) throws XAException {
        if (!finishedXids.contains(xid)) {
            throw new XAException(XAException.XAER_PROTO);
        }
        prepared = true;
        preparedXids.add(xid);
        return XAResource.XA_OK;
    }

    public void commit(Xid xid, boolean onePhase) throws XAException {
        if (!finishedXids.contains(xid)) {
            throw new XAException(XAException.XAER_PROTO);
        }
        if (errorStatus != 0) {
            throw new XAException(errorStatus);
        }
        preparedXids.remove(xid);
        committed = true;
    }

    public void rollback(Xid xid) throws XAException {
        if (!finishedXids.contains(xid)) {
            throw new XAException(XAException.XAER_PROTO);
        }
        rolledback = true;
        preparedXids.remove(xid);
        manager.forget(xid, this);
    }

    public boolean isSameRM(XAResource xaResource) throws XAException {
        if (xaResource instanceof MockResource) {
            return manager == ((MockResource) xaResource).manager;
        }
        return false;
    }

    public void forget(Xid xid) throws XAException {
//        throw new UnsupportedOperationException();
    }

    public Xid[] recover(int flag) throws XAException {
        return preparedXids.toArray(new Xid[preparedXids.size()]);
    }

    public boolean isPrepared() {
        return prepared;
    }

    public boolean isCommitted() {
        return committed;
    }

    public boolean isRolledback() {
        return rolledback;
    }

    public String getName() {
        return xaResourceName;
    }

    public NamedXAResource getNamedXAResource() throws SystemException {
        return this;
    }

    public void returnNamedXAResource(NamedXAResource namedXAResource) {
        if (this != namedXAResource) throw new RuntimeException("Wrong NamedXAResource returned: expected: " + this + " actual: " + namedXAResource);
    }

}
