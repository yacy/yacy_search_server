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
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverAccessTracker;

import org.eclipse.jetty.util.security.Credential;



/**
 * implementation of YaCy's old admin password as jetty Credential
 * supporting BASIC and DIGEST authentication
 * and using MD5 encryptet passwords/credentials. Following RFC recommendation (to use the realm in MD5 hash)
 * expecting a MD5 hash in format  MD5( username:realm:password ), realm configured in yacy.init adminRealm
 * (exception: old style credential MD5( username:password ) still accepted with BASIC auth)
 *
 */
public class YaCyLegacyCredential extends Credential {
	
    private static final long serialVersionUID = -3527894085562480001L;

    private String hash; // remember password hash (for new style with prefix of used encryption supported "MD5:" )
    private String foruser; // remember the user as YaCy credential is username:pwd (not just pwd)
    private boolean isBase64enc; // remember hash encoding  false = encodeMD5Hex(usr:pwd) ; true = encodeMD5Hex(Base64Order.standardCoder.encodeString(usr:pw))
    private Credential c;

    /**
     * internal hash function for admin account
     *
     * @param pw clear password
     * @return hash string
     */
    public static String calcHash(String pw) { // old style hash
        return Digest.encodeMD5Hex(Base64Order.standardCoder.encodeString(pw));
    }

    @Override
    public boolean check(Object credentials) {

        if (credentials instanceof Credential) { // for DIGEST auth
            return ((Credential) credentials).check(c);

        }
        if (credentials instanceof String) { // for BASIC auth
            final String pw = (String) credentials;
            if (isBase64enc) { // for old B64MD5 admin hashes
                if (serverAccessTracker.timeSinceAccessFromLocalhost() < 100) {
                    // we allow localhost accesses also to submit the hash as password
                    // this is very important since that method is used by the scripts in bin/ which are based on bin/apicall.sh
                    // the cleartext password is not stored anywhere, but we must find a way to allow scripts to steer a peer.
                    // this is the exception that makes that possible.
                    // TODO: it should be better to check the actual access IP here, but that is not handed over to Credential classes :(
                    if ((pw).equals(this.hash)) return true;
                }
                // exception for admin use old style MD5hash (user:password)
                return calcHash(foruser + ":" + pw).equals(this.hash); // for admin user
            }

            // normal users (and new admin pwd) for BASIC auth
            if (hash.startsWith(MD5.__TYPE) && hash != null) {
                boolean success = (Digest.encodeMD5Hex(foruser + ":" + Switchboard.getSwitchboard().getConfig(SwitchboardConstants.ADMIN_REALM,"YaCy")+":" + pw).equals(hash.substring(4)));
                // exception: allow the hash as pwd (used  in bin/apicall.sh)
                if (!success && foruser.equals(Switchboard.getSwitchboard().getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"))) {
                    if (pw.equals(hash)) {
                        if (serverAccessTracker.timeSinceAccessFromLocalhost() < 100) {
                            return true;
                        }
                    }
                }
                return success;
            }
            return Digest.encodeMD5Hex(foruser + ":" + pw).equals(hash); // for old userdb hashes
        }
        throw new UnsupportedOperationException();
    }
	
    /**
     * create Credential object from config file hash
     *
     * @param configHash hash as in config file hash(adminuser:pwd)
     * @return
     */
    public static Credential getCredentialForAdmin(String username, String configHash) {
        YaCyLegacyCredential yc = new YaCyLegacyCredential();
        if (configHash.startsWith("MD5:")) {
            yc.isBase64enc = false;
            yc.c = Credential.getCredential(configHash);
        } else {
             yc.isBase64enc = true;
        }
        yc.foruser = username;
        yc.hash = configHash;
        return yc;
    }

    /**
     * create Credential object from password
     *
     * @param username
     * @param configHash encodeMD5Hex("user:realm:pwd") as stored in UserDB
     * @return
     */
    public static Credential getCredentialForUserDB(String username, String configHash) {
        YaCyLegacyCredential yc = new YaCyLegacyCredential();
        yc.c = Credential.getCredential(configHash); // creates a MD5 hash credential
        yc.foruser = username;
        yc.isBase64enc = false;
        yc.hash = configHash;
        return yc;
    }

}
