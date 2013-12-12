import java.io.IOException;
import java.net.MalformedURLException;
import java.util.EnumMap;
import java.util.Iterator;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB;
import net.yacy.data.ymark.YMarkAutoTagger;
import net.yacy.data.ymark.YMarkCrawlStart;
import net.yacy.data.ymark.YMarkEntry;
import net.yacy.data.ymark.YMarkMetadata;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.data.ymark.YMarkUtil;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class get_metadata {

	static serverObjects prop;

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		prop = new serverObjects();
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);

        if(isAdmin || isAuthUser) {

        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);

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

        	try {
				final YMarkMetadata meta = new YMarkMetadata(new DigestURL(url), sb.index);
                ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
				final Document document = meta.loadDocument(sb.loader, agent);
				final EnumMap<YMarkMetadata.METADATA, String> metadata = meta.loadMetadata();

				prop.putXML("title", metadata.get(YMarkMetadata.METADATA.TITLE));
				prop.putXML("desc", metadata.get(YMarkMetadata.METADATA.DESCRIPTION));
				prop.put("keywords", putTags(document.dc_subject(','), "keywords"));
				prop.put("autotags", putTags(YMarkAutoTagger.autoTag(document, 5, sb.tables.bookmarks.getTags(bmk_user)), "autotags"));

				final YMarkCrawlStart crawlStart = new YMarkCrawlStart(sb.tables, url);
    			int count = 0;
				if(!crawlStart.isEmpty()) {
					final Iterator<String> iter = crawlStart.keySet().iterator();
	    			String key;
	    			while(iter.hasNext()) {
	    				key = iter.next();
	    				prop.putXML("crawlstart_"+count+"_key",key.toLowerCase());
	    				prop.putXML("crawlstart_"+count+"_value",crawlStart.get(key));
	    				count++;
	    			}
    			}
    			prop.put("crawlstart", count);

			} catch (final MalformedURLException e) {
				// TODO Auto-generated catch block
				ConcurrentLog.logException(e);
				prop.put("status", "error");
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				ConcurrentLog.logException(e);
				prop.put("status", "error");
			} catch (final Failure e) {
				// TODO Auto-generated catch block
				ConcurrentLog.logException(e);
				prop.put("status", "error");
			}
        } else {
        	prop.put(serverObjects.ACTION_AUTHENTICATE, YMarkTables.USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
	}

	public static int putTags(final String tagString, final String var) {
        final String list[] = YMarkUtil.TAGS_SEPARATOR_PATTERN.split(tagString, 0);
        int count = 0;
        for (final String element : list) {
            final String tag = element;
            if (!tag.equals("")) {
                prop.putXML(var+"_"+count+"_tag", tag);
                count++;
            }
        }
       return count;
	}
}