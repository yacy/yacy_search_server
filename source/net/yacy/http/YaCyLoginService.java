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
import net.yacy.data.UserDB.AccessRight;
import net.yacy.data.UserDB.Entry;

import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.security.Credential;

/**
 * jetty login service, provides one admin user
 */
public class YaCyLoginService extends MappedLoginService {

    @Override
    protected UserIdentity loadUser(String username) {

        // TODO: implement legacy credentials
        final Switchboard sb = Switchboard.getSwitchboard();
        String adminuser = sb.getConfig("adminAccount", "admin");
        if (username.equals(adminuser)) {
            final String adminAccountBase64MD5 = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
            // in YaCy the credential hash is composed of username:pwd so the username is needed to create valid credential 
            // not just the password (as usually in Jetty). As the accountname for the std. adminuser is not stored a useridentity 
            // is created for current user (and the pwd checked against the stored  username:pwd setting)                        
            Credential credential = YaCyLegacyCredential.getCredentialsFromConfig(username, adminAccountBase64MD5);
            // TODO: YaCy user:pwd hashes should longterm likely be switched to separable username + pwd-hash entries
            //       and/or the standard admin account username shuld be fix = "admin"

            Principal userPrincipal = new MappedLoginService.KnownUser(username, credential);
            Subject subject = new Subject();
            subject.getPrincipals().add(userPrincipal);
            subject.getPrivateCredentials().add(credential);
            subject.setReadOnly();
            IdentityService is = getIdentityService();
            return is.newUserIdentity(subject, userPrincipal, new String[]{AccessRight.ADMIN_RIGHT.toString()});
        } else { // get user data from UserDB            
            Entry user = sb.userDB.getEntry(username);
            if (user != null) {
                String[] role;
                if (user.hasRight(AccessRight.ADMIN_RIGHT)) {
                    role = new String[]{AccessRight.ADMIN_RIGHT.toString()};

                    Credential credential = YaCyLegacyCredential.getCredentials(username, user.getMD5EncodedUserPwd());
                    Principal userPrincipal = new MappedLoginService.KnownUser(username, credential);
                    Subject subject = new Subject();
                    subject.getPrincipals().add(userPrincipal);
                    subject.getPrivateCredentials().add(credential);
                    subject.setReadOnly();
                    IdentityService is = getIdentityService();

                    return is.newUserIdentity(subject, userPrincipal, role);
                } 
            }
        }
        return null;
    }
	
	@Override
	protected void loadUsers() throws IOException {
		// don't load any users into MappedLoginService on startup
		// we use loadUser for dynamic checking
	}

}
