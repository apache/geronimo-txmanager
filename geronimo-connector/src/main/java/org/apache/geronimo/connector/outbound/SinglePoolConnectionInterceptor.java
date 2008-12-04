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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * SinglePoolConnectionInterceptor chooses a single connection from the pool.  If selectOneAssumeMatch
 * is true, it simply returns the selected connection.
 * THIS SHOULD BE USED ONLY IF MAXIMUM SPEED IS ESSENTIAL AND YOU HAVE THOROUGLY CHECKED THAT
 * MATCHING WOULD SUCCEED ON THE SELECTED CONNECTION. (i.e., read the docs on your connector
 * to find out how matching works)
 * If selectOneAssumeMatch is false, it checks with the ManagedConnectionFactory that the
 * selected connection does match before returning it: if not it throws an exception.
 *
 * @version $Rev$ $Date$
 */
public class SinglePoolConnectionInterceptor extends AbstractSinglePoolConnectionInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SinglePoolConnectionInterceptor.class.getName());

    private boolean selectOneAssumeMatch;

    //pool is mutable but only changed when protected by write lock on resizelock in superclass
//    private PoolDeque pool;
    private final List<ManagedConnectionInfo> pool;

    public SinglePoolConnectionInterceptor(final ConnectionInterceptor next,
                                           int maxSize,
                                           int minSize,
                                           int blockingTimeoutMilliseconds,
                                           int idleTimeoutMinutes,
                                           boolean selectOneAssumeMatch) {
        super(next, maxSize, minSize, blockingTimeoutMilliseconds, idleTimeoutMinutes);
//        pool = new PoolDeque(maxSize);
        pool = new ArrayList<ManagedConnectionInfo>(maxSize);
        this.selectOneAssumeMatch = selectOneAssumeMatch;
    }

    protected void internalGetConnection(ConnectionInfo connectionInfo) throws ResourceException {
        synchronized (pool) {
            if (destroyed) {
                throw new ResourceException("ManagedConnection pool has been destroyed");
            }

            ManagedConnectionInfo newMCI;
            if (pool.isEmpty()) {
                next.getConnection(connectionInfo);
                connectionCount++;
                if (log.isTraceEnabled()) {
                    log.trace("Supplying new connection MCI: " + connectionInfo.getManagedConnectionInfo() + " MC: " + connectionInfo.getManagedConnectionInfo().getManagedConnection() + " from pool: " + this);
                }
                return;
            } else {
                newMCI = pool.remove(pool.size() - 1);
            }
            if (connectionCount < minSize) {
                timer.schedule(new FillTask(connectionInfo), 10);
            }
            if (selectOneAssumeMatch) {
                connectionInfo.setManagedConnectionInfo(newMCI);
                if (log.isTraceEnabled()) {
                    log.trace("Supplying pooled connection without checking matching MCI: " + connectionInfo.getManagedConnectionInfo() + " MC: " + connectionInfo.getManagedConnectionInfo().getManagedConnection() + " from pool: " + this);
                }
                return;
            }
            try {
                ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
                ManagedConnection matchedMC = newMCI.getManagedConnectionFactory().matchManagedConnections(Collections.singleton(newMCI.getManagedConnection()),
                        mci.getSubject(),
                        mci.getConnectionRequestInfo());
                if (matchedMC != null) {
                    connectionInfo.setManagedConnectionInfo(newMCI);
                    if (log.isTraceEnabled()) {
                        log.trace("Supplying pooled connection  MCI: " + connectionInfo.getManagedConnectionInfo() + " MC: " + connectionInfo.getManagedConnectionInfo().getManagedConnection() + " from pool: " + this);
                    }
                } else {
                    //matching failed.
                    ConnectionInfo returnCI = new ConnectionInfo();
                    returnCI.setManagedConnectionInfo(newMCI);
                    returnConnection(returnCI, ConnectionReturnAction.RETURN_HANDLE);
                    throw new ResourceException("The pooling strategy does not match the MatchManagedConnections implementation.  Please investigate and reconfigure this pool");
                }
            } catch (ResourceException e) {
                //something is wrong: destroy connection, rethrow, release permit
                ConnectionInfo returnCI = new ConnectionInfo();
                returnCI.setManagedConnectionInfo(newMCI);
                returnConnection(returnCI, ConnectionReturnAction.DESTROY);
                throw e;
            }
        }
    }

    protected void internalDestroy() {
        synchronized (pool) {
            while (!pool.isEmpty()) {
                ManagedConnection mc = pool.remove(pool.size() - 1).getManagedConnection();
                if (mc != null) {
                    try {
                        mc.destroy();
                    }
                    catch (ResourceException re) {
                        //ignore
                    }
                }
            }
        }
    }

    protected Object getPool() {
        return pool;
    }

    protected void doAdd(ManagedConnectionInfo mci) {
        pool.add(mci);
    }

    protected boolean doRemove(ManagedConnectionInfo mci) {
        return !pool.remove(mci);
    }

    protected void transferConnections(int maxSize, int shrinkNow) {
        for (int i = 0; i < shrinkNow; i++) {
            ConnectionInfo killInfo = new ConnectionInfo(pool.get(0));
            internalReturn(killInfo, ConnectionReturnAction.DESTROY);
        }
    }

    public int getIdleConnectionCount() {
        return pool.size();
    }


    protected void getExpiredManagedConnectionInfos(long threshold, List<ManagedConnectionInfo> killList) {
        synchronized (pool) {
            for (ManagedConnectionInfo mci : pool) {
                if (mci.getLastUsed() < threshold) {
                    killList.add(mci);
                }
            }
        }
    }

}
