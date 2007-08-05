//PerformaceSearch_p.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004, 2005
//last major change: 16.02.2005
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
//javac -classpath .:../classes Network.java
//if the shell's current path is HTROOT

import java.io.File;
import java.util.Map;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchProcessing;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class PerformanceSearch_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
        serverObjects prop = new serverObjects();
        File defaultSettingsFile = new File(switchboard.getRootPath(), "yacy.init");
        Map defaultSettings = ((post == null) || (!(post.containsKey("submitlocalprofiledefault")))) ? null : serverFileUtils.loadHashMap(defaultSettingsFile);

        prop.put("submitlocalrespond", 0);
        
        // execute commands
        if (post != null) {
            if (post.containsKey("submitlocalprofilecustom")) {
                // first count percentages
                int c = 0;
                for (int i = 0; i < plasmaSearchProcessing.sequence.length; i++) {
                    c += post.getInt("searchProcessLocalTime_" + plasmaSearchProcessing.sequence[i], 0);
                }
                // if check is ok set new values
                if (c == 100) {
                    for (int i = 0; i < plasmaSearchProcessing.sequence.length; i++) {
                        sb.setConfig("searchProcessLocalTime_" + plasmaSearchProcessing.sequence[i], post.get("searchProcessLocalTime_" + plasmaSearchProcessing.sequence[i], ""));
                        sb.setConfig("searchProcessLocalCount_" + plasmaSearchProcessing.sequence[i], post.get("searchProcessLocalCount_" + plasmaSearchProcessing.sequence[i], ""));
                    }
                    prop.put("submitlocalrespond", 1);
                } else {
                    prop.put("submitlocalrespond", 3);
                }
            }
            if (post.containsKey("submitlocalprofiledefault")) {
                for (int i = 0; i < plasmaSearchProcessing.sequence.length; i++) {
                    sb.setConfig("searchProcessLocalTime_" + plasmaSearchProcessing.sequence[i], (String) defaultSettings.get("searchProcessLocalTime_" + plasmaSearchProcessing.sequence[i]));
                    sb.setConfig("searchProcessLocalCount_" + plasmaSearchProcessing.sequence[i], (String) defaultSettings.get("searchProcessLocalCount_" + plasmaSearchProcessing.sequence[i]));
                }
                prop.put("submitlocalrespond", 2);
            }
        }
        
        // prepare values
        plasmaSearchEvent se = plasmaSearchEvent.lastEvent;
        // count complete execution time
        long time = 0;
        long t;
        int c;
        char sequence;
        if (se != null) for (int i = 0; i < plasmaSearchProcessing.sequence.length; i++) {
            t = se.getLocalTiming().getYieldTime(plasmaSearchProcessing.sequence[i]);
            if (t > 0) time += t;
        }
        for (int i = 0; i < plasmaSearchProcessing.sequence.length; i++) {
            sequence = plasmaSearchProcessing.sequence[i];
            prop.put("searchProcessLocalTime_" + sequence, sb.getConfig("searchProcessLocalTime_" + sequence, ""));
            prop.put("searchProcessLocalCount_" + sequence, sb.getConfig("searchProcessLocalCount_" + sequence, ""));
            if (se == null) {
                prop.put("latestLocalTimeAbs_" + sequence, "-");
                prop.put("latestLocalTimeRel_" + sequence, "-");
                prop.put("latestLocalCountAbs_" + sequence, "-");
            } else {
                t = se.getLocalTiming().getYieldTime(sequence);
                prop.put("latestLocalTimeAbs_" + sequence, (t < 0) ? "-" : Long.toString(t));
                prop.put("latestLocalTimeRel_" + sequence, ((t < 0 || time == 0) ? 0 : (t * 100 / time)) + "%");
                c = se.getLocalTiming().getYieldCount(sequence);
                prop.put("latestLocalCountAbs_" + sequence, (c < 0) ? "-" : Integer.toString(c));
            }
        }
        
        return prop;
    }
}
