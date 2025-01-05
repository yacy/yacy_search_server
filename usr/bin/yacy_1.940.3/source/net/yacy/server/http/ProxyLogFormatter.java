//ProxyLogFormatter.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Martin Thelian
//last change: $LastChangedDate$ by $LastChangedBy$
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

package net.yacy.server.http;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public final class ProxyLogFormatter extends SimpleFormatter {

      private final StringBuilder buffer = new StringBuilder();
  
      public ProxyLogFormatter() {
          super();
      }        
      
    @Override
      public synchronized String format(final LogRecord record) {
          
          final StringBuilder sb = this.buffer;
          sb.setLength(0);

          sb.append(formatMessage(record));
          
          // adding the stack trace if available
          sb.append(System.getProperty("line.separator"));
          
          
          return sb.toString();
      }
}
