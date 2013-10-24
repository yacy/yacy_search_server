//GuiHandler.java
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import net.yacy.kelondro.util.MemoryControl;

public class GuiHandler extends Handler {

    private final static int DEFAULT_SIZE = 200; // don't make this too big, it eats up a lot of memory!
    private static int size = DEFAULT_SIZE;
    private static LogRecord buffer[];
    private static int start, count;

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
        GuiHandler.size = parseSize(sizeString);
    }

    private static int parseSize(final String sizeString) {
        int newSize = DEFAULT_SIZE;
        try {
            newSize = Integer.parseInt(sizeString);
        } catch (final NumberFormatException e) {
            newSize = DEFAULT_SIZE;
        }
        return newSize;
    }

    private static Filter makeFilter(final String name) {
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

    private static Formatter makeFormatter(final String name) {
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
        GuiHandler.buffer = new LogRecord[GuiHandler.size];
        GuiHandler.start = 0;
        GuiHandler.count = 0;
    }

    public final int getSize() {
    	return GuiHandler.size;
    }

    @Override
    public final void publish(final LogRecord record) {
        if (!isLoggable(record)) return;

        // write it to the buffer
        final int ix = (GuiHandler.start+GuiHandler.count)%GuiHandler.buffer.length;
        GuiHandler.buffer[ix] = record;
        if (GuiHandler.count < GuiHandler.buffer.length) {
            GuiHandler.count++;
        } else {
            GuiHandler.start++;
        }
        flush();
        if (MemoryControl.shortStatus()) clear();
    }

    public final synchronized LogRecord[] getLogArray() {
    	return this.getLogArray(null);
    }


    public final synchronized LogRecord[] getLogArray(final Long sequenceNumberStart) {
        final List<LogRecord> tempBuffer = new ArrayList<LogRecord>(GuiHandler.count);

        for (int i = 0; i < GuiHandler.count; i++) {
            final int ix = (GuiHandler.start+i)%GuiHandler.buffer.length;
            final LogRecord record = GuiHandler.buffer[ix];
            if ((sequenceNumberStart == null) || (record.getSequenceNumber() >= sequenceNumberStart.longValue())) {
            	tempBuffer.add(record);
            }
        }

        return tempBuffer.toArray(new LogRecord[tempBuffer.size()]);
    }

    public final synchronized String getLog(final boolean reversed, int lineCount) {

        if ((lineCount > GuiHandler.count)||(lineCount < 0)) lineCount = GuiHandler.count;

        final StringBuilder logMessages = new StringBuilder(GuiHandler.count*40);
        final Formatter logFormatter = getFormatter();

        try {
            final int theStart = (reversed)?GuiHandler.start+GuiHandler.count-1:GuiHandler.start;
            LogRecord record=null;
            for (int i = 0; i < lineCount; i++) {
                final int ix = (reversed) ?
                    Math.abs((theStart-i)%GuiHandler.buffer.length) :
                    (theStart+i)%GuiHandler.buffer.length;
                record = GuiHandler.buffer[ix];
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

    public final synchronized String[] getLogLines(final boolean reversed, int lineCount) {

        if ((lineCount > GuiHandler.count)||(lineCount < 0)) lineCount = GuiHandler.count;

        final List<String> logMessages = new ArrayList<String>(GuiHandler.count);
        final Formatter logFormatter = getFormatter();

        try {
            final int theStart = (reversed) ? GuiHandler.start+GuiHandler.count-1 : GuiHandler.start+GuiHandler.count-lineCount;
            LogRecord record=null;
            for (int i = 0; i < lineCount; i++) {
                final int ix = (reversed) ?
                    Math.abs((theStart-i)%GuiHandler.buffer.length) :
                    (theStart + i) % GuiHandler.buffer.length;
                record = GuiHandler.buffer[ix];
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

    @Override
    public void flush() {
    }
    
    public static void clear() {
        for (int i = 0; i < GuiHandler.buffer.length; i++) GuiHandler.buffer[i] = null;
        GuiHandler.start = 0;
        GuiHandler.count = 0;
    }

    @Override
    public synchronized void close() throws SecurityException {
        // Nothing implement here
    }

}
