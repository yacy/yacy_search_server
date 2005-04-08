// SettingsAck_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 16.02.2005
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

// you must compile this file with
// javac -classpath .:../Classes SettingsAck_p.java
// if the shell's current path is HTROOT

import java.util.*;
import java.io.*;
import de.anomic.tools.*;
import de.anomic.server.*;
import de.anomic.yacy.*;
import de.anomic.http.*;

public class SettingsAck_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        
        //if (post == null) System.out.println("POST: NULL"); else System.out.println("POST: " + post.toString());
        
        // set values
        String s;
        int pos;
        
        if (post == null) {
            prop.put("info", 1);//no information submitted
            return prop;
        }
        
        // admin password
        if (post.containsKey("adminaccount")) {
            // read and process data
            String user   = (String) post.get("adminuser");
            String pw1    = (String) post.get("adminpw1");
            String pw2    = (String) post.get("adminpw2");
            // do checks
            if ((user == null) || (pw1 == null) || (pw2 == null)) {
                prop.put("info", 1);//error with submitted information
                return prop;
            }
            if (user.length() == 0) {
                prop.put("info", 2);//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", 3);//pw check failed
                return prop;
            }
            // check passed. set account:
            env.setConfig("adminAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(user + ":" + pw1)));
            env.setConfig("adminAccount", "");
            prop.put("info", 5);//admin account changed
            prop.put("info_user", user);
            return prop;
        }
        
        
        // proxy password
        if (post.containsKey("proxyaccount")) {
            // read and process data
            String filter = (String) post.get("proxyfilter");
            String user   = (String) post.get("proxyuser");
            String pw1    = (String) post.get("proxypw1");
            String pw2    = (String) post.get("proxypw2");
            // do checks
            if ((filter == null) || (user == null) || (pw1 == null) || (pw2 == null)) {
                prop.put("info", 1);//error with submitted information
                return prop;
            }
            if (user.length() == 0) {
                prop.put("info", 2);//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", 3);//pw check failed
                return prop;
            }
            if (filter.length() == 0) filter = "*";
            // check passed. set account:
            env.setConfig("proxyClient", filter);
            if (pw1.length() == 0) {
                // only ip filter setting without account
                env.setConfig("proxyAccountBase64MD5", "");
                env.setConfig("proxyAccount", "");
                prop.put("info", 6);//proxy account has changed(no pw)
                prop.put("info_filter", filter);
            } else {
                // also paccount setting
                env.setConfig("proxyAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(user + ":" + pw1)));
                env.setConfig("proxyAccount", "");
                prop.put("info", 7);//proxy account has changed
                prop.put("info_user", user);
                prop.put("info_filter", filter);
            }
            return prop;
        }
        
        // server password
        if (post.containsKey("serveraccount")) {
            // read and process data
            String filter = (String) post.get("serverfilter");
            String user   = (String) post.get("serveruser");
            String pw1    = (String) post.get("serverpw1");
            String pw2    = (String) post.get("serverpw2");
            // do checks
            if (filter == null) {
                //if ((filter == null) || (user == null) || (pw1 == null) || (pw2 == null)) {
                prop.put("info", 1);//error with submitted information
                return prop;
            }
            if (user.length() == 0) {
                prop.put("info", 2);//username must be given
                return prop;
            }
            if (!(pw1.equals(pw2))) {
                prop.put("info", 3);//pw check failed
                return prop;
            }
            if (filter.length() == 0) filter = "*";
            // check passed. set account:
            env.setConfig("serverClient", filter);
            env.setConfig("serverAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(user + ":" + pw1)));
            env.setConfig("serverAccount", "");
            
            prop.put("info", 8);//server access filter updated
            prop.put("info_user", user);
            prop.put("info_filter", filter);
            return prop;
        }
        
        if (post.containsKey("dispop")) {
            env.setConfig("browserPopUpTrigger", "false");
            prop.put("info", 9);//popup disabled
            return prop;
        }
        
        if (post.containsKey("enpop")) {
            env.setConfig("browserPopUpTrigger", "true");
            prop.put("info", 10);//popup enabled
            return prop;
        }
        
        if (post.containsKey("pmode")) {
            env.setConfig("onlineMode", "2");
            prop.put("info", 11);//permanent online mode
            yacyCore.triggerOnlineAction();
            return prop;
        }
        
        if (post.containsKey("generalsettings")) {
            String port = (String) post.get("port");
            String peerName = (String) post.get("peername");
            String shutdownWaiting = (String) post.get("shutdownWaiting");
            
            // check if peer name already exists
            yacySeed oldSeed = yacyCore.seedDB.lookupByName(peerName);
            
            if (oldSeed == null) {
                // the name is new
                boolean nameOK = (peerName.length() <= 80);
                for (int i = 0; i < peerName.length(); i++) {
                    if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_".indexOf(peerName.charAt(i)) < 0) nameOK = false;
                }
                if (!(nameOK)) {
                    // deny change
                    prop.put("info", 17);//peer name is wrong
                } else {
                    
                    // set values
                    env.setConfig("port", port);
                    env.setConfig("peerName", peerName);
                    env.setConfig("shutdownWaiting", shutdownWaiting);
                    
                    prop.put("info", 12);//port or peername changed
                    prop.put("info_port", port);
                    prop.put("info_peerName", peerName);
                    prop.put("info_shutdownWaiting", shutdownWaiting);
                }
            } else {
                // deny change
                prop.put("info", 16);//peer name is already used by another peer
            }
            return prop;
        }
        
        if (post.containsKey("proxysettings")) {
            httpdProxyHandler.remoteProxyUse = ((String) post.get("remoteProxyUse", "")).equals("on");
            httpdProxyHandler.remoteProxyHost = (String) post.get("remoteProxyHost", "");
            try {
                httpdProxyHandler.remoteProxyPort = Integer.parseInt((String) post.get("remoteProxyPort", ""));
            } catch (NumberFormatException e) {
                httpdProxyHandler.remoteProxyPort = 3128;
            }
            httpdProxyHandler.remoteProxyNoProxy = (String) post.get("remoteProxyNoProxy", "");
            httpdProxyHandler.remoteProxyNoProxyPatterns = httpdProxyHandler.remoteProxyNoProxy.split(",");
            env.setConfig("remoteProxyHost", httpdProxyHandler.remoteProxyHost);
            env.setConfig("remoteProxyPort", "" + httpdProxyHandler.remoteProxyPort);
            env.setConfig("remoteProxyNoProxy", httpdProxyHandler.remoteProxyNoProxy);
            env.setConfig("remoteProxyUse", (httpdProxyHandler.remoteProxyUse) ? "true" : "false");
            prop.put("info", 15); // The remote-proxy setting has been changed
            return prop;
        }
        
        if (post.containsKey("seedFTPsettings")) {
            env.setConfig("seedFTPServer", (String)post.get("seedFTPServer"));
            env.setConfig("seedFTPPath", (String)post.get("seedFTPPath"));
            env.setConfig("seedFTPAccount", (String)post.get("seedFTPAccount"));
            env.setConfig("seedFTPPassword", (String)post.get("seedFTPPassword"));
            env.setConfig("seedURL", (String)post.get("seedURL"));
            if (yacyCore.saveSeedList(env))
                prop.put("info", 13);//SeedServer changed
            else
                prop.put("info", 14);//Seedserver changed, but something is wrong
            return prop;
        }
        
        // nothing made
        prop.put("info", 1);//no information submitted
        return prop;
    }
    
}
