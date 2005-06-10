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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public final class serverLog {
    
//    // log-level categories
    public static final int LOGLEVEL_ZERO    = Level.OFF.intValue(); // no output at all
    public static final int LOGLEVEL_FAILURE = Level.SEVERE.intValue(); // system-level error, internal cause, critical and not fixeable (i.e. inconsistency)
    public static final int LOGLEVEL_ERROR   = 950; // exceptional error, catcheable and non-critical (i.e. file error)
    public static final int LOGLEVEL_WARNING = Level.WARNING.intValue(); // uncritical service failure, may require user activity (i.e. input required, wrong authorization)
    public static final int LOGLEVEL_SYSTEM  = Level.CONFIG.intValue(); // regular system status information (i.e. start-up messages)
    public static final int LOGLEVEL_INFO    = Level.INFO.intValue(); // regular action information (i.e. any httpd request URL)
    public static final int LOGLEVEL_DEBUG   = Level.FINEST.intValue(); // in-function status debug output
//    
//    // these categories are also present as character tokens
    public static final char LOGTOKEN_ZERO    = 'Z';
    public static final char LOGTOKEN_FAILURE = 'F';
    public static final char LOGTOKEN_ERROR   = 'E';
    public static final char LOGTOKEN_WARNING = 'W';
    public static final char LOGTOKEN_SYSTEM  = 'S';
    public static final char LOGTOKEN_INFO    = 'I';
    public static final char LOGTOKEN_DEBUG   = 'D';

    private final Logger theLogger;
    
    public serverLog(String appName) {
        this.theLogger = Logger.getLogger(appName); 
    }
       
    public void setLevel(Level newLevel) {
        this.theLogger.setLevel(newLevel);
    }
    
    // class log messages
    public void logFailure(String message) {this.theLogger.severe(message);}
    public void logFailure(String message, Throwable thrown) {this.theLogger.log(Level.SEVERE,message,thrown);}
    
    public void logError(String message)   {this.theLogger.severe(message);}
    public void logError(String message, Throwable thrown)   {this.theLogger.log(Level.SEVERE,message,thrown);}
    
    public void logWarning(String message) {this.theLogger.warning(message);}
    public void logWarning(String message, Throwable thrown) {this.theLogger.log(Level.WARNING,message,thrown);}
    
    public void logSystem(String message)  {this.theLogger.config(message);}
    public void logSystem(String message, Throwable thrown)  {this.theLogger.log(Level.CONFIG,message,thrown);}
    
    public void logInfo(String message)    {this.theLogger.info(message);}
    public void logInfo(String message, Throwable thrown)    {this.theLogger.log(Level.INFO,message,thrown);}
    
    public void logDebug(String message)   {this.theLogger.finest(message);}
    public void logDebug(String message, Throwable thrown)   {this.theLogger.log(Level.FINEST,message,thrown);}

    
    // static log messages: log everything
    private static void log(String appName, int messageLevel, String message) {
        Logger.getLogger(appName).log(Level.parse(Integer.toString(messageLevel)),message);
    }
    
    private void log(Level level, String msg, Throwable thrown) {
        this.theLogger.log(level, msg, thrown);
    }
    
    public static void logFailure(String appName, String message) {
        Logger.getLogger(appName).severe(message);
    }
    public static void logFailure(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.SEVERE,message,thrown);
    }
    
    public static void logError(String appName, String message)   {
        Logger.getLogger(appName).severe(message);
    }
    public static void logError(String appName, String message, Throwable thrown)   {
        Logger.getLogger(appName).log(Level.SEVERE,message,thrown);
    }
    
    public static void logWarning(String appName, String message) {
        Logger.getLogger(appName).warning(message);
    }
    public static void logWarning(String appName, String message, Throwable thrown) {
        Logger.getLogger(appName).log(Level.WARNING,message,thrown);
    }
    
    public static void logSystem(String appName, String message)  {
        Logger.getLogger(appName).config(message);
    }
    public static void logSystem(String appName, String message, Throwable thrown)  {
        Logger.getLogger(appName).log(Level.CONFIG,message,thrown);
    }    
    
    public static void logInfo(String appName, String message)    {
        Logger.getLogger(appName).info(message);
    }
    public static void logInfo(String appName, String message, Throwable thrown)    {
        Logger.getLogger(appName).log(Level.INFO,message,thrown);
    }
    
    public static void logDebug(String appName, String message)   {
        Logger.getLogger(appName).finest(message);
    }
    public static void logDebug(String appName, String message, Throwable thrown)   {
        Logger.getLogger(appName).log(Level.FINEST,message,thrown);
    }    
    
    
    public static final void configureLogging(String homePath) throws SecurityException, FileNotFoundException, IOException {
        
        // loading the logger configuration from file
        LogManager logManager = LogManager.getLogManager();
        logManager.readConfiguration(new FileInputStream(new File(homePath, "yacy.logging")));
        
        // creating the logging directory
        File log = new File("./log/");
        if(!log.canRead()) log.mkdir();            
        
        // generating the root logger
        Logger logger = Logger.getLogger("");
        
//        System.setOut(new PrintStream(new LoggerOutputStream(Logger.getLogger("STDOUT"),Level.FINEST)));
//        System.setErr(new PrintStream(new LoggerOutputStream(Logger.getLogger("STDERR"),Level.SEVERE)));
    }
}
