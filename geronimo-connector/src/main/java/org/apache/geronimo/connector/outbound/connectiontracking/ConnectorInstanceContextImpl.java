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

package org.apache.geronimo.connector.outbound.connectiontracking;

import org.apache.geronimo.connector.outbound.ConnectionTrackingInterceptor;
import org.apache.geronimo.connector.outbound.ConnectionInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * Simple implementation of ComponentContext satisfying invariant.
 *
 * @version $Rev$ $Date$
 *
 * */
public class ConnectorInstanceContextImpl implements ConnectorInstanceContext {
    private final Map<ConnectionTrackingInterceptor, Set<ConnectionInfo>> connectionManagerMap = new HashMap<ConnectionTrackingInterceptor, Set<ConnectionInfo>>();
    private final Set<String> unshareableResources;
    private final Set<String> applicationManagedSecurityResources;

    public ConnectorInstanceContextImpl(Set<String> unshareableResources, Set<String> applicationManagedSecurityResources) {
        this.unshareableResources = unshareableResources;
        this.applicationManagedSecurityResources = applicationManagedSecurityResources;
    }

    public Map<ConnectionTrackingInterceptor, Set<ConnectionInfo>> getConnectionManagerMap() {
        return connectionManagerMap;
    }

    public Set<String> getUnshareableResources() {
        return unshareableResources;
    }

    public Set<String> getApplicationManagedSecurityResources() {
        return applicationManagedSecurityResources;
    }
}
