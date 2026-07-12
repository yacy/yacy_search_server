//
//  AdminSecurity
//  Copyright 2011 by Florian Richter
//  First released 16.04.2011 at https://yacy.net
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

import java.net.MalformedURLException;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.Domains;

/**
 * Decision logic of YaCy's built-in administrator account security: which
 * paths are protected, when localhost access is granted without login and
 * verification of the supported admin password hash formats.
 *
 * All methods are pure functions on their parameters, free of servlet
 * container (Jetty) and Switchboard dependencies: the container specific
 * nested classes Jetty12HttpServer.AdminSecurityHandler, .AdminLoginService and
 * .AdminCredential are thin adapters delegating here (extracted from them to
 * ease servlet container migration).
 *
 * The complete container-neutral administrator security surface lives in this
 * file: the pure check functions, the request-level {@link AccessPolicy} built
 * on them, and the {@link AuthenticationContext} that carries the socket peer
 * IP through the container's credential verification.
 */
public final class AdminSecurity {

    private AdminSecurity() {
    }

    /**
     * Decide whether a path may only be accessed with admin rights.
     * Pages suffixed with "_p" are always considered protected. When all pages
     * are protected, paths used for peer-to-peer or cluster communication stay
     * public (e.g. /yacy/hello.html for p2p presence, /solr/select for remote
     * Solr searches), except in private robinson mode.
     *
     * @param pathInContext the request path
     * @param adminForAllPages configuration: all pages need admin rights
     * @param privateRobinsonMode true when this peer is in non-public robinson mode
     * @param publicSearchpage configuration: the search page is public
     * @return true when the path needs admin rights
     */
    public static boolean isProtectedPath(final String pathInContext, final boolean adminForAllPages,
            final boolean privateRobinsonMode, final boolean publicSearchpage) {
        boolean protectedPage = adminForAllPages && (privateRobinsonMode ||
                !(pathInContext.startsWith("/yacy/") || pathInContext.startsWith("/solr/")));
        protectedPage = protectedPage || (pathInContext.indexOf("_p.") > 0);
        if (!protectedPage && !publicSearchpage) {
            protectedPage = pathInContext.startsWith("/solr/") || pathInContext.startsWith("/gsa/");
        }
        return protectedPage;
    }

    /**
     * @param remoteip ip address of the client. This must be the true socket peer address:
     *        callers must not pass an X-Real-IP / X-Forwarded-For derived address here,
     *        as those headers are client-controlled and spoofable.
     * @param refererHost host part of the Referer request header (null when absent or unparseable)
     * @return true when the request comes from localhost and is not referred from a remote page
     */
    public static boolean isLocalhostAccess(final String remoteip, final String refererHost) {
        return Domains.isLocalhost(remoteip)
                && (refererHost == null || refererHost.isEmpty() || Domains.isLocalhost(refererHost));
    }

    /**
     * Lazy authentication for localhost access: accept Basic credentials that
     * contain the configured admin password hash as password (only a user with
     * read access to DATA can know that hash).
     *
     * @param authorizationHeader value of the Authorization request header (may be null)
     * @param adminUser configured name of the admin user
     * @param adminAccountBase64MD5 configured admin password hash
     * @return true when the header contains the admin hash as Basic credential
     */
    public static boolean checkLocalhostLazyAuth(final String authorizationHeader, final String adminUser,
            final String adminAccountBase64MD5) {
        // Basic credentials are short "Basic " + b64(user:pwd)
        if (authorizationHeader != null && authorizationHeader.length() < 120 && authorizationHeader.startsWith("Basic ")) {
            final String b64 = Base64Order.standardCoder.encodeString(adminUser + ":" + adminAccountBase64MD5);
            return authorizationHeader.substring(6).equals(b64);
        }
        return false;
    }

