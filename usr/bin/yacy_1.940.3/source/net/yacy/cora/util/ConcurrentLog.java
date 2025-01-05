/**
 *  ConcurrentLog
 *  Copyright 2013 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 2004, redesigned 9.7.2013 on http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 * jdk-based logger tend to block at java.util.logging.Logger.log(Logger.java:476)
 * in concurrent environments. This makes logging a main performance issue.
 * To overcome this problem, this is a add-on to jdk logging to put log entries
 * on a concurrent message queue and log the messages one by one using a
 * separate process
 */
public final class ConcurrentLog {

    private final static Logger ConcurrentLogLogger = Logger.getLogger("ConcurrentLog");
    private final static Message POISON_MESSAGE = new Message();
    private final static BlockingQueue<Message> logQueue = new ArrayBlockingQueue<>(2000);
    private static Worker logRunnerThread = null;
    public static boolean backgroundRunner = false;

    static {
        ensureWorkerIsRunning();
    }

    public static void ensureWorkerIsRunning() {
        if (backgroundRunner && (logRunnerThread == null || !logRunnerThread.isAlive())) {
            logRunnerThread = new Worker();
            logRunnerThread.start();
            ConcurrentLogLogger.log(Level.INFO, "started ConcurrentLog.Worker.");
        }
    }

    private final Logger theLogger;

    public ConcurrentLog(final String appName) {
        this.theLogger = Logger.getLogger(appName);
        //this.theLogger.setLevel(Level.FINEST); // set a default level
    }

    public final void setLevel(final Level newLevel) {
        this.theLogger.setLevel(newLevel);
    }

    public final void severe(final String message) {
        enQueueLog(this.theLogger, Level.SEVERE, message);
    }

