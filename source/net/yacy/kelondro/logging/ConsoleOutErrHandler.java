//ConsoleOutErrHandler.java
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

import java.io.UnsupportedEncodingException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import net.yacy.cora.util.ConcurrentLog;

public final class ConsoleOutErrHandler extends Handler {

    private boolean ignoreCtrlChr = false;
    private Level splitLevel = Level.WARNING;
    private final Handler stdOutHandler;
    private final Handler stdErrHandler;

    public ConsoleOutErrHandler() {
        this.stdOutHandler = new ConsoleOutHandler();
        this.stdErrHandler = new ConsoleHandler();
        this.stdOutHandler.setLevel(Level.FINEST);
        this.stdErrHandler.setLevel(Level.WARNING);
        configure();
    }

    /**
     * Get any configuration properties set
     */
    private void configure() {
        final LogManager manager = LogManager.getLogManager();
        final String className = getClass().getName();

        final String level = manager.getProperty(className + ".level");
        setLevel((level == null) ? Level.INFO : Level.parse(level));

        final Level levelStdOut = parseLevel(manager.getProperty(className + ".levelStdOut"));
        final Level levelSplit = parseLevel(manager.getProperty(className + ".levelSplit"));
        final Level levelStdErr = parseLevel(manager.getProperty(className + ".levelStdErr"));
        setLevels(levelStdOut,levelSplit,levelStdErr);

        final String filter = manager.getProperty(className + ".filter");
        setFilter(makeFilter(filter));

        final String formatter = manager.getProperty(className + ".formatter");
        setFormatter(makeFormatter(formatter));

        final String encoding = manager.getProperty(className + ".encoding");
        try {
            this.stdOutHandler.setEncoding(encoding);
            this.stdErrHandler.setEncoding(encoding);
        } catch (final UnsupportedEncodingException e) {
            ConcurrentLog.logException(e);
        }

        final String ignoreCtrlChrStr = manager.getProperty(className + ".ignoreCtrlChr");
        this.ignoreCtrlChr = (ignoreCtrlChrStr==null) ? false : "true".equalsIgnoreCase(ignoreCtrlChrStr);

    }

    private Level parseLevel(final String levelName) {
        try {
            return (levelName == null) ? Level.INFO : Level.parse(levelName);
        } catch (final Exception e) {
            return Level.ALL;
        }
    }

    private Filter makeFilter(final String name) {
        if (name == null) return null;

        Filter f = null;
        try {
            final Class<?> c = Class.forName(name);
            f = (Filter)c.newInstance();
        } catch (final Exception e) {
            if (name != null) {
                System.err.println("Unable to load filter: " + name);
            }
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


    @Override
    public final void publish(final LogRecord record) {
        if (!isLoggable(record)) return;

        if (this.ignoreCtrlChr) {
            String msg = record.getMessage();
            if (msg != null) {
                msg = msg.replaceAll("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"," ");
            }
            record.setMessage(msg);
        }

        if (record.getLevel().intValue() >= this.splitLevel.intValue()) {
            this.stdErrHandler.publish(record);
            this.stdErrHandler.flush();
        } else {
            this.stdOutHandler.publish(record);
            this.stdOutHandler.flush();
        }
    }

    @Override
    public void flush() {
        this.stdOutHandler.flush();
        this.stdErrHandler.flush();
    }

    @Override
    public synchronized void close() throws SecurityException {
        this.stdOutHandler.close();
        this.stdErrHandler.close();
    }

    @Override
    public synchronized void setLevel(final Level newLevel) throws SecurityException {
        super.setLevel(newLevel);
    }

    public void setLevels(final Level stdOutLevel, final Level splitLevel, final Level stdErrLevel) throws SecurityException {
        this.stdOutHandler.setLevel(stdOutLevel);
        this.splitLevel = splitLevel;
        this.stdErrHandler.setLevel(stdErrLevel);
    }

    @Override
    public void setFormatter(final Formatter newFormatter) throws SecurityException {
        super.setFormatter(newFormatter);
        if (newFormatter == null) return;
        try {
            this.stdOutHandler.setFormatter(newFormatter.getClass().newInstance());
            this.stdErrHandler.setFormatter(newFormatter.getClass().newInstance());
        } catch (final Exception e) {
            throw new SecurityException(e.getMessage());
        }
    }

    @Override
    public final void setFilter(final Filter newFilter) throws SecurityException {
        super.setFilter(newFilter);
        if (newFilter == null) return;
        try {
            this.stdOutHandler.setFilter(newFilter.getClass().newInstance());
            this.stdErrHandler.setFilter(newFilter.getClass().newInstance());
        } catch (final Exception e) {
            throw new SecurityException(e.getMessage());
        }
    }
}
