// yacyNetwork.java 
// ----------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.07.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2007-07-03 22:55:47 +0000 (Di, 03 Jul 2007) $
// $LastChangedRevision: 3950 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.yacy;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class yacyNetwork {

	public static final boolean authentifyRequest(serverObjects post, serverSwitch<?> env) {
		if ((post == null) || (env == null)) return false;
		
		// identify network
		String unitName = post.get("network.unit.name", yacySeed.DFLT_NETWORK_UNIT); // the network unit  
		if (!unitName.equals(env.getConfig("network.unit.name", yacySeed.DFLT_NETWORK_UNIT))) {
			return false;
		}
        
		// check authentification method
		String authentificationControl = env.getConfig("network.unit.protocol.control", "uncontrolled");
		if (authentificationControl.equals("uncontrolled")) return true;
		String authentificationMethod = env.getConfig("network.unit.protocol.request.authentification.method", "");
		if (authentificationMethod.length() == 0) {
			return false;
		}
		if (authentificationMethod.equals("salted-magic-sim")) {
            // authentify the peer using the md5-magic
            String salt = post.get("key", "");
            String iam = post.get("iam", "");
            String magic = env.getConfig("network.unit.protocol.request.authentification.essentials", "");
            String md5 = serverCodings.encodeMD5Hex(salt + iam + magic);
			return post.get("magicmd5", "").equals(md5);
		}
		
		// unknown authentification method
		return false;
	}
	
	public static final List<Part> basicRequestPost(plasmaSwitchboard sb, String targetHash, String salt) {
        // put in all the essentials for routing and network authentification
		// generate a session key
        ArrayList<Part> post = new ArrayList<Part>();
        post.add(new StringPart("key", salt));
        
        // just standard identification essentials
		post.add(new StringPart("iam", sb.wordIndex.seedDB.mySeed().hash));
		if (targetHash != null) post.add(new StringPart("youare", targetHash));
        
        // time information for synchronization
		post.add(new StringPart("mytime", serverDate.formatShortSecond(new Date())));
		post.add(new StringPart("myUTC", Long.toString(System.currentTimeMillis())));

        // network identification
        post.add(new StringPart("network.unit.name", plasmaSwitchboard.getSwitchboard().getConfig("network.unit.name", yacySeed.DFLT_NETWORK_UNIT)));

        // authentification essentials
        String authentificationControl = sb.getConfig("network.unit.protocol.control", "uncontrolled");
        String authentificationMethod = sb.getConfig("network.unit.protocol.request.authentification.method", "");
        if ((authentificationControl.equals("controlled")) && (authentificationMethod.length() > 0)) {
            if (authentificationMethod.equals("salted-magic-sim")) {
                // generate an authentification essential using the salt, the iam-hash and the network magic
                String magic = sb.getConfig("network.unit.protocol.request.authentification.essentials", "");
                String md5 = serverCodings.encodeMD5Hex(salt + sb.wordIndex.seedDB.mySeed().hash + magic);
                post.add(new StringPart("magicmd5", md5));
            }
        }        
        
		return post;
	}
	
}
