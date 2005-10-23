// query.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
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
// javac -classpath .:../../classes query.java
// if the shell's current path is HTROOT

import java.util.Date;
import java.io.IOException;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class query {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch ss) {
        if (post == null || ss == null) { return null; }

        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) ss;
        final serverObjects prop = new serverObjects();
        if (prop == null || sb == null) { return null; }

//      System.out.println("YACYQUERY: RECEIVED POST = " + ((post == null) ? "NULL" : post.toString()));

//      final String iam    = (String) post.get("iam", "");    // complete seed of the requesting peer
        final String youare = (String) post.get("youare", ""); // seed hash of the target peer, used for testing network stability
//      final String key    = (String) post.get("key", "");    // transmission key for response
        final String obj    = (String) post.get("object", ""); // keyword for query subject
        final String env    = (String) post.get("env", "");    // argument to query

        prop.put(yacySeed.MYTIME, yacyCore.universalDateShortString(new Date()));

        // check if we are the right target and requester has correct information about this peer
        if (yacyCore.seedDB.mySeed == null || !yacyCore.seedDB.mySeed.hash.equals(youare)) {
            // this request has a wrong target
            prop.put("response", "-1"); // request rejected
            return prop;
        }

        // requests about environment
        if (obj.equals("rwiurlcount")) {
            // the total number of different urls in the rwi is returned
            // <env> shall contain a word hash, the number of assigned lurls to this hash is returned
            de.anomic.plasma.plasmaWordIndexEntity entity = null;
            try {
                entity = sb.wordIndex.getEntity(env, true, -1);
                prop.put("response", entity.size());
                entity.close();
            } catch (IOException e) {
                prop.put("response", -1);
            } finally {
              if (entity != null) try { entity.close(); } catch (Exception e) {}
            }
            return prop;
        }

        if (obj.equals("rwicount")) {
            // return the total number of available word indexes
            prop.put("response", sb.wordIndex.size());
            return prop;
        }

        if (obj.equals("lurlcount")) {
            // return the number of all available l-url's
            prop.put("response", sb.urlPool.loadedURL.size());
            return prop;
        }

        // requests about requirements

        if (obj.equals("wantedlurls")) {
            prop.put("response", "0"); // dummy response
            return prop;
        }

        if (obj.equals("wantedpurls")) {
            prop.put("response", "0"); // dummy response
            return prop;
        }

        if (obj.equals("wantedword")) {
            // response returns a list of wanted word hashes
            prop.put("response", "0"); // dummy response
            return prop;
        }

        if (obj.equals("wantedrwi")) {
            // <env> shall contain a word hash, the number of wanted lurls for this hash is returned
            prop.put("response", "0"); // dummy response
            return prop;
        }

        if (obj.equals("wantedseeds")) {
            // return a number of wanted seed
            prop.put("response", "0"); // dummy response
            return prop;
        }

        // return rewrite properties
        return prop;
    }

}