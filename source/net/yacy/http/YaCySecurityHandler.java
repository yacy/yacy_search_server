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
			Response response, Object constraintInfo) throws IOException {
		// check the SecurityHandler code, denying here does not provide authentication
		return true;
	}

	@Override
	protected boolean checkWebResourcePermissions(String pathInContext, Request request,
			Response response, Object constraintInfo, UserIdentity userIdentity) throws IOException {
		// deny and request for authentication, if necessary
		Boolean authMand = (Boolean)constraintInfo;
		return !authMand || request.isUserInRole("admin");
	}

	@Override
	protected boolean isAuthMandatory(Request base_request, Response base_response, Object constraintInfo) {
		Boolean authMand = (Boolean)constraintInfo;
		return authMand;
	}

	@Override
	protected Object prepareConstraintInfo(String pathInContext, Request request) {
		// authentication mandatory as simple constraint info
		return pathInContext.contains("_p.");
	}

}
