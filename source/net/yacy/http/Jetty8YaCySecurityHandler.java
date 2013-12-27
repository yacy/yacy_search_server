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

import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.Domains;
import net.yacy.data.UserDB.AccessRight;
import net.yacy.search.Switchboard;

import org.eclipse.jetty.http.HttpSchemes;
import org.eclipse.jetty.security.RoleInfo;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserDataConstraint;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.UserIdentity;

/**
 * jetty security handler
 * demands authentication for pages with _p. inside
 * and updates AccessTracker
 */
public class Jetty8YaCySecurityHandler extends SecurityHandler {

    @Override
    protected boolean checkUserDataPermissions(String pathInContext, Request request, Response response, Object constraintInfo) throws IOException   
     // check the SecurityHandler code, denying here does not provide authentication
     // - identical with ConstraintSecurityHandler.checkUserDataPermissions implementation of Jetty source distribution
    { 
        if (constraintInfo == null)
            return true;

        RoleInfo roleInfo = (RoleInfo)constraintInfo;
        if (roleInfo.isForbidden())
            return false;


        UserDataConstraint dataConstraint = roleInfo.getUserDataConstraint();
        if (dataConstraint == null || dataConstraint == UserDataConstraint.None)
        {
            return true;
        }
        AbstractHttpConnection connection = AbstractHttpConnection.getCurrentConnection();
        Connector connector = connection.getConnector();

        if (dataConstraint == UserDataConstraint.Integral)
        {
            if (connector.isIntegral(request))
                return true;
            if (connector.getIntegralPort() > 0)
            {
                String scheme=connector.getIntegralScheme();
                int port=connector.getIntegralPort();
                String url = (HttpSchemes.HTTPS.equalsIgnoreCase(scheme) && port==443)
                    ? "https://"+request.getServerName()+request.getRequestURI()
                    : scheme + "://" + request.getServerName() + ":" + port + request.getRequestURI();
                if (request.getQueryString() != null)
                    url += "?" + request.getQueryString();
                response.setContentLength(0);
                response.sendRedirect(url);
            }
            else
                response.sendError(HttpServletResponse.SC_FORBIDDEN,"!Integral");

            request.setHandled(true);
            return false;
        }
        else if (dataConstraint == UserDataConstraint.Confidential)
        {
            if (connector.isConfidential(request))
                return true;

            if (connector.getConfidentialPort() > 0)
            {
                String scheme=connector.getConfidentialScheme();
                int port=connector.getConfidentialPort();
                String url = (HttpSchemes.HTTPS.equalsIgnoreCase(scheme) && port==443)
                    ? "https://"+request.getServerName()+request.getRequestURI()
                    : scheme + "://" + request.getServerName() + ":" + port + request.getRequestURI();                    
                if (request.getQueryString() != null)
                    url += "?" + request.getQueryString();
                response.setContentLength(0);
                response.sendRedirect(url);
            }
            else
                response.sendError(HttpServletResponse.SC_FORBIDDEN,"!Confidential");

            request.setHandled(true);
            return false;
        }
        else
        {
            throw new IllegalArgumentException("Invalid dataConstraint value: " + dataConstraint);
        }
    }        

    @Override
    protected boolean checkWebResourcePermissions(String pathInContext, Request request,
            Response response, Object constraintInfo, UserIdentity userIdentity) throws IOException {
        // deny and request for authentication, if necessary
        // - identical with ConstraintSecurityHandler.checkWebResourcePermissions implementation of Jetty source distribution    
        if (constraintInfo == null) {
            return true;
        }
        RoleInfo roleInfo = (RoleInfo) constraintInfo;

        if (!roleInfo.isChecked()) {
            return true;
        }

        if (roleInfo.isAnyRole() && request.getAuthType() != null) {
            return true;
        }

        for (String role : roleInfo.getRoles()) {
            if (userIdentity.isUserInRole(role, null)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isAuthMandatory(Request baseRequest, Response base_response, Object constraintInfo) {
        // identical with ConstraintSecurityHandler.isAuthMandatory implementation of Jetty source distribution
        return constraintInfo != null && ((RoleInfo) constraintInfo).isChecked();
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
        final boolean adminAccountForLocalhost = sb.getConfigBool("adminAccountForLocalhost", false);
        //final String adminAccountBase64MD5 = sb.getConfig(YaCyLegacyCredential.ADMIN_ACCOUNT_B64MD5, "");

        String refererHost;
        // update AccessTracker
        refererHost = request.getRemoteAddr();
        sb.track(refererHost, pathInContext);
        
        try {
            refererHost = new MultiProtocolURL(request.getHeader("Referer")).getHost();
        } catch (MalformedURLException e) {
            refererHost = null;
        }                          
        final boolean accessFromLocalhost = Domains.isLocalhost(request.getRemoteHost()) && (refererHost == null || refererHost.length() == 0 || Domains.isLocalhost(refererHost));
        // ! note : accessFromLocalhost compares localhost ip pattern ( ! currently also any intranet host is a local host)
        final boolean grantedForLocalhost = adminAccountForLocalhost && accessFromLocalhost;
        boolean protectedPage = (pathInContext.indexOf("_p.") > 0);
        // check "/gsa" and "/solr" if not publicSearchpage
        if (!protectedPage && !sb.getConfigBool("publicSearchpage", true)) { 
            protectedPage = pathInContext.startsWith("/solr/") || pathInContext.startsWith("/gsa/");                        
        }
        //final boolean accountEmpty = adminAccountBase64MD5.length() == 0;
     
        if (protectedPage) { // TODO: none public site
            if (!grantedForLocalhost) {
                RoleInfo roleinfo = new RoleInfo();
                roleinfo.setChecked(true); // RoleInfo.setChecked() : in Jetty this means - marked to have any security constraint
                roleinfo.addRole(AccessRight.ADMIN_RIGHT.toString()); // use AccessRights as role
                return roleinfo;
            } // can omit else, as if grantedForLocalhost==true no constraint applies
              // TODO: is this correct or adminAccountBase64MD5 not empty check neccessary ?
        }
        return null;
    }

}
