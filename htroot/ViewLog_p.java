//ViewLog_p.java
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This File is contributed by Alexander Schier
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


//You must compile this file with
//javac -classpath .:../classes ViewLog_p.java
//if the shell's current path is HTROOT

import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.logging.GuiHandler;
import net.yacy.kelondro.logging.LogalizerHandler;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ViewLog_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        String[] log = new String[0];
        boolean reversed = true;
        boolean json = false;
        int maxlines = 200, lines = 200;
        /* Usually a regex like this would make no sense, ".*" would be
         * sufficient, but ".*.*" makes it a little bit more convenient
         * for the user to input regexes like ".*FOO.*" in the HTML
         * interface.
         */
        String filter = ".*.*";

        if (post != null){
            reversed = (post.containsKey("mode") && "reversed".equals(post.get("mode")));
            json = post.containsKey("json");

            if(post.containsKey("lines")){
                lines = post.getInt("lines", lines);
            }

            if(post.containsKey("filter")){
                filter = post.get("filter");
            }
        }

        final Logger logger = Logger.getLogger("");
        final Handler[] handlers = logger.getHandlers();
        boolean displaySubmenu = false;
        for (final Handler handler : handlers) {
            if (handler instanceof GuiHandler) {
                maxlines = ((GuiHandler)handler).getSize();
                if (lines > maxlines) lines = maxlines;
                log = ((GuiHandler)handler).getLogLines(reversed,lines);
            } else if (handler instanceof LogalizerHandler) {
                displaySubmenu = true;
             }
        }

        prop.put("submenu", displaySubmenu ? "1" : "0");
        prop.put("reverseChecked", reversed ? "1" : "0");
        prop.put("lines", lines);
        prop.put("maxlines",maxlines);
        prop.putHTML("filter", filter);

        // trying to compile the regular expression filter expression
        Matcher filterMatcher = null;
        try {
            final Pattern filterPattern = Pattern.compile(filter,Pattern.MULTILINE);
            filterMatcher = filterPattern.matcher("");
        } catch (final PatternSyntaxException e) {
            ConcurrentLog.logException(e);
        }

        int level = 0;
        int lc = 0;
        for (final String logLine : log) {
            final String nextLogLine = logLine.trim();

            if (filterMatcher != null) {
            	filterMatcher.reset(nextLogLine);
            	if (!filterMatcher.find()) continue;
            }

            if (nextLogLine.startsWith("E ")) {
                level = 4;
            } else if (nextLogLine.startsWith("W ")) {
                level = 3;
            } else if (nextLogLine.startsWith("S ")) {
                level = 2;
            } else if (nextLogLine.startsWith("I ")) {
                level = 1;
            } else if (nextLogLine.startsWith("D ")) {
                level = 0;
            }

            prop.put("log_" + lc + "_level", level);

            if (json) {
                prop.putJSON("log_" + lc + "_line", nextLogLine);
            } else {
                prop.putHTML("log_" + lc + "_line", nextLogLine);
            }

            lc++;
        }
        prop.put("log", lc);

        // return rewrite properties
        return prop;
    }
}
