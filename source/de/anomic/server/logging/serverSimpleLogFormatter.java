//severSimpleLogFormatter.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Martin Thelian
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class serverSimpleLogFormatter extends SimpleFormatter {


      private Date date = new Date();      
      private final FieldPosition position = new FieldPosition(0);

      // e.g. 2005/05/25 11:22:53
      private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      
      private final StringBuffer buffer = new StringBuffer();
      
  
      public serverSimpleLogFormatter() {
          super();
      }        
      
      public synchronized String format(LogRecord record) {
          
          StringBuffer buffer = this.buffer;
          buffer.setLength(0);
          
          // adding the loglevel
          int logLevel = record.getLevel().intValue();
          if (logLevel == serverLog.LOGLEVEL_SEVERE) 
              this.buffer.append(serverLog.LOGTOKEN_SEVERE); 
          else if (logLevel == serverLog.LOGLEVEL_WARNING) 
              this.buffer.append(serverLog.LOGTOKEN_WARNING);
          else if (logLevel == serverLog.LOGLEVEL_CONFIG)
              this.buffer.append(serverLog.LOGTOKEN_CONFIG);
          else if (logLevel == serverLog.LOGLEVEL_INFO)
              this.buffer.append(serverLog.LOGTOKEN_INFO);
          else if (logLevel == serverLog.LOGLEVEL_FINE) 
              this.buffer.append(serverLog.LOGTOKEN_FINE);
          else if (logLevel == serverLog.LOGLEVEL_FINER) 
              this.buffer.append(serverLog.LOGTOKEN_FINER);        
          else if (logLevel == serverLog.LOGLEVEL_FINEST) 
              this.buffer.append(serverLog.LOGTOKEN_FINEST);            
          else 
              this.buffer.append(serverLog.LOGTOKEN_FINE);
          this.buffer.append(' ');
          
          // adding the logging date
          this.date.setTime(record.getMillis());
          this.position.setBeginIndex(0);
          this.formatter.format(this.date, this.buffer, this.position);

          // adding the logger name
          buffer.append(' ');
          buffer.append(record.getLoggerName());
          
          // adding the logging message
          buffer.append(' ');
          buffer.append(formatMessage(record));
          
          // adding the stack trace if available
          buffer.append(System.getProperty("line.separator"));
          if (record.getThrown() != null) {
              StringWriter writer = null;
              try {
                  writer = new StringWriter();
                  PrintWriter printer = new PrintWriter(writer);
                  record.getThrown().printStackTrace(printer);
                  buffer.append(writer.toString());
              } catch (Exception e) {
                  buffer.append("Failed to get stack trace: " + e.getMessage());
              } finally {
                  if (writer != null) try {writer.close();} catch (Exception ex) {}
              }
          }
          return buffer.toString();
      }
}
