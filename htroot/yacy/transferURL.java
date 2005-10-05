// transferURL.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last change: 24.01.2005
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
// javac -classpath .:../classes transferRWI.java


import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class transferURL {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        long start = System.currentTimeMillis(); 
        
	// return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();
        
	if ((post == null) || (env == null)) return prop;

	// request values
	String iam      = (String) post.get("iam", "");      // seed hash of requester
        String youare   = (String) post.get("youare", "");   // seed hash of the target peer, needed for network stability
	String key      = (String) post.get("key", "");      // transmission key
	int urlc        = Integer.parseInt((String) post.get("urlc", ""));    // number of transported urls 
        boolean granted = switchboard.getConfig("allowReceiveIndex", "false").equals("true");
	boolean blockBlacklist = switchboard.getConfig("indexReceiveBlockBlacklist", "false").equals("true");

        // response values
        String result = "";
        String doublevalues = "0";
        
        yacySeed otherPeer = yacyCore.seedDB.get(iam);
        String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));        
        
        if (granted) {
            int received = 0;
            int sizeBefore = switchboard.urlPool.loadedURL.size();
            // read the urls from the other properties and store
            String urls;
            plasmaCrawlLURL.Entry lEntry;
            for (int i = 0; i < urlc; i++) {
                urls = (String) post.get("url" + i);
                if (urls == null) {
                    yacyCore.log.logFine("transferURL: got null URL-string from peer " + otherPeerName);
                } else {
                    lEntry = switchboard.urlPool.loadedURL.newEntry(urls, true);
                    if ((lEntry != null) && (blockBlacklist)) {
                        if (plasmaSwitchboard.urlBlacklist.isListed(lEntry.url().getHost().toLowerCase(), lEntry.url().getPath())) {
                            yacyCore.log.logFine("transferURL: blocked blacklisted URL '" + lEntry.url() + "' from peer " + otherPeerName);
                            lEntry = null;
                        }
                    }
                    if (lEntry != null) {
                        switchboard.urlPool.loadedURL.addEntry(lEntry, iam, iam, 3);
                        yacyCore.log.logFine("transferURL: received URL '" + lEntry.url() + "' from peer " + otherPeerName);
                        received++;
                    }
                }
            }
            
            yacyCore.seedDB.mySeed.incRU(received);
            
            // return rewrite properties
            int more = switchboard.urlPool.loadedURL.size() - sizeBefore;
            doublevalues = Integer.toString(received - more);
            switchboard.getLog().logInfo("Received " + received + " URLs from peer " + otherPeerName + 
                                         " in " + (System.currentTimeMillis() - start) + " ms.");
            if ((received - more) > 0) switchboard.getLog().logSevere("Received " + doublevalues + " double URLs from peer " + otherPeerName);
            result = "ok";
        } else {
            switchboard.getLog().logInfo("Rejecting URLs from peer " + otherPeerName + ". Not granted.");
            result = "error_not_granted";
        }
        
        prop.put("double", doublevalues);
        prop.put("result", result);
	return prop;
    }

}
