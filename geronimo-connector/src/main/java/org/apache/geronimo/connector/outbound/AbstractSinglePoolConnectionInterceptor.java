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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.security.auth.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Rev$ $Date$
 */
public abstract class AbstractSinglePoolConnectionInterceptor implements ConnectionInterceptor, PoolingAttributes {
    protected static Logger log = LoggerFactory.getLogger(AbstractSinglePoolConnectionInterceptor.class);
    protected final ConnectionInterceptor next;
    private final ReadWriteLock resizeLock = new ReentrantReadWriteLock();
    protected Semaphore permits;
    protected int blockingTimeoutMilliseconds;
    protected int connectionCount = 0;
    protected long idleTimeoutMilliseconds;
    private IdleReleaser idleReleaser;
    protected Timer timer = PoolIdleReleaserTimer.getTimer();
    protected int maxSize = 0;
    protected int minSize = 0;
    protected int shrinkLater = 0;
    protected volatile boolean destroyed = false;

    public AbstractSinglePoolConnectionInterceptor(final ConnectionInterceptor next,
                                                   int maxSize,
                                                   int minSize,
                                                   int blockingTimeoutMilliseconds,
                                                   int idleTimeoutMinutes) {
        this.next = next;
        this.maxSize = maxSize;
        this.minSize = minSize;
        this.blockingTimeoutMilliseconds = blockingTimeoutMilliseconds;
        setIdleTimeoutMinutes(idleTimeoutMinutes);
        permits = new Semaphore(maxSize, true);
    }

    public void getConnection(ConnectionInfo connectionInfo) throws ResourceException {
        if (connectionInfo.getManagedConnectionInfo().getManagedConnection() != null) {
            if (log.isTraceEnabled()) {
                log.trace("supplying already assigned connection from pool " + this + " " + connectionInfo);
            }
            return;
        }
        try {
            resizeLock.readLock().lock();
            try {
                if (permits.tryAcquire(blockingTimeoutMilliseconds, TimeUnit.MILLISECONDS)) {
                    try {
                        internalGetConnection(connectionInfo);
                    } catch (ResourceException e) {
                        permits.release();
                        throw e;
                    }
                } else {
                    throw new ResourceException("No ManagedConnections available "
                            + "within configured blocking timeout ( "
                            + blockingTimeoutMilliseconds
                            + " [ms] ) for pool " + this);

                }
            } finally {
                resizeLock.readLock().unlock();
            }

        } catch (InterruptedException ie) {
            throw new ResourceException("Interrupted while requesting permit.", ie);
        } // end of try-catch
    }

    protected abstract void internalGetConnection(ConnectionInfo connectionInfo) throws ResourceException;

    public void returnConnection(ConnectionInfo connectionInfo,
                                 ConnectionReturnAction connectionReturnAction) {
        if (log.isTraceEnabled()) {
            log.trace("returning connection " + connectionInfo.getConnectionHandle() + " for MCI " + connectionInfo.getManagedConnectionInfo() + " and MC " + connectionInfo.getManagedConnectionInfo().getManagedConnection() + " to pool " + this);
        }

        // not strictly synchronized with destroy(), but pooled operations in internalReturn() are...
        if (destroyed) {
            try {
                connectionInfo.getManagedConnectionInfo().getManagedConnection().destroy();
            } catch (ResourceException re) {
                // empty
            }
            return;
        }

        resizeLock.readLock().lock();
        try {
            ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
            if (connectionReturnAction == ConnectionReturnAction.RETURN_HANDLE && mci.hasConnectionHandles()) {
                if (log.isTraceEnabled()) {
                    log.trace("Return request at pool with connection handles! " + connectionInfo.getConnectionHandle() + " for MCI " + connectionInfo.getManagedConnectionInfo() + " and MC " + connectionInfo.getManagedConnectionInfo().getManagedConnection() + " to pool " + this, new Exception("Stack trace"));
                }
                return;
            }

            boolean releasePermit = internalReturn(connectionInfo, connectionReturnAction);

            if (releasePermit) {
                permits.release();
            }
        } finally {
            resizeLock.readLock().unlock();
        }
    }


