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
