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

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.resource.ResourceException;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.apache.geronimo.connector.ConnectionReleaser;
import org.apache.geronimo.connector.ConnectorTransactionContext;

/**
 * TransactionCachingInterceptor.java
 * TODO: This implementation does not take account of unshareable resources
 * TODO: This implementation does not take account of application security
 * where several connections with different security info are obtained.
 * TODO: This implementation does not take account of container managed security where,
 * within one transaction, a security domain boundary is crossed
 * and connections are obtained with two (or more) different subjects.
 * <p/>
 * I suggest a state pattern, with the state set in a threadlocal upon entering a component,
 * will be a usable implementation.
 * <p/>
 * The afterCompletion method will need to move to an interface,  and that interface include the
 * security info to distinguish connections.
 * <p/>
 * <p/>
 * Created: Mon Sep 29 15:07:07 2003
 *
 * @version 1.0
 */
public class TransactionCachingInterceptor implements ConnectionInterceptor, ConnectionReleaser {
    protected static Logger log = Logger.getLogger(TransactionCachingInterceptor.class.getName());

    private final ConnectionInterceptor next;
    private final TransactionManager transactionManager;

    public TransactionCachingInterceptor(ConnectionInterceptor next, TransactionManager transactionManager) {
        this.next = next;
        this.transactionManager = transactionManager;
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        //There can be an inactive transaction context when a connection is requested in
        //Synchronization.afterCompletion().

        // get the current transaction and status... if there is a problem just assume there is no transaction present
        Transaction transaction = TxUtil.getTransactionIfActive(transactionManager);
        if (transaction != null) {
            ManagedConnectionInfos managedConnectionInfos = ConnectorTransactionContext.get(transaction, this);
            if (connectionInfo.isUnshareable()) {
                if (!managedConnectionInfos.containsUnshared(connectionInfo.getManagedConnectionInfo())) {
                    next.getConnection(connectionInfo);
                    managedConnectionInfos.addUnshared(connectionInfo.getManagedConnectionInfo());
                    if (log.isLoggable(Level.FINEST)) {
                        log.log(Level.FINEST,"Enlisting connection already associated with handle " + infoString(connectionInfo));
                    }
                }
            } else {
                ManagedConnectionInfo managedConnectionInfo = managedConnectionInfos.getShared();
                if (managedConnectionInfo != null) {
                    ManagedConnectionInfo previousMci = connectionInfo.getManagedConnectionInfo();
                    if (previousMci != null && previousMci != managedConnectionInfo && previousMci.getManagedConnection() != null) {
                        //This might occur if more than one connection were obtained before a UserTransaction were started.
                        //enlists connection
                        next.getConnection(connectionInfo);
                        managedConnectionInfos.addUnshared(previousMci);
                        if (log.isLoggable(Level.FINEST)) {
                            log.log(Level.FINEST,"Enlisting existing connection associated with connection handle with current tx  " + infoString(connectionInfo));
                        }
                    } else {
                        connectionInfo.setManagedConnectionInfo(managedConnectionInfo);

                        //return;
                        if (log.isLoggable(Level.FINEST)) {
                            log.log(Level.FINEST,"supplying connection from tx cache  " + infoString(connectionInfo));
                        }
                    }
                } else {
                    next.getConnection(connectionInfo);
                    managedConnectionInfos.setShared(connectionInfo.getManagedConnectionInfo());
                    if (log.isLoggable(Level.FINEST)) {
                        log.log(Level.FINEST,"supplying connection from pool " + connectionInfo.getConnectionHandle() + " for managed connection " + connectionInfo.getManagedConnectionInfo().getManagedConnection() + " to tx caching interceptor " + this);
                    }
                }
            }
        } else {
            next.getConnection(connectionInfo);
        }
    }

    public void returnConnection(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {

        if (connectionReturnAction == ConnectionReturnAction.DESTROY) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST,"destroying connection " + infoString(connectionInfo));
            }
            next.returnConnection(connectionInfo, connectionReturnAction);
            return;
        }
        Transaction transaction;
        try {
            transaction = transactionManager.getTransaction();
            if (transaction != null) {
                if (TxUtil.isActive(transaction)) {
                    if (log.isLoggable(Level.FINEST)) {
                        log.log(Level.FINEST,"tx active, not returning connection  " + infoString(connectionInfo));
                    }
                    return;
                }
                //We are called from an afterCompletion synchronization.  Remove the MCI from the ManagedConnectionInfos
                //so we don't close it twice
                ManagedConnectionInfos managedConnectionInfos = ConnectorTransactionContext.get(transaction, this);
                managedConnectionInfos.remove(connectionInfo.getManagedConnectionInfo());
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST,"tx ended, return during synchronization afterCompletion " + infoString(connectionInfo));
                }
            }
        } catch (SystemException e) {
            //ignore
        }
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"tx ended, returning connection " + infoString(connectionInfo));
        }
        internalReturn(connectionInfo, connectionReturnAction);
    }

    private void internalReturn(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
        if (connectionInfo.getManagedConnectionInfo().hasConnectionHandles()) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST,"not returning connection from tx cache (has handles) " + infoString(connectionInfo));
            }
            return;
        }
        //No transaction, no handles, we return it.
        next.returnConnection(connectionInfo, connectionReturnAction);
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"completed return of connection through tx cache " + infoString(connectionInfo));
        }
    }

    public void destroy() {
        next.destroy();
    }

    public void afterCompletion(Object stuff) {
        ManagedConnectionInfos managedConnectionInfos = (ManagedConnectionInfos) stuff;
        ManagedConnectionInfo sharedMCI = managedConnectionInfos.getShared();
        if (sharedMCI != null) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST,"Transaction completed, attempting to return shared connection MCI: " + infoString(sharedMCI));
            }
            returnHandle(sharedMCI);
        }
        for (ManagedConnectionInfo managedConnectionInfo : managedConnectionInfos.getUnshared()) {
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST,"Transaction completed, attempting to return unshared connection MCI: " + infoString(managedConnectionInfo));
            }
            returnHandle(managedConnectionInfo);
        }
    }

    private void returnHandle(ManagedConnectionInfo managedConnectionInfo) {
        ConnectionInfo connectionInfo = new ConnectionInfo();
        connectionInfo.setManagedConnectionInfo(managedConnectionInfo);
        internalReturn(connectionInfo, ConnectionReturnAction.RETURN_HANDLE);
    }

    private String infoString(Object connectionInfo) {
        return "for tx caching interceptor " + this + " " + connectionInfo;
    }

    public void info(StringBuilder s) {
        s.append(getClass().getName()).append("[transactionManager=").append(transactionManager).append("]\n");
        next.info(s);
    }
    
    public static class ManagedConnectionInfos {
        private ManagedConnectionInfo shared;
        private Set<ManagedConnectionInfo> unshared = new HashSet<ManagedConnectionInfo>(1);

        public ManagedConnectionInfo getShared() {
            return shared;
        }

        public void setShared(ManagedConnectionInfo shared) {
            this.shared = shared;
        }

        public Set<ManagedConnectionInfo> getUnshared() {
            return unshared;
        }

        public void addUnshared(ManagedConnectionInfo unsharedMCI) {
            unshared.add(unsharedMCI);
        }

        public boolean containsUnshared(ManagedConnectionInfo unsharedMCI) {
            return unshared.contains(unsharedMCI);
        }

        public void remove(ManagedConnectionInfo managedConnectionInfo) {
            if (shared == managedConnectionInfo) {
                shared = null;
            } else {
                unshared.remove(managedConnectionInfo);
            }
        }
    }
}
