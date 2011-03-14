package net.yacy.http;

import java.io.IOException;
import java.security.Principal;

import javax.security.auth.Subject;

import org.eclipse.jetty.http.security.Credential;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.server.UserIdentity;

public class YaCyLoginService extends MappedLoginService {

	@Override
	protected UserIdentity loadUser(String username) {
		if(username.equals("admin")) {
			Credential credential = Credential.getCredential("admin");
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
		// don't load any users into MappedLoginService on boot
	}

}
