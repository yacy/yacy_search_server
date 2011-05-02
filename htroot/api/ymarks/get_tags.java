
import java.io.IOException;
import java.text.CollationKey;
import java.text.Collator;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.cora.protocol.RequestHeader;
import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkEntry;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.data.ymark.YMarkTag;
import de.anomic.data.ymark.YMarkUtil;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class get_tags {
	
	final static String TAG = "tag";
	final static String TOP = "top";
	final static String SORT = "sort";
	final static String SIZE = "size";
	final static String ALPHA = "alpha";
	
	
	private static Switchboard sb = null;
	private static serverObjects prop = null;
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        sb = (Switchboard) env;
        prop = new serverObjects();
        
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
        
        if(isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
            Integer top = Integer.MAX_VALUE;
            Boolean sortAlpha = true;
            Iterator<YMarkTag> tit = null;
            TreeSet<YMarkTag> tags = null;
            int count = 0;
            YMarkTag t;

        	if (post != null && post.containsKey(TAG) && !post.get(TAG).isEmpty()) {
    	    	final String[] tagArray = YMarkUtil.cleanTagsString(post.get(TAG)).split(YMarkUtil.TAGS_SEPARATOR);
    	    	try {
					tags = new TreeSet<YMarkTag>(sb.tables.bookmarks.getTags(sb.tables.bookmarks.getBookmarksByTag(bmk_user, tagArray)).values());
				} catch (IOException e) {
					return prop;
				}
        	} else {
        		try {
					tags = new TreeSet<YMarkTag>(sb.tables.bookmarks.getTags(bmk_user).values());
				} catch (IOException e) {
					return prop;
				}
        	}

            if (post != null && post.containsKey(TOP)) {
            	top = post.getInt(TOP, Integer.MAX_VALUE);
            }
            
            if (post != null && post.containsKey(SORT)) {
                if (SIZE.equals(post.get(SORT))) {
                	sortAlpha = false;
                }
            }
                        
            if(sortAlpha) {
                final TreeMap<CollationKey, YMarkTag> sort = new TreeMap<CollationKey, YMarkTag>();
    			final Collator collator = Collator.getInstance(); 
    			collator.setStrength(Collator.SECONDARY); 
                tit = tags.iterator();
            	while(tit.hasNext() && count < top) {
            		t = tit.next();
            		sort.put(collator.getCollationKey(t.name()), t);
            		count++;
            	}
            	tit = sort.values().iterator();
            } else {
            	tit = tags.iterator();
            }

            count = 0;
            while (tit.hasNext() && count < top) {
                t = tit.next();
                if(!t.name().equals(YMarkEntry.BOOKMARK.TAGS.deflt())) {
                    prop.putXML("tags_" + count + "_name", t.name());
                    prop.put("tags_" + count + "_count", t.size());
                    count++;
                }
            }

            prop.put("tags", count);
        }
        return prop;
	}
}
