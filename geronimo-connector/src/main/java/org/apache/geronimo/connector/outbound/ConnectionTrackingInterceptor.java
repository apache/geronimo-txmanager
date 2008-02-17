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

import java.util.Collection;
import java.util.Iterator;

import javax.resource.ResourceException;
import javax.resource.spi.DissociatableManagedConnection;
import javax.resource.spi.ManagedConnection;

import org.apache.geronimo.connector.outbound.connectiontracking.ConnectionTracker;

/**
 * ConnectionTrackingInterceptor.java handles communication with the
 * CachedConnectionManager.  On method call entry, cached handles are
 * checked for the correct Subject.  On method call exit, cached
 * handles are disassociated if possible. On getting or releasing
 * a connection the CachedConnectionManager is notified.
 *
 *
 * @version $Rev$ $Date$
 */
public class ConnectionTrackingInterceptor implements ConnectionInterceptor {

    private final ConnectionInterceptor next;
    private final String key;
    private final ConnectionTracker connectionTracker;

    public ConnectionTrackingInterceptor(
            final ConnectionInterceptor next,
            final String key,
            final ConnectionTracker connectionTracker
            ) {
        this.next = next;
        this.key = key;
        this.connectionTracker = connectionTracker;
    }

    /**
     * called by: GenericConnectionManager.allocateConnection, GenericConnectionManager.associateConnection, and enter.
     * in: connectionInfo is non-null, and has non-null ManagedConnectionInfo with non-null managedConnectionfactory.
     * connection handle may or may not be null.
     * out: connectionInfo has non-null connection handle, non null ManagedConnectionInfo with non-null ManagedConnection and GeronimoConnectionEventListener.
     * connection tracker has been notified of handle-managed connection association.
     * @param connectionInfo
     * @throws ResourceException
     */
    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        connectionTracker.setEnvironment(connectionInfo, key);
        next.getConnection(connectionInfo);
        connectionTracker.handleObtained(this, connectionInfo, false);
    }

    /**
     * Called when a proxied connection which has been released need to be reassociated with a real connection.
     */
    public void reassociateConnection(ConnectionInfo connectionInfo) throws ResourceException {
        connectionTracker.setEnvironment(connectionInfo, key);
        next.getConnection(connectionInfo);
        connectionTracker.handleObtained(this, connectionInfo, true);
    }

    /**
     * called by: GeronimoConnectionEventListener.connectionClosed, GeronimoConnectionEventListener.connectionErrorOccurred, exit
     * in: handle has already been dissociated from ManagedConnection. connectionInfo not null, has non-null ManagedConnectionInfo, ManagedConnectionInfo has non-null ManagedConnection
     * handle can be null if called from error in ManagedConnection in pool.
     * out: connectionTracker has been notified, ManagedConnectionInfo null.
     * @param connectionInfo
     * @param connectionReturnAction
     */
    public void returnConnection(
            ConnectionInfo connectionInfo,
            ConnectionReturnAction connectionReturnAction) {
        connectionTracker.handleReleased(this, connectionInfo, connectionReturnAction);
        next.returnConnection(connectionInfo, connectionReturnAction);
    }

    public void destroy() {
        next.destroy();
    }
    
    public void enter(Collection<ConnectionInfo> connectionInfos) throws ResourceException {
        for (ConnectionInfo connectionInfo : connectionInfos) {
            next.getConnection(connectionInfo);
        }

    }

    public void exit(Collection<ConnectionInfo> connectionInfos)
            throws ResourceException {
        for (Iterator<ConnectionInfo> iterator = connectionInfos.iterator(); iterator.hasNext();) {
            ConnectionInfo connectionInfo = iterator.next();
            if (connectionInfo.isUnshareable()) {
                //if one is, they all are
                return;
            }
            ManagedConnectionInfo managedConnectionInfo = connectionInfo.getManagedConnectionInfo();
            ManagedConnection managedConnection = managedConnectionInfo.getManagedConnection();
            if (managedConnection instanceof DissociatableManagedConnection
                    && managedConnectionInfo.isFirstConnectionInfo(connectionInfo)) {
                iterator.remove();
                ((DissociatableManagedConnection) managedConnection).dissociateConnections();
                managedConnectionInfo.clearConnectionHandles();
                //todo this needs some kind of check so cx isn't returned more than once
                //in case dissociate calls connection closed event and returns cx to pool.
                returnConnection(connectionInfo, ConnectionReturnAction.RETURN_HANDLE);
            }
        }
    }
}
