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

import javax.resource.ResourceException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * TransactionEnlistingInterceptor.java
 * <p/>
 * <p/>
 * Created: Fri Sep 26 14:52:24 2003
 *
 * @version 1.0
 */
public class TransactionEnlistingInterceptor implements ConnectionInterceptor {
    protected static Log log = LogFactory.getLog(TransactionEnlistingInterceptor.class);

    private final ConnectionInterceptor next;
    private final TransactionManager transactionManager;

    public TransactionEnlistingInterceptor(ConnectionInterceptor next, TransactionManager transactionManager) {
        this.next = next;
        this.transactionManager = transactionManager;
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        next.getConnection(connectionInfo);
        try {
            ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();

            // get the current transation and status... if there is a problem just assume there is no transaction present
            Transaction transaction = TxUtil.getTransactionIfActive(transactionManager);
            if (transaction != null) {
                XAResource xares = mci.getXAResource();
                if (log.isTraceEnabled()) {
                    log.trace("Enlisting connection " + connectionInfo + " with XAResource " + xares + " in transaction: " + transaction);
                }
                transaction.enlistResource(xares);
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("not enlisting connection " + connectionInfo + " with XAResource " + mci.getXAResource() + " no transaction");
                }
            }
        } catch (SystemException e) {
            returnConnection(connectionInfo, ConnectionReturnAction.DESTROY);
            throw new ResourceException("Could not get transaction", e);
        } catch (RollbackException e) {
            //transaction is marked rolled back, so the xaresource could not have been enlisted
            next.returnConnection(connectionInfo, ConnectionReturnAction.RETURN_HANDLE);
            throw new ResourceException("Could not enlist resource in rolled back transaction", e);
        } catch (Throwable t) {
            returnConnection(connectionInfo, ConnectionReturnAction.DESTROY);
            throw new ResourceException("Unknown throwable when trying to enlist connection in tx", t);
        }
    }

    /**
     * The <code>returnConnection</code> method
     * <p/>
     * todo Probably the logic needs improvement if a connection
     * error occurred and we are destroying the handle.
     *
     * @param connectionInfo         a <code>ConnectionInfo</code> value
     * @param connectionReturnAction a <code>ConnectionReturnAction</code> value
     */
    public void returnConnection(ConnectionInfo connectionInfo,
                                 ConnectionReturnAction connectionReturnAction) {
        try {
            ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
            Transaction transaction = TxUtil.getTransactionIfActive(transactionManager);
            if (transaction != null) {
                XAResource xares = mci.getXAResource();
                if (log.isTraceEnabled()) {
                    log.trace("Delisting connection " + connectionInfo + " with XAResource " + xares + " in transaction: " + transaction, new Exception("stack trace"));
                }
                transaction.delistResource(xares, XAResource.TMSUSPEND);
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("not delisting connection " + connectionInfo + " with XAResource " + mci.getXAResource() + " no transaction");
                }
            }

        } catch (SystemException e) {
            //maybe we should warn???
            log.info("Could not delist resource: " + connectionInfo  + " with XAResource: " + connectionInfo.getManagedConnectionInfo().getXAResource(), e);
            connectionReturnAction = ConnectionReturnAction.DESTROY;
        } catch (IllegalStateException e) {
            connectionReturnAction = ConnectionReturnAction.DESTROY;
        }

        next.returnConnection(connectionInfo, connectionReturnAction);
    }

    public void destroy() {
        next.destroy();
    }

    public void info(StringBuilder s) {
        s.append(getClass().getName()).append("[transactionManager=").append(transactionManager).append("]\n");
        next.info(s);
    }

}
