//ViewLog_p.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This File is contributed by Alexander Schier
//last major change: 14.12.2004
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


//You must compile this file with
//javac -classpath .:../classes ViewLog_p.java
//if the shell's current path is HTROOT

import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.GuiHandler;
import de.anomic.server.logging.LogalizerHandler;

public class ViewLog_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        String[] log = new String[0];
        boolean reversed = false;
        int maxlines = 400, lines = 200;
        String filter = ".*.*";
        
        if(post != null){
            if(post.containsKey("mode") && ((String)post.get("mode")).equals("reversed")){
                reversed=true;
            }
            if(post.containsKey("lines")){
                lines = Integer.parseInt((String)post.get("lines"));
            }
            if(post.containsKey("filter")){
                filter = (String)post.get("filter");
            }
        }
        
        
        Logger logger = Logger.getLogger("");
        Handler[] handlers = logger.getHandlers();
        boolean displaySubmenu = false;
        for (int i=0; i<handlers.length; i++) {
            if (handlers[i] instanceof GuiHandler) {
                maxlines = ((GuiHandler)handlers[i]).getSize();
                if (lines > maxlines) lines = maxlines;
                log = ((GuiHandler)handlers[i]).getLogLines(reversed,lines);
            } else if (handlers[i] instanceof LogalizerHandler) {
                displaySubmenu = true;
            }
        }
        
        prop.put("submenu", displaySubmenu ? "1" : "0");
        prop.put("reverseChecked", reversed ? "1" : "0");
        prop.put("lines", lines);
        prop.put("maxlines",maxlines);
        prop.put("filter", filter);
        
        // trying to compile the regular expression filter expression
        Matcher filterMatcher = null;
        try {
        	Pattern filterPattern = Pattern.compile(filter,Pattern.MULTILINE);
        	filterMatcher = filterPattern.matcher("");
        } catch (PatternSyntaxException e) {
        	e.printStackTrace();
        }
        

        int level = 0;
        int lc = 0;
        for (int i=0; i < log.length; i++) {
            String nextLogLine = log[i].trim();
            
            if (filterMatcher != null) {
            	filterMatcher.reset(nextLogLine);
            	if (!filterMatcher.find()) continue;
            }
            
            if (nextLogLine.startsWith("E ")) level = 4;
            else if (nextLogLine.startsWith("W ")) level = 3;
            else if (nextLogLine.startsWith("S ")) level = 2;
            else if (nextLogLine.startsWith("I ")) level = 1;
            else if (nextLogLine.startsWith("D ")) level = 0;
            
            prop.put("log_" + lc + "_level", level);
            prop.putHTML("log_" + lc + "_line", nextLogLine); 
            lc++;
        }
        prop.put("log", lc);
        
        // return rewrite properties
        return prop;
    }
}
