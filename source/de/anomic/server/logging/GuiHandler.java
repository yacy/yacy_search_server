//GuiHandler.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.server.logging;

import java.util.ArrayList;
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
        final LogManager manager = LogManager.getLogManager();
        final String className = getClass().getName();
        
        final String level = manager.getProperty(className + ".level");
        setLevel((level == null) ? Level.INFO : Level.parse(level));
        
        final String filter = manager.getProperty(className + ".filter");
        setFilter(makeFilter(filter));
        
        final String formatter = manager.getProperty(className + ".formatter");
        setFormatter(makeFormatter(formatter));
        
        final String sizeString = manager.getProperty(className + ".size");
        this.size = parseSize(sizeString);
    }    
    
    private int parseSize(final String sizeString) {
        int newSize = DEFAULT_SIZE;
        try {
            newSize = Integer.parseInt(sizeString);
        } catch (final NumberFormatException e) {
            newSize = DEFAULT_SIZE;
        }
        return newSize;
    }
    
    private Filter makeFilter(final String name) {
        if (name == null) return null;
        
        Filter f = null;
        try {
            final Class<?> c = Class.forName(name);
            f = (Filter)c.newInstance();
        } catch (final Exception e) {
            System.err.println("Unable to load filter: " + name);
        }
        return f;
    }    
    
    private Formatter makeFormatter(final String name) {
        if (name == null) return null;
        
        Formatter f = null;
        try {
            final Class<?> c = Class.forName(name);
            f = (Formatter)c.newInstance();
        } catch (final Exception e) {
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
    
    public int getSize() {
    	return this.size;
    }
    
    public synchronized void publish(final LogRecord record) {
        if (!isLoggable(record)) return;
        
        // write it to the buffer
        final int ix = (this.start+this.count)%this.buffer.length;
        this.buffer[ix] = record;
        if (this.count < this.buffer.length) {
            this.count++;
        } else {
            this.start++;
        }     
    }
    
    public synchronized LogRecord[] getLogArray() {
    	return this.getLogArray(null);
    }


    public synchronized LogRecord[] getLogArray(final Long sequenceNumberStart) {
        final ArrayList<LogRecord> tempBuffer = new ArrayList<LogRecord>(this.count);
        
        for (int i = 0; i < this.count; i++) {
            final int ix = (this.start+i)%this.buffer.length;
            final LogRecord record = this.buffer[ix];
            if ((sequenceNumberStart == null) || (record.getSequenceNumber() >= sequenceNumberStart.longValue())) {
            	tempBuffer.add(record);
            }
        }
        
        return tempBuffer.toArray(new LogRecord[tempBuffer.size()]);
    }    
    
    public synchronized String getLog(final boolean reversed, int lineCount) { 
        
        if ((lineCount > this.count)||(lineCount < 0)) lineCount = this.count;
        
        final StringBuffer logMessages = new StringBuffer(this.count*40);
        final Formatter logFormatter = getFormatter();
        
        try {
                final int start = (reversed)?this.start+this.count-1:this.start;
                LogRecord record=null;
                for (int i = 0; i < lineCount; i++) {
                    final int ix = (reversed) ?
                                Math.abs((start-i)%this.buffer.length) :
                                (start+i)%this.buffer.length;
                    record = this.buffer[ix];
                    logMessages.append(logFormatter.format(record));                
                }             
            return logMessages.toString();
        } catch (final Exception ex) {
            // We don't want to throw an exception here, but we
            // report the exception to any registered ErrorManager.
            reportError(null, ex, ErrorManager.FORMAT_FAILURE);
            return "Error while formatting the logging message";
        }
    }
    
    public synchronized String[] getLogLines(final boolean reversed, int lineCount) { 
        
        if ((lineCount > this.count)||(lineCount < 0)) lineCount = this.count;
        
        final ArrayList<String> logMessages = new ArrayList<String>(this.count);
        final Formatter logFormatter = getFormatter();
        
        try {
                final int theStart = (reversed)?this.start+this.count-1:this.start+this.count-lineCount;
                LogRecord record=null;
                for (int i = 0; i < lineCount; i++) {
                    final int ix = (reversed) ?
                                Math.abs((theStart-i)%this.buffer.length) :
                                (theStart + i) % this.buffer.length;
                    record = this.buffer[ix];
                    logMessages.add(logFormatter.format(record));                
                }             
            return logMessages.toArray(new String[logMessages.size()]);
        } catch (final Exception ex) {
            // We don't want to throw an exception here, but we
            // report the exception to any registered ErrorManager.
            reportError(null, ex, ErrorManager.FORMAT_FAILURE);
            return new String[]{"Error while formatting the logging message"};
        }
    }    
    
    public void flush() {
        // TODO Auto-generated method stub
        
    }

    public void close() throws SecurityException {
        // TODO Auto-generated method stub
        
    }

}
