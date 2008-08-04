// Bookmarks_p.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
//
// $LastChangedDate: 2008-01-22 12:51:43 +0100 (Di, 22 Jan 2008) $
// $LastChangedRevision: 4374 $
// $LastChangedBy: low012 $
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

// You must compile this file with
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import de.anomic.data.bookmarksDB;
import de.anomic.data.listManager;
import de.anomic.data.userDB;
import de.anomic.data.bookmarksDB.Tag;
import de.anomic.http.HttpClient;
import de.anomic.http.httpHeader;
import de.anomic.index.indexURLReference;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacyURL;


public class Bookmarks {

	private static final serverObjects prop = new serverObjects();
	private static plasmaSwitchboard sb = null;
	private static userDB.Entry user = null;
	private static boolean isAdmin = false;	

	final static int SORT_ALPHA = 1;
	final static int SORT_SIZE = 2;
	final static int SHOW_ALL = -1;
	final static boolean TAGS = false;
	final static boolean FOLDERS = true;
	
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {

    	int max_count = 10;
    	int start=0;
    	String tagName = "";
    	String username="";
    	
    	prop.clear();
    	sb = (plasmaSwitchboard) env;
    	user = sb.userDB.getUser(header);   
    	isAdmin=(sb.verifyAuthentication(header, true) || user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT));
    
    	// set user name
    	if(user != null) username=user.getUserName();
    	else if(isAdmin) username="admin";
    	prop.put("user", username);
    	
    	//redirect to userpage
    	/*
    	if(username!="" &&(post == null || !post.containsKey("user") && !post.containsKey("mode")))
        prop.put("LOCATION", "/Bookmarks.html?user="+username);
    	*/
    
    	// set peer address
    	final String address = sb.webIndex.seedDB.mySeed().getPublicAddress();
    	prop.put("address", address);
    
    	//defaultvalues
    	if(isAdmin) {
    		prop.put("mode", "1");
                prop.put("admin", "1");
        } else {
    		prop.put("mode", "0");
                prop.put("admin", "0");
        }
    	prop.put("mode_edit", "0");
    	prop.put("mode_title", "");
    	prop.put("mode_description", "");
    	prop.put("mode_url", "");
    	prop.put("mode_tags", "");
    	prop.put("mode_path", "");
    	prop.put("mode_public", "1"); //1=is public
    	prop.put("mode_feed", "0"); //no newsfeed
    	