    /**
     *
     * @param connectionInfo connection info to return to pool
     * @param connectionReturnAction whether to return to pool or destroy
     * @return true if a connection for which a permit was issued was returned (so the permit should be released),
     * false if no permit was issued (for instance if the connection was already in the pool and we are destroying it).
     */
    protected boolean internalReturn(ConnectionInfo connectionInfo, ConnectionReturnAction connectionReturnAction) {
        ManagedConnectionInfo mci = connectionInfo.getManagedConnectionInfo();
        ManagedConnection mc = mci.getManagedConnection();
        try {
            mc.cleanup();
        } catch (ResourceException e) {
            connectionReturnAction = ConnectionReturnAction.DESTROY;
        }

        boolean releasePermit;
        synchronized (getPool()) {
            // a bit redundant, but this closes a small timing hole...
            if (destroyed) {
                try {
                    mc.destroy();
                }
                catch (ResourceException re) {
                    //ignore
                }
                return doRemove(mci);
            }
            if (shrinkLater > 0) {
                //nothing can get in the pool while shrinkLater > 0, so releasePermit is false here.
                connectionReturnAction = ConnectionReturnAction.DESTROY;
                shrinkLater--;
                releasePermit = false;
            } else if (connectionReturnAction == ConnectionReturnAction.RETURN_HANDLE) {
                mci.setLastUsed(System.currentTimeMillis());
                doAdd(mci);
                return true;
            } else {
                releasePermit = doRemove(mci);
            }
        }
        //we must destroy connection.
        if (log.isTraceEnabled()) {
            log.trace("Discarding connection in pool " + this + " " + connectionInfo);
        }
        next.returnConnection(connectionInfo, connectionReturnAction);
        connectionCount--;
        return releasePermit;
    }

    protected abstract void internalDestroy();

    // Cancel the IdleReleaser TimerTask (fixes memory leak) and clean up the pool
    public void destroy() {
        destroyed = true;
        if (idleReleaser != null)
            idleReleaser.cancel();
        internalDestroy();
        next.destroy();
    }

    public int getPartitionCount() {
        return 1;
    }

    public int getPartitionMaxSize() {
        return maxSize;
    }

    public void setPartitionMaxSize(int newMaxSize) throws InterruptedException {
        if (newMaxSize <= 0) {
            throw new IllegalArgumentException("Max size must be positive, not " + newMaxSize);
        }
        if (newMaxSize != getPartitionMaxSize()) {
            resizeLock.writeLock().lock();
            try {
                ResizeInfo resizeInfo = new ResizeInfo(this.minSize, permits.availablePermits(), connectionCount, newMaxSize);
                permits = new Semaphore(newMaxSize, true);
                //pre-acquire permits for the existing checked out connections that will not be closed when they are returned.
                for (int i = 0; i < resizeInfo.getTransferCheckedOut(); i++) {
                    permits.acquire();
                }
                //make sure shrinkLater is 0 while discarding excess connections
                this.shrinkLater = 0;
                //transfer connections we are going to keep
                transferConnections(newMaxSize, resizeInfo.getShrinkNow());
                this.shrinkLater = resizeInfo.getShrinkLater();
                this.minSize = resizeInfo.getNewMinSize();
                this.maxSize = newMaxSize;
            } finally {
                resizeLock.writeLock().unlock();
            }
        }
    }

    protected abstract boolean doRemove(ManagedConnectionInfo mci);

    protected abstract void doAdd(ManagedConnectionInfo mci);

    protected abstract Object getPool();


    static final class ResizeInfo {

        private final int newMinSize;
        private final int shrinkNow;
        private final int shrinkLater;
        private final int transferCheckedOut;

        ResizeInfo(final int oldMinSize, final int oldPermitsAvailable, final int oldConnectionCount, final int newMaxSize) {
            final int checkedOut = oldConnectionCount - oldPermitsAvailable;
            int shrinkLater = checkedOut - newMaxSize;
            if (shrinkLater < 0) {
                shrinkLater = 0;
            }
            this.shrinkLater = shrinkLater;
            int shrinkNow = oldConnectionCount - newMaxSize - shrinkLater;
            if (shrinkNow < 0) {
                shrinkNow = 0;
            }
            this.shrinkNow = shrinkNow;
            if (newMaxSize >= oldMinSize) {
                newMinSize = oldMinSize;
            } else {
                newMinSize = newMaxSize;
            }
            this.transferCheckedOut = checkedOut - shrinkLater;
        }

        public int getNewMinSize() {
            return newMinSize;
        }

        public int getShrinkNow() {
            return shrinkNow;
        }

        public int getShrinkLater() {
            return shrinkLater;
        }

        public int getTransferCheckedOut() {
            return transferCheckedOut;
        }


    }

    protected abstract void transferConnections(int maxSize, int shrinkNow);

    public abstract int getIdleConnectionCount();

    public int getConnectionCount() {
        return connectionCount;
    }

    public int getPartitionMinSize() {
        return minSize;
    }

    public void setPartitionMinSize(int minSize) {
        this.minSize = minSize;
    }

