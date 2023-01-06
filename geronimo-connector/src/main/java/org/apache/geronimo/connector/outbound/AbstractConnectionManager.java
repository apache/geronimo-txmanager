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

package org.apache.geronimo.connector.outbound;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.LazyAssociatableConnectionManager;
import jakarta.resource.spi.ManagedConnectionFactory;

import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PoolingSupport;
import org.apache.geronimo.transaction.manager.RecoverableTransactionManager;

/**
 * @version $Rev$ $Date$
 */
public abstract class AbstractConnectionManager implements ConnectionManagerContainer, ConnectionManager, LazyAssociatableConnectionManager, PoolingAttributes {
    protected transient final Interceptors interceptors;
    private transient final RecoverableTransactionManager transactionManager;
    private transient final ManagedConnectionFactory managedConnectionFactory;
    private final String name;

    //default constructor to support externalizable subclasses
    public AbstractConnectionManager() {
        interceptors = null;
        transactionManager = null;
        managedConnectionFactory = null;
        this.name = null;
    }

    public AbstractConnectionManager(Interceptors interceptors, RecoverableTransactionManager transactionManager, ManagedConnectionFactory mcf, String name) {
        this.interceptors = interceptors;
        this.transactionManager = transactionManager;
        this.managedConnectionFactory = mcf;
        this.name = name;
    }

    public Object createConnectionFactory() throws ResourceException {
        return managedConnectionFactory.createConnectionFactory(this);
    }

    protected ConnectionManager getConnectionManager() {
        return this;
    }

    public ManagedConnectionFactory getManagedConnectionFactory() {
        return managedConnectionFactory;
    }

    public void doRecovery() {
        if (!getIsRecoverable()) {
            return;
        }
        transactionManager.registerNamedXAResourceFactory(new OutboundNamedXAResourceFactory(name, getRecoveryStack(), managedConnectionFactory));
    }

    /**
     * in: mcf != null, is a deployed mcf
     * out: useable connection object.
     */
    public Object allocateConnection(ManagedConnectionFactory managedConnectionFactory,
                                     ConnectionRequestInfo connectionRequestInfo)
            throws ResourceException {
        ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, connectionRequestInfo);
        ConnectionInfo ci = new ConnectionInfo(mci);
        getStack().getConnection(ci);
        Object connection = ci.getConnectionProxy();
        if (connection == null) {
            connection = ci.getConnectionHandle();
        } else {
            // connection proxy is used only once so we can be notified
            // by the garbage collector when a connection is abandoned 
            ci.setConnectionProxy(null);
        }
        return connection;
    }

    /**
     * in: non-null connection object, from non-null mcf.
     * connection object is not associated with a managed connection
     * out: supplied connection object is assiciated with a non-null ManagedConnection from mcf.
     */
    public void associateConnection(Object connection,
                                    ManagedConnectionFactory managedConnectionFactory,
                                    ConnectionRequestInfo connectionRequestInfo)
            throws ResourceException {
        ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, connectionRequestInfo);
        ConnectionInfo ci = new ConnectionInfo(mci);
        ci.setConnectionHandle(connection);
        getStack().getConnection(ci);
    }

    public void inactiveConnectionClosed(Object connection, ManagedConnectionFactory managedConnectionFactory) {
        //TODO If we are tracking connections, we need to stop tracking this one.
        //I don't see why we don't get a connectionClosed event for it.
    }

    ConnectionInterceptor getConnectionInterceptor() {
        return getStack();
    }

    //statistics

    public int getPartitionCount() {
        return getPooling().getPartitionCount();
    }

    public int getPartitionMaxSize() {
        return getPooling().getPartitionMaxSize();
    }

    public void setPartitionMaxSize(int maxSize) throws InterruptedException {
        getPooling().setPartitionMaxSize(maxSize);
    }

    public int getPartitionMinSize() {
        return getPooling().getPartitionMinSize();
    }

    public void setPartitionMinSize(int minSize) {
        getPooling().setPartitionMinSize(minSize);
    }

    public int getIdleConnectionCount() {
        return getPooling().getIdleConnectionCount();
    }

    public int getConnectionCount() {
        return getPooling().getConnectionCount();
    }

    public int getBlockingTimeoutMilliseconds() {
        return getPooling().getBlockingTimeoutMilliseconds();
    }

    public void setBlockingTimeoutMilliseconds(int timeoutMilliseconds) {
        getPooling().setBlockingTimeoutMilliseconds(timeoutMilliseconds);
    }

    public int getIdleTimeoutMinutes() {
        return getPooling().getIdleTimeoutMinutes();
    }

    public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {
        getPooling().setIdleTimeoutMinutes(idleTimeoutMinutes);
    }

    private ConnectionInterceptor getStack() {
        return interceptors.getStack();
    }

    private ConnectionInterceptor getRecoveryStack() {
        return interceptors.getRecoveryStack();
    }

    private boolean getIsRecoverable() {
        return interceptors.getRecoveryStack() != null;
    }

    //public for persistence of pooling attributes (max, min size, blocking/idle timeouts)
    public PoolingSupport getPooling() {
        return interceptors.getPoolingAttributes();
    }

    public interface Interceptors {
        ConnectionInterceptor getStack();

        ConnectionInterceptor getRecoveryStack();
        
        PoolingSupport getPoolingAttributes();
    }

    public void doStart() throws Exception {

    }

    public void doStop() throws Exception {
        if (transactionManager != null) {
            transactionManager.unregisterNamedXAResourceFactory(name);
        }
        interceptors.getStack().destroy();
    }

    public void doFail() {
        if (transactionManager != null) {
            transactionManager.unregisterNamedXAResourceFactory(name);
        }
        interceptors.getStack().destroy();
    }
}
