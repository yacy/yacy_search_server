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

import java.net.MalformedURLException;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.Domains;
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
public class Jetty8YaCySecurityHandler extends ConstraintSecurityHandler {
   
    public Jetty8YaCySecurityHandler() {
        super();

        for (AccessRight right : AccessRight.values()) {
            addRole(right.toString()); // add default YaCy roles
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
        final boolean adminAccountGrantedForLocalhost = sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false);
        final boolean adminAccountNeededForAllPages = sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES, false);
        //final String adminAccountBase64MD5 = sb.getConfig(YaCyLegacyCredential.ADMIN_ACCOUNT_B64MD5, "");

        String refererHost;
        // update AccessTracker
        refererHost = request.getRemoteAddr();
        serverAccessTracker.track(refererHost, pathInContext);
        
        try {
            refererHost = new MultiProtocolURL(request.getHeader("Referer")).getHost();
        } catch (MalformedURLException e) {
            refererHost = null;
        }                          
        final boolean accessFromLocalhost = Domains.isLocalhost(request.getRemoteHost()) && (refererHost == null || refererHost.length() == 0 || Domains.isLocalhost(refererHost));
        // ! note : accessFromLocalhost compares localhost ip pattern
        final boolean grantedForLocalhost = adminAccountGrantedForLocalhost && accessFromLocalhost;
        boolean protectedPage = adminAccountNeededForAllPages || (pathInContext.indexOf("_p.") > 0);
        // check "/gsa" and "/solr" if not publicSearchpage
        if (!protectedPage && !sb.getConfigBool(SwitchboardConstants.PUBLIC_SEARCHPAGE, true)) { 
            protectedPage = pathInContext.startsWith("/solr/") || pathInContext.startsWith("/gsa/");                        
        }
        //final boolean accountEmpty = adminAccountBase64MD5.length() == 0;

        if (protectedPage) {
            if (grantedForLocalhost) {
                return null; // quick return for local admin
            }
            RoleInfo roleinfo = new RoleInfo();
            roleinfo.setChecked(true); // RoleInfo.setChecked() : in Jetty this means - marked to have any security constraint
            roleinfo.addRole(AccessRight.ADMIN_RIGHT.toString()); // use AccessRights as role
            return roleinfo; 
        }
        return (RoleInfo)super.prepareConstraintInfo(pathInContext, request);
    }
}
