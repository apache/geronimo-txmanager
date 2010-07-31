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

/**
 * @version $Rev$ $Date$
 */
public class TCCLInterceptor implements ConnectionInterceptor{
    private final ConnectionInterceptor next;
    private final ClassLoader classLoader;

    public TCCLInterceptor(ConnectionInterceptor next, ClassLoader classLoader) {
        this.next = next;
        this.classLoader = classLoader;
    }


    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        Thread currentThread = Thread.currentThread();
        ClassLoader oldClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(classLoader);
            next.getConnection(connectionInfo);
        } finally {
            currentThread.setContextClassLoader(oldClassLoader);
        }
    }

    public void returnConnection(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
        Thread currentThread = Thread.currentThread();
        ClassLoader oldClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(classLoader);
            next.returnConnection(connectionInfo, connectionReturnAction);
        } finally {
            currentThread.setContextClassLoader(oldClassLoader);
        }
    }
    
    public void destroy() {
        this.next.destroy();
    }

    public void info(StringBuilder s) {
        s.append(getClass().getName()).append("[classLoader=").append(classLoader).append("]\n");
        next.info(s);
    }
    
}
