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

import net.yacy.cora.protocol.Domains;
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

    /**
     * True socket peer IP of the request currently being authenticated on this thread.
     * <p>
     * Jetty's {@link Credential#check(Object)} API does not hand over the request, so the
     * credential can not tell on its own whether a request comes from localhost. The
     * {@link YaCySecurityHandler} therefore publishes the request's socket peer IP here for
     * the duration of the request (set at the start of handling, cleared in a finally block).
     * This replaces the former global "recent localhost access" timestamp, which was a
     * process-wide value not bound to the request being checked.
     */
    private static final ThreadLocal<String> REQUEST_CLIENT_IP = new ThreadLocal<String>();

    /**
     * Publish the socket peer IP of the request being authenticated on the current thread.
     * Must be paired with {@link #clearRequestClientIP()} in a finally block.
     * @param ip the true socket peer IP (never an X-Real-IP derived address)
     */
    public static void setRequestClientIP(final String ip) {
        REQUEST_CLIENT_IP.set(ip);
    }

    /**
     * Remove the request client IP published for the current thread.
     */
    public static void clearRequestClientIP() {
        REQUEST_CLIENT_IP.remove();
    }

    /**
     * @return true when the request currently authenticated on this thread comes from
     *         localhost. Fails closed (returns false) when no request IP was published.
     */
    private static boolean isRequestFromLocalhost() {
        final String ip = REQUEST_CLIENT_IP.get();
        return ip != null && Domains.isLocalhost(ip);
    }

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
            // the request's true socket peer IP into REQUEST_CLIENT_IP for the duration of the
            // request (see the detailed rationale on YaCySecurityHandler.handle()); we read it
            // back here. Using the socket peer - not the spoofable X-Real-IP header - keeps
            // this exception restricted to genuine local callers.
            return AdminSecurity.checkAdminPassword(this.foruser, this.hash,
                    sb.getConfig(SwitchboardConstants.ADMIN_REALM, ""),
                    sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"),
                    isRequestFromLocalhost(),
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