    public int getBlockingTimeoutMilliseconds() {
        return blockingTimeoutMilliseconds;
    }

    public void setBlockingTimeoutMilliseconds(int blockingTimeoutMilliseconds) {
        if (blockingTimeoutMilliseconds < 0) {
            throw new IllegalArgumentException("blockingTimeoutMilliseconds must be positive or 0, not " + blockingTimeoutMilliseconds);
        }
        if (blockingTimeoutMilliseconds == 0) {
            this.blockingTimeoutMilliseconds = Integer.MAX_VALUE;
        } else {
            this.blockingTimeoutMilliseconds = blockingTimeoutMilliseconds;
        }
    }

    public int getIdleTimeoutMinutes() {
        return (int) idleTimeoutMilliseconds / (1000 * 60);
    }

    public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {
        if (idleTimeoutMinutes < 0) {
            throw new IllegalArgumentException("idleTimeoutMinutes must be positive or 0, not " + idleTimeoutMinutes);
        }
        if (idleReleaser != null) {
            idleReleaser.cancel();
        }
        if (idleTimeoutMinutes > 0) {
            this.idleTimeoutMilliseconds = idleTimeoutMinutes * 60 * 1000;
            idleReleaser = new IdleReleaser(this);
            timer.schedule(idleReleaser, this.idleTimeoutMilliseconds, this.idleTimeoutMilliseconds);
        }
    }

    protected abstract void getExpiredManagedConnectionInfos(long threshold, List<ManagedConnectionInfo> killList);

    protected boolean addToPool(ManagedConnectionInfo mci) {
        boolean added;
        synchronized (getPool()) {
            connectionCount++;
            added = getPartitionMaxSize() > getIdleConnectionCount();
            if (added) {
                doAdd(mci);
            }
        }
        return added;
    }

    // static class to permit chain of strong references from preventing ClassLoaders
    // from being GC'ed.
    private static class IdleReleaser extends TimerTask {
        private AbstractSinglePoolConnectionInterceptor parent;

        private IdleReleaser(AbstractSinglePoolConnectionInterceptor parent) {
            this.parent = parent;
        }

        public boolean cancel() {
            this.parent = null;
            return super.cancel();
        }

        public void run() {
            // protect against interceptor being set to null mid-execution
            AbstractSinglePoolConnectionInterceptor interceptor = parent;
            if (interceptor == null)
                return;

            interceptor.resizeLock.readLock().lock();
            try {
                long threshold = System.currentTimeMillis() - interceptor.idleTimeoutMilliseconds;
                List<ManagedConnectionInfo> killList = new ArrayList<ManagedConnectionInfo>(interceptor.getPartitionMaxSize());
                interceptor.getExpiredManagedConnectionInfos(threshold, killList);
                for (ManagedConnectionInfo managedConnectionInfo : killList) {
                    ConnectionInfo killInfo = new ConnectionInfo(managedConnectionInfo);
                    interceptor.internalReturn(killInfo, ConnectionReturnAction.DESTROY);
                }
            } catch (Throwable t) {
                log.error("Error occurred during execution of ExpirationMonitor TimerTask", t);
            } finally {
                interceptor.resizeLock.readLock().unlock();
            }
        }

    }

    // Currently only a short-lived (10 millisecond) task.
    // So, FillTask, unlike IdleReleaser, shouldn't cause GC problems.
    protected class FillTask extends TimerTask {
        private final ManagedConnectionFactory managedConnectionFactory;
        private final Subject subject;
        private final ConnectionRequestInfo cri;

        public FillTask(ConnectionInfo connectionInfo) {
            managedConnectionFactory = connectionInfo.getManagedConnectionInfo().getManagedConnectionFactory();
            subject = connectionInfo.getManagedConnectionInfo().getSubject();
            cri = connectionInfo.getManagedConnectionInfo().getConnectionRequestInfo();
        }

        public void run() {
            resizeLock.readLock().lock();
            try {
                while (connectionCount < minSize) {
                    ManagedConnectionInfo mci = new ManagedConnectionInfo(managedConnectionFactory, cri);
                    mci.setSubject(subject);
                    ConnectionInfo ci = new ConnectionInfo(mci);
                    try {
                        next.getConnection(ci);
                    } catch (ResourceException e) {
                        return;
                    }
                    boolean added = addToPool(mci);
                    if (!added) {
                        internalReturn(ci, ConnectionReturnAction.DESTROY);
                        return;
                    }
                }
            } catch (Throwable t) {
                log.error("FillTask encountered error in run method", t);
            } finally {
                resizeLock.readLock().unlock();
            }
        }

    }
}
