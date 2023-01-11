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

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import javax.transaction.xa.XAException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class WrapperNamedXAResource implements NamedXAResource {
    protected static Logger log = Logger.getLogger(WrapperNamedXAResource.class.getName());

    private final XAResource xaResource;
    private final String name;

    public WrapperNamedXAResource(XAResource xaResource, String name) {
        if (xaResource == null) throw new NullPointerException("No XAResource supplied.  XA support may not be configured properly");
        if (name == null) throw new NullPointerException("No name supplied. Resource adapter not properly configured");
        this.xaResource = xaResource;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void commit(Xid xid, boolean onePhase) throws XAException {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"Commit called on XAResource " + getName() + "\n Xid: " + xid + "\n onePhase:" + onePhase);
        }
        xaResource.commit(xid, onePhase);
    }                                                                                                                   

    public void end(Xid xid, int flags) throws XAException {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"End called on XAResource " + getName() + "\n Xid: " + xid + "\n flags:" + decodeFlags(flags));
        }
        xaResource.end(xid, flags);
    }

    public void forget(Xid xid) throws XAException {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"Forget called on XAResource " + getName() + "\n Xid: " + xid);
        }
        xaResource.forget(xid);
    }

    public int getTransactionTimeout() throws XAException {
        return xaResource.getTransactionTimeout();
    }

    public boolean isSameRM(XAResource other) throws XAException {
        if (other instanceof WrapperNamedXAResource) {
            return xaResource.isSameRM(((WrapperNamedXAResource)other).xaResource);
        }
        return false;
    }

    public int prepare(Xid xid) throws XAException {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"Prepare called on XAResource " + getName() + "\n Xid: " + xid);
        }
        return xaResource.prepare(xid);
    }

    public Xid[] recover(int flag) throws XAException {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"Recover called on XAResource " + getName() + "\n flags: " + decodeFlags(flag));
        }
        return xaResource.recover(flag);
    }

    public void rollback(Xid xid) throws XAException {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"Rollback called on XAResource " + getName() + "\n Xid: " + xid);
        }
        xaResource.rollback(xid);
    }

    public boolean setTransactionTimeout(int seconds) throws XAException {
        return xaResource.setTransactionTimeout(seconds);
    }

    public void start(Xid xid, int flags) throws XAException {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST,"Start called on XAResource " + getName() + "\n Xid: " + xid + "\n flags:" + decodeFlags(flags));
        }
        xaResource.start(xid, flags);
    }
    private String decodeFlags(int flags) {
        if (flags == 0) {
            return " TMNOFLAGS";
        }
        StringBuilder b = new StringBuilder();
        decodeFlag(flags, b, TMENDRSCAN, " TMENDRSCAN");
        decodeFlag(flags, b, TMFAIL, " TMFAIL");
        decodeFlag(flags, b, TMJOIN, " TMJOIN");
        decodeFlag(flags, b, TMONEPHASE, " TMONEPHASE");
        decodeFlag(flags, b, TMRESUME, " TMRESUME");
        decodeFlag(flags, b, TMSTARTRSCAN, " TMSTARTRSCAN");
        decodeFlag(flags, b, TMSUCCESS, " TMSUCCESS");
        decodeFlag(flags, b, TMSUSPEND, " TMSUSPEND");
        if (flags != 0) {
            b.append(" remaining: ").append(flags);
        }
        return b.toString();
    }

    private void decodeFlag(int flags, StringBuilder b, int flag, String flagName) {
        if ((flags & flag) == flag) {
            b.append(flagName);
            flags = flags ^ flag;
        }
    }
}