    /**
     * Verify a clear text password (BASIC auth) against the configured admin
     * password hash. Two hash formats are supported:
     * "MD5:" + MD5Hex(user:realm:password) and the Base64 based format
     * MD5Hex(Base64(user:password)).
     *
     * For both formats the configured hash itself is also accepted as password
     * when the request comes from localhost: this allows the scripts in bin/
     * (based on bin/apicall.sh) to steer a peer without knowing the clear text
     * password.
     *
     * @param foruser user name the credential was created for
     * @param configHash the configured password hash
     * @param realm the configured authentication realm (part of "MD5:" hashes)
     * @param adminUser configured name of the admin user
     * @param fromLocalhost true when the request being authenticated comes from localhost
     *        (determined from the request's true socket peer IP)
     * @param pw the clear text password to check
     * @return true when the password matches the configured hash
     */
    public static boolean checkAdminPassword(final String foruser, final String configHash, final String realm,
            final String adminUser, final boolean fromLocalhost, final String pw) {
        if (!configHash.startsWith("MD5:")) { // B64MD5 admin hashes without realm
            if (fromLocalhost && pw.equals(configHash)) return true;
            return calcHash(foruser + ":" + pw).equals(configHash);
        }
        final boolean success = Digest.encodeMD5Hex(foruser + ":" + realm + ":" + pw).equals(configHash.substring(4));
        if (!success && foruser.equals(adminUser) && fromLocalhost && pw.equals(configHash)) return true;
        return success;
    }

    /**
     * internal hash function for the Base64 based admin account hash format
     *
     * @param pw clear password
     * @return hash string MD5Hex(Base64(pw))
     */
    public static String calcHash(final String pw) {
        return Digest.encodeMD5Hex(Base64Order.standardCoder.encodeString(pw));
    }

    /** Container-neutral policy for administrator access to a request path. */
    public static final class AccessPolicy {

        public enum Decision {
            PUBLIC,
            LOCAL_BYPASS,
            ADMIN_REQUIRED
        }

        private final boolean protectAllPages;
        private final boolean privateRobinsonMode;
        private final boolean publicSearchPage;
        private final boolean allowLocalhostWithoutLogin;
        private final String adminUser;
        private final String adminHash;

        public AccessPolicy(final boolean protectAllPages, final boolean privateRobinsonMode,
                final boolean publicSearchPage, final boolean allowLocalhostWithoutLogin,
                final String adminUser, final String adminHash) {
            this.protectAllPages = protectAllPages;
            this.privateRobinsonMode = privateRobinsonMode;
            this.publicSearchPage = publicSearchPage;
            this.allowLocalhostWithoutLogin = allowLocalhostWithoutLogin;
            this.adminUser = adminUser;
            this.adminHash = adminHash;
        }

        public Decision decide(final String path, final String socketPeerIp, final String referer,
                final String authorizationHeader) {
            if (!AdminSecurity.isProtectedPath(path, this.protectAllPages,
                    this.privateRobinsonMode, this.publicSearchPage)) {
                return Decision.PUBLIC;
            }

            if (AdminSecurity.isLocalhostAccess(socketPeerIp, refererHost(referer))) {
                if (this.allowLocalhostWithoutLogin || AdminSecurity.checkLocalhostLazyAuth(
                        authorizationHeader, this.adminUser, this.adminHash)) {
                    return Decision.LOCAL_BYPASS;
                }
            }
            return Decision.ADMIN_REQUIRED;
        }

        private static String refererHost(final String referer) {
            if (referer == null || referer.isEmpty()) {
                return null;
            }
            try {
                return new MultiProtocolURL(referer).getHost();
            } catch (final MalformedURLException e) {
                return null;
            }
        }
    }

    /** Request-bound facts needed while the container verifies admin credentials. */
    public static final class AuthenticationContext {

        private static final ThreadLocal<String> SOCKET_PEER_IP = new ThreadLocal<>();

        private AuthenticationContext() {
        }

        public static void setSocketPeerIp(final String ip) {
            SOCKET_PEER_IP.set(ip);
        }

        public static void clear() {
            SOCKET_PEER_IP.remove();
        }

        public static boolean isLocalhostRequest() {
            final String ip = SOCKET_PEER_IP.get();
            return ip != null && Domains.isLocalhost(ip);
        }
    }
}
