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

package org.apache.geronimo.transaction.manager;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * TODO improve shutdown
 *
 * @version $Revision$ $Date$
 */
public class TransactionTimer {
    private static volatile long currentTime;

    private static class CurrentTime extends Thread {
        protected CurrentTime() {
            currentTime = System.currentTimeMillis();
            setContextClassLoader(null);
        }

        public void run() {
            for (; ;) {
                currentTime = System.currentTimeMillis();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore exception
                }
            }
        }
    }

    static {
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		CurrentTime tm = new CurrentTime();
		tm.setDaemon(true);
		tm.start();
		return null;
	    }
	});
    }

    public static long getCurrentTime() {
        return currentTime;
    }

}
