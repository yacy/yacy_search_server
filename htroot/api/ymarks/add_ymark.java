import java.io.IOException;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB;
import net.yacy.data.ymark.YMarkEntry;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.data.ymark.YMarkUtil;
import net.yacy.document.Parser.Failure;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class add_ymark {

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);

        if (isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);

            if (post.containsKey("redirect") && post.get("redirect").length() > 0) {
                prop.put("redirect_url", post.get("redirect"));
                prop.put("redirect", "1");
            }

            if (post.containsKey("urlHash")) {
            	final String urlHash = post.get("urlHash",YMarkUtil.EMPTY_STRING);
            	final DigestURL url = sb.index.fulltext().getURL(urlHash);
            	final String folders = post.get(YMarkEntry.BOOKMARK.FOLDERS.key(),YMarkEntry.BOOKMARK.FOLDERS.deflt());
            	final String tags = post.get(YMarkEntry.BOOKMARK.TAGS.key(),YMarkUtil.EMPTY_STRING);
            	try {
                    ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
					sb.tables.bookmarks.createBookmark(sb.loader, url, agent, bmk_user, true, tags, folders);
					prop.put("status", "1");
				} catch (final IOException e) {
					// TODO Auto-generated catch block
					ConcurrentLog.logException(e);
				} catch (final Failure e) {
					// TODO Auto-generated catch block
					ConcurrentLog.logException(e);
				}

            } else if (post.containsKey(YMarkEntry.BOOKMARK.URL.key())) {
	        	String url = post.get(YMarkEntry.BOOKMARK.URL.key(),YMarkEntry.BOOKMARK.URL.deflt());
				boolean hasProtocol = false;
				for (final YMarkTables.PROTOCOLS p : YMarkTables.PROTOCOLS.values()) {
					if(url.toLowerCase().startsWith(p.protocol())) {
						hasProtocol = true;
						break;
					}
				}
				if (!hasProtocol) {
				    url=YMarkTables.PROTOCOLS.HTTP.protocol(url);
				}

	        	final YMarkEntry bmk = new YMarkEntry();

	        	bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);
	        	bmk.put(YMarkEntry.BOOKMARK.TITLE.key(), post.get(YMarkEntry.BOOKMARK.TITLE.key(),YMarkEntry.BOOKMARK.TITLE.deflt()));
	        	bmk.put(YMarkEntry.BOOKMARK.DESC.key(), post.get(YMarkEntry.BOOKMARK.DESC.key(),YMarkEntry.BOOKMARK.DESC.deflt()));
	        	bmk.put(YMarkEntry.BOOKMARK.PUBLIC.key(), post.get(YMarkEntry.BOOKMARK.PUBLIC.key(),YMarkEntry.BOOKMARK.PUBLIC.deflt()));
	        	bmk.put(YMarkEntry.BOOKMARK.TAGS.key(), YMarkUtil.cleanTagsString(post.get(YMarkEntry.BOOKMARK.TAGS.key(),YMarkEntry.BOOKMARK.TAGS.deflt()),YMarkEntry.BOOKMARK.TAGS.deflt()));
	        	bmk.put(YMarkEntry.BOOKMARK.FOLDERS.key(), YMarkUtil.cleanFoldersString(post.get(YMarkEntry.BOOKMARK.FOLDERS.key(),YMarkEntry.BOOKMARK.FOLDERS.deflt()),YMarkEntry.BOOKMARK.FOLDERS.deflt()));

	            try {
					sb.tables.bookmarks.addBookmark(bmk_user, bmk, false, false);
					} catch (final IOException e) {
					    ConcurrentLog.logException(e);
					}
	            prop.put("status", "1");
            } else {
            	prop.put("status", "0");
            }
        } else {
        	prop.put(serverObjects.ACTION_AUTHENTICATE, YMarkTables.USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
    }
}
