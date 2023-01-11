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

package org.apache.geronimo.connector.outbound.connectionmanagerconfig;

import jakarta.transaction.TransactionManager;

import org.apache.geronimo.connector.outbound.ConnectionInterceptor;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class NoTransactions extends TransactionSupport {
    public static final TransactionSupport INSTANCE = new NoTransactions();

    private NoTransactions() {
    }

    public ConnectionInterceptor addXAResourceInsertionInterceptor(ConnectionInterceptor stack, String name) {
        return stack;
    }

    public ConnectionInterceptor addTransactionInterceptors(ConnectionInterceptor stack, TransactionManager transactionManager) {
        return stack;
    }
    
    public boolean isRecoverable() {
        return false;
    }
}
