package de.anomic.server.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.collections.map.CaseInsensitiveMap;

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
          if (logLevel == serverLog.LOGLEVEL_FAILURE) 
              this.buffer.append(serverLog.LOGTOKEN_FAILURE); 
          else if (logLevel == serverLog.LOGLEVEL_ERROR)
              this.buffer.append(serverLog.LOGTOKEN_ERROR); 
          else if (logLevel == serverLog.LOGLEVEL_WARNING) 
              this.buffer.append(serverLog.LOGTOKEN_WARNING);
          else if (logLevel == serverLog.LOGLEVEL_SYSTEM)
              this.buffer.append(serverLog.LOGTOKEN_SYSTEM);
          else if (logLevel == serverLog.LOGLEVEL_INFO)
              this.buffer.append(serverLog.LOGTOKEN_INFO);
          else if (logLevel == serverLog.LOGLEVEL_DEBUG) 
              this.buffer.append(serverLog.LOGTOKEN_DEBUG);
          else 
              this.buffer.append(serverLog.LOGTOKEN_DEBUG);
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
