// Settings.p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 02.05.2004
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
// javac -classpath .:../Classes Settings_p.java
// if the shell's current path is HTROOT

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Settings_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
	serverObjects prop = new serverObjects();

	//if (post == null) System.out.println("POST: NULL"); else System.out.println("POST: " + post.toString());

	prop.put("port", env.getConfig("port", "8080"));
	prop.put("peerName", env.getConfig("peerName", "nameless"));
    prop.put("isTransparentProxy", env.getConfig("isTransparentProxy", "false").equals("true") ? 1 : 0);
    
	// set values
	String s;
	int pos;

	// admin password
	if (env.getConfig("adminAccountBase64", "").length() == 0) {
	    // no password has been specified
	    prop.put("adminuser","admin");
	} else {
	    s = env.getConfig("adminAccount", "admin:void");
	    pos = s.indexOf(":");
	    if (pos < 0) {
		prop.put("adminuser","admin");
	    } else {
		prop.put("adminuser",s.substring(0, pos));
	    }
	}

        // remote proxy
        prop.put("remoteProxyHost", env.getConfig("remoteProxyHost", ""));
        prop.put("remoteProxyPort", env.getConfig("remoteProxyPort", ""));
        prop.put("remoteProxyNoProxy", env.getConfig("remoteProxyNoProxy", ""));
        prop.put("remoteProxyUseChecked", ((String) env.getConfig("remoteProxyUse", "false")).equals("true") ? 1 : 0);

	// proxy access filter
	prop.put("proxyfilter", env.getConfig("proxyClient", "*"));

	// proxy password
	if (env.getConfig("proxyAccountBase64", "").length() == 0) {
	    // no password has been specified
	    prop.put("proxyuser","proxy");
	} else {
	    s = env.getConfig("proxyAccount", "proxy:void");
	    pos = s.indexOf(":");
	    if (pos < 0) {
		prop.put("proxyuser","proxy");
	    } else {
		prop.put("proxyuser",s.substring(0, pos));
	    }
	}

	// server access filter
	prop.put("serverfilter", env.getConfig("serverClient", "*"));

	// server password
	if (env.getConfig("serverAccountBase64", "").length() == 0) {
	    // no password has been specified
	    prop.put("serveruser","server");
	} else {
	    s = env.getConfig("serverAccount", "server:void");
	    pos = s.indexOf(":");
	    if (pos < 0) {
		prop.put("serveruser","server");
	    } else {
		prop.put("serveruser",s.substring(0, pos));
	    }
	}

	// clientIP
	prop.put("clientIP", (String) header.get("CLIENTIP", "<unknown>")); // read an artificial header addendum
	//seedFTPSettings
	prop.put("seedFTPServer", env.getConfig("seedFTPServer", ""));
	prop.put("seedFTPPath", env.getConfig("seedFTPPath", ""));
	prop.put("seedFTPAccount", env.getConfig("seedFTPAccount", ""));
	prop.put("seedFTPPassword", env.getConfig("seedFTPPassword", ""));
    prop.put("seedURL", env.getConfig("seedURL", ""));
        
    
    /*
     * Parser Configuration
     */
    plasmaSwitchboard sb = (plasmaSwitchboard)env;
    Hashtable enabledParsers = sb.parser.getEnabledParserList();
    Hashtable availableParsers = sb.parser.getAvailableParserList();
    
    // fetching a list of all available mimetypes
    List availableParserKeys = Arrays.asList(availableParsers.keySet().toArray(new String[availableParsers.size()]));
    
    // sort it
    Collections.sort(availableParserKeys);

    // loop through the mimeTypes and add it to the properties
    int parserIdx = 0;
    Iterator availableParserIter = availableParserKeys.iterator();
    while (availableParserIter.hasNext()) {
        String mimeType = (String) availableParserIter.next();
        
        prop.put("parser_" + parserIdx + "_mime", mimeType);
        prop.put("parser_" + parserIdx + "_name", availableParsers.get(mimeType));
        prop.put("parser_" + parserIdx + "_status", enabledParsers.containsKey(mimeType) ? 1:0);
        
        parserIdx++;
    }
    
    prop.put("parser", parserIdx);
    
	// return rewrite properties
	return prop;
    }

}
