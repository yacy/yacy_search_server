// Bookmarks_p.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
// last change: 26.12.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import java.lang.Math;

import de.anomic.data.bookmarksDB;
import de.anomic.data.listManager;
import de.anomic.data.userDB;
import de.anomic.data.bookmarksDB.Tag;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.index.indexURLEntry;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacyURL;

public class Bookmarks {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
    serverObjects prop = new serverObjects();
    plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
    int max_count=10;
    String tagName="";
    int start=0;
    userDB.Entry user=switchboard.userDB.getUser(header);
    boolean isAdmin=(switchboard.verifyAuthentication(header, true) || user!= null && user.hasRight(userDB.Entry.BOOKMARK_RIGHT));
    String username="";
    if(user != null) username=user.getUserName();
    else if(isAdmin) username="admin";    
    
    //redirect to userpage
    /*if(username!="" &&(post == null || !post.containsKey("user") && !post.containsKey("mode")))
        prop.put("LOCATION", "/Bookmarks.html?user="+username);*/
    prop.put("user", username);
    
    // set peer address
    final String address = yacyCore.seedDB.mySeed().getPublicAddress();
    prop.put("address", address);
    
    //defaultvalues
    prop.put("mode", "0");
    if(isAdmin){
        prop.put("mode", "1");
    }
    prop.put("mode_edit", "0");
    prop.put("mode_title", "");
    prop.put("mode_description", "");
    prop.put("mode_url", "");
    prop.put("mode_tags", "");
    prop.put("mode_public", "1"); //1=is public
    prop.put("mode_feed", "0"); //no newsfeed
    if(post != null){
        
        if(!isAdmin){
            if(post.containsKey("login")){
                prop.put("AUTHENTICATE","admin log-in");
            }
        }else if(post.containsKey("mode")){
            String mode=(String) post.get("mode");
            if(mode.equals("add")){
            	prop.put("mode", "2");
            }else if(mode.equals("importxml")){
            	prop.put("mode", "3");
            }else if(mode.equals("importbookmarks")){
                prop.put("mode", "4");
            }
        }else if(post.containsKey("add")){ //add an Entry
            String url=(String) post.get("url");
            String title=(String) post.get("title");
            String description=(String) post.get("description");
            String tagsString = (String)post.get("tags");
            if(tagsString.equals("")){
                tagsString="unsorted"; //default tag
            }
            Set tags=listManager.string2set(tagsString.replaceAll(",\\s+", ",")); // space characters following a comma are removed
        
            bookmarksDB.Bookmark bookmark = switchboard.bookmarksDB.createBookmark(url, username);
            if(bookmark != null){
                bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_TITLE, title);
                bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
                if(user!=null) bookmark.setOwner(user.getUserName());
                
                if(((String) post.get("public")).equals("public")){
                    bookmark.setPublic(true);
                    
                    publishNews(url, title, description, tagsString);
                }else{
                    bookmark.setPublic(false);
                }
                if(post.containsKey("feed") && ((String) post.get("feed")).equals("feed")){
                	bookmark.setFeed(true);
                }else{
                	bookmark.setFeed(false);
                }
                bookmark.setTags(tags, true);
                switchboard.bookmarksDB.saveBookmark(bookmark);
                
                
            }else{
                //ERROR
            }
        }else if(post.containsKey("edit")){
            String urlHash=(String) post.get("edit");
            prop.put("mode", "2");
            if (urlHash.length() == 0) {
                prop.put("mode_edit", "0"); // create mode
                prop.putHTML("mode_title", (String) post.get("title"));
                prop.putHTML("mode_description", (String) post.get("description"));
                prop.put("mode_url", (String) post.get("url"));
                prop.putHTML("mode_tags", (String) post.get("tags"));
                prop.put("mode_public", "0");
                prop.put("mode_feed", "0");
            } else {
                    bookmarksDB.Bookmark bookmark = switchboard.bookmarksDB.getBookmark(urlHash);
                    if (bookmark == null) {
                        // try to get the bookmark from the LURL database
                        indexURLEntry urlentry = switchboard.wordIndex.loadedURL.load(urlHash, null, 0);
                        plasmaParserDocument document = null;
                        if (urlentry != null) {
                            indexURLEntry.Components comp = urlentry.comp();
                            document = plasmaSnippetCache.retrieveDocument(comp.url(), true, 5000, true);
                            prop.put("mode_edit", "0"); // create mode
                            prop.put("mode_url", comp.url().toNormalform(false, true));
                            prop.putHTML("mode_title", comp.title());
                            prop.putHTML("mode_description", (document == null) ? comp.title(): document.dc_title());
                            prop.putHTML("mode_author", comp.author());
                            prop.putHTML("mode_tags", (document == null) ? comp.tags() : document.dc_subject(','));
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
        }else if(post.containsKey("bookmarksfile")){
            boolean isPublic=false;
            if(((String) post.get("public")).equals("public")){
                isPublic=true;
            }
            String tags=(String) post.get("tags");
            if(tags.equals("")){
                tags="unsorted";
            }
            try {
                File file=new File((String)post.get("bookmarksfile"));
                switchboard.bookmarksDB.importFromBookmarks(new yacyURL(file) , post.get("bookmarksfile$file"), tags, isPublic);
            } catch (MalformedURLException e) {}
            
        }else if(post.containsKey("xmlfile")){
        	boolean isPublic=false;
        	if(((String) post.get("public")).equals("public")){
            	isPublic=true;
            }
        	switchboard.bookmarksDB.importFromXML(post.get("xmlfile$file"), isPublic);
        }else if(post.containsKey("delete")){
            String urlHash=(String) post.get("delete");
            switchboard.bookmarksDB.removeBookmark(urlHash);
        }
        
        if(post.containsKey("tag")){
            tagName=(String) post.get("tag");
        }
        if(post.containsKey("start")){
            start=Integer.parseInt((String) post.get("start"));
        }
        if(post.containsKey("num")){
            max_count=Integer.parseInt((String) post.get("num"));
        }
    }
    // only list tags according to current tag-selection
    Iterator it = null;
        if (tagName.equals("")) {
        	it = switchboard.bookmarksDB.getTagIterator(isAdmin);
        } else {
        	it = switchboard.bookmarksDB.getTagIterator(tagName, isAdmin);
        }
    
    // Iterator it=switchboard.bookmarksDB.getTagIterator(isAdmin);
    int count=0;
    bookmarksDB.Tag tag;
    prop.put("num-bookmarks", switchboard.bookmarksDB.bookmarksSize());
    while(it.hasNext()){
        tag=(Tag) it.next();
        prop.putHTML("taglist_"+count+"_name", tag.getFriendlyName());
        prop.putHTML("taglist_"+count+"_tag", tag.getTagName());
        prop.put("taglist_"+count+"_num", tag.size());
        prop.put("taglist_"+count+"_size", Math.round((1.1+Math.log(tag.size())/4)*100)/100.); // font-size is pseudo-rounded to 2 decimals
        if(tagName.equals(tag.getFriendlyName())){
        	prop.put("taglist_"+count+"_selected", " selected=\"selected\"");
        } else {
        	prop.put("taglist_"+count+"_selected", "");
        };
        	
        count++;
    }
    prop.put("taglist", count);
    count=0;
    if(!tagName.equals("")){
    	prop.put("selected", "");
        it=switchboard.bookmarksDB.getBookmarksIterator(tagName, isAdmin);
    }else{
    	prop.put("selected", " selected=\"selected\"");
    	it=switchboard.bookmarksDB.getBookmarksIterator(isAdmin);
    }
    bookmarksDB.Bookmark bookmark;
    //skip the first entries (display next page)
    count=0;
    while(count < start && it.hasNext()){
        it.next();
        count++;
    }
    count=0;
    Set<String> tags;
    Iterator<String> tagsIt;
    int tagCount;
    while(count<max_count && it.hasNext()){
        bookmark=switchboard.bookmarksDB.getBookmark((String)it.next());
        if(bookmark!=null){
        	if(bookmark.getFeed() && isAdmin)
        		prop.put("bookmarks_"+count+"_link", "/FeedReader_p.html?url="+bookmark.getUrl());
        	else
        		prop.put("bookmarks_"+count+"_link",bookmark.getUrl());
            prop.putHTML("bookmarks_"+count+"_title", bookmark.getTitle());
            prop.putHTML("bookmarks_"+count+"_description", bookmark.getDescription());
            prop.put("bookmarks_"+count+"_date", serverDate.formatISO8601(new Date(bookmark.getTimeStamp())));
            prop.put("bookmarks_"+count+"_rfc822date", httpc.dateString(new Date(bookmark.getTimeStamp())));
            prop.put("bookmarks_"+count+"_public", (bookmark.getPublic() ? "1" : "0"));
            
            //List Tags.
            tags=bookmark.getTags();
            tagsIt=tags.iterator();
            tagCount=0;
            while (tagsIt.hasNext()) {
            	prop.put("bookmarks_"+count+"_tags_"+tagCount+"_tag", tagsIt.next());
            	tagCount++;
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
    return prop;
    }

    private static void publishNews(String url, String title, String description, String tagsString) {
        // create a news message
        HashMap map = new HashMap();
        map.put("url", url.replace(',', '|'));
        map.put("title", title.replace(',', ' '));
        map.put("description", description.replace(',', ' '));
        map.put("tags", tagsString.replace(',', ' '));
        yacyCore.newsPool.publishMyNews(yacyNewsRecord.newRecord(yacyNewsPool.CATEGORY_BOOKMARK_ADD, map));
    }

}
