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


package org.apache.geronimo.connector.outbound;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.apache.geronimo.transaction.manager.NamedXAResource;
import org.apache.geronimo.transaction.manager.NamedXAResourceFactory;

/**
 * @version $Rev$ $Date$
 */
public class OutboundNamedXAResourceFactory implements NamedXAResourceFactory {

    private final String name;
    private final ConnectionInterceptor recoveryStack;
    private final ManagedConnectionFactory managedConnectionFactory;

    public OutboundNamedXAResourceFactory(String name, ConnectionInterceptor recoveryStack, ManagedConnectionFactory managedConnectionFactory) {
        this.name = name;
        this.recoveryStack = recoveryStack;
        this.managedConnectionFactory = managedConnectionFactory;
    }

    public String getName() {
        return name;
    }

    public NamedXAResource getNamedXAResource() throws SystemException {
        try {
            ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, null);

            ConnectionInfo recoveryConnectionInfo = new ConnectionInfo(mci);
            recoveryStack.getConnection(recoveryConnectionInfo);

            // For pooled resources, we may now have a new MCI (not the one constructed above). Make sure we use the correct MCI
            return new NamedXAResourceWithConnectioninfo((NamedXAResource) recoveryConnectionInfo.getManagedConnectionInfo().getXAResource(), recoveryConnectionInfo);
        } catch (ResourceException e) {
            throw (SystemException) new SystemException("Could not get XAResource for recovery for mcf: " + name).initCause(e);
        }
    }

    public void returnNamedXAResource(NamedXAResource namedXAResource) {
        NamedXAResourceWithConnectioninfo xares = (NamedXAResourceWithConnectioninfo) namedXAResource;
        recoveryStack.returnConnection(xares.getConnectionInfo(), ConnectionReturnAction.DESTROY);
    }

    private static class NamedXAResourceWithConnectioninfo implements NamedXAResource {

        private final NamedXAResource delegate;
        private final ConnectionInfo connectionInfo;

        private NamedXAResourceWithConnectioninfo(NamedXAResource delegate, ConnectionInfo connectionInfo) {
            this.delegate = delegate;
            this.connectionInfo = connectionInfo;
        }

        public ConnectionInfo getConnectionInfo() {
            return connectionInfo;
        }

        public String getName() {
            return delegate.getName();
        }

        public void commit(Xid xid, boolean b) throws XAException {
            delegate.commit(xid, b);
        }

        public void end(Xid xid, int i) throws XAException {
            delegate.end(xid, i);
        }

        public void forget(Xid xid) throws XAException {
            delegate.forget(xid);
        }

        public int getTransactionTimeout() throws XAException {
            return delegate.getTransactionTimeout();
        }

        public boolean isSameRM(XAResource xaResource) throws XAException {
            return delegate.isSameRM(xaResource);
        }

        public int prepare(Xid xid) throws XAException {
            return delegate.prepare(xid);
        }

        public Xid[] recover(int i) throws XAException {
            return delegate.recover(i);
        }

        public void rollback(Xid xid) throws XAException {
            delegate.rollback(xid);
        }

        public boolean setTransactionTimeout(int i) throws XAException {
            return delegate.setTransactionTimeout(i);
        }

        public void start(Xid xid, int i) throws XAException {
            delegate.start(xid, i);
        }
    }
}
