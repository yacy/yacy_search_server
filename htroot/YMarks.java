import java.io.IOException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class YMarks {
	public static serverObjects respond(final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);

        if(isAdmin || isAuthUser) {
        	prop.put("login", 1);
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
        	prop.putHTML("user", bmk_user.substring(0,1).toUpperCase() + bmk_user.substring(1));
            int size;
			try {
				size = sb.tables.bookmarks.getSize(bmk_user);
			} catch (final IOException e) {
				ConcurrentLog.logException(e);
				size = 0;
			}
            prop.put("size", size);
        } else {
        	prop.put("login", 0);
        }        
        return prop;
	}
}