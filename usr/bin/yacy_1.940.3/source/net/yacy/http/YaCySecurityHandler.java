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

import java.net.MalformedURLException;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.UserDB.AccessRight;
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
 */
public class YaCySecurityHandler extends ConstraintSecurityHandler {

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
        final boolean adminAccountGrantedForLocalhost = sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false);
        final boolean adminAccountNeededForAllPages = sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES, false);

        String refererHost;
        // update AccessTracker
        final String remoteip = RequestHeader.client(request);
        serverAccessTracker.track(remoteip, pathInContext);

        try {
            refererHost = new MultiProtocolURL(request.getHeader(RequestHeader.REFERER)).getHost();
        } catch (MalformedURLException e) {
            refererHost = null;
        }
        final boolean accessFromLocalhost = Domains.isLocalhost(remoteip) && (refererHost == null || refererHost.length() == 0 || Domains.isLocalhost(refererHost));
        // ! note : accessFromLocalhost compares localhost ip pattern
        final boolean grantedForLocalhost = adminAccountGrantedForLocalhost && accessFromLocalhost;

        // Even when all pages are protected, we don't want to block those used for peer-to-peer or cluster communication (except in private robinson mode) 
        // (examples : /yacy/hello.html is required for p2p and cluster network presence and /solr/select for remote Solr search requests)
        boolean protectedPage = (adminAccountNeededForAllPages && ((sb.isRobinsonMode() && !sb.isPublicRobinson()) ||
                !(pathInContext.startsWith("/yacy/") || pathInContext.startsWith("/solr/"))));

        // Pages suffixed with "_p" are by the way always considered protected
        protectedPage = protectedPage || (pathInContext.indexOf("_p.") > 0);

        // check "/gsa" and "/solr" if not publicSearchpage
        if (!protectedPage && !sb.getConfigBool(SwitchboardConstants.PUBLIC_SEARCHPAGE, true)) { 
            protectedPage = pathInContext.startsWith("/solr/") || pathInContext.startsWith("/gsa/");
        }

        if (protectedPage) {
            if (grantedForLocalhost) {
                return null;
            } else if (accessFromLocalhost) {
                // last chance to authorize using the admin from localhost
                final String adminAccountBase64MD5 = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
                final String credentials = request.getHeader(RequestHeader.AUTHORIZATION);
                if (credentials != null && credentials.length() < 120 && credentials.startsWith("Basic ")) { // Basic credentials are short "Basic " + b64(user:pwd)
                    final String foruser = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin");
                    final String b64 = Base64Order.standardCoder.encodeString(foruser + ":" + adminAccountBase64MD5); // TODO: is this valid? ; consider "MD5:" prefixed config
                    if ((credentials.substring(6)).equals(b64)) return null; // lazy authentication for local access with credential from config (only a user with read access to DATA can do that)
                }
            }
            RoleInfo roleinfo = new RoleInfo();
            roleinfo.setChecked(true); // RoleInfo.setChecked() : in Jetty this means - marked to have any security constraint
            roleinfo.addRole(AccessRight.ADMIN_RIGHT.toString()); // use AccessRights as role
            return roleinfo; 
        }
        return super.prepareConstraintInfo(pathInContext, request);
    }
}
