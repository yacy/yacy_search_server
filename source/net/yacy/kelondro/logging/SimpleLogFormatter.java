//SimpleLogFormatter.java
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;


public final class SimpleLogFormatter extends SimpleFormatter {

    // log-level categories
    public static final int LOGLEVEL_ZERO    = Level.OFF.intValue(); // no output at all
    public static final int LOGLEVEL_SEVERE  = Level.SEVERE.intValue(); // system-level error, internal cause, critical and not fixeable (i.e. inconsistency)
    public static final int LOGLEVEL_WARNING = Level.WARNING.intValue(); // uncritical service failure, may require user activity (i.e. input required, wrong authorization)
    public static final int LOGLEVEL_CONFIG  = Level.CONFIG.intValue(); // regular system status information (i.e. start-up messages)
    public static final int LOGLEVEL_INFO    = Level.INFO.intValue(); // regular action information (i.e. any httpd request URL)
    public static final int LOGLEVEL_FINE    = Level.FINE.intValue(); // in-function status debug output
    public static final int LOGLEVEL_FINER   = Level.FINER.intValue(); // in-function status debug output
    public static final int LOGLEVEL_FINEST  = Level.FINEST.intValue(); // in-function status debug output

    // these categories are also present as character tokens
    public static final char LOGTOKEN_ZERO    = 'Z';
    public static final char LOGTOKEN_SEVERE  = 'E';
    public static final char LOGTOKEN_WARNING = 'W';
    public static final char LOGTOKEN_CONFIG  = 'S';
    public static final char LOGTOKEN_INFO    = 'I';
    public static final char LOGTOKEN_FINE    = 'D';
    public static final char LOGTOKEN_FINER   = 'D';
    public static final char LOGTOKEN_FINEST  = 'D';
    
    private final Date date = new Date();
    private final FieldPosition position = new FieldPosition(0);

    // e.g. 2005/05/25 11:22:53
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);

    private final StringBuffer buffer = new StringBuffer(80); // we use StringBuffer on purpose instead of StringBuilder because of concurrency issues

    public SimpleLogFormatter() {
        super();
    }

    @Override
    public final synchronized String format(final LogRecord record) {

        final StringBuffer stringBuffer = this.buffer;
        stringBuffer.setLength(0);

        // adding the loglevel
        final int logLevel = record.getLevel().intValue();
        if (logLevel == LOGLEVEL_SEVERE)
            this.buffer.append(LOGTOKEN_SEVERE);
        else if (logLevel == LOGLEVEL_WARNING)
            this.buffer.append(LOGTOKEN_WARNING);
        else if (logLevel == LOGLEVEL_CONFIG)
            this.buffer.append(LOGTOKEN_CONFIG);
        else if (logLevel == LOGLEVEL_INFO)
            this.buffer.append(LOGTOKEN_INFO);
        else if (logLevel == LOGLEVEL_FINE)
            this.buffer.append(LOGTOKEN_FINE);
        else if (logLevel == LOGLEVEL_FINER)
            this.buffer.append(LOGTOKEN_FINER);
        else if (logLevel == LOGLEVEL_FINEST)
            this.buffer.append(LOGTOKEN_FINEST);
        else
            this.buffer.append(LOGTOKEN_FINE);
        this.buffer.append(' ');

        // adding the logging date
        this.date.setTime(record.getMillis());
        this.position.setBeginIndex(0);
        this.formatter.format(this.date, this.buffer, this.position);

        // adding the logger name
        stringBuffer.append(' ');
        stringBuffer.append(record.getLoggerName());

        // adding the logging message
        stringBuffer.append(' ');
        stringBuffer.append(formatMessage(record));

        // adding the stack trace if available
        stringBuffer.append(System.getProperty("line.separator"));
        if (record.getThrown() != null) {
            StringWriter writer = null;
            try {
                writer = new StringWriter();
                final PrintWriter printer = new PrintWriter(writer);
                record.getThrown().printStackTrace(printer);
                stringBuffer.append(writer.toString());
            } catch (final Exception e) {
                stringBuffer.append("Failed to get stack trace: ").append(e.getMessage());
            } finally {
                if (writer != null) try {writer.close();} catch (final Exception ex) {}
            }
        }
        return stringBuffer.toString();
    }
}
