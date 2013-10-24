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

    private final static int DEFAULT_SIZE = 1000; // don't make this too big, it eats up a lot of memory!
    private static int size = DEFAULT_SIZE;
    private static String buffer[];
    private static int start, count;

    public GuiHandler() {
        super();
        final LogManager manager = LogManager.getLogManager();
        final String className = getClass().getName();
        final String level = manager.getProperty(className + ".level");
        setLevel((level == null) ? Level.INFO : Level.parse(level));
        setFilter(makeFilter(manager.getProperty(className + ".filter")));
        setFormatter(makeFormatter(manager.getProperty(className + ".formatter")));
        try {
            GuiHandler.size = Integer.parseInt(manager.getProperty(className + ".size"));
        } catch (final NumberFormatException e) {
            GuiHandler.size = DEFAULT_SIZE;
        }
        GuiHandler.buffer = new String[GuiHandler.size];
        GuiHandler.start = 0;
        GuiHandler.count = 0;
    }

    private static Filter makeFilter(final String name) {
        if (name == null) return null;
        try {
            return (Filter) Class.forName(name).newInstance();
        } catch (final Exception e) {
            System.err.println("Unable to load filter: " + name);
        }
        return null;
    }

    private static Formatter makeFormatter(final String name) {
        if (name == null) return null;
        try {
            return (Formatter) Class.forName(name).newInstance();
        } catch (final Exception e) {
            return new SimpleFormatter();
        }
    }

    public final int getSize() {
    	return GuiHandler.size;
    }

    @Override
    public final void publish(final LogRecord record) {
        if (!isLoggable(record)) return;
        final int ix = (GuiHandler.start + GuiHandler.count) % GuiHandler.buffer.length;
        GuiHandler.buffer[ix] = getFormatter().format(record);
        if (GuiHandler.count < GuiHandler.buffer.length) {
            GuiHandler.count++;
        } else {
            GuiHandler.start++;
        }
        flush();
        if (MemoryControl.shortStatus()) clear();
    }

    public final synchronized String[] getLogLines(final boolean reversed, int lineCount) {
        if (lineCount > GuiHandler.count || lineCount < 0) lineCount = GuiHandler.count;
        final List<String> logMessages = new ArrayList<String>(GuiHandler.count);
        try {
            final int theStart = (reversed) ? GuiHandler.start + GuiHandler.count - 1 : GuiHandler.start + GuiHandler.count - lineCount;
            String record = null;
            for (int i = 0; i < lineCount; i++) {
                final int ix = (reversed) ? Math.abs((theStart - i) % GuiHandler.buffer.length) : (theStart + i) % GuiHandler.buffer.length;
                record = GuiHandler.buffer[ix];
                logMessages.add(record);
            }
            return logMessages.toArray(new String[logMessages.size()]);
        } catch (final Exception ex) {
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
    }

}
