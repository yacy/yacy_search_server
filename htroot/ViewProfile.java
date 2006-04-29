// ViewProfile_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// This File is contributed by Alexander Schier
// last change: 29.06.2005 by Marc Nause
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
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.data.wikiCode;

public class ViewProfile {

	public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	    // listManager.switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        wikiCode wikiTransformer = new wikiCode(switchboard);
        boolean authenticated = switchboard.adminAuthenticated(header) >= 2;
        int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        prop.put("display", display);
        String hash = (post == null) ? null : (String) post.get("hash");
        
        if ((hash == null) || (yacyCore.seedDB == null)) {
            // wrong access
            prop.put("success","0");
            return prop;
        }
        
        // get the profile
        HashMap profile = null;
        if (hash.equals("localhash")) {
            // read the profile from local peer
            Properties p = new Properties();
            FileInputStream fileIn = null;
            try {
                fileIn = new FileInputStream(new File("DATA/SETTINGS/profile.txt"));
                p.load(fileIn);        
            } catch(IOException e) {} finally {
                if (fileIn != null) try { fileIn.close(); fileIn = null; } catch (Exception e) {}
            }
            profile = new HashMap();
            profile.putAll(p);
            prop.put("success", "3"); // everything ok
            prop.put("localremotepeer", 0);
            prop.put("success_peername", yacyCore.seedDB.mySeed.getName());
        } else {
            // read the profile from remote peer
            yacySeed seed = yacyCore.seedDB.getConnected(hash);
            if (seed == null) {
                seed = yacyCore.seedDB.getDisconnected(hash);
                if (seed == null) {
                    prop.put("success", "1"); // peer unknown
                } else {
                    prop.put("success", "2"); // peer known, but disconnected
                    prop.put("success_peername", seed.getName());
                }
            } else {
                prop.put("success", "3"); // everything ok
                // process news if existent
                try {
                    yacyNewsRecord record = yacyCore.newsPool.getByOriginator(yacyNewsPool.INCOMING_DB, "prfleupd", seed.hash);
                    if (record != null) yacyCore.newsPool.moveOff(yacyNewsPool.INCOMING_DB, record.id());
                } catch (IOException e) {}

                // read profile from other peer
                profile = yacyClient.getProfile(seed);
                yacyCore.log.logInfo("fetched profile:" + profile);
            }
            prop.put("localremotepeer", 1);
            prop.put("success_peername", seed.getName());
        }
        Iterator i;
        if (profile != null) {
            i = profile.entrySet().iterator();
        } else {
            i = (new ArrayList()).iterator();
        }
        Map.Entry entry;
        // all known keys which should be set as they are
        ArrayList knownKeys = new ArrayList();
        knownKeys.add("name");
        knownKeys.add("nickname");
        // knownKeys.add("homepage");//+http
        knownKeys.add("email");
        knownKeys.add("icq");
        knownKeys.add("jabber");
        knownKeys.add("yahoo");
        knownKeys.add("msn");
        knownKeys.add("comment");

        //empty values
        Iterator it = knownKeys.iterator();
        while (it.hasNext()) {
            prop.put("success_" + (String) it.next(), 0);
        }

        //number of not explicitly recognized but displayed items
        int numUnknown = 0;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            String key = (String) entry.getKey();
            String value = new String();

            // only comments get "wikified"
            // this prevents broken links ending in <br>
            if (key.equals("comment")) {
                value = wikiTransformer.transform(((String) entry.getValue()).replaceAll("\r", "").replaceAll("\\\\n", "\n"));
            } else { // else only HTML tags get transformed to regular text   
                value = wikiCode.replaceHTML(((String) entry.getValue()).replaceAll("\r", "").replaceAll("\\\\n", "\n"));
            }

            //all known Keys which should be set as they are
            if ((knownKeys.contains(key)) && (value.length() > 0)) {
                prop.put("success_" + key, 1);
                prop.put("success_" + key + "_value", value);
                //special handling, hide flower if no icq uin is set
            } else if ((key.equals("homepage")) && (value.length() > 0)) {
                if (!(value.startsWith("http"))) {
                    value = "http://" + value;
                }
                prop.put("success_" + key, 1);
                prop.put("success_" + key + "_value", value);
                //This will display unknown items(of newer versions) as plaintext
            } else {
                //unknown
                prop.put("success_other_" + numUnknown + "_key", key);
                prop.put("success_other_" + numUnknown + "_value", value);
                numUnknown++;
            }
        }
        prop.put("success_other", numUnknown);

        return prop;
    }

}
