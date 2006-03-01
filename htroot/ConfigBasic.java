// ConfigBasic_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created 28.02.2006
//
// $LastChangedDate: 2005-09-13 00:20:37 +0200 (Di, 13 Sep 2005) $
// $LastChangedRevision: 715 $
// $LastChangedBy: borg-0300 $
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
// javac -classpath .:../classes ConfigBasic_p.java
// if the shell's current path is HTROOT

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class ConfigBasic {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();

        //yacyCore.peerPing();
        
        // password settings
        String user   = (post == null) ? "" : (String) post.get("adminuser", "");
        String pw1    = (post == null) ? "" : (String) post.get("adminpw1", "");
        String pw2    = (post == null) ? "" : (String) post.get("adminpw2", "");
        
        // peer name settings
        String peerName = (post == null) ? "" : (String) post.get("peername", "");
        
        // port settings
        String port = (post == null) ? "8080" : (String) post.get("port", "8080");
        
        // admin password
        if ((user.length() > 0) && (!(pw1.equals(pw2)))) {
            // check passed. set account:
            env.setConfig("adminAccountBase64MD5", serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(user + ":" + pw1)));
            env.setConfig("adminAccount", "");
        }

        // check if peer name already exists
        yacySeed oldSeed = yacyCore.seedDB.lookupByName(peerName);
        if ((oldSeed == null) || (env.getConfig("peerName", "").equals(peerName))) {
            // the name is new
            boolean nameOK = (peerName.length() <= 80);
            for (int i = 0; i < peerName.length(); i++) {
                if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_".indexOf(peerName.charAt(i)) < 0)
                    nameOK = false;
            }
            if (nameOK) {
                // set values
                env.setConfig("peerName", peerName);
            }
        }
 
        // check port
        if (!env.getConfig("port", port).equals(port)) {
            // validate port
            serverCore theServerCore = (serverCore) env.getThread("10_httpd");
            env.setConfig("port", port);
            theServerCore.reconnect();
        }
        
        // check if values are proper
        boolean properName = (env.getConfig("peerName","").length() > 0) && (env.getConfig("peerName","0").charAt(0) > '9');
        boolean properPW = (env.getConfig("adminAccount", "").length() == 0) && (env.getConfig("adminAccountBase64MD5", "").length() > 0);
        boolean properPort = yacyCore.seedDB.mySeed.isSenior();
        
        prop.put("statusName", (properName) ? 1 : 0);
        prop.put("statusPassword", (properPW) ? 1 : 0);
        prop.put("statusPort", (properPort) ? 1 : 0);
        if (properName) {
            if (properPW) {
                if (properPort) {
                    prop.put("nextStep", 0);
                } else {
                    prop.put("nextStep", 3);
                }
            } else {
                prop.put("nextStep", 2);
            }
        } else {
            prop.put("nextStep", 1);
        }
        
        // set default values
        prop.put("defaultName", env.getConfig("peerName", ""));
        prop.put("defaultUser", "admin");
        prop.put("defaultPort", env.getConfig("port", "8080"));

        return prop;
    }
    
}
