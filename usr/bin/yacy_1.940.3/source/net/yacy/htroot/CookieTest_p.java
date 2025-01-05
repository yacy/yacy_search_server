// CookieTest_p.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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
//
// You must compile this file with
// javac -classpath .:../classes index.java
// if the shell's current path is HTROOT

package net.yacy.htroot;

import javax.servlet.http.Cookie;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

public class CookieTest_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {


        // case if no values are requested
        if (post == null || env == null) {

            // we create empty entries for template strings
            final serverObjects prop = new serverObjects();
            return prop;
        }

        final servletProperties prop = new servletProperties();
        if (post.containsKey("act") && "clear_cookie".equals(post.get("act"))) {
            final ResponseHeader outgoingHeader = new ResponseHeader(200);
            final Cookie[] cookies = header.getCookies();
            if (cookies != null) {
                for (final Cookie cookie : cookies) {
                    outgoingHeader.setCookie(cookie.getName(), cookie.getValue(), cookie.getMaxAge(), cookie.getPath(), cookie.getDomain(), cookie.getSecure());
                }
            }
            prop.setOutgoingHeader(outgoingHeader);
            prop.put("coockiesout", "0");

        } else if (post.containsKey("act") && "set_cookie".equals(post.get("act"))) {
            final String cookieName = post.get("cookie_name").trim();
            final String cookieValue = post.get("cookie_value").trim();
            final ResponseHeader outgoingHeader = new ResponseHeader(200);

            outgoingHeader.setCookie(cookieName,cookieValue);
            prop.setOutgoingHeader(outgoingHeader);
            prop.put("cookiesin", "1");
            prop.putHTML("cookiesin_0_name", cookieName);
            prop.putHTML("cookiesin_0_value", cookieValue);
       }

        final Cookie[] cookielst = header.getCookies();
        int i = 0;
        if (cookielst != null) {
            for (final Cookie singleco : cookielst) {
                prop.putHTML("cookiesout_" + i + "_string", singleco.getName() + "=" + singleco.getValue() + ";"); // output with ";" for compatiblity with cookiesin
                i++;
            }
        }
        prop.put("cookiesout", i);
        return prop;
    }
}
