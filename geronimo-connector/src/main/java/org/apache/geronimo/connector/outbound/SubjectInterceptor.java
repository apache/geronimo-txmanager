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
import jakarta.resource.spi.ApplicationServerInternalException;
import javax.security.auth.Subject;

/**
 * SubjectInterceptor.java This is installed only when the plan includes a container-managed-security element.
 *
 *
 * Created: Mon Oct  6 14:31:56 2003
 *
 * @version $Rev$ $Date$
 */
public class SubjectInterceptor implements ConnectionInterceptor {

    private final ConnectionInterceptor next;
    private final SubjectSource subjectSource;

    public SubjectInterceptor(final ConnectionInterceptor next, final SubjectSource subjectSource) {
        this.next = next;
        this.subjectSource = subjectSource;
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        Subject currentSubject = null;
        if (!connectionInfo.isApplicationManagedSecurity()) {
            try {
                currentSubject = subjectSource.getSubject();
            } catch (SecurityException e) {
                throw new ResourceException("Can not obtain Subject for login", e);
            }
            if (currentSubject == null) {
                throw new ResourceException("No subject for container managed security");
            }
        }
        ManagedConnectionInfo originalManagedConnectionInfo = connectionInfo.getManagedConnectionInfo();
        //No existing managed connection, get an appropriate one and return.
        if (originalManagedConnectionInfo.getManagedConnection() == null) {
            originalManagedConnectionInfo.setSubject(currentSubject);
            next.getConnection(connectionInfo);
        } else {
            Subject oldSubject = originalManagedConnectionInfo.getSubject();
            if (currentSubject == null ? oldSubject != null : !currentSubject.equals(oldSubject)) {
                if (connectionInfo.isUnshareable()) {
                    throw new ApplicationServerInternalException("Unshareable resource is attempting to change security context: expected request under: " + oldSubject + ", received request under: " + currentSubject);
                } else {
                    //existing managed connection, wrong subject: must re-associate.
                    //make a ConnectionInfo to process removing the handle from the old mc
                    ConnectionInfo returningConnectionInfo = new ConnectionInfo();
                    returningConnectionInfo.setManagedConnectionInfo(originalManagedConnectionInfo);
                    //This should decrement handle count, but not close the handle, when returnConnection is called
                    //I'm not sure how to test/assure this.
                    returningConnectionInfo.setConnectionHandle(connectionInfo.getConnectionHandle());

                    //make a new ManagedConnectionInfo for the mc we will ask for
                    ManagedConnectionInfo newManagedConnectionInfo =
                            new ManagedConnectionInfo(
                                    originalManagedConnectionInfo.getManagedConnectionFactory(),
                                    originalManagedConnectionInfo.getConnectionRequestInfo());
                    newManagedConnectionInfo.setSubject(currentSubject);
                    connectionInfo.setManagedConnectionInfo(newManagedConnectionInfo);
                    next.getConnection(connectionInfo);
                    //process the removal of the handle from the previous mc
                    returnConnection(returningConnectionInfo, ConnectionReturnAction.RETURN_HANDLE);
                }
            } else {
                //otherwise, the current ManagedConnection matches the security info, we keep it.
                //set up the tx context
                next.getConnection(connectionInfo);
            }
        }
    }

    public void returnConnection(
            ConnectionInfo connectionInfo,
            ConnectionReturnAction connectionReturnAction) {
        next.returnConnection(connectionInfo, connectionReturnAction);
    }

    public void destroy() {
        next.destroy();
    }

    public void info(StringBuilder s) {
        s.append(getClass().getName()).append("[subjectSource=").append(subjectSource).append("]\n");
        next.info(s);
    }

}
