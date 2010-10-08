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

package org.apache.geronimo.connector;

import java.util.Set; 

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.InvalidPropertyException; 
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.validation.ConstraintViolation; 
import javax.validation.ConstraintViolationException; 
import javax.validation.Validator; 
import javax.validation.ValidatorFactory; 

/**
 * Wrapper for ActivationSpec instances.
 * The framework assumes all RequiredConfigProperties are of type String, although it
 * is unclear if this is required by the spec.
 *
 * @version $Rev$ $Date$
 */
public class ActivationSpecWrapper {

    protected final ActivationSpec activationSpec;

    private final ResourceAdapterWrapper resourceAdapterWrapper;
    private final String containerId;
    private final ValidatorFactory validatorFactory; 
    // indicates we've validated the spec on the first usage. 
    private boolean validated = false; 

    /**
     * Default constructor required when a class is used as a GBean Endpoint.
     */
    public ActivationSpecWrapper() {
        activationSpec = null;
        containerId = null;
        resourceAdapterWrapper = null;
        validatorFactory = null; 
        validated = false; 
    }

    /**
     * Normal managed constructor.
     *
     * @param activationSpecClass Class of admin object to be wrapped.
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public ActivationSpecWrapper(final String activationSpecClass,
                                 final String containerId,
                                 final ResourceAdapterWrapper resourceAdapterWrapper,
                                 final ClassLoader cl, 
                                 final ValidatorFactory validatorFactory) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        Class clazz = cl.loadClass(activationSpecClass);
        this.activationSpec = (ActivationSpec) clazz.newInstance();
        this.containerId = containerId;
        this.resourceAdapterWrapper = resourceAdapterWrapper;
        this.validatorFactory = validatorFactory; 
        this.validated = false; 
    }

    /**
     */
    public ActivationSpecWrapper(ActivationSpec activationSpec, ResourceAdapterWrapper resourceAdapterWrapper, ValidatorFactory validatorFactory)  {
        this.activationSpec = activationSpec;
        this.resourceAdapterWrapper = resourceAdapterWrapper;
        this.containerId = null;
        this.validatorFactory = validatorFactory; 
        this.validated = false; 
    }

    /**
     * Returns class of wrapped ActivationSpec.
     *
     * @return class of wrapped ActivationSpec
     */
//    public String getActivationSpecClass() {
//        return activationSpecClass;
//    }

    public String getContainerId() {
        return containerId;
    }

    public ResourceAdapterWrapper getResourceAdapterWrapper() {
        return resourceAdapterWrapper;
    }


    //GBeanLifecycle implementation
    public void activate(final MessageEndpointFactory messageEndpointFactory) throws ResourceException {
        checkConstraints(activationSpec);
        
        ResourceAdapter resourceAdapter = activationSpec.getResourceAdapter();
        if (resourceAdapter == null) {
            resourceAdapterWrapper.registerResourceAdapterAssociation(activationSpec);
        }
        resourceAdapterWrapper.endpointActivation(messageEndpointFactory, activationSpec);
        resourceAdapterWrapper.doRecovery(activationSpec, containerId);
    }

    public void deactivate(final MessageEndpointFactory messageEndpointFactory) {
        ResourceAdapter resourceAdapter = activationSpec.getResourceAdapter();
        if (resourceAdapter != null) {
            resourceAdapterWrapper.deregisterRecovery(containerId);
            resourceAdapterWrapper.endpointDeactivation(messageEndpointFactory, activationSpec);
        } else {
            //this should never happen, activation spec should have been registered with r.a.
            throw new IllegalStateException("ActivationSpec was never registered with ResourceAdapter");
        }
    }
    
    /**
     * Validate an ActivationSpec instance on the first usage. 
     * 
     * @param spec   The spec instance to validate.
     */
    private void checkConstraints(ActivationSpec spec) throws InvalidPropertyException {
        if (!validated) {
            // There are two potential validations, self validation via the 
            // validate() method and container-driven validation using bean validation. 
            try {
                spec.validate();
            } catch (UnsupportedOperationException uoe) {
                // ignore if not implemented. 
            }

            // if we have a validator factory at this point, then validate 
            // the resource adaptor instance 
            if (validatorFactory != null) {
                Validator validator = validatorFactory.getValidator(); 

                Set generalSet = validator.validate(spec);
                if (!generalSet.isEmpty()) {
                    throw new ConstraintViolationException("Constraint violation for ActitvationSpec", generalSet); 
                }
            }
            validated = true; 
        }
    }
}