    public final void severe(final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.SEVERE, message, thrown);
    }

    public final boolean isSevere() {
        return this.theLogger.isLoggable(Level.SEVERE);
    }

    public final void warn(final String message) {
        enQueueLog(this.theLogger, Level.WARNING, message);
    }

    public final void warn(final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.WARNING, thrown.getMessage(), thrown);
    }

    public final void warn(final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.WARNING, message, thrown);
    }

    public final boolean isWarn() {
        return this.theLogger.isLoggable(Level.WARNING);
    }

    public final void config(final String message) {
        enQueueLog(this.theLogger, Level.CONFIG, message);
    }

    public final void config(final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.CONFIG, message, thrown);
    }

    public final boolean isConfig() {
        return this.theLogger.isLoggable(Level.CONFIG);
    }

    public final void info(final String message) {
        enQueueLog(this.theLogger, Level.INFO, message);
    }

    public final void info(final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.INFO, message, thrown);
    }

    public boolean isInfo() {
        return this.theLogger.isLoggable(Level.INFO);
    }

    public final void fine(final String message) {
        enQueueLog(this.theLogger, Level.FINE, message);
    }

    public final void fine(final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.FINE, message, thrown);
    }

    public final boolean isFine() {
        return this.theLogger.isLoggable(Level.FINE);
    }

    public final void finer(final String message) {
        enQueueLog(this.theLogger, Level.FINER, message);
    }

    public final void finer(final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.FINER, message, thrown);
    }

    public final boolean isFiner() {
        return this.theLogger.isLoggable(Level.FINER);
    }

    public final void finest(final String message) {
        enQueueLog(this.theLogger, Level.FINEST, message);
    }

    public final void finest(final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.FINEST, message, thrown);
    }

    public final boolean isFinest() {
        return this.theLogger.isLoggable(Level.FINEST);
    }

    public final boolean isLoggable(final Level level) {
        return this.theLogger.isLoggable(level);
    }

    /*
    public final void logException(final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(this.theLogger, Level.WARNING, thrown.getMessage(), thrown);
    }
     */

    // static log messages
    public final static void logException(final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog("ConcurrentLog", Level.WARNING, thrown.toString(), thrown);
    }
    public final static void severe(final String appName, final String message) {
        enQueueLog(appName, Level.SEVERE, message);
    }
    public final static void severe(final String appName, final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(appName, Level.SEVERE, message, thrown);
    }

    public final static void warn(final String appName, final String message) {
        enQueueLog(appName, Level.WARNING, message);
    }
    public final static void warn(final String appName, final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(appName, Level.WARNING, message, thrown);
    }

    public final static void config(final String appName, final String message) {
        enQueueLog(appName, Level.CONFIG, message);
    }
    public final static void config(final String appName, final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(appName, Level.CONFIG, message, thrown);
    }

    public final static void info(final String appName, final String message) {
        enQueueLog(appName, Level.INFO, message);
    }
    public final static void info(final String appName, final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(appName, Level.INFO, message, thrown);
    }

    public final static void fine(final String appName, final String message) {
        enQueueLog(appName, Level.FINE, message);
    }
    public final static void fine(final String appName, final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(appName, Level.FINE, message, thrown);
    }
    public final static boolean isFine(final String appName) {
        return Logger.getLogger(appName).isLoggable(Level.FINE);
    }

    public final static void finer(final String appName, final String message) {
        enQueueLog(appName, Level.FINER, message);
    }
    public final static void finer(final String appName, final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(appName, Level.FINER, message, thrown);
    }

    public final static void finest(final String appName, final String message) {
        enQueueLog(appName, Level.FINEST, message);
    }
    public final static void finest(final String appName, final String message, final Throwable thrown) {
        if (thrown == null) return;
        enQueueLog(appName, Level.FINEST, message, thrown);
    }
    public final static boolean isFinest(final String appName) {
        return Logger.getLogger(appName).isLoggable(Level.FINEST);
    }

    // private
    private final static void enQueueLog(final Logger logger, final Level level, final String message, final Throwable thrown) {
        if (!logger.isLoggable(level)) return;
        if (!backgroundRunner || logRunnerThread == null || !logRunnerThread.isAlive()) {
            if (thrown == null) logger.log(level, "* " + message); else logger.log(level, "* " + message, thrown); // the * is inefficient, but should show up only in emergency cases
        } else {
            try {
                if (thrown == null) logQueue.put(new Message(logger, level, message)); else logQueue.put(new Message(logger, level, message, thrown));
            } catch (final InterruptedException e) {
                if (thrown == null) logger.log(level, message); else  logger.log(level, message, thrown);
            }
        }
    }

    private final static void enQueueLog(final Logger logger, final Level level, final String message) {
        if (!logger.isLoggable(level)) return;
        if (!backgroundRunner || logRunnerThread == null || !logRunnerThread.isAlive()) {
            logger.log(level, "* " + message); // the * is inefficient, but should show up only in emergency cases
        } else {
            try {
                logQueue.put(new Message(logger, level, message));
            } catch (final InterruptedException e) {
                logger.log(level, message);
            }
        }
    }

    private final static void enQueueLog(final String loggername, final Level level, final String message, final Throwable thrown) {
        if (!backgroundRunner || logRunnerThread == null || !logRunnerThread.isAlive()) {
            if (thrown == null) Logger.getLogger(loggername).log(level, "* " + message); else Logger.getLogger(loggername).log(level, "* " + message, thrown); // the * is inefficient, but should show up only in emergency cases
        } else {
            try {
                if (thrown == null) logQueue.put(new Message(loggername, level, message)); else logQueue.put(new Message(loggername, level, message, thrown));
            } catch (final InterruptedException e) {
                if (thrown == null) Logger.getLogger(loggername).log(level, message); else Logger.getLogger(loggername).log(level, message, thrown);
            }
        }
    }

    private final static void enQueueLog(final String loggername, final Level level, final String message) {
        if (!backgroundRunner || logRunnerThread == null || !logRunnerThread.isAlive()) {
            Logger.getLogger(loggername).log(level, "* " + message); // the * is inefficient, but should show up only in emergency cases
        } else {
            try {
                logQueue.put(new Message(loggername, level, message));
            } catch (final InterruptedException e) {
                Logger.getLogger(loggername).log(level, message);
            }
        }
    }

    protected final static class Message {
        private final Level level;
        private final String message;
        private Logger logger;
        private String loggername;
        private Throwable thrown;
        private Message(final Level level, final String message) {
            this.level = level;
            this.message = message == null || message.length() <= 4096 ? message : message.substring(0, 4096);
        }
        public Message(final Logger logger, final Level level, final String message, final Throwable thrown) {
            this(level, message);
            this.logger = logger;
            this.loggername = null;
            this.thrown = thrown;
        }
        public Message(final Logger logger, final Level level, final String message) {
            this(level, message);
            this.logger = logger;
            this.loggername = null;
            this.thrown = null;
        }
        public Message(final String loggername, final Level level, final String message, final Throwable thrown) {
            this(level, message);
            this.logger = null;
            this.loggername = loggername;
            this.thrown = thrown;
        }
        public Message(final String loggername, final Level level, final String message) {
            this(level, message);
            this.logger = null;
            this.loggername = loggername;
            this.thrown = null;
        }
        public Message() {
            this.logger = null;
            this.loggername = null;
            this.level = null;
            this.message = null;
            this.thrown = null;
        }
    }

    protected final static class Worker extends Thread {
        public Worker() {
            super("Log Worker");
        }

        @Override
        public void run() {
            Message entry;
            final Map<String, Logger> loggerCache = new HashMap<>();
            try {
                while ((entry = logQueue.take()) != POISON_MESSAGE) {
                    if (entry.logger == null) {
                        assert entry.loggername != null;
                        Logger l = loggerCache.get(entry.loggername);
                        if (l == null) {l = Logger.getLogger(entry.loggername); loggerCache.put(entry.loggername, l);}
                        if (entry.thrown == null) {
                            l.log(entry.level, entry.message);
                        } else {
                            l.log(entry.level, entry.message, entry.thrown);
                        }
                    } else {
                        assert entry.loggername == null;
                        if (entry.thrown == null) {
                            entry.logger.log(entry.level, entry.message);
                        } else {
                            entry.logger.log(entry.level, entry.message, entry.thrown);
                        }
                    }
                }
            } catch (final Throwable e) {
                ConcurrentLogLogger.log(Level.SEVERE, "ConcurrentLog.Worker has terminated", e);
            }
            ConcurrentLogLogger.log(Level.INFO, "terminating ConcurrentLog.Worker with " + logQueue.size() + " cached loglines.");
        }
    }

    public static final void configureLogging(final File dataPath, final File loggingConfigFile) throws SecurityException, FileNotFoundException, IOException {
        System.out.println("STARTUP: Trying to load logging configuration from file " + loggingConfigFile.toString());
        try (final FileInputStream fileIn = new FileInputStream(loggingConfigFile);){

            final String logFilePatternKey = "java.util.logging.FileHandler.pattern";
            final Properties logProperties = new Properties();
            logProperties.load(fileIn);
            String logFilePattern = logProperties.getProperty(logFilePatternKey, "%h/java%u.log" /* default FileHandler pattern*/);

            File logFile;
            if(logFilePattern.startsWith("%h")) {
                logFile = new File(System.getProperty("user.home") + logFilePattern.substring(2));
            } else if(logFilePattern.startsWith("%t")) {
                String tmpDir = System.getProperty("java.io.tmpdir");
                if (tmpDir == null) {
                    tmpDir = System.getProperty("user.home");
                }
                logFile = new File(tmpDir, logFilePattern.substring(2));
            } else {
                logFile = new File(logFilePattern);
                if (!logFile.isAbsolute()) {
                    logFile = new File(dataPath, logFilePattern);
                    logFilePattern = logFile.getAbsolutePath();

                    /*
                     * Update the file pattern with the absolute path flavor as LogManager and
                     * FileHandler classes do not offer a way to configure the base parent path when
                     * using relative path
                     */
                    logProperties.setProperty(logFilePatternKey, logFilePattern);
                }
            }


            // creating the logging directory if necessary
            final File logDirectory = logFile.getParentFile();
            if(logDirectory != null) {
                if (!logDirectory.exists()) {
                    if(!logDirectory.mkdirs()) {
                        System.err.println("STARTUP: Could not create the logs directory at " + logDirectory.getAbsolutePath());
                    }
                } else if(!logDirectory.isDirectory()) {
                    System.err.println("STARTUP: Log file parent path at " + logDirectory.getAbsolutePath() + "is not a directory");
                }
            }

            final ByteArrayOutputStream propsStream = new ByteArrayOutputStream();
            logProperties.store(propsStream, null);

            // loading the logger configuration from properties
            final LogManager logManager = LogManager.getLogManager();
            logManager.readConfiguration(new ByteArrayInputStream(propsStream.toByteArray()));

            // redirect uncaught exceptions to logging
            final ConcurrentLog exceptionLog = new ConcurrentLog("UNCAUGHT-EXCEPTION");
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
                @Override
                public void uncaughtException(final Thread t, final Throwable e) {
                    if (e == null) return;
                    final String msg = String.format("Thread %s: %s",t.getName(), e.getMessage());
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final PrintStream ps = new PrintStream(baos);
                    e.printStackTrace(ps);
                    ps.close();
                    exceptionLog.severe(msg + "\n" + baos.toString(), e);
                    ConcurrentLogLogger.log(Level.SEVERE, e.getMessage(), e);
                    if (e instanceof InvocationTargetException) {
                        final Throwable target = ((InvocationTargetException) e).getTargetException();
                        ConcurrentLogLogger.log(Level.SEVERE, target.getMessage(), target);
                    }
                }
            });
        }
    }

    public final static void shutdown() {
        if (logRunnerThread == null || !logRunnerThread.isAlive()) {
            ConcurrentLogLogger.log(Level.INFO, "shutdown of ConcurrentLog.Worker void because it was not running.");
            return;
        }
        try {
            ConcurrentLogLogger.log(Level.INFO, "shutdown of ConcurrentLog.Worker: injection of poison message");
            logQueue.put(POISON_MESSAGE);
            logRunnerThread.join(2000);
            ConcurrentLogLogger.log(Level.INFO, "shutdown of ConcurrentLog.Worker: terminated");
        } catch (final InterruptedException e) {
        }
    }

    public static String stackTrace() {
        final Throwable t = new Throwable();
        final StackTraceElement[] e = t.getStackTrace();
        final StringBuilder sb = new StringBuilder(80);
        for (int i = 2; i < e.length - 1; i++) {
            sb.append(e[i].toString()).append(" -> ");
        }
        sb.append(e[e.length - 1].toString());
        return sb.toString();
    }
}
