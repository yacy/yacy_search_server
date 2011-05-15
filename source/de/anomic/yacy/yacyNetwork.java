// yacyNetwork.java 
// ----------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.07.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import java.util.LinkedHashMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.order.Digest;
import org.apache.http.entity.mime.content.ContentBody;

import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class yacyNetwork {

	public static final boolean authentifyRequest(final serverObjects post, final serverSwitch env) {
		if (post == null || env == null) return false;
		
		// identify network
		final String unitName = post.get(SwitchboardConstants.NETWORK_NAME, yacySeed.DFLT_NETWORK_UNIT); // the network unit  
		if (!unitName.equals(env.getConfig(SwitchboardConstants.NETWORK_NAME, yacySeed.DFLT_NETWORK_UNIT))) {
			return false;
		}
        
		// check authentication method
		final String authenticationControl = env.getConfig("network.unit.protocol.control", "uncontrolled");
		if (authenticationControl.equals("uncontrolled")) return true;
		final String authenticationMethod = env.getConfig("network.unit.protocol.request.authentication.method", "");
		if (authenticationMethod.length() == 0) {
			return false;
		}
		if (authenticationMethod.equals("salted-magic-sim")) {
            // authorize the peer using the md5-magic
            final String salt = post.get("key", "");
            final String iam = post.get("iam", "");
            final String magic = env.getConfig("network.unit.protocol.request.authentication.essentials", "");
            final String md5 = Digest.encodeMD5Hex(salt + iam + magic);
			return post.get("magicmd5", "").equals(md5);
		}
		
		// unknown authentication method
		return false;
	}
	
	public static final LinkedHashMap<String,ContentBody> basicRequestParts(final Switchboard sb, final String targetHash, final String salt) {
        // put in all the essentials for routing and network authentication
		// generate a session key
        final LinkedHashMap<String,ContentBody> parts = basicRequestParts(sb.peers.mySeed().hash, targetHash, Switchboard.getSwitchboard().getConfig(SwitchboardConstants.NETWORK_NAME, yacySeed.DFLT_NETWORK_UNIT));
        parts.put("key", UTF8.StringBody(salt));
        
        // authentication essentials
        final String authenticationControl = sb.getConfig("network.unit.protocol.control", "uncontrolled");
        final String authenticationMethod = sb.getConfig("network.unit.protocol.request.authentication.method", "");
        if ((authenticationControl.equals("controlled")) && (authenticationMethod.length() > 0)) {
            if (authenticationMethod.equals("salted-magic-sim")) {
                // generate an authentication essential using the salt, the iam-hash and the network magic
                final String magic = sb.getConfig("network.unit.protocol.request.authentication.essentials", "");
                final String md5 = Digest.encodeMD5Hex(salt + sb.peers.mySeed().hash + magic);
                parts.put("magicmd5", UTF8.StringBody(md5));
            }
        }        
        
		return parts;
	}
	
    public static final LinkedHashMap<String,ContentBody> basicRequestParts(String myHash, String targetHash, String networkName) {
        // put in all the essentials for routing and network authentication
        // generate a session key
        final LinkedHashMap<String,ContentBody> parts = new LinkedHashMap<String,ContentBody>();
        
        // just standard identification essentials
        if (myHash != null) {
            parts.put("iam", UTF8.StringBody(myHash));
            if (targetHash != null) parts.put("youare", UTF8.StringBody(targetHash));
            
            // time information for synchronization
            // use our own formatter to prevent concurrency locks with other processes
            GenericFormatter my_SHORT_SECOND_FORMATTER  = new GenericFormatter(GenericFormatter.FORMAT_SHORT_SECOND, GenericFormatter.time_second);
            parts.put("mytime", UTF8.StringBody(my_SHORT_SECOND_FORMATTER.format()));
            parts.put("myUTC", UTF8.StringBody(Long.toString(System.currentTimeMillis())));

            // network identification
            parts.put(SwitchboardConstants.NETWORK_NAME, UTF8.StringBody(networkName));
        }
        
        return parts;
    }
}
