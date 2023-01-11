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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.resource.spi.ConnectionEvent;
import jakarta.resource.spi.ConnectionEventListener;

/**
 * ConnectionEventListener.java
 *
 *
 * Created: Thu Oct  2 14:57:43 2003
 *
 * @version 1.0
 */
public class GeronimoConnectionEventListener implements ConnectionEventListener {

    private static Logger log = Logger.getLogger(GeronimoConnectionEventListener.class.getName());

    private final ManagedConnectionInfo managedConnectionInfo;
    private final ConnectionInterceptor stack;
    private final List<ConnectionInfo> connectionInfos = new ArrayList<ConnectionInfo>();
    private boolean errorOccurred = false;

    public GeronimoConnectionEventListener(
            final ConnectionInterceptor stack,
            final ManagedConnectionInfo managedConnectionInfo) {
        this.stack = stack;
        this.managedConnectionInfo = managedConnectionInfo;
    }

    public void connectionClosed(ConnectionEvent connectionEvent) {
        if (connectionEvent.getSource() != managedConnectionInfo.getManagedConnection()) {
            throw new IllegalArgumentException(
                    "ConnectionClosed event received from wrong ManagedConnection. Expected "
                    + managedConnectionInfo.getManagedConnection()
                    + ", actual "
                    + connectionEvent.getSource());
        }
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"connectionClosed called with " + connectionEvent.getConnectionHandle() + " for MCI: " + managedConnectionInfo + " and MC: " + managedConnectionInfo.getManagedConnection());
        }
        ConnectionInfo ci = new ConnectionInfo(managedConnectionInfo);
        ci.setConnectionHandle(connectionEvent.getConnectionHandle());
        try {
            stack.returnConnection(ci, ConnectionReturnAction.RETURN_HANDLE);
        } catch (Throwable e) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST,"connectionClosed failed with " + connectionEvent.getConnectionHandle() + " for MCI: " + managedConnectionInfo + " and MC: " + managedConnectionInfo.getManagedConnection(), e);
            }
            if (e instanceof Error) {
                throw (Error)e;
            }
        }
    }

    public void connectionErrorOccurred(ConnectionEvent connectionEvent) {
        if (connectionEvent.getSource() != managedConnectionInfo.getManagedConnection()) {
            throw new IllegalArgumentException(
                    "ConnectionError event received from wrong ManagedConnection. Expected "
                    + managedConnectionInfo.getManagedConnection()
                    + ", actual "
                    + connectionEvent.getSource());
        }
        log.log(Level.WARNING,"connectionErrorOccurred called with " + connectionEvent.getConnectionHandle(), connectionEvent.getException());
        boolean errorOccurred = this.errorOccurred;
        this.errorOccurred = true;
        if (!errorOccurred) {
            ConnectionInfo ci = new ConnectionInfo(managedConnectionInfo);
            ci.setConnectionHandle(connectionEvent.getConnectionHandle());
            stack.returnConnection(ci, ConnectionReturnAction.DESTROY);
        }
    }

    public void localTransactionStarted(ConnectionEvent event) {
        //TODO implement this method
    }

    /**
     * The <code>localTransactionCommitted</code> method
     *
     * @param event a <code>ConnectionEvent</code> value
     * todo implement this method
     */
    public void localTransactionCommitted(ConnectionEvent event) {
    }

    /**
     * The <code>localTransactionRolledback</code> method
     *
     * @param event a <code>ConnectionEvent</code> value
     * todo implement this method
     */
    public void localTransactionRolledback(ConnectionEvent event) {
    }

    public void addConnectionInfo(ConnectionInfo connectionInfo) {
        assert connectionInfo.getConnectionHandle() != null;
        connectionInfos.add(connectionInfo);
    }

    public void removeConnectionInfo(ConnectionInfo connectionInfo) {
        assert connectionInfo.getConnectionHandle() != null;
        connectionInfos.remove(connectionInfo);
    }

    public boolean hasConnectionInfos() {
        return !connectionInfos.isEmpty();
    }

    public void clearConnectionInfos() {
        connectionInfos.clear();
    }

    public boolean hasConnectionInfo(ConnectionInfo connectionInfo) {
        return connectionInfos.contains(connectionInfo);
    }

    public boolean isFirstConnectionInfo(ConnectionInfo connectionInfo) {
        return !connectionInfos.isEmpty() && connectionInfos.get(0) == connectionInfo;
    }

    public Collection<ConnectionInfo> getConnectionInfos() {
        return Collections.unmodifiableCollection(connectionInfos);
    }

}
