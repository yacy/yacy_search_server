import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

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
                
        if(isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);        	
        	if(post.containsKey("htmlfile")){
        		ByteArrayInputStream byteIn = null;
				try {
					byteIn = new ByteArrayInputStream(post.get("htmlfile$file").getBytes("UTF-8"));
				} catch (UnsupportedEncodingException e) {
					Log.logException(e);
				}
        		if(byteIn !=null) {
					final YMarksHTMLImporter htmlImporter = new YMarksHTMLImporter(byteIn, 100);
		            Thread t = new Thread(htmlImporter, "YMarks - HTML Importer");
		            t.start();
		            HashMap<String,String> bmk;
		            while ((bmk = htmlImporter.take()) != YMarkTables.POISON) {
						try {
							sb.tables.bookmarks.addBookmark(bmk_user, bmk, true);
						} catch (IOException e) {
							Log.logWarning(YMarkTables.BOOKMARKS_LOG.toString(), "IOException for URL: "+bmk.get(YMarkTables.BOOKMARK.URL.key()));
							continue;
						} catch (RowSpaceExceededException e) {
							Log.logException(e);
						}
		            }
				}
        		prop.put("result", "1");
        	}
        	if(post.containsKey("xbelfile")){
				try {
					final ByteArrayInputStream byteIn = new ByteArrayInputStream(post.get("xbelfile$file").getBytes("UTF-8"));
					if(byteIn != null) {
						final YMarksXBELImporter xbelImporter = new YMarksXBELImporter(byteIn, 100);
			            Thread t = new Thread(xbelImporter, "YMarks - HTML Importer");
			            t.start();
			            HashMap<String,String> bmk;
			            while ((bmk = xbelImporter.take()) != YMarkTables.POISON) {
			            	try {
								sb.tables.bookmarks.addBookmark(bmk_user, bmk, true);
							} catch (RowSpaceExceededException e) {
							    Log.logException(e);
							}
			            }
					}
				} catch (UnsupportedEncodingException e) {
					Log.logException(e);
				} catch (IOException e) {
					Log.logException(e);
				}
				prop.put("result", "1");
        	}
        }  else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        } 
        // return rewrite properties
        return prop;
	}
}









