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

import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.transaction.TransactionManager;

import org.apache.geronimo.connector.outbound.connectionmanagerconfig.LocalTransactions;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.NoTransactions;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PartitionedPool;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PoolingSupport;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.TransactionSupport;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.XATransactions;
import org.apache.geronimo.connector.outbound.connectiontracking.ConnectionTracker;
import org.apache.geronimo.transaction.manager.RecoverableTransactionManager;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GenericConnectionManager sets up a connection manager stack according to the
 * policies described in the attributes.
 *
 * @version $Rev$ $Date$
 */
public class GenericConnectionManager extends AbstractConnectionManager {
    protected static final Logger log = Logger.getLogger(GenericConnectionManager.class.getName());

    //default constructor to support externalizable subclasses
    public GenericConnectionManager() {
        super();
    }

    /**
     *
     * @param transactionSupport configuration of transaction support
     * @param pooling configuration of pooling
     * @param subjectSource If not null, use container managed security, getting the Subject from the SubjectSource
     * @param connectionTracker tracks connections between calls as needed
     * @param transactionManager transaction manager
     * @param mcf
     * @param name name
     * @param classLoader classloader this component is running in.
     */
    public GenericConnectionManager(TransactionSupport transactionSupport,
                                    PoolingSupport pooling,
                                    SubjectSource subjectSource,
                                    ConnectionTracker connectionTracker,
                                    RecoverableTransactionManager transactionManager,
                                    ManagedConnectionFactory mcf,
                                    String name,
                                    ClassLoader classLoader) {
        super(new InterceptorsImpl(transactionSupport, pooling, subjectSource, name, connectionTracker, transactionManager, mcf, classLoader), transactionManager, mcf, name);
    }

    private static class InterceptorsImpl implements AbstractConnectionManager.Interceptors {

        private final ConnectionInterceptor stack;
        private final ConnectionInterceptor recoveryStack;
        private final PoolingSupport poolingSupport;

        /**
         * Order of constructed interceptors:
         * <p/>
         * ConnectionTrackingInterceptor (connectionTracker != null)
         * TCCLInterceptor
         * ConnectionHandleInterceptor
         * TransactionCachingInterceptor (useTransactions & useTransactionCaching)
         * TransactionEnlistingInterceptor (useTransactions)
         * SubjectInterceptor (realmBridge != null)
         * SinglePoolConnectionInterceptor or MultiPoolConnectionInterceptor
         * LocalXAResourceInsertionInterceptor or XAResourceInsertionInterceptor (useTransactions (&localTransactions))
         * MCFConnectionInterceptor
         */
        public InterceptorsImpl(TransactionSupport transactionSupport,
                                PoolingSupport pooling,
                                SubjectSource subjectSource,
                                String name,
                                ConnectionTracker connectionTracker,
                                TransactionManager transactionManager,
                                ManagedConnectionFactory mcf, ClassLoader classLoader) {
            //check for consistency between attributes
            if (subjectSource == null && pooling instanceof PartitionedPool && ((PartitionedPool) pooling).isPartitionBySubject()) {
                throw new IllegalStateException("To use Subject in pooling, you need a SecurityDomain");
            }

            if (mcf == null) {
                throw new NullPointerException("No ManagedConnectionFactory supplied for " + name);
            }
            if (mcf instanceof jakarta.resource.spi.TransactionSupport) {
                jakarta.resource.spi.TransactionSupport txSupport = (jakarta.resource.spi.TransactionSupport)mcf;
                jakarta.resource.spi.TransactionSupport.TransactionSupportLevel txSupportLevel = txSupport.getTransactionSupport();
                log.info("Runtime TransactionSupport level: " + txSupportLevel);
                if (txSupportLevel != null) {
                    if (txSupportLevel == jakarta.resource.spi.TransactionSupport.TransactionSupportLevel.NoTransaction) {
                        transactionSupport = NoTransactions.INSTANCE;
                    } else if (txSupportLevel == jakarta.resource.spi.TransactionSupport.TransactionSupportLevel.LocalTransaction) {
                        if (transactionSupport != NoTransactions.INSTANCE) {
                            transactionSupport = LocalTransactions.INSTANCE;
                        }
                    } else {
                        if (transactionSupport != NoTransactions.INSTANCE && transactionSupport != LocalTransactions.INSTANCE) {
                            transactionSupport = new XATransactions(true, false);
                        }
                    }
                }
            } else {
                log.info("No runtime TransactionSupport");
            }

            //Set up the interceptor stack
            MCFConnectionInterceptor tail = new MCFConnectionInterceptor();
            ConnectionInterceptor stack = tail;

            stack = transactionSupport.addXAResourceInsertionInterceptor(stack, name);
            stack = pooling.addPoolingInterceptors(stack);
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST,"Connection Manager " + name + " installed pool " + stack);
            }

            this.poolingSupport = pooling;
            stack = transactionSupport.addTransactionInterceptors(stack, transactionManager);

            if (subjectSource != null) {
                stack = new SubjectInterceptor(stack, subjectSource);
            }

            if (transactionSupport.isRecoverable()) {
        	this.recoveryStack = new TCCLInterceptor(stack, classLoader);
            } else {
        	this.recoveryStack = null;
            }
            

            stack = new ConnectionHandleInterceptor(stack);
            stack = new TCCLInterceptor(stack, classLoader);
            if (connectionTracker != null) {
                stack = new ConnectionTrackingInterceptor(stack,
                        name,
                        connectionTracker);
            }
            tail.setStack(stack);
            this.stack = stack;
            if (log.isLoggable(Level.FINE)) {
                StringBuilder s = new StringBuilder("ConnectionManager Interceptor stack;\n");
                stack.info(s);
                log.log(Level.FINE, s.toString());
            }
        }

        public ConnectionInterceptor getStack() {
            return stack;
        }

        public ConnectionInterceptor getRecoveryStack() {
            return recoveryStack;
        }
        
        public PoolingSupport getPoolingAttributes() {
            return poolingSupport;
        }

    }

}
