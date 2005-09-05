//GuiHandler.java 
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

import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class GuiHandler extends Handler{

    private final static int DEFAULT_SIZE = 400;
    private int size = DEFAULT_SIZE;    
    private LogRecord buffer[];
    int start, count;
    
    
    public GuiHandler() {
        super();
        configure();
        init();
    }    
    
    /**
     * Get any configuration properties set
     */
    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String className = getClass().getName();
        
        String level = manager.getProperty(className + ".level");
        setLevel((level == null) ? Level.INFO : Level.parse(level));
        
        String filter = manager.getProperty(className + ".filter");
        setFilter(makeFilter(filter));
        
        String formatter = manager.getProperty(className + ".formatter");
        setFormatter(makeFormatter(formatter));
        
        String sizeString = manager.getProperty(className + ".size");
        this.size = parseSize(sizeString);
    }    
    
    private int parseSize(String sizeString) {
        int newSize = DEFAULT_SIZE;
        try {
            newSize = Integer.parseInt(sizeString);
        } catch (NumberFormatException e) {
            newSize = DEFAULT_SIZE;
        }
        return newSize;
    }
    
    private Filter makeFilter(String name) {
        if (name == null) return null;
        
        Filter f = null;
        try {
            Class c = Class.forName(name);
            f = (Filter)c.newInstance();
        } catch (Exception e) {
            System.err.println("Unable to load filter: " + name);
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
    
    // Initialize.  Size is a count of LogRecords.
    private void init() {
        this.buffer = new LogRecord[this.size];
        this.start = 0;
        this.count = 0;
    }
    
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) return;
        
        // write it to the buffer
        int ix = (this.start+this.count)%this.buffer.length;
        this.buffer[ix] = record;
        if (this.count < this.buffer.length) {
            this.count++;
        } else {
            this.start++;
        }     
    }

    /**
     * Push any buffered output to the target <tt>Handler</tt>.
     * <p>
     * The buffer is then cleared.
     */
    public synchronized LogRecord[] getLogArray() {
        
        LogRecord tempBuffer[] = new LogRecord[this.count];
        
        for (int i = 0; i < this.count; i++) {
            int ix = (this.start+i)%this.buffer.length;
            LogRecord record = this.buffer[ix];
            tempBuffer[i] = record;
        }
        
        return tempBuffer;
    }    
    
    public synchronized String getLog(boolean reversed, int lineCount) { 
        
        if ((lineCount > this.count)||(lineCount < 0)) lineCount = this.count;
        
        StringBuffer logMessages = new StringBuffer(this.count*40);
        Formatter logFormatter = getFormatter();
        
        try {
                int start = (reversed)?this.start+this.count-1:this.start;
                for (int i = 0; i < Math.min(this.count,lineCount); i++) {
                    int ix = (reversed) ?
                                Math.abs((start-i)%this.buffer.length) :
                                (start+i)%this.buffer.length;
                    LogRecord record = this.buffer[ix];
                    logMessages.append(logFormatter.format(record));                
                }             
            return logMessages.toString();
        } catch (Exception ex) {
            // We don't want to throw an exception here, but we
            // report the exception to any registered ErrorManager.
            reportError(null, ex, ErrorManager.FORMAT_FAILURE);
            return "Error while formatting the logging message";
        }
    }
    
    public void flush() {
        // TODO Auto-generated method stub
        
    }

    public void close() throws SecurityException {
        // TODO Auto-generated method stub
        
    }

}
