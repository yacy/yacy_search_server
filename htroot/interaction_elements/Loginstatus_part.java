package interaction_elements;

//ViewLog_p.java
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
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


//You must compile this file with
//javac -classpath .:../classes ViewLog_p.java
//if the shell's current path is HTROOT

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.UserDB;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

public class Loginstatus_part {

    public static serverObjects respond(final RequestHeader requestHeader, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {

    	final Switchboard sb = (Switchboard) env;

    	final servletProperties prop = new servletProperties();

        prop.put("enabled", env.getConfigBool("interaction.userlogon.enabled", false) ? "1" : "0");

        prop.put("enabled_color", env.getConfig("color_tableheader", ""));

        prop.put("enabled_logged-in_registrationenabled", env.getConfigBool("interaction.userselfregistration.enabled", false) ? "1" : "0");

//
//        final String address = sb.peers.mySeed().getPublicAddress();

        prop.put("enabled_peer", sb.peers.myName());

        prop.put("enabled_logged-in_returnto", "/index.html");


        UserDB.Entry entry=null;

        //default values
        prop.put("enabled_logged_in", "0");
        prop.put("enabled_logged-in_limit", "0");
        prop.put("enabled_logged-in_username", "anonymous");
        prop.put("enabled_status", "0");
        //identified via HTTPPassword
        entry=sb.userDB.proxyAuth((requestHeader.get(RequestHeader.AUTHORIZATION, "xxxxxx")));
        if(entry != null){
        	prop.put("enabled_logged-in_identified-by", "1");
        //try via cookie
        }else{
            entry=sb.userDB.cookieAuth(requestHeader.getHeaderCookies());
            prop.put("enabled_logged-in_identified-by", "2");
            //try via ip
            if(entry == null){
                entry=sb.userDB.ipAuth((requestHeader.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "xxxxxx")));
                if(entry != null){
                    prop.put("enabled_logged-in_identified-by", "0");
                }
            }
        }

        //identified via userDB
        if(entry != null){
            prop.put("enabled_logged-in", "1");
            prop.put("enabled_logged-in_username", entry.getUserName());
            if(entry.getTimeLimit() > 0){
                prop.put("enabled_logged-in_limit", "1");
                final long limit=entry.getTimeLimit();
                final long used=entry.getTimeUsed();
                prop.put("enabled_logged-in_limit_timelimit", limit);
                prop.put("enabled_logged-in_limit_timeused", used);
                int percent=0;
                if(limit!=0 && used != 0)
                    percent=(int)((float)used/(float)limit*100);
                prop.put("enabled_logged-in_limit_percent", percent/3);
                prop.put("enabled_logged-in_limit_percent2", (100-percent)/3);
            }
        //logged in via static Password
        }else if(sb.verifyAuthentication(requestHeader)){
            prop.put("enabled_logged-in", "2");
            prop.put("enabled_logged-in_username", "staticadmin");
        //identified via form-login
        //TODO: this does not work for a static admin, yet.
        }

        // return rewrite properties
        return prop;

    }
}
