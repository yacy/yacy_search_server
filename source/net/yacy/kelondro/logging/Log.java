// Log.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: $LastChangedDate: 2009-01-30 14:48:11 +0000 (Fr, 30 Jan 2009) $ by $LastChangedBy: orbiter $
// Revision: $LastChangedRevision: 5539 $
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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

    public void setLevel(final Level newLevel) {
        this.theLogger.setLevel(newLevel);
    }
    
    public void logSevere(final String message) {
        enQueueLog(this.theLogger, Level.SEVERE, message);
    }
    
    public void logSevere(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.SEVERE, message, thrown);
    }
    
    public boolean isSevere() {
        return this.theLogger.isLoggable(Level.SEVERE);
    }

    public void logWarning(final String message) {
        enQueueLog(this.theLogger, Level.WARNING, message);
    }
    
    public void logWarning(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.WARNING, message, thrown);
    }
    
    public boolean isWarning() {
        return this.theLogger.isLoggable(Level.WARNING);
    }
    
    public void logConfig(final String message) {
        enQueueLog(this.theLogger, Level.CONFIG, message);
    }
    
    public void logConfig(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.CONFIG, message, thrown);
    }
    
    public boolean isConfig() {
        return this.theLogger.isLoggable(Level.CONFIG);
    }

    public void logInfo(final String message) {
        enQueueLog(this.theLogger, Level.INFO, message);
    }
    
    public void logInfo(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.INFO, message, thrown);
    }
    
    public boolean isInfo() {
        return this.theLogger.isLoggable(Level.INFO);
    }

    public void logFine(final String message) {
        enQueueLog(this.theLogger, Level.FINE, message);
    }
    
    public void logFine(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.FINE, message, thrown);
    }
    
    public boolean isFine() {
        return this.theLogger.isLoggable(Level.FINE);
    }

    public void logFiner(final String message) {
        enQueueLog(this.theLogger, Level.FINER, message);
    }
    
    public void logFiner(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.FINER, message, thrown);
    }
    
    public boolean isFiner() { 
       return this.theLogger.isLoggable(Level.FINER);
    }
    
    public void logFinest(final String message) {
        enQueueLog(this.theLogger, Level.FINEST, message);
    }
    
    public void logFinest(final String message, final Throwable thrown) {
        enQueueLog(this.theLogger, Level.FINEST, message, thrown);
    }
    
    public boolean isFinest() {
        return this.theLogger.isLoggable(Level.FINEST);
    }
    
    public boolean isLoggable(final Level level) {
        return this.theLogger.isLoggable(level);
    }
    
    
    // static log messages
    public static void logSevere(final String appName, final String message) {
        enQueueLog(appName, Level.SEVERE, message);
    }
    public static void logSevere(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.SEVERE, message, thrown);
    }
    
    public static void logWarning(final String appName, final String message) {
        enQueueLog(appName, Level.WARNING, message);
    }
    public static void logWarning(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.WARNING, message, thrown);
    }
    
    public static void logConfig(final String appName, final String message) {
        enQueueLog(appName, Level.CONFIG, message);
    }
    public static void logConfig(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.CONFIG, message, thrown);
    }    
    
    public static void logInfo(final String appName, final String message) {
        enQueueLog(appName, Level.INFO, message);
    }
    public static void logInfo(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.INFO, message, thrown);
    }
    
    public static void logFine(final String appName, final String message) {
        enQueueLog(appName, Level.FINE, message);
    }
    public static void logFine(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.FINE, message, thrown);
    }
    public static boolean isFine(final String appName) {
        return Logger.getLogger(appName).isLoggable(Level.FINE);
    } 
    
    public static void logFiner(final String appName, final String message) {
        enQueueLog(appName, Level.FINER, message);
    }
    public static void logFiner(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.FINER, message, thrown);
    }
    
    public static void logFinest(final String appName, final String message) {
        enQueueLog(appName, Level.FINEST, message);
    }
    public static void logFinest(final String appName, final String message, final Throwable thrown) {
        enQueueLog(appName, Level.FINEST, message, thrown);
    }    
    public static boolean isFinest(final String appName) {
        return Logger.getLogger(appName).isLoggable(Level.FINEST);
    }
    
    private static void enQueueLog(Logger logger, Level level, String message, final Throwable thrown) {
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
    
    private static void enQueueLog(Logger logger, Level level, String message) {
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
    
    private static void enQueueLog(String loggername, Level level, String message, final Throwable thrown) {
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
    
    private static void enQueueLog(String loggername, Level level, String message) {
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
    
    protected static class logEntry {
        public final Logger logger;
        public final String loggername;
        public final Level level;
        public final String message;
        public final Throwable thrown;
        public logEntry(Logger logger, Level level, String message, final Throwable thrown) {
            this.logger = logger;
            this.loggername = null;
            this.level = level;
            this.message = message;
            this.thrown = thrown;
        }
        public logEntry(Logger logger, Level level, String message) {
            this.logger = logger;
            this.loggername = null;
            this.level = level;
            this.message = message;
            this.thrown = null;
        }
        public logEntry(String loggername, Level level, String message, final Throwable thrown) {
            this.logger = null;
            this.loggername = loggername;
            this.level = level;
            this.message = message;
            this.thrown = thrown;
        }
        public logEntry(String loggername, Level level, String message) {
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
    
    protected static logEntry poison = new logEntry();
    protected static BlockingQueue<logEntry> logQueue = new LinkedBlockingQueue<logEntry>();
    private   static logRunner logRunnerThread = null;
    
    protected static class logRunner extends Thread {
        public logRunner() {
        	super("Log Runner");
        }
        
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
                e.printStackTrace();
            }
            
        }
    }
    
    public static final void configureLogging(final File homePath, final File loggingConfigFile) throws SecurityException, FileNotFoundException, IOException {
        FileInputStream fileIn = null;
        try {
            System.out.println("STARTUP: Trying to load logging configuration from file " + loggingConfigFile.toString());
            fileIn = new FileInputStream(loggingConfigFile);

            // loading the logger configuration from file
            final LogManager logManager = LogManager.getLogManager();
            logManager.readConfiguration(fileIn);

            // creating the logging directory
            final String logPattern = logManager.getProperty("java.util.logging.FileHandler.pattern");
            int stripPos = logPattern.lastIndexOf('/');
            if (stripPos < 0) stripPos = logPattern.lastIndexOf(File.pathSeparatorChar);
            File log = new File(logPattern.substring(0, stripPos));
            if (!log.isAbsolute()) log = new File(homePath, log.getPath());
            if (!log.canRead()) log.mkdir();

            // TODO: changing the pattern settings for the file handlers
            
            // generating the root logger
            /*Logger logger =*/ Logger.getLogger("");

//          System.setOut(new PrintStream(new LoggerOutputStream(Logger.getLogger("STDOUT"), Level.FINEST)));
//          System.setErr(new PrintStream(new LoggerOutputStream(Logger.getLogger("STDERR"), Level.SEVERE)));
            logRunnerThread = new logRunner();
            logRunnerThread.start();
        } finally {
            if (fileIn != null) try {fileIn.close();}catch(final Exception e){}
        }
    }
    
    public static void shutdown() {
        if (logRunnerThread == null || !logRunnerThread.isAlive()) return;
        try {
            logQueue.put(poison);
            logRunnerThread.join(10000);
        } catch (InterruptedException e) {
        }
    }
    
    public static final String format(final String s, int n, final int fillChar) {
        final int l = s.length();
        if (l >= n) return s;
        final StringBuilder sb = new StringBuilder(l + n);
        for (final int i = l + n; i > n; n--) sb.insert(0, fillChar);
        return sb.toString();
    }
    
    public static final boolean allZero(final byte[] a) {
        return allZero(a, 0, a.length);
    }
    
    public static final boolean allZero(final byte[] a, final int astart, final int alength) {
        for (int i = 0; i < alength; i++) if (a[astart + i] != 0) return false;
        return true;
    }

}