    	if(post != null){        
    		if(!isAdmin){
    			if(post.containsKey("login")){
    				prop.put("AUTHENTICATE","admin log-in");
    			}
    		}else if(post.containsKey("mode")){
    			final String mode=post.get("mode");            
    			if(mode.equals("add")){
    				prop.put("mode", "2");
    			}else if(mode.equals("importxml")){
    				prop.put("mode", "3");
    			}else if(mode.equals("manage")){
    				prop.put("mode", "4");
    			}
    		}else if(post.containsKey("add")){ //add an Entry
    			final String url=post.get("url");
    			final String title=post.get("title");
    			final String description=post.get("description");
    			String tagsString = post.get("tags");
    			final String pathString = post.get("path");
    			tagsString=tagsString+","+pathString;
    			if(tagsString.equals("")){
    				tagsString="/unsorted"; //default tag
    			}
    			final Set<String> tags=listManager.string2set(bookmarksDB.cleanTagsString(tagsString)); 
    			final bookmarksDB.Bookmark bookmark = sb.bookmarksDB.createBookmark(url, username);
    			if(bookmark != null){
    				bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_TITLE, title);
    				bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
    				if(user!=null){ 
    					bookmark.setOwner(user.getUserName());
    				}
    				if((post.get("public")).equals("public")){
    					bookmark.setPublic(true);
    					publishNews(url, title, description, tagsString);
    				}else{
    					bookmark.setPublic(false);
    				}
    				if(post.containsKey("feed") && (post.get("feed")).equals("feed")){
    					bookmark.setFeed(true);
    				}else{
    					bookmark.setFeed(false);
    				}
    				bookmark.setTags(tags, true);
    				sb.bookmarksDB.saveBookmark(bookmark);
    			//}else{
    	                    //ERROR
    			}
    		}else if(post.containsKey("edit")){
    			final String urlHash=post.get("edit");
    			prop.put("mode", "2");
    			if (urlHash.length() == 0) {
    				prop.put("mode_edit", "0"); // create mode
    				prop.putHTML("mode_title", post.get("title"));
    				prop.putHTML("mode_description", post.get("description"));
    				prop.put("mode_url", post.get("url"));
    				prop.putHTML("mode_tags", post.get("tags"));
    				prop.putHTML("mode_path", post.get("path"));
    				prop.put("mode_public", "0");
    				prop.put("mode_feed", "0");
    			} else {
                    final bookmarksDB.Bookmark bookmark = sb.bookmarksDB.getBookmark(urlHash);
                    if (bookmark == null) {
                        // try to get the bookmark from the LURL database
                        final indexURLReference urlentry = sb.webIndex.getURL(urlHash, null, 0);
                        plasmaParserDocument document = null;
                        if (urlentry != null) {
                            final indexURLReference.Components comp = urlentry.comp();
                            document = plasmaSnippetCache.retrieveDocument(comp.url(), true, 5000, true, false);
                            prop.put("mode_edit", "0"); // create mode
                            prop.put("mode_url", comp.url().toNormalform(false, true));
                            prop.putHTML("mode_title", comp.dc_title());
                            prop.putHTML("mode_description", (document == null) ? comp.dc_title(): document.dc_title());
                            prop.putHTML("mode_author", comp.dc_creator());
                            prop.putHTML("mode_tags", (document == null) ? comp.dc_subject() : document.dc_subject(','));
                            prop.putHTML("mode_path","");
                            prop.put("mode_public", "0");
                            prop.put("mode_feed", "0"); //TODO: check if it IS a feed
                        }
                        if (document != null) document.close();
                    } else {
                        // get from the bookmark database
                        prop.put("mode_edit", "1"); // edit mode
                        prop.putHTML("mode_title", bookmark.getTitle());
                        prop.putHTML("mode_description", bookmark.getDescription());
                        prop.put("mode_url", bookmark.getUrl());
                        prop.putHTML("mode_tags", bookmark.getTagsString());
                        prop.putHTML("mode_path",bookmark.getFoldersString());
                        if (bookmark.getPublic()) {
                            prop.put("mode_public", "1");
                        } else {
                            prop.put("mode_public", "0");
                        }
                        if (bookmark.getFeed()) {
                            prop.put("mode_feed", "1");
                        } else {
                            prop.put("mode_feed", "0");
                        }
                    }
                }
    		} else if(post.containsKey("htmlfile")){
    			boolean isPublic=false;
    			if((post.get("public")).equals("public")){
    				isPublic=true;
    			}
    			String tags=post.get("tags");
    			if(tags.equals("")){
    				tags="unsorted";
    			}
    			serverLog.logInfo("BOOKMARKS", "I try to import bookmarks from HTML-file");
    			try {
    				final File file=new File(post.get("htmlfile"));    			
    				sb.bookmarksDB.importFromBookmarks(new yacyURL(file) , post.get("htmlfile$file"), tags, isPublic);
    			} catch (final MalformedURLException e) {}
    			serverLog.logInfo("BOOKMARKS", "success!!");
    		}else if(post.containsKey("xmlfile")){
    			boolean isPublic=false;
    			if((post.get("public")).equals("public")){
    				isPublic=true;
    			}
    			sb.bookmarksDB.importFromXML(post.get("xmlfile$file"), isPublic);
    		}else if(post.containsKey("delete")){
    			final String urlHash=post.get("delete");
    			sb.bookmarksDB.removeBookmark(urlHash);
    		}
    		if(post.containsKey("tag")){
    			tagName=post.get("tag");
    		}
    		if(post.containsKey("start")){
    			start=Integer.parseInt(post.get("start"));
    		}
    		if(post.containsKey("num")){
    			max_count=Integer.parseInt(post.get("num"));
    		}
    	} // END if(post != null)
    	
    	
    	//-----------------------
    	// create tag list
    	//-----------------------
    	printTagList("taglist", tagName, SORT_SIZE, 25, false);
    	printTagList("optlist", tagName, SORT_ALPHA, SHOW_ALL, true);
    	       	
    	//-----------------------
    	// create bookmark list
    	//-----------------------
    	int count=0;
        Iterator<String> it = null;    	
       	bookmarksDB.Bookmark bookmark;
       	Set<String> tags;
       	Iterator<String> tagsIt;
       	int tagCount;
       	
       	prop.put("num-bookmarks", sb.bookmarksDB.bookmarksSize());
       	
       	count=0;
       	if(!tagName.equals("")){
       		prop.put("selected", "");
       		it=sb.bookmarksDB.getBookmarksIterator(tagName, isAdmin);
       	}else{
       		prop.put("selected", " selected=\"selected\"");
       		it=sb.bookmarksDB.getBookmarksIterator(isAdmin);
       	}
       	
       	//skip the first entries (display next page)
       	count=0;
       	while(count < start && it.hasNext()){
       		it.next();
       		count++;
       	}
       	
       	count=0;
       	while(count<max_count && it.hasNext()){
       		bookmark=sb.bookmarksDB.getBookmark(it.next());
       		if(bookmark!=null){
       			if(bookmark.getFeed() && isAdmin)
       				prop.put("bookmarks_"+count+"_link", "/FeedReader_p.html?url="+bookmark.getUrl());
       			else
       				prop.put("bookmarks_"+count+"_link",bookmark.getUrl());
       			prop.putHTML("bookmarks_"+count+"_title", bookmark.getTitle());
       			prop.putHTML("bookmarks_"+count+"_description", bookmark.getDescription());
       			prop.put("bookmarks_"+count+"_date", serverDate.formatISO8601(new Date(bookmark.getTimeStamp())));
       			prop.put("bookmarks_"+count+"_rfc822date", HttpClient.dateString(new Date(bookmark.getTimeStamp())));
       			prop.put("bookmarks_"+count+"_public", (bookmark.getPublic() ? "1" : "0"));
            
       			//List Tags.
       			tags=bookmark.getTags();
       			tagsIt=tags.iterator();
       			tagCount=0;
       			while (tagsIt.hasNext()) {            	
       				final String tname = tagsIt.next();
       				if ((!tname.startsWith("/")) && (!tname.equals(""))) {
       					prop.put("bookmarks_"+count+"_tags_"+tagCount+"_tag", tname);
       					tagCount++;
       				}
       			}
       			prop.put("bookmarks_"+count+"_tags", tagCount);
       			prop.put("bookmarks_"+count+"_hash", bookmark.getUrlHash());
       			count++;
       		}
       	}
       	prop.putHTML("tag", tagName);
       	prop.put("start", start);
       	if(it.hasNext()){
       		prop.put("next-page", "1");
       		prop.put("next-page_start", start+max_count);
       		prop.putHTML("next-page_tag", tagName);
       		prop.put("next-page_num", max_count);
       	}
       	if(start >= max_count){
       		start=start-max_count;
       		if(start <0){
       			start=0;
       		}
       		prop.put("prev-page", "1");
       		prop.put("prev-page_start", start);
       		prop.putHTML("prev-page_tag", tagName);
       		prop.put("prev-page_num", max_count);
       	}
       	prop.put("bookmarks", count);
    
    
    	//-----------------------
    	// create folder list
    	//-----------------------
       	
       	count = 0;
       	count = recurseFolders(sb.bookmarksDB.getFolderList(isAdmin),"/",0,true,"");
       	prop.put("folderlist", count);
       	
    
       	return prop;    // return from serverObjects respond()
    }    
    
    private static void printTagList(final String id, final String tagName, final int comp, final int max, final boolean opt){    	
    	int count=0;
        bookmarksDB.Tag tag;
    	Iterator<Tag> it = null;
    	
        if (tagName.equals("")) {
        	it = sb.bookmarksDB.getTagIterator(isAdmin, comp, max);
        } else {
        	it = sb.bookmarksDB.getTagIterator(tagName, isAdmin, comp, max);
        }
       	while(it.hasNext()){
       		tag=it.next();
       		if ((!tag.getTagName().startsWith("/")) && (!tag.getTagName().equals(""))) {
       			prop.putHTML(id+"_"+count+"_name", tag.getFriendlyName());
       			prop.putHTML(id+"_"+count+"_tag", tag.getTagName());
       			prop.put(id+"_"+count+"_num", tag.size());
       			if (opt){
       				if(tagName.equals(tag.getFriendlyName())){
       					prop.put(id+"_"+count+"_selected", " selected=\"selected\"");
       				} else {
       					prop.put(id+"_"+count+"_selected", "");
       				}
       			} else {
       				// font-size is pseudo-rounded to 2 decimals
       				prop.put(id+"_"+count+"_size", Math.round((1.1+Math.log(tag.size())/4)*100)/100.);
       			}
       			count++;
       		}
       	}
       	prop.put(id, count);    	
    }
    
    private static int recurseFolders(final Iterator<String> it, String root, int count, final boolean next, final String prev){
    	String fn="";    	
    	bookmarksDB.Bookmark bookmark;
   	
    	if (next) fn = it.next().toString();    		
    	else fn = prev;

    	if(fn.equals("\uffff")) {    		
    		int i = prev.replaceAll("[^/]","").length();
    		while(i>0){
    			prop.put("folderlist_"+count+"_folder", "</ul></li>");
    			count++;
    			i--;
    		}    		
    		return count;
    	}
   
    	if(fn.startsWith(root)){
    		prop.put("folderlist_"+count+"_folder", "<li>"+fn.replaceFirst(root+"/*","")+"<ul class=\"folder\">");
    		count++;    
    		final Iterator<String> bit=sb.bookmarksDB.getBookmarksIterator(fn, isAdmin);
    		while(bit.hasNext()){
    			bookmark=sb.bookmarksDB.getBookmark(bit.next());
    			prop.put("folderlist_"+count+"_folder", "<li><a href=\""+bookmark.getUrl()+"\" title=\""+bookmark.getDescription()+"\">"+ bookmark.getTitle()+"</a></li>");
    			count++;
    		}    	
    		if(it.hasNext()){
    			count = recurseFolders(it, fn, count, true, fn);
    		}
    	} else {		
    		prop.put("folderlist_"+count+"_folder", "</ul></li>");        		
    		count++;
    		root = root.replaceAll("(/.[^/]*$)", ""); 		
    		if(root.equals("")) root = "/";    		
    		count = recurseFolders(it, root, count, false, fn);
    	} 
    	return count;
    }

    private static void publishNews(final String url, final String title, final String description, final String tagsString) {
    	// create a news message
    	final HashMap<String, String> map = new HashMap<String, String>();
    	map.put("url", url.replace(',', '|'));
    	map.put("title", title.replace(',', ' '));
    	map.put("description", description.replace(',', ' '));
    	map.put("tags", tagsString.replace(',', ' '));
    	sb.webIndex.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.webIndex.seedDB.mySeed(), yacyNewsPool.CATEGORY_BOOKMARK_ADD, map));
    }

}
