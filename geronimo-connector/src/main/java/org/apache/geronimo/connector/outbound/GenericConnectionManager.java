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

import javax.transaction.TransactionManager;

import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PartitionedPool;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.PoolingSupport;
import org.apache.geronimo.connector.outbound.connectionmanagerconfig.TransactionSupport;
import org.apache.geronimo.connector.outbound.connectiontracking.ConnectionTracker;
import org.apache.geronimo.transaction.manager.RecoverableTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GenericConnectionManager sets up a connection manager stack according to the
 * policies described in the attributes.
 *
 * @version $Rev$ $Date$
 */
public class GenericConnectionManager extends AbstractConnectionManager {
    protected static final Logger log = LoggerFactory.getLogger(AbstractSinglePoolConnectionInterceptor.class);

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
     * @param name name
     * @param classLoader classloader this component is running in.
     */
    public GenericConnectionManager(TransactionSupport transactionSupport,
                                    PoolingSupport pooling,
                                    SubjectSource subjectSource,
                                    ConnectionTracker connectionTracker,
                                    RecoverableTransactionManager transactionManager,
                                    String name,
                                    ClassLoader classLoader) {
        super(new InterceptorsImpl(transactionSupport, pooling, subjectSource, name, connectionTracker, transactionManager, classLoader), transactionManager, name);
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
                                ClassLoader classLoader) {
            //check for consistency between attributes
            if (subjectSource == null && pooling instanceof PartitionedPool && ((PartitionedPool) pooling).isPartitionBySubject()) {
                throw new IllegalStateException("To use Subject in pooling, you need a SecurityDomain");
            }

            //Set up the interceptor stack
            MCFConnectionInterceptor tail = new MCFConnectionInterceptor();
            ConnectionInterceptor stack = tail;

            stack = transactionSupport.addXAResourceInsertionInterceptor(stack, name);
            stack = pooling.addPoolingInterceptors(stack);
            if (log.isTraceEnabled()) {
                log.trace("Connection Manager " + name + " installed pool " + stack);
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
