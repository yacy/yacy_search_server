import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;

import org.xml.sax.SAXException;

import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkHTMLImporter;
import de.anomic.data.ymark.YMarkJSONImporter;
import de.anomic.data.ymark.YMarkMetadata;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.data.ymark.YMarkXBELImporter;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class import_ymark {
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
        Thread t;
        HashMap<String,String> bmk;
		ByteArrayInputStream byteIn = null;
        
        if(isAdmin || isAuthUser) {
        	String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);        	
        	if(isAdmin && post.containsKey("table") && post.get("table").length() > 0) {
        		bmk_user = post.get("table").substring(0, post.get("table").indexOf('_'));
        	}
        	
        	if(post.containsKey("bmkfile") && post.containsKey("importer")){
        		byteIn = new ByteArrayInputStream(UTF8.getBytes(post.get("bmkfile$file")));
        		if(post.get("importer").equals("html") && byteIn != null) {
					final YMarkHTMLImporter htmlImporter = new YMarkHTMLImporter(byteIn, 10);
		            t = new Thread(htmlImporter, "YMarks - HTML Importer");
		            t.start();
		            while ((bmk = htmlImporter.take()) != YMarkTables.POISON) {
						putBookmark(sb, bmk_user, bmk);
		            }
            		prop.put("result", "1");        			
        		} else if(post.get("importer").equals("xbel") && byteIn != null) {
        			final YMarkXBELImporter xbelImporter;	
    				try {
						//TODO: make RootFold 
    					xbelImporter = new YMarkXBELImporter(byteIn, 100, YMarkTables.FOLDERS_IMPORTED);
					} catch (SAXException e) {
						//TODO: display an error message
						Log.logException(e);
						prop.put("result", "0");
						return prop;
					}
		            t = new Thread(xbelImporter, "YMarks - XBEL Importer");
		            t.start();
		            while ((bmk = xbelImporter.take()) != YMarkTables.POISON) {
						putBookmark(sb, bmk_user, bmk);
		            }
    				prop.put("result", "1");
            	} else if(post.get("importer").equals("json") && byteIn != null) {
            		YMarkJSONImporter jsonImporter;
                    try {
                        jsonImporter = new YMarkJSONImporter(byteIn, 10);
                    } catch (UnsupportedEncodingException e) {
                        prop.put("result", "1");
                        return prop;
                    }
		            t = new Thread(jsonImporter, "YMarks - JSON Importer");
		            t.start();
		            while ((bmk = jsonImporter.take()) != YMarkTables.POISON) {
						putBookmark(sb, bmk_user, bmk);
		            }
		            prop.put("result", "1");
            	}
        	}
        }  else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }
		if(post.containsKey("redirect") && post.get("redirect").length() > 0) {
			prop.put("redirect_url", post.get("redirect"));
			prop.put("redirect", "1");
		}
        // return rewrite properties
        return prop;
	}
	
	public static void putBookmark(final Switchboard sb, final String bmk_user, final HashMap<String, String> bmk) {
		try {
			if(!bmk.containsKey(YMarkTables.BOOKMARK.TAGS.key()) || bmk.get(YMarkTables.BOOKMARK.TAGS.key()).equals(YMarkTables.BOOKMARK.TAGS.deflt())) {
	            final YMarkMetadata meta = new YMarkMetadata(new DigestURI(bmk.get(YMarkTables.BOOKMARK.URL.key())));
	            meta.loadDocument(sb.loader);
				bmk.put(YMarkTables.BOOKMARK.TAGS.key(), meta.autoTag(3));
			}
			sb.tables.bookmarks.addBookmark(bmk_user, bmk, true);
		} catch (IOException e) {
			Log.logWarning(YMarkTables.BOOKMARKS_LOG.toString(), "Importer - IOException for URL: "+bmk.get(YMarkTables.BOOKMARK.URL.key()));
		} catch (RowSpaceExceededException e) {
			Log.logException(e);
		} catch (Failure e) {
			Log.logException(e);
		}
	}
}









