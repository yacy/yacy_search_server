//
//  YaCyLegacyCredentials
//  Copyright 2011 by Florian Richter
//  First released 16.04.2011 at http://yacy.net
//  
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
//
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//  
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//  
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program in the file lgpl21.txt
//  If not, see <http://www.gnu.org/licenses/>.
//

package net.yacy.http;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.server.serverAccessTracker;

import org.eclipse.jetty.util.security.Credential;



/**
 * implementation of YaCy's old admin password as jetty Credential
 */
public class YaCyLegacyCredential extends Credential {
	
    private static final long serialVersionUID = -3527894085562480001L;
    private String hash;
    private String foruser; // remember the user as YaCy credential is username:pwd (not just pwd)
    private boolean isBase64enc; // remember hash encoding  false = encodeMD5Hex(usr:pwd) ; true = encodeMD5Hex(Base64Order.standardCoder.encodeString(usr:pw))
    
    /**
     * internal hash function
     *
     * @param clear password
     * @return hash string
     */
    public static String calcHash(String pw) {
        return Digest.encodeMD5Hex(Base64Order.standardCoder.encodeString(pw));
    }

    @Override
    public boolean check(Object credentials) {
        if (credentials instanceof String) {
            final String pw = (String) credentials;
            if (isBase64enc) {
                if (serverAccessTracker.timeSinceAccessFromLocalhost() < 100) {
                    // we allow localhost accesses also to submit the hash as password
                    // this is very important since that method is used by the scripts in bin/ which are based on bin/apicall.sh
                    // the cleartext password is not stored anywhere, but we must find a way to allow scripts to steer a peer.
                    // this is the exception that makes that possible.
                    // TODO: it should be better to check the actual access IP here, but that is not handed over to Credential classes :(
                    if (pw.equals(this.hash)) return true;
                }
                return calcHash(foruser + ":" + pw).equals(this.hash); // for admin user
            }
            // normal users
            return Digest.encodeMD5Hex(foruser + ":" + pw).equals(this.hash);
        }
        throw new UnsupportedOperationException();
    }
	
	/**
	 * create Credential object from config file hash
	 * @param configHash hash as in config file hash(adminuser:pwd)
	 * @return
	 */
	public static Credential getCredentialsFromConfig(String username, String configHash) {
		YaCyLegacyCredential c = new YaCyLegacyCredential();
        c.foruser = username;
        c.isBase64enc = true;
		c.hash = configHash;
		return c;
	}
	
	/**
	 * create Credential object from password
         * @param username
	 * @param configHash encodeMD5Hex("user:pwd") as stored in UserDB
	 * @return
	 */
	public static Credential getCredentials(String username, String configHash) {
		YaCyLegacyCredential c = new YaCyLegacyCredential();
        c.foruser = username;
        c.isBase64enc = false;
        c.hash = configHash;
		//c.hash = calcHash(user + ":" + password);
		return c;
	}

}
