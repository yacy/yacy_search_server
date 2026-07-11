//
//  YaCyDigestCredential
//  Copyright 2011 by Florian Richter
//  First released 16.04.2011 at https://yacy.net
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

import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

import org.eclipse.jetty.util.security.Credential;



/**
 * implementation of YaCy's admin password as jetty Credential
 * supporting BASIC and DIGEST authentication
 * and using MD5 digested passwords/credentials. Following RFC recommendation (to use the realm in MD5 hash)
 * expecting a MD5 hash in format  MD5( username:realm:password ), realm configured in yacy.init adminRealm
 * (a credential in format  MD5( username:password ) is also accepted with BASIC auth)
 *
 * This is a thin adapter to the servlet container: the password verification
 * logic is in the container neutral {@link AdminSecurity}.
 */
public class YaCyDigestCredential extends Credential {

    private static final long serialVersionUID = -3527894085562480001L;

    private String hash; // remember password hash, either MD5(Base64(user:pwd)) or with encryption prefix "MD5:" + MD5(user:realm:pwd)
    private String foruser; // remember the user as YaCy credential is username:pwd (not just pwd)
    private Credential c;

    @Override
    public boolean check(Object credentials) {

        if (credentials instanceof Credential) { // for DIGEST auth
            if (this.c == null) {
                /* credential may be null after switching from BASIC to DIGEST authentication without re-encoding the password */
                return false;
            }
            Credential credential = (Credential) credentials;
            return credential.check(this.c);
        }
        if (credentials instanceof String) { // for BASIC auth
            final Switchboard sb = Switchboard.getSwitchboard();
            // The "fromLocalhost" exception (see AdminSecurity.checkAdminPassword) lets a
            // localhost caller submit the stored password hash itself as the password. This
            // is what the bin/*.sh scripts do via bin/apicall.sh: they read the hash from the
            // configuration file to steer a local peer, because the cleartext password is
            // never stored anywhere.
            //
            // We must therefore know whether THIS request comes from localhost. Jetty's
            // Credential.check() is not given the request, so YaCySecurityHandler publishes
            // the true socket peer IP through AdminAuthenticationContext for this request.
            return AdminSecurity.checkAdminPassword(this.foruser, this.hash,
                    sb.getConfig(SwitchboardConstants.ADMIN_REALM, ""),
                    sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"),
                    AdminAuthenticationContext.isLocalhostRequest(),
                    (String) credentials);
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
        YaCyDigestCredential yc = new YaCyDigestCredential();
        if (configHash.startsWith("MD5:")) {
            yc.c = Credential.getCredential(configHash); // for DIGEST auth
        }
        yc.foruser = username;
        yc.hash = configHash;
        return yc;
    }

}
