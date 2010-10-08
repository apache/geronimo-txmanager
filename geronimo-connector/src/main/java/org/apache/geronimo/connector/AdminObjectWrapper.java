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

import javax.resource.spi.ResourceAdapterAssociation;
import javax.validation.ConstraintViolation; 
import javax.validation.ConstraintViolationException; 
import javax.validation.Validator; 
import javax.validation.ValidatorFactory; 


/**
 * Wrapper around AdminObject that manages the AdminObject lifecycle. 
 *
 * @version $Rev$ $Date$
 */
public class AdminObjectWrapper {

    private final String adminObjectInterface;
    private final String adminObjectClass;
    protected ResourceAdapterWrapper resourceAdapterWrapper;
    
    protected Object adminObject;
    private final ValidatorFactory validatorFactory; 

    /**
     * Normal managed constructor.
     *
     * @param adminObjectInterface Interface the proxy will implement.
     * @param adminObjectClass Class of admin object to be wrapped.
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public AdminObjectWrapper(final String adminObjectInterface,
                              final String adminObjectClass,
                              final ResourceAdapterWrapper resourceAdapterWrapper,
                              final ClassLoader cl,
                              final ValidatorFactory validatorFactory) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        this.adminObjectInterface = adminObjectInterface;
        this.adminObjectClass = adminObjectClass;
        this.resourceAdapterWrapper = resourceAdapterWrapper;
        this.validatorFactory = validatorFactory; 
        Class clazz = cl.loadClass(adminObjectClass);
        adminObject = clazz.newInstance();
    }

    public String getAdminObjectInterface() {
        return adminObjectInterface;
    }

    /**
     * Returns class of wrapped AdminObject.
     * @return class of wrapped AdminObject
     */
    public String getAdminObjectClass() {
        return adminObjectClass;
    }


    /**
     * Starts the AdminObject.  This will validate the instance and register the object instance           
     * with its ResourceAdapter
     *
     * @throws Exception if the target failed to start; this will cause a transition to the failed state
     */
    public void doStart() throws Exception {
        // if we have a validator factory at this point, then validate 
        // the resource adaptor instance 
        if (validatorFactory != null) {
            Validator validator = validatorFactory.getValidator(); 
            
            Set generalSet = validator.validate(adminObject);
            if (!generalSet.isEmpty()) {
                throw new ConstraintViolationException("Constraint violation for AdminObject " + adminObjectClass, generalSet); 
            }
        }
        if (adminObject instanceof ResourceAdapterAssociation) {
            if (resourceAdapterWrapper == null) {
                throw new IllegalStateException("Admin object expects to be registered with a ResourceAdapter, but there is no ResourceAdapter");
            }
            resourceAdapterWrapper.registerResourceAdapterAssociation((ResourceAdapterAssociation) adminObject);
        }
    }

    /**
     * Stops the target.  This is a NOP for AdminObjects
     *
     * @throws Exception if the target failed to stop; this will cause a transition to the failed state
     */
    public void doStop() throws Exception {
    }

    /**
     * Fails the target.  This is a nop for an AdminObject. 
     */
    public void doFail() {
    }
}
