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
import org.apache.geronimo.connector.outbound.ThreadLocalCachingConnectionInterceptor;
import org.apache.geronimo.connector.outbound.TransactionCachingInterceptor;
import org.apache.geronimo.connector.outbound.TransactionEnlistingInterceptor;
import org.apache.geronimo.connector.outbound.XAResourceInsertionInterceptor;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class XATransactions extends TransactionSupport {
    
    private boolean useTransactionCaching;
    private boolean useThreadCaching;

    public XATransactions(boolean useTransactionCaching, boolean useThreadCaching) {
        this.useTransactionCaching = useTransactionCaching;
        this.useThreadCaching = useThreadCaching;
    }

    public boolean isUseTransactionCaching() {
        return useTransactionCaching;
    }

    public void setUseTransactionCaching(boolean useTransactionCaching) {
        this.useTransactionCaching = useTransactionCaching;
    }

    public boolean isUseThreadCaching() {
        return useThreadCaching;
    }

    public void setUseThreadCaching(boolean useThreadCaching) {
        this.useThreadCaching = useThreadCaching;
    }

    public ConnectionInterceptor addXAResourceInsertionInterceptor(ConnectionInterceptor stack, String name) {
        return new XAResourceInsertionInterceptor(stack, name);
    }

    public ConnectionInterceptor addTransactionInterceptors(ConnectionInterceptor stack, TransactionManager transactionManager) {
        //experimental thread local caching
        if (isUseThreadCaching()) {
            //useMatching should be configurable
            stack = new ThreadLocalCachingConnectionInterceptor(stack, false);
        }
        stack = new TransactionEnlistingInterceptor(stack, transactionManager);
        if (isUseTransactionCaching()) {
            stack = new TransactionCachingInterceptor(stack, transactionManager);
        }
        return stack;
    }
    
    public boolean isRecoverable() {
        return true;
    }
}
