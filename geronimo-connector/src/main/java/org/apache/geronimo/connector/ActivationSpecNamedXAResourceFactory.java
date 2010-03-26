/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.geronimo.connector;

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.ResourceAdapter;
import javax.transaction.SystemException;
import javax.transaction.xa.XAResource;
import org.apache.geronimo.transaction.manager.NamedXAResource;
import org.apache.geronimo.transaction.manager.NamedXAResourceFactory;
import org.apache.geronimo.transaction.manager.WrapperNamedXAResource;

/**
 * @version $Rev$ $Date$
 */
public class ActivationSpecNamedXAResourceFactory implements NamedXAResourceFactory {

    private final String name;
    private final ActivationSpec activationSpec;
    private final ResourceAdapter resourceAdapter;

    public ActivationSpecNamedXAResourceFactory(String name, ActivationSpec activationSpec, ResourceAdapter resourceAdapter) {
        this.name = name;
        this.activationSpec = activationSpec;
        this.resourceAdapter = resourceAdapter;
    }

    public String getName() {
        return name;
    }

    public NamedXAResource getNamedXAResource() throws SystemException {
        try {
            XAResource[] xaResources = resourceAdapter.getXAResources(new ActivationSpec[]{activationSpec});
            if (xaResources == null || xaResources.length == 0) {
                return null;
            }
            return new WrapperNamedXAResource(xaResources[0], name);
        } catch (ResourceException e) {
            throw (SystemException) new SystemException("Could not get XAResource for recovery for mdb: " + name).initCause(e);
        }
    }

    public void returnNamedXAResource(NamedXAResource namedXAResource) {
        // nothing to do AFAICT
    }
}
