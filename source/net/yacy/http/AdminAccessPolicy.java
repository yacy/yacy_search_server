/**
 *  AdminAccessPolicy
 *  Copyright 2026 by Michael Peter Christen
 *  First released 12.07.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.http;

import java.net.MalformedURLException;

import net.yacy.cora.document.id.MultiProtocolURL;

/** Container-neutral policy for administrator access to a request path. */
public final class AdminAccessPolicy {

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

    public AdminAccessPolicy(final boolean protectAllPages, final boolean privateRobinsonMode,
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
