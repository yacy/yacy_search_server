// Log.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.logging;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


public final class Log {

    // log-level categories
    public static final int LOGLEVEL_ZERO    = Level.OFF.intValue(); // no output at all
    public static final int LOGLEVEL_SEVERE  = Level.SEVERE.intValue(); // system-level error, internal cause, critical and not fixeable (i.e. inconsistency)
    public static final int LOGLEVEL_WARNING = Level.WARNING.intValue(); // uncritical service failure, may require user activity (i.e. input required, wrong authorization)
    public static final int LOGLEVEL_CONFIG  = Level.CONFIG.intValue(); // regular system status information (i.e. start-up messages)
    public static final int LOGLEVEL_INFO    = Level.INFO.intValue(); // regular action information (i.e. any httpd request URL)
    public static final int LOGLEVEL_FINE    = Level.FINE.intValue(); // in-function status debug output
    public static final int LOGLEVEL_FINER    = Level.FINER.intValue(); // in-function status debug output
    public static final int LOGLEVEL_FINEST    = Level.FINEST.intValue(); // in-function status debug output

    // these categories are also present as character tokens
    public static final char LOGTOKEN_ZERO    = 'Z';
    public static final char LOGTOKEN_SEVERE  = 'E';
    public static final char LOGTOKEN_WARNING = 'W';
    public static final char LOGTOKEN_CONFIG  = 'S';
    public static final char LOGTOKEN_INFO    = 'I';
    public static final char LOGTOKEN_FINE    = 'D';
    public static final char LOGTOKEN_FINER   = 'D';
    public static final char LOGTOKEN_FINEST  = 'D';

    private final Logger theLogger;
    
    public Log(final String appName) {
        this.theLogger = Logger.getLogger(appName);
        //this.theLogger.setLevel(Level.FINEST); // set a default level
    }

    public final void setLevel(final Level newLevel) {
        this.theLogger.setLevel(newLevel);
    }
    
    public final void logSevere(final String message) {
        enQueueLog(this.theLogger, Level.SEVERE, message);
    }
    
