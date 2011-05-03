import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.content.SurrogateReader;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;

import org.xml.sax.SAXException;

import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkAutoTagger;
import de.anomic.data.ymark.YMarkEntry;
import de.anomic.data.ymark.YMarkHTMLImporter;
import de.anomic.data.ymark.YMarkJSONImporter;
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
        final int queueSize = 20;
        
        Thread t;
        YMarkEntry bmk;
        String root = YMarkEntry.FOLDERS_IMPORTED;
        ByteArrayInputStream stream = null;
		
        if(isAdmin || isAuthUser) {
        	String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);        	
        	final ArrayBlockingQueue<String> autoTaggingQueue = new ArrayBlockingQueue<String>(2*queueSize);
            boolean autotag = false;
        	boolean merge = false; 
        	boolean empty = false;
            
        	if(post.containsKey("autotag") && !post.get("autotag", "off").equals("off")) {            	                          
        		autotag = true;
        		if(post.get("autotag").equals("merge")) {                	
                	merge = true;
                }
                if(post.get("autotag").equals("empty")) {
                	empty = true;
                }
                t = new Thread(new YMarkAutoTagger(autoTaggingQueue, sb.loader, sb.tables.bookmarks, bmk_user, merge),"YMarks - autoTagger");
                t.start();
        	}
        	
        	if(isAdmin && post.containsKey("table") && post.get("table").length() > 0) {
        		bmk_user = post.get("table").substring(0, post.get("table").indexOf('_'));
        	}
            if(post.containsKey("redirect") && post.get("redirect").length() > 0) {
                prop.put("redirect_url", post.get("redirect"));
                prop.put("redirect", "1");
            }
            if(post.containsKey("root") && post.get("root").length() > 0) {
                root = post.get("root");
            }
        	if(post.containsKey("bmkfile") && post.containsKey("importer")){
        		stream = new ByteArrayInputStream(UTF8.getBytes(post.get("bmkfile$file")));
        		if(post.get("importer").equals("surro") && stream != null) {
                    SurrogateReader surrogateReader;
                    try {
                        surrogateReader = new SurrogateReader(stream, queueSize);
                    } catch (IOException e) {
                        //TODO: display an error message
                        Log.logException(e);
                        prop.put("result", "0");
                        return prop;   
                    }
                    t = new Thread(surrogateReader, "YMarks - Surrogate Reader");
                    t.start();
                    while ((bmk = new YMarkEntry(surrogateReader.take())) != YMarkEntry.POISON) {
                        putBookmark(sb.tables.bookmarks, bmk_user, bmk, autoTaggingQueue, autotag, empty);
                    }
                    prop.put("result", "1");
                } else {
                    InputStreamReader reader = null;
                    try {
                        reader = new InputStreamReader(stream,"UTF-8");
                    } catch (UnsupportedEncodingException e1) {
                        //TODO: display an error message
                        Log.logException(e1);
                        prop.put("result", "0");
                        return prop; 
                    }
                    if(post.get("importer").equals("html") && reader != null) {
                        final YMarkHTMLImporter htmlImporter = new YMarkHTMLImporter(reader, queueSize, root);
                        t = new Thread(htmlImporter, "YMarks - HTML Importer");
                        t.start();
                        while ((bmk = htmlImporter.take()) != YMarkEntry.POISON) {
                            putBookmark(sb.tables.bookmarks, bmk_user, bmk, autoTaggingQueue, autotag, empty);
                        }
                        prop.put("result", "1");                    
                    } else if(post.get("importer").equals("xbel") && reader != null) {
                        final YMarkXBELImporter xbelImporter;   
                        try {
                            //TODO: make RootFold 
                            xbelImporter = new YMarkXBELImporter(reader, queueSize, root);
                        } catch (SAXException e) {
                            //TODO: display an error message
                            Log.logException(e);
                            prop.put("result", "0");
                            return prop;
                        }
                        t = new Thread(xbelImporter, "YMarks - XBEL Importer");
                        t.start();
                        while ((bmk = xbelImporter.take()) != YMarkEntry.POISON) {
                            putBookmark(sb.tables.bookmarks, bmk_user, bmk, autoTaggingQueue, autotag, empty);
                        }
                        prop.put("result", "1");
                    } else if(post.get("importer").equals("json") && reader != null) {
                        YMarkJSONImporter jsonImporter;
                        jsonImporter = new YMarkJSONImporter(reader, queueSize, root);
                        t = new Thread(jsonImporter, "YMarks - JSON Importer");
                        t.start();
                        while ((bmk = jsonImporter.take()) != YMarkEntry.POISON) {
                        	putBookmark(sb.tables.bookmarks, bmk_user, bmk, autoTaggingQueue, autotag, empty);
                        }
                        prop.put("result", "1");
                    }
                }        		
        	}
        	if(post.containsKey("autotag") && !post.get("autotag", "off").equals("off")) {
            	try {
    				autoTaggingQueue.put(YMarkAutoTagger.POISON);
    				Log.logInfo(YMarkTables.BOOKMARKS_LOG, "Importer inserted poison pill in autoTagging queue"); 
    			} catch (InterruptedException e) {
    				Log.logException(e);
    			} 	
        	}       	
        }  else {
        	prop.put(YMarkTables.USER_AUTHENTICATE,YMarkTables.USER_AUTHENTICATE_MSG);
        }                
        // return rewrite properties
        return prop;
	}
	
	public static void putBookmark(final YMarkTables ymarks, final String bmk_user, final YMarkEntry bmk, 
			final ArrayBlockingQueue<String> autoTaggingQueue, final boolean autotag, final boolean empty) {
		try {
			String url = bmk.get(YMarkEntry.BOOKMARK.URL.key());
			// other protocols could cause problems
			if(url != null && url.startsWith("http")) {
				ymarks.addBookmark(bmk_user, bmk, true, true);				
				if(autotag) {
					if(!empty) {
						autoTaggingQueue.put(url);
					} else if(!bmk.containsKey(YMarkEntry.BOOKMARK.TAGS.key()) || bmk.get(YMarkEntry.BOOKMARK.TAGS.key()).equals(YMarkEntry.BOOKMARK.TAGS.deflt())) {
						autoTaggingQueue.put(url);
					}					
				}	
			}
		} catch (IOException e) {
			Log.logException(e);		
		} catch (RowSpaceExceededException e) {
			Log.logException(e);
		} catch (InterruptedException e) {
			Log.logException(e);
		}
	}
}









