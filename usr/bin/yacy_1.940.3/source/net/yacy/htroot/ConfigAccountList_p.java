// ConfigAccountList_p.java
// -------------------------
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

import java.util.Iterator;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.UserDB;
import net.yacy.data.UserDB.AccessRight;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigAccountList_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        UserDB.Entry entry;

        if (sb.userDB == null) {
            prop.put("userlist", 0);
            return prop;
        }

        //Generate Userlist
        final Iterator<UserDB.Entry> it = sb.userDB.iterator(true);
        int numUsers = 0;
        while (it.hasNext()) {
            entry = it.next();
            if (entry == null) {
                continue;
            }
            prop.putHTML("userlist_" + numUsers + "_username", entry.getUserName());
            prop.putHTML("userlist_" + numUsers + "_lastname", entry.getLastName());
            prop.putHTML("userlist_" + numUsers + "_firstname", entry.getFirstName());
            prop.putHTML("userlist_" + numUsers + "_address", entry.getAddress());
            if (entry.getLastAccess() != null) {
				prop.put("userlist_" + numUsers + "_lastaccess",
						GenericFormatter.formatSafely(entry.getLastAccess(), GenericFormatter.FORMAT_SIMPLE));
            } else {
                prop.put("userlist_" + numUsers + "_lastaccess", "never");
            }

            final AccessRight[] rights = AccessRight.values();
            String rightStr = "";
            for (final AccessRight right : rights) {
                if (entry.hasRight(right)) {
                    if (rightStr.isEmpty()) {
                        rightStr = right.getFriendlyName();
                    } else {
                        rightStr += ", " + right.getFriendlyName();
                    }
                }
            }
            prop.putHTML("userlist_" + numUsers + "_rights", rightStr);

            long percent;
            if (entry.getTrafficLimit() != null) {
                final long limit = entry.getTrafficLimit();
                final long used = entry.getTrafficSize();
                percent = used * 100 / limit;
                prop.put("userlist_" + numUsers + "_time", percent);
            } else {
                prop.put("userlist_" + numUsers + "_time", "");
            }

            percent = -1;
            if (entry.getTimeLimit() > 0) {
                final long limit = entry.getTimeLimit();
                final long used = entry.getTimeUsed();
                percent = used * 100 / limit;
                prop.put("userlist_" + numUsers + "_traffic", percent);
            } else {
                prop.put("userlist_" + numUsers + "_traffic", "");
            }


            numUsers++;
        }
        prop.put("userlist", numUsers);

        // return rewrite properties
        return prop;
    }
}
