// IndexShare_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 24.08.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../classes IndexShare_p.java
// if the shell's current path is HTROOT

//import java.util.*;
//import java.net.*;
//import java.io.*;
//import de.anomic.tools.*;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
//import de.anomic.htmlFilter.*;

public class IndexShare_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();

        if ((post == null) || (env == null)) {
            prop.put("linkfreq", switchboard.getConfig("defaultLinkReceiveFrequency","30"));
            prop.put("wordfreq", switchboard.getConfig("defaultWordReceiveFrequency","10"));
            prop.put("dtable", "");
            prop.put("rtable", "");
            prop.put("wcount", "" + switchboard.wordIndex.size());
            prop.put("ucount", "" + switchboard.loadedURL.size());
            return prop; // be save
        }
        
        if (post.containsKey("indexsharesetting")) {
            switchboard.setConfig("allowDistributeIndex", (post.containsKey("distribute")) ? "true" : "false");
            switchboard.setConfig("allowReceiveIndex", (post.containsKey("receive")) ? "true" : "false");
            switchboard.setConfig("defaultLinkReceiveFrequency", (String) post.get("linkfreq", "30"));
            switchboard.setConfig("defaultWordReceiveFrequency", (String) post.get("wordfreq", "10"));
        }

        // insert constants
        prop.put("wcount", "" + switchboard.wordIndex.size());
        prop.put("ucount", "" + switchboard.loadedURL.size());
	// return rewrite properties
	return prop;
    }

}
