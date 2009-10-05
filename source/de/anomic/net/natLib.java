// natLib.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 04.05.2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

import de.anomic.search.Switchboard;

public class natLib {

    private static boolean isNotLocal(final String ip) {
	if ((ip.equals("localhost")) ||
	    (ip.startsWith("127")) ||
	    (ip.startsWith("192.168")) ||
        (ip.startsWith("172.16")) ||
	    (ip.startsWith("10."))
	    ) return false;
	return true;
    }
    
    private static boolean isIP(final String ip) {
	if (ip == null) return false;
	try {
	    /*InetAddress dummy =*/ InetAddress.getByName(ip);
	    return true;
	} catch (final Exception e) {
	    return false;
	}
    }

    //TODO: This is not IPv6 compatible
    public static boolean isProper(final String ip) {
        final Switchboard sb=Switchboard.getSwitchboard();
        if (sb != null) {
        	if (sb.isRobinsonMode()) return true;
            final String yacyDebugMode = sb.getConfig("yacyDebugMode", "false");
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
    
    public static final InetAddress getInetAddress(final String ip) {
        if (ip == null) return null;
        if (ip.length() < 8) return null;
        final String[] ips = ip.split("\\.");
        if (ips.length != 4) return null;
        final byte[] ipb = new byte[4];
        try {
            ipb[0] = (byte) Integer.parseInt(ips[0]);
            ipb[1] = (byte) Integer.parseInt(ips[1]);
            ipb[2] = (byte) Integer.parseInt(ips[2]);
            ipb[3] = (byte) Integer.parseInt(ips[3]);
        } catch (final NumberFormatException e) {
            return null;
        }
        try {
            return InetAddress.getByAddress(ipb);
        } catch (final UnknownHostException e) {
            return null;
        }
    }

    // rDNS services:
    // http://www.xdr2.net/reverse_DNS_lookup.asp
    // http://remote.12dt.com/rns/
    // http://bl.reynolds.net.au/search/
    // http://www.declude.com/Articles.asp?ID=97
    // http://www.dnsstuff.com/
    
    // listlist: http://www.aspnetimap.com/help/welcome/dnsbl.html
    

}
