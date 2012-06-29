//
//  YaCyLoginService
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
import java.security.Principal;

import javax.security.auth.Subject;

import net.yacy.search.Switchboard;

import org.eclipse.jetty.http.security.Credential;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.server.UserIdentity;

/**
 * jetty login service, provides one admin user
 */
public class YaCyLoginService extends MappedLoginService {

	@Override
	protected UserIdentity loadUser(String username) {
		if(username.equals("admin")) {
			// TODO: implement legacy credentials
			final Switchboard sb = Switchboard.getSwitchboard();
			final String adminAccountBase64MD5 = sb.getConfig(YaCyLegacyCredential.ADMIN_ACCOUNT_B64MD5, "");
			Credential credential = YaCyLegacyCredential.getCredentialsFromConfig(adminAccountBase64MD5);
			Principal userPrincipal = new MappedLoginService.KnownUser("admin", credential); 
			Subject subject = new Subject();
			subject.getPrincipals().add(userPrincipal);
			subject.getPrivateCredentials().add(credential);
			subject.setReadOnly();
			IdentityService is = getIdentityService();
			return is.newUserIdentity(subject, userPrincipal, new String[]{"admin"});
		}
		return null;
	}
	
	@Override
	protected void loadUsers() throws IOException {
		// don't load any users into MappedLoginService on startup
		// we use loadUser for dynamic checking
	}

}
