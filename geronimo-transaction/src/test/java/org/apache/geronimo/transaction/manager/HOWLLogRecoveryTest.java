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

import java.io.File;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.geronimo.transaction.log.HOWLLog;

/**
 *
 *
 * @version $Rev$ $Date$
 *
 * */
public class HOWLLogRecoveryTest extends AbstractRecoveryTest {
    private static final File basedir = new File(System.getProperty("basedir", System.getProperty("user.dir")));
    private static final String LOG_FILE_NAME = "howl_test_";
    private static final String logFileDir = "txlog";
    private static final String targetDir = new File(basedir, "target").getAbsolutePath();
    private static final File txlogDir = new File(basedir, "target/" + logFileDir);

    @Override
    public void setUp() throws Exception {
        // Deletes the previous transaction log files.
        File[] files = txlogDir.listFiles();
        if ( null != files ) {
            for (int i = 0; i < files.length; i++) {
                files[i].delete();
            }
        }
        super.setUp();
    }

    public void test2Again() throws Exception {
        test2ResOnlineAfterRecoveryStart();
    }

    public void test3Again() throws Exception {
        test3ResOnlineAfterRecoveryStart();
    }

    @Override
    protected TransactionManagerImpl createTransactionManager() throws Exception {
        XidFactory xidFactory = new XidFactoryImpl("hi".getBytes());
        HOWLLog howlLog = new HOWLLog(
                "org.objectweb.howl.log.BlockLogBuffer", //                "bufferClassName",
                4, //                "bufferSizeKBytes",
                true, //                "checksumEnabled",
                true, //                "adler32Checksum",
                20, //                "flushSleepTime",
                logFileDir, //                "logFileDir",
                "log", //                "logFileExt",
                LOG_FILE_NAME, //                "logFileName",
                200, //                "maxBlocksPerFile",
                10, //                "maxBuffers",                       log
                2, //                "maxLogFiles",
                2, //                "minBuffers",
                10,//                "threadsWaitingForceThreshold"});
                xidFactory,
                new File(targetDir)
        );
        howlLog.doStart();
        return new TransactionManagerImpl(1, xidFactory, howlLog);
    }

    protected void tearDown() throws Exception {
        ((HOWLLog) txManager.getTransactionLog()).doStop();
    }

    protected void prepareForReplay() throws Exception {
        Thread.sleep(100);
        tearDown();
        txManager = createTransactionManager();
    }

    public static Test suite() {
        return new TestSetup(new TestSuite(HOWLLogRecoveryTest.class)) {
            protected void setUp() throws Exception {
                File logFile = new File(txlogDir, LOG_FILE_NAME + "_1.log");
                if (logFile.exists()) {
                    logFile.delete();
                }
                logFile = new File(txlogDir, LOG_FILE_NAME + "_2.log");
                if (logFile.exists()) {
                    logFile.delete();
                }
            }
        };
    }
}
