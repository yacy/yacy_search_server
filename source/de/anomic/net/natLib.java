// natLib.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 04.05.2004
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

package de.anomic.net;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;

import de.anomic.http.httpc;
import de.anomic.server.serverCore;
import de.anomic.tools.disorderHeap;
import de.anomic.tools.nxTools;
import de.anomic.plasma.plasmaSwitchboard;

public class natLib {

    public static String getDI604(String password) {
	// this pulls off the ip number from the DI-604 router/nat
	/*
	  wget --quiet --ignore-length http://admin:<pw>@192.168.0.1:80/status.htm > /dev/null
	  grep -A 1 "IP Address" status.htm | tail -1 | awk '{print $1}' | awk 'BEGIN{FS=">"} {print $2}'
	  rm status.htm
	*/
	try {
	    ArrayList x = httpc.wget(new URL("http://192.168.0.1:80/status.htm"), 5000, "admin", password, null);
	    x = nxTools.grep(x, 1, "IP Address");
	    if ((x == null) || (x.size() == 0)) return null;
	    String line = nxTools.tail1(x);
	    return nxTools.awk(nxTools.awk(line, " ", 1), ">", 2);
	} catch (Exception e) {
	    return null;
	}
    }

    private static String getWhatIsMyIP() {
	try {
        ArrayList x = httpc.wget(new URL("http://www.whatismyip.com/"), 5000, null, null, null);
	    x = nxTools.grep(x, 0, "Your IP is");
	    String line = nxTools.tail1(x);
	    return nxTools.awk(line, " ", 4);
	} catch (Exception e) {
	    return null;
	}
    }

    private static String getStanford() {
	try {
        ArrayList x = httpc.wget(new URL("http://www.slac.stanford.edu/cgi-bin/nph-traceroute.pl"), 5000, null, null, null);
	    x = nxTools.grep(x, 0, "firewall protecting your browser");
	    String line = nxTools.tail1(x);
	    return nxTools.awk(line, " ", 7);
	} catch (Exception e) {
	    return null;
	}
    }

    private static String getIPID() {
	try {
        ArrayList x = httpc.wget(new URL("http://ipid.shat.net/"), 5000, null, null, null);
	    x = nxTools.grep(x, 2, "Your IP address");
	    String line = nxTools.tail1(x);
	    return nxTools.awk(nxTools.awk(nxTools.awk(line, " ", 5), ">", 2), "<", 1);
	} catch (Exception e) {
	    return null;
	}
    }

    private static boolean isNotLocal(String ip) {
	if ((ip.equals("localhost")) ||
	    (ip.startsWith("127")) ||
	    (ip.startsWith("192.168")) ||
        (ip.startsWith("172.16")) ||
	    (ip.startsWith("10."))
	    ) return false;
	return true;
    }
    
    private static boolean isIP(String ip) {
	if (ip == null) return false;
	try {
	    /*InetAddress dummy =*/ InetAddress.getByName(ip);
	    return true;
	} catch (Exception e) {
	    return false;
	}
    }

    //TODO: This is not IPv6 compatible
    public static boolean isProper(String ip) {
        plasmaSwitchboard sb=plasmaSwitchboard.getSwitchboard();
        if (sb != null) {
            String yacyDebugMode = sb.getConfig("yacyDebugMode", "false");
            if (yacyDebugMode.equals("true")) {
                return true;
            }
            // support for staticIP
            if (sb.getConfig("staticIP", "").equals(ip)) {
                return true;
            }
        }
        if (ip == null) return false;
        if (ip.indexOf(":") >= 0) return false; // ipv6...
        return (isNotLocal(ip)) && (isIP(ip));
    }

    private static int retrieveOptions() {
	return 3;
    }
    
    private static String retrieveFrom(int option) {
	if ((option < 0) || (option >= retrieveOptions())) return null;
	if (option == 0) return getWhatIsMyIP();
	if (option == 1) return getStanford();
	if (option == 2) return getIPID();
	return null;
    }

    public static String retrieveIP(boolean DI604, String password) {
	String ip;
	if (DI604) {
	    // first try the simple way...
	    ip = getDI604(password);
	    if (isProper(ip)) {
		//System.out.print("{DI604}");
		return ip;
	    }
	}

	// maybe this is a dial-up connection (or LAN and DebugMode) and we can get it from java variables
	/*InetAddress ia = serverCore.publicIP();
	if (ia != null) {
	    ip = ia.getHostAddress();
	    if (isProper(ip)) return ip;
	}*/
	ip = serverCore.publicIP();
	if (isProper(ip)) return ip;

	// now go the uneasy way and ask some web responder
	disorderHeap random = new disorderHeap(retrieveOptions());
	for (int i = 0; i < retrieveOptions(); i++) {
	    ip = retrieveFrom(random.number());
	    if (isProper(ip)) return ip;
	}
	return null;
    }

    // rDNS services:
    // http://www.xdr2.net/reverse_DNS_lookup.asp
    // http://remote.12dt.com/rns/
    // http://bl.reynolds.net.au/search/
    // http://www.declude.com/Articles.asp?ID=97
    // http://www.dnsstuff.com/
    
    // listlist: http://www.aspnetimap.com/help/welcome/dnsbl.html
    
    
    public static void main(String[] args) {
	//System.out.println("PROBE DI604     : " + getDI604(""));
	//System.out.println("PROBE whatismyip: " + getWhatIsMyIP());
	//System.out.println("PROBE stanford  : " + getStanford());
	//System.out.println("PROBE ipid      : " + getIPID());
	//System.out.println("retrieveIP-NAT : " + retrieveIP(true,""));
	//System.out.println("retrieveIP     : " + retrieveIP(false,"12345"));

	System.out.println(isProper(args[0]) ? "yes" : "no");
    }

}
