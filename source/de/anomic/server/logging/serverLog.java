// serverLog.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
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

package de.anomic.server.logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public final class serverLog {

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

    public serverLog(final String appName) {
        this.theLogger = Logger.getLogger(appName);
        //this.theLogger.setLevel(Level.FINEST); // set a default level
    }

    public void setLevel(final Level newLevel) {
        this.theLogger.setLevel(newLevel);
    }
    
    public void logSevere(final String message) {this.theLogger.severe(message);}
    public void logSevere(final String message, final Throwable thrown) {this.theLogger.log(Level.SEVERE,message,thrown);}
    public boolean isSevere() { return this.theLogger.isLoggable(Level.SEVERE); }

    public void logWarning(final String message) {this.theLogger.warning(message);}
    public void logWarning(final String message, final Throwable thrown) {this.theLogger.log(Level.WARNING,message,thrown);}
    public boolean isWarning() { return this.theLogger.isLoggable(Level.WARNING); }
    
    public void logConfig(final String message) {this.theLogger.config(message);}
    public void logConfig(final String message, final Throwable thrown) {this.theLogger.log(Level.CONFIG,message,thrown);}
    public boolean isConfig() { return this.theLogger.isLoggable(Level.CONFIG); }

    public void logInfo(final String message) {this.theLogger.info(message);}
    public void logInfo(final String message, final Throwable thrown) {this.theLogger.log(Level.INFO,message,thrown);}
    public boolean isInfo() { return this.theLogger.isLoggable(Level.INFO); }

    public void logFine(final String message) {this.theLogger.fine(message);}
    public void logFine(final String message, final Throwable thrown) {this.theLogger.log(Level.FINE,message,thrown);}
    public boolean isFine() { return this.theLogger.isLoggable(Level.FINE); }

    public void logFiner(final String message) {this.theLogger.finer(message);}
    public void logFiner(final String message, final Throwable thrown) {this.theLogger.log(Level.FINER,message,thrown);}   
    public boolean isFiner() { return this.theLogger.isLoggable(Level.FINER); }
    
    public void logFinest(final String message) {this.theLogger.finest(message);}
    public void logFinest(final String message, final Throwable thrown) {this.theLogger.log(Level.FINEST,message,thrown);} 
    public boolean isFinest() { return this.theLogger.isLoggable(Level.FINEST); }
    
    public boolean isLoggable(final Level level) {
        return this.theLogger.isLoggable(level);
    }
    
    
    // static log messages: log everything
    public static void logSevere(final String appName, final String message) {
        Logger.getLogger(appName).severe(message);
    }
    public static void logSevere(final String appName, final String message, final Throwable thrown) {
        Logger.getLogger(appName).log(Level.SEVERE,message,thrown);
    }
    public static void isSevere(final String appName) {
        Logger.getLogger(appName).isLoggable(Level.SEVERE);
    }    
    
    public static void logWarning(final String appName, final String message) {
        Logger.getLogger(appName).warning(message);
    }
    public static void logWarning(final String appName, final String message, final Throwable thrown) {
        Logger.getLogger(appName).log(Level.WARNING,message,thrown);
    }
    public static void isWarning(final String appName) {
        Logger.getLogger(appName).isLoggable(Level.WARNING);
    }      
    
    public static void logConfig(final String appName, final String message) {
        Logger.getLogger(appName).config(message);
    }
    public static void logConfig(final String appName, final String message, final Throwable thrown) {
        Logger.getLogger(appName).log(Level.CONFIG,message,thrown);
    }    
    public static void isConfig(final String appName) {
        Logger.getLogger(appName).isLoggable(Level.CONFIG);
    }     
    
    public static void logInfo(final String appName, final String message) {
        Logger.getLogger(appName).info(message);
    }
    public static void logInfo(final String appName, final String message, final Throwable thrown) {
        Logger.getLogger(appName).log(Level.INFO,message,thrown);
    }
    public static void isInfo(final String appName) {
        Logger.getLogger(appName).isLoggable(Level.INFO);
    }     
    
    public static void logFine(final String appName, final String message) {
        Logger.getLogger(appName).fine(message);
    }
    public static void logFine(final String appName, final String message, final Throwable thrown) {
        Logger.getLogger(appName).log(Level.FINE,message,thrown);
    }
    public static void isFine(final String appName) {
        Logger.getLogger(appName).isLoggable(Level.FINE);
    } 
    
    public static void logFiner(final String appName, final String message) {
        Logger.getLogger(appName).finer(message);
    }
    public static void logFiner(final String appName, final String message, final Throwable thrown) {
        Logger.getLogger(appName).log(Level.FINER,message,thrown);
    }
    public static void isFiner(final String appName) {
        Logger.getLogger(appName).isLoggable(Level.FINER);
    } 
    
    public static void logFinest(final String appName, final String message) {
        Logger.getLogger(appName).finest(message);
    }
    public static void logFinest(final String appName, final String message, final Throwable thrown) {
        Logger.getLogger(appName).log(Level.FINEST,message,thrown);
    }    
    public static void isFinest(final String appName) {
        Logger.getLogger(appName).isLoggable(Level.FINEST);
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

//          System.setOut(new PrintStream(new LoggerOutputStream(Logger.getLogger("STDOUT"),Level.FINEST)));
//          System.setErr(new PrintStream(new LoggerOutputStream(Logger.getLogger("STDERR"),Level.SEVERE)));
        } finally {
            if (fileIn != null) try {fileIn.close();}catch(final Exception e){}
        }
    }
    
    public static final String format(final String s, int n, final int fillChar) {
        final int l = s.length();
        if (l >= n) return s;
        final StringBuffer sb = new StringBuffer(l + n);
        for (final int i = l + n; i > n; n--) sb.insert(0, fillChar);
        return sb.toString();
    }
    
    public static final String arrayList(final byte[] b, final int start, int length) {
        if (b == null) return "NULL";
        if (b.length == 0) return "[]";
        length = Math.min(length, b.length - start);
        final StringBuffer sb = new StringBuffer(b.length * 4);
        sb.append('[').append(Integer.toString(b[start])).append(',');
        for (int i = 1; i < length; i++) sb.append(' ').append(Integer.toString(b[start + i])).append(',');
        sb.append(']');
        return sb.toString();
    }
    
    public static final String table(final byte[] b, final int linewidth, final int marker) {
        if (b == null) return "NULL";
        if (b.length == 0) return "[]";
        final StringBuffer sb = new StringBuffer(b.length * 4);
        for (int i = 0; i < b.length; i++) {
            if (i % linewidth == 0)
                sb.append('\n').append("# ").append(Integer.toHexString(i)).append(": ");
            else
                sb.append(',');
            sb.append(' ').append(Integer.toString(0xff & b[i]));
            if (i >= 65535) break;
        }
        sb.append('\n');
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
