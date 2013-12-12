import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.TreeMap;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.UserDB;
import net.yacy.data.ymark.YMarkAutoTagger;
import net.yacy.data.ymark.YMarkCrawlStart;
import net.yacy.data.ymark.YMarkEntry;
import net.yacy.data.ymark.YMarkMetadata;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.data.ymark.YMarkTag;
import net.yacy.data.ymark.YMarkUtil;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.blob.Tables;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class get_treeview {

	public static final String ROOT = "root";
	public static final String SOURCE = "source";

	static serverObjects prop;

	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		prop = new serverObjects();
        final UserDB.Entry user = sb.userDB.getUser(header);
        final boolean isAdmin = (sb.verifyAuthentication(header));
        final boolean isAuthUser = user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT);


        if(isAdmin || isAuthUser) {
        	final String bmk_user = (isAuthUser ? user.getUserName() : YMarkTables.USER_ADMIN);

        	String root = YMarkTables.FOLDERS_ROOT;
        	String[] foldername = null;
        	boolean isFolder = true;
        	boolean isBookmark = false;
        	boolean isMetadata = false;
        	boolean isURLdb = false;
        	boolean isCrawlStart = false;
        	boolean isAutoTagger = false;
        	boolean displayBmk = false;

        	if (post != null){
        		if(post.containsKey("display") && post.get("display").equals("bmk")) {
        			displayBmk = true;
        		}

        		if (post.containsKey(ROOT)) {
            		if (post.get(ROOT).equals(SOURCE) || post.get(ROOT).equals(YMarkTables.FOLDERS_ROOT)) {
            			root = "";
            		} else if (post.get(ROOT).startsWith(YMarkTables.FOLDERS_ROOT)) {
            			root = post.get(ROOT);
            		} else if (post.get(ROOT).startsWith("b:")) {
            			isBookmark = true;
            			isFolder = false;
            		} else if (post.get(ROOT).startsWith("m:")) {
            			isMetadata = true;
            			isFolder = false;
            		} else if (post.get(ROOT).startsWith("u:")) {
            			isURLdb = true;
            			isFolder = false;
            		} else if (post.get(ROOT).startsWith("w:")) {
            			isAutoTagger = true;
            			isFolder = false;
            		} else if (post.get(ROOT).startsWith("c:")) {
            			isCrawlStart = true;
            			isFolder = false;
            		}
        		}
        	}

        	Iterator<String> it = null;
        	Iterator<Tables.Row> bit = null;
        	Tables.Row bmk_row = null;
        	int count = 0;

        	if(isFolder) {
	        	// loop through folderList
	        	try {
	        		// it = sb.tables.bookmarks.folders.getFolders(bmk_user, root);
	        		it = sb.tables.bookmarks.getFolders(bmk_user, root).iterator();
				} catch (final IOException e) {
					ConcurrentLog.logException(e);
				}
	        	int n = YMarkUtil.FOLDERS_SEPARATOR_PATTERN.split(root, 0).length;
	        	if (n == 0) n = 1;
	        	while (it.hasNext()) {
	        		final String folder = it.next();
	        		foldername = YMarkUtil.FOLDERS_SEPARATOR_PATTERN.split(folder);
	        		if (foldername.length == n+1) {
	        			prop.put("folders_"+count+"_foldername", foldername[n]);
	    	    		prop.put("folders_"+count+"_expanded", "false");
	    	    		if(foldername[n].equals("IOExceptions"))
	    	    			prop.put("folders_"+count+"_type", "err");	 
	    	    		else if(foldername[n].equals("unsorted"))
	    	    			prop.put("folders_"+count+"_type", "question");
	    	    		else if(foldername[n].equals("Crawl Start"))
	    	    			prop.put("folders_"+count+"_type", "crawl");	
	    	    		else
	    	    			prop.put("folders_"+count+"_type", "folder");	    	    		
	    	    		prop.put("folders_"+count+"_hash", folder);				//TODO: switch from pathString to folderHash
	    	    		prop.put("folders_"+count+"_url", "");					//TODO: insert folder url
	    	    		prop.put("folders_"+count+"_hasChildren", "true");		//TODO: determine if folder has children
	    	    		prop.put("folders_"+count+"_comma", ",");
	    	    		count++;
	        		}
	        	}
	        	if(displayBmk && !root.isEmpty()) {
					bit = sb.tables.bookmarks.getBookmarksByFolder(bmk_user, root);
					while (bit.hasNext()) {
						bmk_row = bit.next();
						if(bmk_row != null) {
							final String url = UTF8.String(bmk_row.get(YMarkEntry.BOOKMARK.URL.key()));
							final String title = bmk_row.get(YMarkEntry.BOOKMARK.TITLE.key(), YMarkEntry.BOOKMARK.TITLE.deflt());

				    		// TODO: get_treeview - get rid of bmtype
				    		if (post.containsKey("bmtype")) {
				    			if (post.get("bmtype").equals("title")) {
				    				prop.putJSON("folders_"+count+"_foldername", title);
				    			} else if (post.get("bmtype").equals("href")) {
				    				prop.putJSON("folders_"+count+"_foldername", "<a href='"+url+"' target='_blank'>"+title+"</a>");
				    			}
				    		} else {
				    				prop.putJSON("folders_"+count+"_foldername", url);
							}
				    		prop.put("folders_"+count+"_expanded", "false");
				    		prop.put("folders_"+count+"_url", url);
				    		prop.put("folders_"+count+"_type", "file");
				    		prop.put("folders_"+count+"_hash", "b:"+new String(bmk_row.getPK()));
				    		prop.put("folders_"+count+"_hasChildren", "true");
				    		prop.put("folders_"+count+"_comma", ",");
				    		count++;
				    	}
					}
				}
				count--;
				prop.put("folders_"+count+"_comma", "");
				count++;
				prop.put("folders", count);
	        } else if(displayBmk && isBookmark) {
	        	try {
					final String urlHash = post.get(ROOT).substring(2);
	        		String url = "";
					bmk_row = sb.tables.select(YMarkTables.TABLES.BOOKMARKS.tablename(bmk_user), urlHash.getBytes());
					if(bmk_row != null) {
			            it = bmk_row.keySet().iterator();
			            while(it.hasNext()) {
			            	final String key = it.next();
			            	if(key.startsWith("date")) {
				            	final String d = UTF8.String(bmk_row.get(key));
				            	if(!d.isEmpty()) {
				            		final String date = ISO8601Formatter.FORMATTER.format(new Date(Long.parseLong(d)));
					            	prop.put("folders_"+count+"_foldername","<small><b>"+key+":</b> " + date + "</small>");
			    					putProp(count, "date");
			    					count++;
				            	}
			            	} else {
								final String value = UTF8.String(bmk_row.get(key));
								if (key.equals("url"))
									url = value;
								prop.put("folders_"+count+"_foldername","<small><b>"+key+":</b> " + value + "</small>");
								if(YMarkEntry.BOOKMARK.contains(key))
									putProp(count, YMarkEntry.BOOKMARK.get(key).type());
								else
									putProp(count, "meta");
								count++;
			            	}
			            }
			            prop.put("folders_"+count+"_foldername","<small><b>MetaData</b></small>");
			            putProp(count, "meta");
			            prop.put("folders_"+count+"_hash", "m:"+url);
			    		prop.put("folders_"+count+"_hasChildren", "true");
			            count++;
			            prop.put("folders_"+count+"_foldername","<small><b>URLdb</b></small>");
			            putProp(count, "meta");
			            prop.put("folders_"+count+"_hash", "u:"+url);
			    		prop.put("folders_"+count+"_hasChildren", "true");
			            count++;
			            prop.put("folders_"+count+"_foldername","<small><b>CrawlStart</b></small>");
			            putProp(count, "meta");
			            prop.put("folders_"+count+"_hash", "c:"+url);
			    		prop.put("folders_"+count+"_hasChildren", "true");
			            count++;
			            prop.put("folders_"+count+"_foldername","<small><b>AutoTagger</b></small>");
			            putProp(count, "meta");
			            prop.put("folders_"+count+"_hash", "w:"+url);
			    		prop.put("folders_"+count+"_hasChildren", "true");
						prop.put("folders_"+count+"_comma", "");
			    		count++;
		        		prop.put("folders", count);
					}
				} catch (final IOException e) {
					ConcurrentLog.logException(e);
				} catch (final SpaceExceededException e) {
					ConcurrentLog.logException(e);
				}
	        } else if (isAutoTagger || isMetadata || isURLdb || isCrawlStart) {
	        	try {
	                final YMarkMetadata meta = new YMarkMetadata(new DigestURL(post.get(ROOT).substring(2)), sb.index);
	                ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
        			final Document document = meta.loadDocument(sb.loader, agent);
        			final TreeMap<String, YMarkTag> tags = sb.tables.bookmarks.getTags(bmk_user);
        			if(isAutoTagger)  {
        				prop.put("folders_"+count+"_foldername","<small><b>meta-"+YMarkMetadata.METADATA.KEYWORDS.name().toLowerCase()+":</b> " + meta.loadMetadata().get(YMarkMetadata.METADATA.KEYWORDS) + "</small>");
        				putProp(count, "meta");
        				count++;
						prop.put("folders_"+count+"_foldername","<small><b>with preference: </b>"+YMarkAutoTagger.autoTag(document, 4, tags)+"</small>");
    					putProp(count, "meta");
    					count++;
						prop.put("folders_"+count+"_foldername","<small><b>without preference: </b>"+YMarkAutoTagger.autoTag(document, 4, new  TreeMap<String, YMarkTag>())+"</small>");
    					putProp(count, "meta");
    					count++;
    	        		prop.put("folders", count);
	        		} else if(isMetadata) {
	        			count = putMeta(count, meta.loadMetadata());
	        		} else if(isURLdb) {
						count = putMeta(count, meta.getMetadata());
	        		} else if(isCrawlStart) {
	        			ConcurrentLog.info("YMark", "I am looking for CrawlStart: "+post.get(ROOT).substring(2));
	        			final YMarkCrawlStart crawlStart = new YMarkCrawlStart(sb.tables, post.get(ROOT).substring(2));
	        			final Iterator<String> iter = crawlStart.keySet().iterator();
	        			String key;
	        			while(iter.hasNext()) {
	        				key = iter.next();
	        				prop.put("folders_"+count+"_foldername","<small><b>"+key.toLowerCase()+":</b> " + crawlStart.get(key) + "</small>");
	        				putProp(count, "meta");
	        				count++;
	        			}
	        			prop.put("folders", count);
	        		}

				} catch (final MalformedURLException e) {
					ConcurrentLog.logException(e);
				} catch (final IOException e) {
					ConcurrentLog.logException(e);
				} catch (final Failure e) {
					ConcurrentLog.logException(e);
				}
	        }
        } else {
        	prop.put(serverObjects.ACTION_AUTHENTICATE, YMarkTables.USER_AUTHENTICATE_MSG);
        }
        // return rewrite properties
        return prop;
	}
	public static void putProp(final int count, final String type) {
		prop.put("folders_"+count+"_expanded", "false");
		prop.put("folders_"+count+"_url", "");
		prop.put("folders_"+count+"_type", type);
		prop.put("folders_"+count+"_hash", "");
		prop.put("folders_"+count+"_hasChildren", "false");
		prop.put("folders_"+count+"_comma", ",");
	}
	public static int putMeta(int count, final EnumMap<YMarkMetadata.METADATA, String> metadata) {
		final Iterator<YMarkMetadata.METADATA> iter = metadata.keySet().iterator();
		while (iter.hasNext()) {
			final YMarkMetadata.METADATA key = iter.next();
			final String value = metadata.get(key);
			prop.put("folders_"+count+"_foldername","<small><b>"+key.toString().toLowerCase()+":</b> " + value + "</small>");
			putProp(count, "meta");
			count++;
		}
		prop.put("folders", count);
		return count;
	}
}
