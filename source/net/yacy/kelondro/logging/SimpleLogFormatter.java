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
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;


public final class SimpleLogFormatter extends SimpleFormatter {

    private final Date date = new Date();
    private final FieldPosition position = new FieldPosition(0);

    // e.g. 2005/05/25 11:22:53
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);

    private final StringBuffer buffer = new StringBuffer(80);

    public SimpleLogFormatter() {
        super();
    }
      
    @Override
    public final synchronized String format(final LogRecord record) {
          
        final StringBuffer stringBuffer = this.buffer;
        stringBuffer.setLength(0);
          
        // adding the loglevel
        final int logLevel = record.getLevel().intValue();
        if (logLevel == Log.LOGLEVEL_SEVERE)
            this.buffer.append(Log.LOGTOKEN_SEVERE);
        else if (logLevel == Log.LOGLEVEL_WARNING)
            this.buffer.append(Log.LOGTOKEN_WARNING);
        else if (logLevel == Log.LOGLEVEL_CONFIG)
            this.buffer.append(Log.LOGTOKEN_CONFIG);
        else if (logLevel == Log.LOGLEVEL_INFO)
            this.buffer.append(Log.LOGTOKEN_INFO);
        else if (logLevel == Log.LOGLEVEL_FINE) 
            this.buffer.append(Log.LOGTOKEN_FINE);
        else if (logLevel == Log.LOGLEVEL_FINER) 
            this.buffer.append(Log.LOGTOKEN_FINER);        
        else if (logLevel == Log.LOGLEVEL_FINEST) 
            this.buffer.append(Log.LOGTOKEN_FINEST);            
        else 
            this.buffer.append(Log.LOGTOKEN_FINE);
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
