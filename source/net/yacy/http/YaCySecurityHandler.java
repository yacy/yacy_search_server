//
//  YaCySecurityHandler
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

import java.io.IOException;
import java.net.MalformedURLException;
import net.yacy.cora.document.id.DigestURL;

import net.yacy.cora.protocol.Domains;
import net.yacy.search.Switchboard;
import org.eclipse.jetty.security.RoleInfo;

import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.UserIdentity;

/**
 * jetty security handler
 * demands authentication for pages with _p. inside
 */
public class YaCySecurityHandler extends SecurityHandler {

    @Override
    protected boolean checkUserDataPermissions(String pathInContext, Request request,
            Response response, RoleInfo constraintInfo) throws IOException {
        // check the SecurityHandler code, denying here does not provide authentication
        return constraintInfo.isChecked();
    }

    @Override
    protected boolean checkWebResourcePermissions(String pathInContext, Request request,
            Response response, Object constraintInfo, UserIdentity userIdentity) throws IOException {
        // deny and request for authentication, if necessary
        boolean auth = ((RoleInfo) constraintInfo).isChecked();
        return auth || request.isUserInRole("admin");
    }

    @Override
    protected boolean isAuthMandatory(Request base_request, Response base_response, Object constraintInfo) {
        return false;
    }

    @Override
    protected RoleInfo prepareConstraintInfo(String pathInContext, Request request) {
        final Switchboard sb = Switchboard.getSwitchboard();
        final boolean adminAccountForLocalhost = sb.getConfigBool("adminAccountForLocalhost", false);
        final String adminAccountBase64MD5 = sb.getConfig(YaCyLegacyCredential.ADMIN_ACCOUNT_B64MD5, "");

        String refererHost;
        try {
            refererHost = new DigestURL(request.getHeader("Referer")).getHost();
        } catch (MalformedURLException e) {
            refererHost = null;
        }
        final boolean accessFromLocalhost = Domains.isLocalhost(request.getRemoteHost()) && (refererHost == null || refererHost.length() == 0 || Domains.isLocalhost(refererHost));
        final boolean grantedForLocalhost = adminAccountForLocalhost && accessFromLocalhost;
        final boolean protectedPage = pathInContext.indexOf("_p.") > 0;
        final boolean accountEmpty = adminAccountBase64MD5.length() == 0;
        final boolean yacyBot = request.getHeader("User-Agent").startsWith("yacybot");

        RoleInfo roleinfo = new RoleInfo();
        if (protectedPage) { // TODO: handle admin authentication & none public site
            roleinfo.setChecked(((grantedForLocalhost && !accountEmpty) || yacyBot));
        } else {
            roleinfo.setChecked(true); 
        }
        return roleinfo;
    }
}
