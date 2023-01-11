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

import org.apache.geronimo.transaction.manager.WrapperNamedXAResource;

/**
 * XAResourceInsertionInterceptor.java
 *
 *
 * @version $Rev$ $Date$
 */
public class XAResourceInsertionInterceptor implements ConnectionInterceptor {

    private final ConnectionInterceptor next;
    private final String name;

    public XAResourceInsertionInterceptor(final ConnectionInterceptor next, final String name) {
        this.next = next;
        this.name = name;
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        next.getConnection(connectionInfo);
        ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
        mci.setXAResource(new WrapperNamedXAResource(mci.getManagedConnection().getXAResource(), name));
    }

    public void returnConnection(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
        next.returnConnection(connectionInfo, connectionReturnAction);
    }
    
    public void destroy() {
        next.destroy();
    }

    public void info(StringBuilder s) {
        s.append(getClass().getName()).append("[name=").append(name).append("]\n");
        next.info(s);
    }

}
