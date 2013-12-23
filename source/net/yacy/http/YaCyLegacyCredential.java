//
//  YaCyLegacyCredentials
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

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.kelondro.util.MapTools;
import org.eclipse.jetty.util.security.Credential;



/**
 * implementation of YaCy's old admin password as jetty Credential
 */
public class YaCyLegacyCredential extends Credential {
	
	private static final long serialVersionUID = -3527894085562480001L;
	private String hash;
	
    /**
     * <p><code>public static final String <strong>ADMIN_ACCOUNT_B64MD5</strong> = "adminAccountBase64MD5"</code></p>
     * <p>Name of the setting holding the authentication hash for the static <code>admin</code>-account. It is calculated
     * by first encoding <code>username:password</code> as Base64 and hashing it using {@link MapTools#encodeMD5Hex(String)}.</p>
     */
    public static final String ADMIN_ACCOUNT_B64MD5 = "adminAccountBase64MD5";

	/**
	 * internal hash function
	 * @param clear password
	 * @return hash string
	 */
	private static String calcHash(String pw) {
		return Digest.encodeMD5Hex(Base64Order.standardCoder.encodeString("admin:" + pw));
	}

	@Override
	public boolean check(Object credentials) {
		if(credentials instanceof String) {
			final String pw = (String) credentials;
			return calcHash(pw).equals(this.hash);
		}
        throw new UnsupportedOperationException();
	}
	
	/**
	 * create Credential object from config file hash
	 * @param configHash hash as in config file
	 * @return
	 */
	public static Credential getCredentialsFromConfig(String configHash) {
		YaCyLegacyCredential c = new YaCyLegacyCredential();
		c.hash = configHash;
		return c;
	}
	
	/**
	 * create Credential object from password
	 * @param password
	 * @return
	 */
	public static Credential getCredentials(String password) {
		YaCyLegacyCredential c = new YaCyLegacyCredential();
		c.hash = calcHash(password);
		return c;
	}

}
