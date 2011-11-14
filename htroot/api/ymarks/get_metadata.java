import java.io.IOException;
import java.net.MalformedURLException;
import java.util.EnumMap;
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.search.Switchboard;
import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkAutoTagger;
import de.anomic.data.ymark.YMarkCrawlStart;
import de.anomic.data.ymark.YMarkEntry;
import de.anomic.data.ymark.YMarkMetadata;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.data.ymark.YMarkUtil;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get_metadata {    
		
	static serverObjects prop;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		prop = new serverObjects();        
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);        
        
        if(isAdmin || isAuthUser) {

        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);        	
			

        	String url = post.get(YMarkEntry.BOOKMARK.URL.key(),YMarkEntry.BOOKMARK.URL.deflt());
        	boolean hasProtocol = false;
			for (YMarkTables.PROTOCOLS p : YMarkTables.PROTOCOLS.values()) {
				if(url.toLowerCase().startsWith(p.protocol())) {
					hasProtocol = true;
					break;
				}
			}
			if (!hasProtocol) {
			    url=YMarkTables.PROTOCOLS.HTTP.protocol(url);
			}
    	
        	try {				
				YMarkMetadata meta = new YMarkMetadata(new DigestURI(url), sb.indexSegments);
				final Document document = meta.loadDocument(sb.loader);
				final EnumMap<YMarkMetadata.METADATA, String> metadata = meta.loadMetadata();
				
				prop.putXML("title", metadata.get(YMarkMetadata.METADATA.TITLE));
				prop.putXML("desc", metadata.get(YMarkMetadata.METADATA.DESCRIPTION));				
				prop.put("keywords", putTags(document.dc_subject(','), "keywords"));
				prop.put("autotags", putTags(YMarkAutoTagger.autoTag(document, 5, sb.tables.bookmarks.getTags(bmk_user)), "autotags"));
    			
				final YMarkCrawlStart crawlStart = new YMarkCrawlStart(sb.tables, url);
    			final Iterator<String> iter = crawlStart.keySet().iterator();
    			int count = 0;
    			String key;
    			while(iter.hasNext()) {
    				key = iter.next();
    				prop.putXML("crawlstart_"+count+"_key",key.toLowerCase());
    				prop.putXML("crawlstart_"+count+"_value",crawlStart.get(key));
    				count++;
    			}
    			prop.put("crawlstart", count);

			} catch (MalformedURLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Failure e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}  
        } else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }	
        // return rewrite properties
        return prop;
	}
	
	public static int putTags(final String tagString, final String var) {
        final String list[] = tagString.split(YMarkUtil.TAGS_SEPARATOR);
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