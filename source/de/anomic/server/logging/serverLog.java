// serverLog.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 04.08.2004
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

    // these categories are also present as character tokens
    public static final char LOGTOKEN_ZERO    = 'Z';
    public static final char LOGTOKEN_SEVERE  = 'E';
    public static final char LOGTOKEN_WARNING = 'W';
    public static final char LOGTOKEN_CONFIG  = 'S';
    public static final char LOGTOKEN_INFO    = 'I';
    public static final char LOGTOKEN_FINE    = 'D';

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

    public void logWarning(String message) {this.theLogger.warning(message);}
    public void logWarning(String message, Throwable thrown) {this.theLogger.log(Level.WARNING,message,thrown);}

    public void logConfig(String message) {this.theLogger.config(message);}
    public void logConfig(String message, Throwable thrown) {this.theLogger.log(Level.CONFIG,message,thrown);}

    public void logInfo(String message) {this.theLogger.info(message);}
    public void logInfo(String message, Throwable thrown) {this.theLogger.log(Level.INFO,message,thrown);}

    public void logFine(String message) {this.theLogger.fine(message);}
    public void logFine(String message, Throwable thrown) {this.theLogger.log(Level.FINE,message,thrown);}

    // static log messages: log everything
    private static void log(String appName, int messageLevel, String message) {
        Logger.getLogger(appName).log(Level.parse(Integer.toString(messageLevel)),message);
    }
    private void log(Level level, String msg, Throwable thrown) {
        this.theLogger.log(level, msg, thrown);
    }
    public static void logSevere(String appName, String message) {
        Logger.getLogger(appName).severe(message);
    }
    public static void logSevere(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.SEVERE,message,thrown);
    }
    public static void logWarning(String appName, String message) {
        Logger.getLogger(appName).warning(message);
    }
    public static void logWarning(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.WARNING,message,thrown);
    }
    public static void logConfig(String appName, String message) {
        Logger.getLogger(appName).config(message);
    }
    public static void logConfig(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.CONFIG,message,thrown);
    }    
    public static void logInfo(String appName, String message) {
        Logger.getLogger(appName).info(message);
    }
    public static void logInfo(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.INFO,message,thrown);
    }
    public static void logFine(String appName, String message) {
        Logger.getLogger(appName).finest(message);
    }
    public static void logFine(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.FINEST,message,thrown);
    }
    public static final void configureLogging(File loggingConfigFile) throws SecurityException, FileNotFoundException, IOException {
        FileInputStream fileIn = null;
        try {
            System.out.println("STARTUP: Trying to load logging configuration from file " + loggingConfigFile.toString());
            fileIn = new FileInputStream(loggingConfigFile);

            // loading the logger configuration from file
            LogManager logManager = LogManager.getLogManager();
            logManager.readConfiguration(fileIn);

            // creating the logging directory
            File log = new File("./log/");
            if(!log.canRead()) log.mkdir();

            // generating the root logger
            Logger logger = Logger.getLogger("");

//          System.setOut(new PrintStream(new LoggerOutputStream(Logger.getLogger("STDOUT"),Level.FINEST)));
//          System.setErr(new PrintStream(new LoggerOutputStream(Logger.getLogger("STDERR"),Level.SEVERE)));
        } finally {
            if (fileIn != null) try {fileIn.close();}catch(Exception e){}
        }
    }
}
