import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.Parser.Failure;
import net.yacy.document.content.SurrogateReader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;

import org.xml.sax.SAXException;

import de.anomic.data.BookmarksDB;
import de.anomic.data.UserDB;
import de.anomic.data.WorkTables;
import de.anomic.data.ymark.YMarkAutoTagger;
import de.anomic.data.ymark.YMarkEntry;
import de.anomic.data.ymark.YMarkHTMLImporter;
import de.anomic.data.ymark.YMarkJSONImporter;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.data.ymark.YMarkUtil;
import de.anomic.data.ymark.YMarkXBELImporter;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class import_ymark {

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
        final int queueSize = 200;

        Thread t;
        YMarkEntry bmk;
        // String root = YMarkEntry.FOLDERS_IMPORTED;
        String root = "";
        ByteArrayInputStream stream = null;

        if(isAdmin || isAuthUser) {
        	String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);
        	final ArrayBlockingQueue<String> autoTaggingQueue = new ArrayBlockingQueue<String>(10*queueSize);
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
        		bmk_user = post.get("table").substring(0, post.get("table").indexOf('_',0));
        	}
            if(post.containsKey("redirect") && post.get("redirect").length() > 0) {
                prop.put("redirect_url", post.get("redirect"));
                prop.put("redirect", "1");
            }
            if(post.containsKey("root") && post.get("root").length() > 0) {
                root = post.get("root");
            }
        	if(post.containsKey("bmkfile") && !post.get("bmkfile").isEmpty() && post.containsKey("importer")){
        		stream = new ByteArrayInputStream(UTF8.getBytes(post.get("bmkfile$file")));
        		if(post.get("importer").equals("surro") && stream != null) {
                    SurrogateReader surrogateReader;
                    try {
                        surrogateReader = new SurrogateReader(stream, queueSize);
                    } catch (final IOException e) {
                        //TODO: display an error message
                        Log.logException(e);
                        prop.put("status", "0");
                        return prop;
                    }
                    t = new Thread(surrogateReader, "YMarks - Surrogate Reader");
                    t.start();
                    while ((bmk = new YMarkEntry(surrogateReader.take())) != YMarkEntry.POISON) {
                        putBookmark(sb.tables.bookmarks, bmk_user, bmk, autoTaggingQueue, autotag, empty);
                    }
                    prop.put("status", "1");
                } else {
                    InputStreamReader reader = null;
                    try {
                        reader = new InputStreamReader(stream,"UTF-8");
                    } catch (final UnsupportedEncodingException e1) {
                        //TODO: display an error message
                        Log.logException(e1);
                        prop.put("status", "0");
                        return prop;
                    }
                    if(post.get("importer").equals("html") && reader != null) {
                        final YMarkHTMLImporter htmlImporter = new YMarkHTMLImporter(reader, queueSize, root);
                        t = new Thread(htmlImporter, "YMarks - HTML Importer");
                        t.start();
                        while ((bmk = htmlImporter.take()) != YMarkEntry.POISON) {
                            putBookmark(sb.tables.bookmarks, bmk_user, bmk, autoTaggingQueue, autotag, empty);
                        }
                        prop.put("status", "1");
                    } else if(post.get("importer").equals("xbel") && reader != null) {
                        final YMarkXBELImporter xbelImporter;
                        try {
                            //TODO: make RootFold
                            xbelImporter = new YMarkXBELImporter(reader, queueSize, root);
                        } catch (final SAXException e) {
                            //TODO: display an error message
                            Log.logException(e);
                            prop.put("status", "0");
                            return prop;
                        }
                        t = new Thread(xbelImporter, "YMarks - XBEL Importer");
                        t.start();
                        while ((bmk = xbelImporter.take()) != YMarkEntry.POISON) {
                            putBookmark(sb.tables.bookmarks, bmk_user, bmk, autoTaggingQueue, autotag, empty);
                        }
                        prop.put("status", "1");
                    } else if(post.get("importer").equals("json") && reader != null) {
                        YMarkJSONImporter jsonImporter;
                        jsonImporter = new YMarkJSONImporter(reader, queueSize, root);
                        t = new Thread(jsonImporter, "YMarks - JSON Importer");
                        t.start();
                        while ((bmk = jsonImporter.take()) != YMarkEntry.POISON) {
                        	putBookmark(sb.tables.bookmarks, bmk_user, bmk, autoTaggingQueue, autotag, empty);
                        }
                        prop.put("status", "1");
                    }
                }
        	} else if(post.containsKey("importer") && post.get("importer").equals("crawls")) {
        		try {
	    			final Pattern pattern = Pattern.compile("^crawl start for.*");
					final Iterator<Tables.Row> APIcalls = sb.tables.iterator(WorkTables.TABLE_API_NAME, WorkTables.TABLE_API_COL_COMMENT, pattern);
	    			Tables.Row row = null;
	    			while(APIcalls.hasNext()) {
	    				row = APIcalls.next();
	    				if(row.get(WorkTables.TABLE_API_COL_TYPE, "").equals("crawler")) {
	    					final String url = row.get(WorkTables.TABLE_API_COL_COMMENT, "").substring(16);
	    					sb.tables.bookmarks.createBookmark(sb.loader, url, bmk_user, autotag, "crawlStart", "/Crawl Start");
	    				}
	    			}
	    			prop.put("status", "1");
				} catch (final IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (final RowSpaceExceededException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (final Failure e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	} else if(post.containsKey("importer") && post.get("importer").equals("bmks")) {
        		final Iterator<String> bit=sb.bookmarksDB.getBookmarksIterator(isAdmin);
            	BookmarksDB.Bookmark bookmark;
            	while(bit.hasNext()){
        			bookmark=sb.bookmarksDB.getBookmark(bit.next());
        			final YMarkEntry bmk_entry = new YMarkEntry(false);
        			bmk_entry.put(YMarkEntry.BOOKMARK.URL.key(), bookmark.getUrl());
        			try {
						if(!sb.tables.has(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), YMarkUtil.getBookmarkId(bookmark.getUrl()))) {
    						bmk_entry.put(YMarkEntry.BOOKMARK.PUBLIC.key(), bookmark.getPublic() ? "true" : "false");
	    					bmk_entry.put(YMarkEntry.BOOKMARK.TITLE.key(), bookmark.getTitle());
	    					bmk_entry.put(YMarkEntry.BOOKMARK.DESC.key(), bookmark.getDescription());
	    					bmk_entry.put(YMarkEntry.BOOKMARK.TAGS.key(), bookmark.getTagsString());
	    					bmk_entry.put(YMarkEntry.BOOKMARK.FOLDERS.key(), root+bookmark.getFoldersString().replaceAll(".*"+YMarkUtil.TAGS_SEPARATOR+YMarkUtil.FOLDERS_SEPARATOR, root+YMarkUtil.FOLDERS_SEPARATOR));
						}
						if(autotag) {
							bmk_entry.put(YMarkEntry.BOOKMARK.TAGS.key(), YMarkAutoTagger.autoTag(bookmark.getUrl(), sb.loader, 3, sb.tables.bookmarks.getTags(bmk_user)));
						}
						sb.tables.bookmarks.addBookmark(bmk_user, bmk_entry, merge, true);
			        	prop.put("status", "1");
					} catch (final MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (final IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (final RowSpaceExceededException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
            	}
            }
        	if(post.containsKey("autotag") && !post.get("autotag", "off").equals("off")) {
            	try {
    				autoTaggingQueue.put(YMarkAutoTagger.POISON);
    				Log.logInfo(YMarkTables.BOOKMARKS_LOG, "Importer inserted poison pill in autoTagging queue");
    			} catch (final InterruptedException e) {
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
			final String url = bmk.get(YMarkEntry.BOOKMARK.URL.key());
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
		} catch (final IOException e) {
			Log.logException(e);
		} catch (final RowSpaceExceededException e) {
			Log.logException(e);
		} catch (final InterruptedException e) {
			Log.logException(e);
		}
	}
}