    public final void logSevere(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.SEVERE, message, thrown);
    }
    
    public final boolean isSevere() {
        return this.theLogger.isLoggable(Level.SEVERE);
    }

    public final void logWarning(final String message) {
        enQueueLog(this.theLogger, Level.WARNING, message);
    }
    
    public final void logWarning(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.WARNING, message, thrown);
    }
    
    public final boolean isWarning() {
        return this.theLogger.isLoggable(Level.WARNING);
    }
    
    public final void logConfig(final String message) {
        enQueueLog(this.theLogger, Level.CONFIG, message);
    }
    
    public final void logConfig(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.CONFIG, message, thrown);
    }
    
    public final boolean isConfig() {
        return this.theLogger.isLoggable(Level.CONFIG);
    }

    public final void logInfo(final String message) {
        enQueueLog(this.theLogger, Level.INFO, message);
    }
    
    public final void logInfo(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.INFO, message, thrown);
    }
    
    public boolean isInfo() {
        return this.theLogger.isLoggable(Level.INFO);
    }

    public final void logFine(final String message) {
        enQueueLog(this.theLogger, Level.FINE, message);
    }
    
    public final void logFine(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.FINE, message, thrown);
    }
    
    public final boolean isFine() {
        return this.theLogger.isLoggable(Level.FINE);
    }

    public final void logFiner(final String message) {
        enQueueLog(this.theLogger, Level.FINER, message);
    }
    
    public final void logFiner(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.FINER, message, thrown);
    }
    
    public final boolean isFiner() { 
       return this.theLogger.isLoggable(Level.FINER);
    }
    
    public final void logFinest(final String message) {
        enQueueLog(this.theLogger, Level.FINEST, message);
    }
    
    public final void logFinest(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.FINEST, message, thrown);
    }
    
    public final boolean isFinest() {
        return this.theLogger.isLoggable(Level.FINEST);
    }
    
    public final boolean isLoggable(final Level level) {
        return this.theLogger.isLoggable(level);
    }
    
    
    // static log messages
    public final static void logSevere(final String appName, final String message) {
        enQueueLog(appName, Level.SEVERE, message);
    }
    public final static void logSevere(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.SEVERE, message, thrown);
    }
    
    public final static void logWarning(final String appName, final String message) {
        enQueueLog(appName, Level.WARNING, message);
    }
    public final static void logException(final Throwable thrown) {
        enQueueLog("StackTrace", Level.WARNING, thrown.getMessage(), thrown);
    }
    public final static void logWarning(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.WARNING, message, thrown);
    }
    
    public final static void logConfig(final String appName, final String message) {
        enQueueLog(appName, Level.CONFIG, message);
    }
    public final static void logConfig(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.CONFIG, message, thrown);
    }    
    
    public final static void logInfo(final String appName, final String message) {
        enQueueLog(appName, Level.INFO, message);
    }
    public final static void logInfo(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.INFO, message, thrown);
    }
    
    public final static void logFine(final String appName, final String message) {
        enQueueLog(appName, Level.FINE, message);
    }
    public final static void logFine(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.FINE, message, thrown);
    }
    public final static boolean isFine(final String appName) {
        return Logger.getLogger(appName).isLoggable(Level.FINE);
    } 
    
    public final static void logFiner(final String appName, final String message) {
        enQueueLog(appName, Level.FINER, message);
    }
    public final static void logFiner(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.FINER, message, thrown);
    }
    
    public final static void logFinest(final String appName, final String message) {
        enQueueLog(appName, Level.FINEST, message);
    }
    public final static void logFinest(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.FINEST, message, thrown);
    }    
    public final static boolean isFinest(final String appName) {
        return Logger.getLogger(appName).isLoggable(Level.FINEST);
    }
    
    private final static void enQueueLog(final Logger logger, final Level level, final String message, final Throwable thrown) {
        if (logRunnerThread == null || !logRunnerThread.isAlive()) {
            logger.log(level, message, thrown);
        } else {
            try {
                logQueue.put(new logEntry(logger, level, message, thrown));
            } catch (InterruptedException e) {
                logger.log(level, message, thrown);
            }
        }
    }
    
    private final static void enQueueLog(final Logger logger, final Level level, final String message) {
        if (logRunnerThread == null || !logRunnerThread.isAlive()) {
            logger.log(level, message);
        } else {
            try {
                logQueue.put(new logEntry(logger, level, message));
            } catch (InterruptedException e) {
                logger.log(level, message);
            }
        }
    }
    
    private final static void enQueueLog(final String loggername, final Level level, final String message, final Throwable thrown) {
        if (logRunnerThread == null || !logRunnerThread.isAlive()) {
            Logger.getLogger(loggername).log(level, message, thrown);
        } else {
            try {
                logQueue.put(new logEntry(loggername, level, message, thrown));
            } catch (InterruptedException e) {
                Logger.getLogger(loggername).log(level, message, thrown);
            }
        }
    }
    
    private final static void enQueueLog(final String loggername, final Level level, final String message) {
        if (logRunnerThread == null || !logRunnerThread.isAlive()) {
            Logger.getLogger(loggername).log(level, message);
        } else {
            try {
                logQueue.put(new logEntry(loggername, level, message));
            } catch (InterruptedException e) {
                Logger.getLogger(loggername).log(level, message);
            }
        }
    }
    
    protected final static class logEntry {
        public final Logger logger;
        public final String loggername;
        public final Level level;
        public final String message;
        public final Throwable thrown;
        public logEntry(final Logger logger, final Level level, final String message, final Throwable thrown) {
            this.logger = logger;
            this.loggername = null;
            this.level = level;
            this.message = message;
            this.thrown = thrown;
        }
        public logEntry(final Logger logger, final Level level, final String message) {
            this.logger = logger;
            this.loggername = null;
            this.level = level;
            this.message = message;
            this.thrown = null;
        }
        public logEntry(final String loggername, final Level level, final String message, final Throwable thrown) {
            this.logger = null;
            this.loggername = loggername;
            this.level = level;
            this.message = message;
            this.thrown = thrown;
        }
        public logEntry(final String loggername, final Level level, final String message) {
            this.logger = null;
            this.loggername = loggername;
            this.level = level;
            this.message = message;
            this.thrown = null;
        }
        public logEntry() {
            this.logger = null;
            this.loggername = null;
            this.level = null;
            this.message = null;
            this.thrown = null;
        }
    }
    
    protected final static logEntry poison = new logEntry();
    protected final static BlockingQueue<logEntry> logQueue = new LinkedBlockingQueue<logEntry>();
    private   final static logRunner logRunnerThread = new logRunner();

    static {
        logRunnerThread.start();
    }

    protected final static class logRunner extends Thread {
        public logRunner() {
            super("Log Runner");
        }
        
        @Override
        public void run() {
            logEntry entry;
            try {
                while ((entry = logQueue.take()) != poison) {
                    if (entry.logger == null) {
                        assert entry.loggername != null;
                        if (entry.thrown == null) {
                            Logger.getLogger(entry.loggername).log(entry.level, entry.message);
                        } else {
                            Logger.getLogger(entry.loggername).log(entry.level, entry.message, entry.thrown);
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
            } catch (InterruptedException e) {
                Log.logException(e);
            }
            
        }
    }
    
    public final static void configureLogging(final File dataPath, final File appPath, final File loggingConfigFile) throws SecurityException, FileNotFoundException, IOException {
        FileInputStream fileIn = null;
        try {
            System.out.println("STARTUP: Trying to load logging configuration from file " + loggingConfigFile.toString());
            fileIn = new FileInputStream(loggingConfigFile);

            // loading the logger configuration from file
            final LogManager logManager = LogManager.getLogManager();
            logManager.readConfiguration(fileIn);

            // creating the logging directory
            String logPattern = logManager.getProperty("java.util.logging.FileHandler.pattern");
            int stripPos = logPattern.lastIndexOf(File.separatorChar);
            if (!new File(logPattern).isAbsolute()) logPattern = new File(dataPath, logPattern).getAbsolutePath();
            if (stripPos < 0) stripPos = logPattern.lastIndexOf(File.separatorChar);
            File log = new File(logPattern.substring(0, stripPos));
            if (!log.isAbsolute()) log = new File(dataPath, log.getPath());
            if (!log.canRead()) log.mkdir();

            // generating the root logger
            final Logger logger = Logger.getLogger("");
            logger.setUseParentHandlers(false);
            
            //for (Handler h: logger.getHandlers()) logger.removeHandler(h);
            if (!dataPath.getAbsolutePath().equals(appPath.getAbsolutePath())) {
                final FileHandler handler = new FileHandler(logPattern, 1024*1024, 20, true);
                logger.addHandler(handler); 
            }
            
            // redirect uncaught exceptions to logging
            final Log exceptionLog = new Log("UNCAUGHT-EXCEPTION");
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
                public void uncaughtException(final Thread t, final Throwable e) {
                    final String msg = String.format("Thread %s: %s",t.getName(), e.getMessage());
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final PrintStream ps = new PrintStream(baos);
                    e.printStackTrace(ps);
                    ps.close();
                    exceptionLog.logSevere(msg + "\n" + baos.toString(), e);
                    //System.err.print("Exception in thread \"" + t.getName() + "\" ");
                    //e.printStackTrace(System.err);
                }
            });
        } finally {
            if (fileIn != null) try {fileIn.close();}catch(final Exception e){}
        }
    }
    
    public final static void shutdown() {
        if (logRunnerThread == null || !logRunnerThread.isAlive()) return;
        try {
            logQueue.put(poison);
            logRunnerThread.join(10000);
        } catch (InterruptedException e) {
        }
    }
    
    public final static String format(final String s, int n, final int fillChar) {
        final int l = s.length();
        if (l >= n) return s;
        final StringBuilder sb = new StringBuilder(l + n);
        for (final int i = l + n; i > n; n--) sb.insert(0, fillChar);
        return sb.toString();
    }
    
    public final static boolean allZero(final byte[] a) {
        return allZero(a, 0, a.length);
    }
    
    public final static boolean allZero(final byte[] a, final int astart, final int alength) {
        for (int i = 0; i < alength; i++) if (a[astart + i] != 0) return false;
        return true;
    }
}
