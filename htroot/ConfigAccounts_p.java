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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB;
import net.yacy.data.UserDB.AccessRight;
import net.yacy.http.Jetty8HttpServerImpl;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigAccounts_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = Switchboard.getSwitchboard();
        UserDB.Entry entry = null;

        // admin password
        boolean localhostAccess = sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false);

        if (post != null && post.containsKey("setAccess")) {
            sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES, post.getBoolean(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES));
        }
        
        if (post != null && post.containsKey("setAdmin")) {
            localhostAccess = post.get("access", "").equals("localhost");
            final String user = post.get("adminuser", "");
            final String pw1  = post.get("adminpw1", "");
            final String pw2  = post.get("adminpw2", "");
            int inputerror=0;
            // may be overwritten if new password is given
            if (user.length() > 0 && pw1.length() > 3 && pw1.equals(pw2)) {
                String oldusername = env.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME,user);
                // check passed. set account:
                // old: // env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, Digest.encodeMD5Hex(Base64Order.standardCoder.encodeString(user + ":" + pw1)));
                env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "MD5:"+Digest.encodeMD5Hex(user + ":" + sb.getConfig(SwitchboardConstants.ADMIN_REALM,"YaCy")+":"+ pw1));
                env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT, "");
                env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME,user);
                // make sure server accepts new credentials
                Jetty8HttpServerImpl jhttpserver = (Jetty8HttpServerImpl)sb.getHttpServer();
                if (!user.equals(oldusername)) jhttpserver.removeUser(oldusername);
                jhttpserver.resetUser(user);
            } else {
                if (!localhostAccess) {
                    if (user.isEmpty()) {
                        inputerror = 3;
                    } else {
                        inputerror = 2; // password match error
                    }
                    prop.put("error", inputerror);
                } 
            }

            if (inputerror == 0) {
                if (localhostAccess) {

                    sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, true);
                    // if an localhost access is configured, check if a local password is given
                    // if not, set a random password
                    if (env.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").isEmpty()) {
                        // make a 'random' password
                        env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "0000" + sb.genRandomPassword());
                        env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT, "");
                    }
                } else {
                    sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false);
                    if (env.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").startsWith("0000")) {
                        // make shure that the user can still use the interface after a random password was set
                        env.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
                    }
                }
            } 
        }

        if (env.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").isEmpty() && !env.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_FOR_LOCALHOST, false)) {
            prop.put("passwordNotSetWarning", 1);
        }

        prop.put(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES + ".checked", sb.getConfigBool(SwitchboardConstants.ADMIN_ACCOUNT_All_PAGES, false) ? 1 : 0);
        prop.put("localhost.checked", (localhostAccess) ? 1 : 0);
        prop.put("account.checked", (localhostAccess) ? 0 : 1);
        prop.put("statusPassword", localhostAccess ? "0" : "1");
        prop.put("defaultUser", env.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"));

        //default values
        prop.put("current_user", "newuser");
        prop.put("username", "");
        prop.put("firstname", "");
        prop.put("lastname", "");
        prop.put("address", "");
        prop.put("timelimit", "");
        prop.put("timeused", "");

        final AccessRight[] rights = AccessRight.values();
        int c = 0;
        for (final AccessRight right : rights) {
            prop.put("rights_" + c + "_name", right.toString());
            prop.put("rights_" + c +"_friendlyName", right.getFriendlyName());
            prop.put("rights_" + c + "_set", "0");
            c++;
        }
        prop.put("rights", c);

        prop.put("users", "0");

        if (sb.userDB == null) {
            return prop;
        }

        if (post == null) {
            //do nothing

            //user != current_user
            //user=from userlist
            //current_user = edited user
        } else if (post.containsKey("user") && !"newuser".equals(post.get("user"))){
            if (post.containsKey("change_user")) {
                //defaults for newuser are set above
                entry = sb.userDB.getEntry(post.get("user"));
                // program crashes if a submit with empty username was made on previous mask and the user clicked on the
                // link: "If you want to manage more Users, return to the user page." (parameter "user" is empty)
                if (entry != null) {
                    //TODO: set username read-only in html
                    prop.putHTML("current_user", post.get("user"));
                    prop.putHTML("username", post.get("user"));
                    prop.putHTML("firstname", entry.getFirstName());
                    prop.putHTML("lastname", entry.getLastName());
                    prop.putHTML("address", entry.getAddress());
                    prop.put("timelimit", entry.getTimeLimit());
                    prop.put("timeused", entry.getTimeUsed());
                    int count = 0;
                    for (final AccessRight right : rights){
                        prop.put("rights_" + count + "_set", entry.hasRight(right) ? "1" : "0");
                        count++;
                    }
                    prop.put("rights", count);
                }
            } else if (post.containsKey("delete_user") && !"newuser".equals(post.get("user"))){
                sb.userDB.removeEntry(post.get("user"));
            }
        } else if (post.containsKey("change")) { //New User / edit User
            prop.put("text", "0");
            prop.put("error", "0");

            final String username = post.get("username");
            final String pw1 = post.get("password");
            final String pw2 = post.get("password2");

            if (pw1 == null || !pw1.equals(pw2)) {
                prop.put("error", "2"); //PW does not match
                return prop;
            }

            final String firstName = post.get("firstname");
            final String lastName = post.get("lastname");
            final String address = post.get("address");
            final String timeLimit = post.get("timelimit");
            final String timeUsed = post.get("timeused");
            final Map<AccessRight, String> rightsSet = new EnumMap<AccessRight, String>(AccessRight.class);

            for(final AccessRight right : rights) {
                rightsSet.put(right, post.containsKey(right.toString()) && "on".equals(post.get(right.toString())) ? "true" : "false");
            }

            final Map<String, String> mem = new HashMap<String, String>();
            if( "newuser".equals(post.get("current_user"))){ //new user

                if (!"".equals(pw1)) { //change only if set
                    // MD5 according to HTTP Digest RFC 2617 (3.2.2) name:realm:pwd (use seed.hash as realm)
                    // with prefix of encoding method (supported MD5: )
                    mem.put(UserDB.Entry.MD5ENCODED_USERPWD_STRING, "MD5:"+Digest.encodeMD5Hex(username + ":" + sb.getConfig(SwitchboardConstants.ADMIN_REALM,"YaCy")+":"+pw1));
                }

                mem.put(UserDB.Entry.USER_FIRSTNAME, firstName);
                mem.put(UserDB.Entry.USER_LASTNAME, lastName);
                mem.put(UserDB.Entry.USER_ADDRESS, address);
                mem.put(UserDB.Entry.TIME_LIMIT, timeLimit);
                mem.put(UserDB.Entry.TIME_USED, timeUsed);

                for (final AccessRight right : rights) {
                    mem.put(right.toString(), rightsSet.get(right));
                }

                try {
                    entry = sb.userDB.createEntry(username, mem);
                    sb.userDB.addEntry(entry);
                    prop.putHTML("text_username", username);
                    prop.put("text", "1");
                } catch (final IllegalArgumentException e) {
                    prop.put("error", "3");
                }

            } else { //edit user

                entry = sb.userDB.getEntry(username);

                if (entry != null) {
                    try{
                        if (!"".equals(pw1)) {
                            // with prefix of encoding method (supported MD5: )
                            entry.setProperty(UserDB.Entry.MD5ENCODED_USERPWD_STRING, "MD5:"+Digest.encodeMD5Hex(username+ ":" + sb.getConfig(SwitchboardConstants.ADMIN_REALM,"YaCy") + ":"+pw1));
                        }

                        entry.setProperty(UserDB.Entry.USER_FIRSTNAME, firstName);
                        entry.setProperty(UserDB.Entry.USER_LASTNAME, lastName);
                        entry.setProperty(UserDB.Entry.USER_ADDRESS, address);
                        entry.setProperty(UserDB.Entry.TIME_LIMIT, timeLimit);
                        entry.setProperty(UserDB.Entry.TIME_USED, timeUsed);

                        for(final AccessRight right : rights) {
                            entry.setProperty(right.toString(), rightsSet.get(right));
                        }

                    } catch (final Exception e) {
                        ConcurrentLog.logException(e);
                    }

                } else {
                    prop.put("error", "1");
                }
                prop.putHTML("text_username", username);
                prop.put("text", "2");
            }//edit user
            prop.putHTML("username", username);
        }

        //Generate Userlist
        final Iterator<UserDB.Entry> it = sb.userDB.iterator(true);
        int numUsers=0;
        while (it.hasNext()) {
            entry = it.next();
            if (entry == null) {
                continue;
            }
            prop.putHTML("users_"+numUsers+"_user", entry.getUserName());
            numUsers++;
        }
        prop.put("users", numUsers);

        // return rewrite properties
        return prop;
    }
}
