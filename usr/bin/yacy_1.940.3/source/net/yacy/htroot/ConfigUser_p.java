// ConfigUser_p.java
// -----------------------
// (c) 2017 by reger24; https://github.com/reger24
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.htroot;

import java.util.EnumMap;
import java.util.Map;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB;
import net.yacy.data.UserDB.AccessRight;
import net.yacy.http.YaCyHttpServer;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigUser_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null && post.containsKey("cancel")) {
            prop.put(serverObjects.ACTION_LOCATION, "ConfigAccountList_p.html");
            return prop;
        }

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
            prop.put("rights_" + c + "_friendlyName", right.getFriendlyName());
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
        } else if (post.containsKey("user") && !"newuser".equals(post.get("user"))) {

            final UserDB.Entry entry = sb.userDB.getEntry(post.get("user"));
            if (entry != null) {
                //TODO: set username read-only in html
                prop.putHTML("current_user", entry.getUserName());
                prop.putHTML("username", entry.getUserName());
                prop.putHTML("firstname", entry.getFirstName());
                prop.putHTML("lastname", entry.getLastName());
                prop.putHTML("address", entry.getAddress());
                prop.put("timelimit", entry.getTimeLimit());
                prop.put("timeused", entry.getTimeUsed());
                int count = 0;
                for (final AccessRight right : rights) {
                    prop.put("rights_" + count + "_set", entry.hasRight(right) ? "1" : "0");
                    count++;
                }
                prop.put("rights", count);
            }
        } else if (post.containsKey("change")) { // edit User
            prop.put("text", "0");
            prop.put("error", "0");

            final String username = post.get("username");
            final String pw1 = post.get("password");
            final String pw2 = post.get("password2");

            if (pw1 == null || !pw1.equals(pw2)) {
                prop.put("error", "2"); //PW does not match
                return prop;
            }
            // do not allow same username as staticadmin
            if (username.equalsIgnoreCase(sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"))) {
                prop.put("error", "4");
                return prop;
            }
            final String firstName = post.get("firstname");
            final String lastName = post.get("lastname");
            final String address = post.get("address");
            final String timeLimit = post.get("timelimit");
            final String timeUsed = post.get("timeused");
            final Map<AccessRight, String> rightsSet = new EnumMap<AccessRight, String>(AccessRight.class);

            for (final AccessRight right : rights) {
                rightsSet.put(right, post.containsKey(right.toString()) && "on".equals(post.get(right.toString())) ? "true" : "false");
            }

            final UserDB.Entry entry = sb.userDB.getEntry(username);

            if (entry != null) {
                try {
                    // with prefix of encoding method (supported MD5: )
                    entry.setProperty(UserDB.Entry.MD5ENCODED_USERPWD_STRING, sb.encodeDigestAuth(username, pw1));
                    entry.setProperty(UserDB.Entry.USER_FIRSTNAME, firstName);
                    entry.setProperty(UserDB.Entry.USER_LASTNAME, lastName);
                    entry.setProperty(UserDB.Entry.USER_ADDRESS, address);
                    entry.setProperty(UserDB.Entry.TIME_LIMIT, timeLimit);
                    entry.setProperty(UserDB.Entry.TIME_USED, timeUsed);

                    for (final AccessRight right : rights) {
                        entry.setProperty(right.toString(), rightsSet.get(right));
                    }

                    //TODO: set username read-only in html
                    prop.putHTML("current_user", entry.getUserName());
                    prop.putHTML("username", entry.getUserName());
                    prop.putHTML("firstname", entry.getFirstName());
                    prop.putHTML("lastname", entry.getLastName());
                    prop.putHTML("address", entry.getAddress());
                    prop.put("timelimit", entry.getTimeLimit());
                    prop.put("timeused", entry.getTimeUsed());
                    int count = 0;
                    for (final AccessRight right : rights) {
                        prop.put("rights_" + count + "_set", entry.hasRight(right) ? "1" : "0");
                        count++;
                    }
                    prop.put("rights", count);

                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                }
                final YaCyHttpServer jhttpserver = sb.getHttpServer();
                jhttpserver.resetUser(entry.getUserName());
            } else {
                prop.put("error", "1");
            }
            prop.putHTML("text_username", username);
            prop.put("text", "2");

            prop.putHTML("username", username);
        } else if (post.containsKey("delete")) {
            sb.userDB.removeEntry(post.get("username"));
            final YaCyHttpServer jhttpserver = sb.getHttpServer();
            jhttpserver.removeUser(post.get("username"));
            prop.put(serverObjects.ACTION_LOCATION, "ConfigAccountList_p.html"); // jump back to user list
        }

        // return rewrite properties
        return prop;
    }
}
