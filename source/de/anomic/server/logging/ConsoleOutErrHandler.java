//ConsoleOutErrHandler.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.server.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public final class ConsoleOutErrHandler extends Handler{

    private boolean ignoreCtrlChr = false;
    private Level splitLevel = Level.WARNING;
    private final Handler stdOutHandler = new ConsoleOutHandler();
    private final Handler stdErrHandler = new ConsoleHandler();    
    
    public ConsoleOutErrHandler() {
        this.stdOutHandler.setLevel(Level.FINEST);
        this.stdErrHandler.setLevel(Level.WARNING);
        configure();
    }
    
    /**
     * Get any configuration properties set
     */
    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String className = getClass().getName();
        
        String level = manager.getProperty(className + ".level");
        setLevel((level == null) ? Level.INFO : Level.parse(level));
        
        Level levelStdOut = parseLevel(manager.getProperty(className + ".levelStdOut"));
        Level levelSplit = parseLevel(manager.getProperty(className + ".levelSplit"));
        Level levelStdErr = parseLevel(manager.getProperty(className + ".levelStdErr"));
        setLevels(levelStdOut,levelSplit,levelStdErr);
        
        String filter = manager.getProperty(className + ".filter");
        setFilter(makeFilter(filter));
        
        String formatter = manager.getProperty(className + ".formatter");
        setFormatter(makeFormatter(formatter));
        
        String ignoreCtrlChrStr = manager.getProperty(className + ".ignoreCtrlChr");
        this.ignoreCtrlChr = (ignoreCtrlChrStr==null)?false:ignoreCtrlChrStr.equalsIgnoreCase("true");
        
    }    
    
    private Level parseLevel(String levelName) {
        try {
            return (levelName == null) ? Level.INFO : Level.parse(levelName);
        } catch (Exception e) {
            return Level.ALL;
        }
    }
    
    private Filter makeFilter(String name) {
        if (name == null) return null;
        
        Filter f = null;
        try {
            Class c = Class.forName(name);
            f = (Filter)c.newInstance();
        } catch (Exception e) {
            if (name != null) {
                System.err.println("Unable to load filter: " + name);
            }
        }
        return f;
    }    
    
    private Formatter makeFormatter(String name) {
        if (name == null) return null;
        
        Formatter f = null;
        try {
            Class c = Class.forName(name);
            f = (Formatter)c.newInstance();
        } catch (Exception e) {
            f = new SimpleFormatter();
        }
        return f;
    }    
    
    
    public void publish(LogRecord record) {
        if (!isLoggable(record)) return;
        
        if (this.ignoreCtrlChr) {
            String msg = record.getMessage();
            if (msg != null) {
                msg = msg.replaceAll("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"," ");
            }
            record.setMessage(msg);
        }
        
        if (record.getLevel().intValue() >= splitLevel.intValue()) {
            this.stdErrHandler.publish(record);
        } else {
            this.stdOutHandler.publish(record);
        }
    }

    public void flush() {
        this.stdOutHandler.flush();
        this.stdErrHandler.flush();
    }

    public void close() throws SecurityException {
        this.stdOutHandler.close();  
        this.stdErrHandler.close();
    }
    
    public synchronized void setLevel(Level newLevel) throws SecurityException {
        super.setLevel(newLevel);
    }
    
    public void setLevels(Level stdOutLevel, Level splitLevel, Level stdErrLevel) throws SecurityException {
        this.stdOutHandler.setLevel(stdOutLevel);
        this.splitLevel = splitLevel;
        this.stdErrHandler.setLevel(stdErrLevel);
    }
    
    public void setFormatter(Formatter newFormatter) throws SecurityException {
        super.setFormatter(newFormatter);
        if (newFormatter == null) return;
        try {
            this.stdOutHandler.setFormatter((Formatter)newFormatter.getClass().newInstance());
            this.stdErrHandler.setFormatter((Formatter)newFormatter.getClass().newInstance());
        } catch (Exception e) {
            throw new SecurityException(e.getMessage());
        }
    }

    public void setFilter(Filter newFilter) throws SecurityException {
        super.setFilter(newFilter);
        if (newFilter == null) return;
        try {
            this.stdOutHandler.setFilter((Filter)newFilter.getClass().newInstance());
            this.stdErrHandler.setFilter((Filter)newFilter.getClass().newInstance());
        } catch (Exception e) {
            throw new SecurityException(e.getMessage());
        }
    }
}
