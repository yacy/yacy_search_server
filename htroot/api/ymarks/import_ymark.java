import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.BookmarksDB;
import net.yacy.data.UserDB;
import net.yacy.data.WorkTables;
import net.yacy.data.ymark.MonitoredReader;
import net.yacy.data.ymark.YMarkAutoTagger;
import net.yacy.data.ymark.YMarkCrawlStart;
import net.yacy.data.ymark.YMarkDMOZImporter;
import net.yacy.data.ymark.YMarkEntry;
import net.yacy.data.ymark.YMarkHTMLImporter;
import net.yacy.data.ymark.YMarkJSONImporter;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.data.ymark.YMarkUtil;
import net.yacy.data.ymark.YMarkXBELImporter;
import net.yacy.document.Parser.Failure;
import net.yacy.document.content.SurrogateReader;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.workflow.InstantBusyThread;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

import org.xml.sax.SAXException;



public class import_ymark {

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);
        final int queueSize = 200;

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
        	final String indexing = post.get("indexing", "off");
        	final boolean medialink = post.getBoolean("medialink");

        	if(post.containsKey("autotag") && !post.get("autotag", "off").equals("off")) {
        		autotag = true;
        		if(post.get("autotag").equals("merge")) {
                	merge = true;
                }
                if(post.get("autotag").equals("empty")) {
                	empty = true;
                }
                YMarkAutoTagger autoTagger = new YMarkAutoTagger(autoTaggingQueue, sb.loader, sb.tables.bookmarks, bmk_user, merge);
                InstantBusyThread.oneTimeJob(autoTagger, 0);
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
            ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
        	if(post.containsKey("bmkfile") && !post.get("bmkfile").isEmpty() && post.containsKey("importer")){
        		final byte[] bytes = UTF8.getBytes(post.get("bmkfile$file"));
        		stream = new ByteArrayInputStream(bytes);
        		if(post.get("importer").equals("surro") && stream != null) {
                    SurrogateReader surrogateReader;
                    try {
                        surrogateReader = new SurrogateReader(stream, queueSize);
                    } catch (final IOException e) {
                        //TODO: display an error message
                        ConcurrentLog.logException(e);
                        prop.put("status", "0");
                        return prop;
                    }
                    InstantBusyThread.oneTimeJob(surrogateReader, 0);
                    while ((bmk = new YMarkEntry(surrogateReader.take())) != YMarkEntry.POISON) {
                        putBookmark(sb, bmk_user, bmk, autoTaggingQueue, autotag, empty, indexing, medialink);
                    }
                    prop.put("status", "1");
                } else {
                    MonitoredReader reader = null;
                    try {
                        reader = new MonitoredReader(new InputStreamReader(stream,"UTF-8"), 1024*16, bytes.length);
                    } catch (final UnsupportedEncodingException e1) {
                        //TODO: display an error message
                        ConcurrentLog.logException(e1);
                        prop.put("status", "0");
                        return prop;
                    }
                    if(post.get("importer").equals("html") && reader != null) {
                        final YMarkHTMLImporter htmlImporter = new YMarkHTMLImporter(reader, queueSize, root);
                        InstantBusyThread.oneTimeJob(htmlImporter, 0);
                        InstantBusyThread.oneTimeJob(htmlImporter.getConsumer(sb, bmk_user, autoTaggingQueue, autotag, empty, indexing, medialink), 0);
                        prop.put("status", "1");
                    } else if(post.get("importer").equals("xbel") && reader != null) {
                        final YMarkXBELImporter xbelImporter;
                        try {
                            //TODO: make RootFold
                            xbelImporter = new YMarkXBELImporter(reader, queueSize, root);
                        } catch (final SAXException e) {
                            //TODO: display an error message
                            ConcurrentLog.logException(e);
                            prop.put("status", "0");
                            return prop;
                        }
                        InstantBusyThread.oneTimeJob(xbelImporter, 0);
                        InstantBusyThread.oneTimeJob(xbelImporter.getConsumer(sb, bmk_user, autoTaggingQueue, autotag, empty, indexing, medialink), 0);
                        prop.put("status", "1");
                    } else if(post.get("importer").equals("json") && reader != null) {
                        YMarkJSONImporter jsonImporter;
                        jsonImporter = new YMarkJSONImporter(reader, queueSize, root);
                        InstantBusyThread.oneTimeJob(jsonImporter, 0);
                        while ((bmk = jsonImporter.take()) != YMarkEntry.POISON) {
                        	putBookmark(sb, bmk_user, bmk, autoTaggingQueue, autotag, empty, indexing, medialink);
                        }
                        prop.put("status", "1");
                    }
                }
        	} else if(post.containsKey("importer") && post.get("importer").equals("crawls")) {
        		if(!isAdmin) {
        			prop.authenticationRequired();
        			return prop;
        		}
        		try {
	    			final Pattern pattern = Pattern.compile("^crawl start for.*");
					final Iterator<Tables.Row> APIcalls = sb.tables.iterator(WorkTables.TABLE_API_NAME, WorkTables.TABLE_API_COL_COMMENT, pattern);
	    			Tables.Row row = null;
	    			while(APIcalls.hasNext()) {
	    				row = APIcalls.next();
	    				if(row.get(WorkTables.TABLE_API_COL_TYPE, "").equals("crawler")) {
	    					final String url = row.get(WorkTables.TABLE_API_COL_COMMENT, "").substring(16);
	    					sb.tables.bookmarks.createBookmark(sb.loader, url, agent, bmk_user, autotag, "crawlStart", "/Crawl Start");
	    				}
	    			}
	    			prop.put("status", "1");
				} catch (final IOException e) {
					ConcurrentLog.logException(e);
				} catch (final Failure e) {
					ConcurrentLog.logException(e);
				}
        	} else if(post.containsKey("importer") && post.get("importer").equals("bmks")) {
        		if(!isAdmin) {
        			prop.authenticationRequired();
        			return prop;
        		}
        		final Iterator<String> bit=sb.bookmarksDB.getBookmarksIterator(isAdmin);
            	BookmarksDB.Bookmark bookmark;
            	while(bit.hasNext()){
        			try {
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
                                bmk_entry.put(YMarkEntry.BOOKMARK.TAGS.key(), YMarkAutoTagger.autoTag(bookmark.getUrl(), sb.loader, agent, 3, sb.tables.bookmarks.getTags(bmk_user)));
                            }
                            sb.tables.bookmarks.addBookmark(bmk_user, bmk_entry, merge, true);
                            prop.put("status", "1");
                        } catch (final MalformedURLException e) {
                            ConcurrentLog.logException(e);
                        } catch (final IOException e) {
                            ConcurrentLog.logException(e);
                        }
                    } catch (final IOException e1) {
                    }
            	}
            } else if(post.containsKey("importer") && post.get("importer").equals("dmoz")) {
        		if(!isAdmin) {
        			prop.authenticationRequired();
        			return prop;
        		}
        		try {
        			final File in = new File(sb.workPath, "content.rdf.u8.gz");
        			final InputStream gzip = new FileInputStream(in);
        			final InputStream content = new GZIPInputStream(gzip);
        			final InputStreamReader reader = new InputStreamReader(content, "UTF-8");
        			final BufferedReader breader = new BufferedReader(reader);
        			final MonitoredReader mreader = new MonitoredReader(breader, 1024*1024, in.length());

        			final String source = post.get("source", "");
        			final YMarkDMOZImporter DMOZImporter = new YMarkDMOZImporter(mreader, queueSize, root, source);

        			mreader.addChangeListener(sb.tables.bookmarks.getProgressListener("DMOZImporter"));
        			DMOZImporter.setDepth(6);
        			InstantBusyThread.oneTimeJob(DMOZImporter, 0);
        			InstantBusyThread.oneTimeJob(DMOZImporter.getConsumer(sb, bmk_user, autoTaggingQueue, autotag, empty, indexing, medialink), 0);

        			prop.put("status", "1");
				} catch (final Exception e) {
					ConcurrentLog.logException(e);
				}
            }
        }  else {
        	prop.put(serverObjects.ACTION_AUTHENTICATE, YMarkTables.USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
	}

	public static void putBookmark(final Switchboard sb, final String bmk_user, final YMarkEntry bmk,
			final ArrayBlockingQueue<String> autoTaggingQueue, final boolean autotag, final boolean empty, final String indexing, final boolean medialink) {
		try {
			final String url = bmk.get(YMarkEntry.BOOKMARK.URL.key());
			// other protocols could cause problems
			if(url != null && url.startsWith("http")) {
			    sb.tables.bookmarks.addBookmark(bmk_user, bmk, true, true);
				if(autotag) {
					if(!empty) {
						autoTaggingQueue.put(url);
					} else if(!bmk.containsKey(YMarkEntry.BOOKMARK.TAGS.key()) || bmk.get(YMarkEntry.BOOKMARK.TAGS.key()).equals(YMarkEntry.BOOKMARK.TAGS.deflt())) {
						autoTaggingQueue.put(url);
					}
				}
				// fill crawler
				if (indexing.equals("single")) {
					bmk.crawl(YMarkCrawlStart.CRAWLSTART.SINGLE, medialink, sb);
				} else if (indexing.equals("onelink")) {
					bmk.crawl(YMarkCrawlStart.CRAWLSTART.ONE_LINK, medialink, sb);
                } else if (indexing.equals("fulldomain")) {
                	bmk.crawl(YMarkCrawlStart.CRAWLSTART.FULL_DOMAIN, medialink, sb);
                }
			}
		} catch (final IOException e) {
			ConcurrentLog.logException(e);
		} catch (final InterruptedException e) {
			ConcurrentLog.logException(e);
		}
	}
}
