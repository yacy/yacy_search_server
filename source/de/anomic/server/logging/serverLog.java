// serverLog.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

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

    public serverLog(String appName) {
        this.theLogger = Logger.getLogger(appName);
        //this.theLogger.setLevel(Level.FINEST); // set a default level
    }

    public void setLevel(Level newLevel) {
        this.theLogger.setLevel(newLevel);
    }
    
    public void logSevere(String message) {this.theLogger.severe(message);}
    public void logSevere(String message, Throwable thrown) {this.theLogger.log(Level.SEVERE,message,thrown);}
    public boolean isSevere() { return this.theLogger.isLoggable(Level.SEVERE); }

    public void logWarning(String message) {this.theLogger.warning(message);}
    public void logWarning(String message, Throwable thrown) {this.theLogger.log(Level.WARNING,message,thrown);}
    public boolean isWarning() { return this.theLogger.isLoggable(Level.WARNING); }
    
    public void logConfig(String message) {this.theLogger.config(message);}
    public void logConfig(String message, Throwable thrown) {this.theLogger.log(Level.CONFIG,message,thrown);}
    public boolean isConfig() { return this.theLogger.isLoggable(Level.CONFIG); }

    public void logInfo(String message) {this.theLogger.info(message);}
    public void logInfo(String message, Throwable thrown) {this.theLogger.log(Level.INFO,message,thrown);}
    public boolean isInfo() { return this.theLogger.isLoggable(Level.INFO); }

    public void logFine(String message) {this.theLogger.fine(message);}
    public void logFine(String message, Throwable thrown) {this.theLogger.log(Level.FINE,message,thrown);}
    public boolean isFine() { return this.theLogger.isLoggable(Level.FINE); }

    public void logFiner(String message) {this.theLogger.finer(message);}
    public void logFiner(String message, Throwable thrown) {this.theLogger.log(Level.FINER,message,thrown);}   
    public boolean isFiner() { return this.theLogger.isLoggable(Level.FINER); }
    
    public void logFinest(String message) {this.theLogger.finest(message);}
    public void logFinest(String message, Throwable thrown) {this.theLogger.log(Level.FINEST,message,thrown);} 
    public boolean isFinest() { return this.theLogger.isLoggable(Level.FINEST); }
    
    public boolean isLoggable(Level level) {
        return this.theLogger.isLoggable(level);
    }
    
    
    // static log messages: log everything
    public static void logSevere(String appName, String message) {
        Logger.getLogger(appName).severe(message);
    }
    public static void logSevere(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.SEVERE,message,thrown);
    }
    public static void isSevere(String appName) {
        Logger.getLogger(appName).isLoggable(Level.SEVERE);
    }    
    
    public static void logWarning(String appName, String message) {
        Logger.getLogger(appName).warning(message);
    }
    public static void logWarning(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.WARNING,message,thrown);
    }
    public static void isWarning(String appName) {
        Logger.getLogger(appName).isLoggable(Level.WARNING);
    }      
    
    public static void logConfig(String appName, String message) {
        Logger.getLogger(appName).config(message);
    }
    public static void logConfig(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.CONFIG,message,thrown);
    }    
    public static void isConfig(String appName) {
        Logger.getLogger(appName).isLoggable(Level.CONFIG);
    }     
    
    public static void logInfo(String appName, String message) {
        Logger.getLogger(appName).info(message);
    }
    public static void logInfo(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.INFO,message,thrown);
    }
    public static void isInfo(String appName) {
        Logger.getLogger(appName).isLoggable(Level.INFO);
    }     
    
    public static void logFine(String appName, String message) {
        Logger.getLogger(appName).fine(message);
    }
    public static void logFine(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.FINE,message,thrown);
    }
    public static void isFine(String appName) {
        Logger.getLogger(appName).isLoggable(Level.FINE);
    } 
    
    public static void logFiner(String appName, String message) {
        Logger.getLogger(appName).finer(message);
    }
    public static void logFiner(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.FINER,message,thrown);
    }
    public static void isFiner(String appName) {
        Logger.getLogger(appName).isLoggable(Level.FINER);
    } 
    
    public static void logFinest(String appName, String message) {
        Logger.getLogger(appName).finest(message);
    }
    public static void logFinest(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.FINEST,message,thrown);
    }    
    public static void isFinest(String appName) {
        Logger.getLogger(appName).isLoggable(Level.FINEST);
    } 
    
    public static final void configureLogging(File homePath, File loggingConfigFile) throws SecurityException, FileNotFoundException, IOException {
        FileInputStream fileIn = null;
        try {
            System.out.println("STARTUP: Trying to load logging configuration from file " + loggingConfigFile.toString());
            fileIn = new FileInputStream(loggingConfigFile);

            // loading the logger configuration from file
            LogManager logManager = LogManager.getLogManager();
            logManager.readConfiguration(fileIn);

            // creating the logging directory
            String logPattern = logManager.getProperty("java.util.logging.FileHandler.pattern");
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
            if (fileIn != null) try {fileIn.close();}catch(Exception e){}
        }
    }
    
    public static final String format(String s, int n, int fillChar) {
        int l = s.length();
        if (l >= n) return s;
        StringBuffer sb = new StringBuffer(l + n);
        for (int i = l + n; i > n; n--) sb.insert(0, fillChar);
        return sb.toString();
    }
    
    public static final String arrayList(byte[] b, int start, int length) {
        if (b == null) return "NULL";
        if (b.length == 0) return "[]";
        length = Math.min(length, b.length - start);
        StringBuffer sb = new StringBuffer(b.length * 4);
        sb.append('[').append(Integer.toString(b[start])).append(',');
        for (int i = 1; i < length; i++) sb.append(' ').append(Integer.toString(b[start + i])).append(',');
        sb.append(']');
        return sb.toString();
    }
    
    public static final String table(byte[] b, int linewidth, int marker) {
        if (b == null) return "NULL";
        if (b.length == 0) return "[]";
        StringBuffer sb = new StringBuffer(b.length * 4);
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
    
    public static final boolean allZero(byte[] a) {
        return allZero(a, 0, a.length);
    }
    
    public static final boolean allZero(byte[] a, int astart, int alength) {
        for (int i = 0; i < alength; i++) if (a[astart + i] != 0) return false;
        return true;
    }

}
