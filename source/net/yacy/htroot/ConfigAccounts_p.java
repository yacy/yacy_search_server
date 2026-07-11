//Config_Accounts_p.java
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This File is contributed by Alexander Schier
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//You must compile this file with
//javac -classpath .:../Classes Message.java
//if the shell's current path is HTROOT

package net.yacy.htroot;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.TransactionManager;
import net.yacy.http.YaCyHttpServer;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigAccounts_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();

        /* Acquire a transaction token for the next POST form submission */
        final Switchboard sb = (Switchboard) env;
        try {
            prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, TransactionManager.getTransactionToken(header));
        } catch (IllegalArgumentException e) {
            sb.log.fine("access by unauthorized or unknown user: no transaction token delivered");
        }
        // Page protection policy
        if (post != null && post.containsKey("setAccess")) {
            TransactionManager.checkPostTransaction(header, post);
            sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES, post.getBoolean(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES));
        }

        // Unauthenticated administrator access for eligible localhost requests
        if (post != null && post.containsKey("setLocalhostAccess")) {
            TransactionManager.checkPostTransaction(header, post);
            sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, post.getBoolean(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST));
        }

        // Built-in administrator credentials
        if (post != null && post.containsKey("setAdmin")) {
            TransactionManager.checkPostTransaction(header, post);
            final String user = post.get("adminuser", "");
            final String pw1  = post.get("adminpw1", "");
            final String pw2  = post.get("adminpw2", "");
            int inputerror = 0;
            if (user.isEmpty()) {
                inputerror = 3;
            } else if (pw1.isEmpty()) {
                inputerror = 4;
            } else if (!pw1.equals(pw2)) {
                inputerror = 2;
            } else {
                final String oldusername = env.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, user);
                env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, sb.encodeDigestAuth(user, pw1));
                env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, user);
                // make sure server accepts new credentials
                final YaCyHttpServer jhttpserver = sb.getHttpServer();
                if (!user.equals(oldusername)) jhttpserver.removeUser(oldusername);
                jhttpserver.resetUser(user);
            }
            prop.put("error", inputerror);
        }

        // set a warning in case that the default password was not changed
        final String currpw = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
        final String dfltpw = SwitchboardConstants.ADMIN_ACCOUNT_B64MD5_DEFAULT;
        prop.put("changedfltpw", currpw.equals(dfltpw) ? "1" : "0");

        prop.put(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES + ".checked", sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES, false) ? 1 : 0);
        prop.put(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST + ".checked", sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false) ? 1 : 0);
        prop.putHTML("defaultUser", env.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"));

        // return rewrite properties
        return prop;
    }
}
