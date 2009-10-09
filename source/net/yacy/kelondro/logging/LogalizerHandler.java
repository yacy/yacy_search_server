//LogalizerHandler.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Matthias Soehnholz
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

package net.yacy.kelondro.logging;

import java.util.Hashtable;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;


public class LogalizerHandler extends Handler {

    public static boolean enabled=false;
    public static boolean debug=false;
    
    public LogalizerHandler() {
        super();
        
        final LogManager manager = LogManager.getLogManager();
        String className = getClass().getName();

        if(manager.getProperty(className + ".enabled").equalsIgnoreCase("true")) enabled = true;
        if(manager.getProperty(className + ".debug").equalsIgnoreCase("true")) debug = true;
    }
    
    public void publish(final LogRecord record) {
        if (enabled) {
            final LogParser temp = new LogParser();
            if (temp != null) try {
                final int returnV = temp.parse(record.getLevel().toString(), record.getMessage());
                //if (debug) System.out.println("Logalizertest: " + returnV + " --- " + record.getLevel() + " --- " + record.getMessage());
                if (debug) System.out.println("Logalizertest: " + returnV + " --- " + record.getLevel());
            } catch (Exception e) {}
        }
        flush();
    }
    
    public Hashtable<String, Object> getParserResults(final LogParser parsername) {
        return (parsername == null) ? null : parsername.getResults();
    }
    
    public void close() throws SecurityException {
    }

    public void flush() {
    }
}
