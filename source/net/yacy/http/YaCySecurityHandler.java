//
//  YaCySecurityHandler
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

import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverAccessTracker;

import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.RoleInfo;
import org.eclipse.jetty.server.Request;

/**
 * jetty security handler
 * demands authentication for pages with _p. inside
 * and updates AccessTracker
 *
 * This is a thin adapter to the servlet container: the decision logic is in
 * the container neutral {@link AdminSecurity}.
 */
public class YaCySecurityHandler extends ConstraintSecurityHandler {

    /**
     * Request-scoped hand-off of the client IP to the admin password check.
     * <p>
     * <b>What happens here and why:</b> YaCy accepts the stored admin password hash itself
     * as a password, but only for requests originating from localhost. This is used by the
     * {@code bin/*.sh} scripts (via {@code bin/apicall.sh}), which read the hash from the
     * configuration file to steer a local peer - the cleartext password is never stored
     * anywhere. That exception is evaluated in {@link YaCyDigestCredential#check(Object)},
     * which the servlet container invokes through the {@link YaCyLoginService}.
     * <p>
     * The problem: Jetty's {@link org.eclipse.jetty.util.security.Credential} API only
     * receives the submitted password, not the request, so the credential can not tell on
     * its own whether the request comes from localhost. This handler is the single place
     * that both (a) sees the request and (b) spans the whole authentication of that request:
     * {@code prepareConstraintInfo()}, the authenticator, the login service and finally
     * {@code Credential.check()} all run synchronously inside {@code super.handle()} on this
     * same thread. We therefore publish the request's true socket peer IP here for the
     * duration of the request and clear it in a {@code finally} block (so it can not leak
     * across pooled request threads). The credential check then reads a value that is bound
     * to the exact request being authenticated.
     * <p>
     * This replaced a former process-global "last localhost access" timestamp with a 100ms
     * window: that value was not tied to the request being checked and could be opened by
     * unrelated concurrent localhost traffic. The IP used here is the real socket peer
     * ({@link Request#getRemoteAddr()}); the client-controlled and spoofable X-Real-IP
     * header is never consulted for this authentication decision.
     */
    @Override
    public void handle(final String pathInContext, final Request baseRequest,
            final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException {
        YaCyDigestCredential.setRequestClientIP(baseRequest.getRemoteAddr());
        try {
            super.handle(pathInContext, baseRequest, request, response);
        } finally {
            YaCyDigestCredential.clearRequestClientIP();
        }
    }

     /**
     * create the constraint for the given path
     * for urls containing *_p. (like info_p.html) admin access is required,
     * on localhost = admin setting no constraint is set
     * @param pathInContext
     * @param request
     * @return RoleInfo with
     *     isChecked=true if any security contraint applies (compare reference implementation org.eclipse.jetty.security.ConstraintSecurityHandler)
     *     role = "admin" for resource name containint _p.
     */
    @Override
    protected RoleInfo prepareConstraintInfo(String pathInContext, Request request) {
        final Switchboard sb = Switchboard.getSwitchboard();

        // Use the true socket peer IP for the access-control decision below, never the
        // client-controlled (spoofable) X-Real-IP header that RequestHeader.client() would apply.
        final String remoteip = request.getRemoteAddr();
        serverAccessTracker.track(remoteip, pathInContext);

        final boolean protectedPage = AdminSecurity.isProtectedPath(pathInContext,
                sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES, false),
                sb.isRobinsonMode() && !sb.isPublicRobinson(),
                sb.getConfigBool(SwitchboardConstants.PUBLIC_SEARCHPAGE, true));
        if (!protectedPage) {
            return super.prepareConstraintInfo(pathInContext, request);
        }

        String refererHost;
        try {
            refererHost = new MultiProtocolURL(request.getHeader(RequestHeader.REFERER)).getHost();
        } catch (MalformedURLException e) {
            refererHost = null;
        }
        if (AdminSecurity.isLocalhostAccess(remoteip, refererHost)) {
            if (sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false)) {
                return null;
            }
            // last chance to authorize using the admin from localhost
            if (AdminSecurity.checkLocalhostLazyAuth(request.getHeader(RequestHeader.AUTHORIZATION),
                    sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"),
                    sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""))) {
                return null;
            }
        }
        RoleInfo roleinfo = new RoleInfo();
        roleinfo.setChecked(true); // RoleInfo.setChecked() : in Jetty this means - marked to have any security constraint
        roleinfo.addRole(SwitchboardConstants.ADMIN_ACCOUNT_ROLE);
        return roleinfo;
    }
}
