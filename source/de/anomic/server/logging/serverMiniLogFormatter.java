package de.anomic.server.logging;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public final class serverMiniLogFormatter extends SimpleFormatter {

      private final StringBuffer buffer = new StringBuffer();
  
      public serverMiniLogFormatter() {
          super();
      }        
      
      public synchronized String format(LogRecord record) {
          
          StringBuffer buffer = this.buffer;
          buffer.setLength(0);

          buffer.append(formatMessage(record));
          
          // adding the stack trace if available
          buffer.append(System.getProperty("line.separator"));
          
          
          return buffer.toString();
      }
}
