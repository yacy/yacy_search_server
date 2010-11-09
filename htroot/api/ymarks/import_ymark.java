import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;

import org.xml.sax.SAXException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.data.YMarkTables;
import de.anomic.data.YMarksHTMLImporter;
import de.anomic.data.YMarksXBELImporter;
import de.anomic.data.userDB;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class import_ymark {
	
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final userDB.Entry user = sb.userDB.getUser(header); 
        final boolean isAdmin = (sb.verifyAuthentication(header, true));
        final boolean isAuthUser = user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT);
        Thread t;
        HashMap<String,String> bmk;
		ByteArrayInputStream byteIn = null;
        
        if(isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);        	
        	if(post.containsKey("bmkfile") && post.containsKey("importer")){
				try {
					byteIn = new ByteArrayInputStream(post.get("bmkfile$file").getBytes("UTF-8"));
				} catch (UnsupportedEncodingException e) {
					//TODO: display an error message
					Log.logException(e);
					prop.put("result", "0");
					return prop;
				}
        		if(post.get("importer").equals("html") && byteIn != null) {
					final YMarksHTMLImporter htmlImporter = new YMarksHTMLImporter(byteIn, 100);
		            t = new Thread(htmlImporter, "YMarks - HTML Importer");
		            t.start();
		            while ((bmk = htmlImporter.take()) != YMarkTables.POISON) {
						try {
							sb.tables.bookmarks.addBookmark(bmk_user, bmk, true);
						} catch (IOException e) {
							Log.logWarning(YMarkTables.BOOKMARKS_LOG.toString(), "HTML Importer - IOException for URL: "+bmk.get(YMarkTables.BOOKMARK.URL.key()));
							continue;
						} catch (RowSpaceExceededException e) {
							//TODO: display an error message
							Log.logException(e);
							prop.put("result", "0");
							return prop;
						}
		            }
            		prop.put("result", "1");        			
        		} else if(post.get("importer").equals("xbel") && byteIn != null) {
        			final YMarksXBELImporter xbelImporter;	
    				try {
						 xbelImporter = new YMarksXBELImporter(byteIn, 100);
					} catch (SAXException e) {
						//TODO: display an error message
						Log.logException(e);
						prop.put("result", "0");
						return prop;
					}
		            t = new Thread(xbelImporter, "YMarks - XBEL Importer");
		            t.start();
		            while ((bmk = xbelImporter.take()) != YMarkTables.POISON) {
						try {
							sb.tables.bookmarks.addBookmark(bmk_user, bmk, true);
						} catch (IOException e) {
							Log.logWarning(YMarkTables.BOOKMARKS_LOG.toString(), "XBEL Importer - IOException for URL: "+bmk.get(YMarkTables.BOOKMARK.URL.key()));
							continue;
						} catch (RowSpaceExceededException e) {
							//TODO: display an error message
							Log.logException(e);
							prop.put("result", "0");
							return prop;
						}
		            }
		            // update bookmarks with aliases
		            final Iterator<HashMap<String,String>> it = xbelImporter.getAliases().iterator();
		            while (it.hasNext()) {
		            	try {
							sb.tables.bookmarks.addBookmark(bmk_user, it.next(), true);
						} catch (IOException e) {
							Log.logWarning(YMarkTables.BOOKMARKS_LOG.toString(), "XBEL Importer - IOException for URL: "+bmk.get(YMarkTables.BOOKMARK.URL.key()));
							continue;
						} catch (RowSpaceExceededException e) {
							//TODO: display an error message
							Log.logException(e);
							prop.put("result", "0");
							return prop;
						}
		            }
    				prop.put("result", "1");
            	}        			
        	}
        }  else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        } 
        // return rewrite properties
        return prop;
	}
}









