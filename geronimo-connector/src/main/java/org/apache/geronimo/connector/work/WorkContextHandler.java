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


package org.apache.geronimo.connector.work;

import javax.resource.spi.work.WorkContext;
import javax.resource.spi.work.WorkCompletedException;

/**
 * @version $Rev$ $Date$
 */
public interface WorkContextHandler<E extends WorkContext> {

    void before(E workContext) throws WorkCompletedException;

    void after(E workContext) throws WorkCompletedException;

    boolean supports(Class<? extends WorkContext> clazz); 

    boolean required();

}
