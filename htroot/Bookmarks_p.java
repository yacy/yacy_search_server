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

import java.util.Iterator;
import java.util.Vector;

import de.anomic.data.bookmarksDB;
import de.anomic.data.bookmarksDB.Tag;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Bookmarks_p {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
    serverObjects prop = new serverObjects();
    plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
    int MAX_COUNT=10; //TODO: Changeable per Interface
    String tagName="";
    int start=0;
    
    //defaultvalues
    prop.put("edit", 0);
    prop.put("title", "");
    prop.put("description", "");
    prop.put("url", "");
    prop.put("tags", "");
    prop.put("public", 1); //1=is public
    if(post != null){
        if(post.containsKey("add")){ //add an Entry
            String url=(String) post.get("url");
            String title=(String) post.get("title");
            String description=(String) post.get("description");
            String tagsString = (String)post.get("tags");
            if(tagsString.equals("")){
                tagsString="unsorted"; //defaulttag
            }
            Vector tags=new Vector();
            String[] tagsArray=tagsString.split(",");
            for(int i=0;i<tagsArray.length; i++){
                tags.add(tagsArray[i].trim());
            }
        
            bookmarksDB.Bookmark bookmark = switchboard.bookmarksDB.createBookmark(url);
            if(bookmark != null){
                bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_TITLE, title);
                bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
                bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_PUBLIC, (String) post.get("public"));
                bookmark.setTags(tags);
                bookmark.setBookmarksTable();
            }else{
                //ERROR
            }
        }
        if(post.containsKey("edit")){
            String urlHash=(String) post.get("edit");
            if (urlHash.length() == 0) {
                prop.put("edit", 0); // create mode
                prop.put("title", (String) post.get("title"));
                prop.put("description", (String) post.get("description"));
                prop.put("url", (String) post.get("url"));
                prop.put("tags", (String) post.get("tags"));
                prop.put("public", 0);
            } else {
                    bookmarksDB.Bookmark bookmark = switchboard.bookmarksDB.getBookmark(urlHash);
                    prop.put("edit", 1); // edit mode
                    prop.put("title", bookmark.getTitle());
                    prop.put("description", bookmark.getDescription());
                    prop.put("url", bookmark.getUrl());
                    prop.put("tags", bookmark.getTags());
                    if (bookmark.getPublic()) {
                        prop.put("public", 1);
                    } else {
                        prop.put("public", 0);
                    }
            }
        }
        if(post.containsKey("delete")){
            String urlHash=(String) post.get("delete");
            switchboard.bookmarksDB.removeBookmark(urlHash);
        }
        if(post.containsKey("tag")){
            tagName=(String) post.get("tag");
        }
        if(post.containsKey("start")){
            start=Integer.parseInt((String) post.get("start"));
        }
    }
    Iterator it=switchboard.bookmarksDB.tagIterator(true);
    int count=0;
    bookmarksDB.Tag tag;
    while(it.hasNext()){
        tag=(Tag) it.next();
        prop.put("taglist_"+count+"_name", tag.getFriendlyName());
        prop.put("taglist_"+count+"_tag", tag.getTagName());
        count++;
    }
    prop.put("taglist", count);
    count=0;
    if(!tagName.equals("")){
        it=switchboard.bookmarksDB.getBookmarksIterator(tagName);
    }else{
        it=switchboard.bookmarksDB.getBookmarksIterator();
    }
    bookmarksDB.Bookmark bookmark;
    //skip the first entries (display next page)
    count=0;
    while(count < start && it.hasNext()){
        it.next();
        count++;
    }
    count=0;
    Vector tags;
    Iterator tagsIt;
    int tagCount;
    while(count<MAX_COUNT && it.hasNext()){
        bookmark=switchboard.bookmarksDB.getBookmark((String)it.next());
        if(bookmark!=null){
            prop.put("bookmarks_"+count+"_link", bookmark.getUrl());
            prop.put("bookmarks_"+count+"_title", bookmark.getTitle());
            prop.put("bookmarks_"+count+"_description", bookmark.getDescription());
            prop.put("bookmarks_"+count+"_public", (bookmark.getPublic()? 1:0));
            
            //List Tags.
            tags=bookmark.getTagsVector();
            tagsIt=tags.iterator();
            tagCount=0;
            while(tagsIt.hasNext()){
            	prop.put("bookmarks_"+count+"_tags_"+tagCount+"_tag", tagsIt.next());
            	tagCount++;
            }
            prop.put("bookmarks_"+count+"_tags", tagCount);
            
            prop.put("bookmarks_"+count+"_hash", bookmark.getUrlHash());
            count++;
        }
    }
    if(it.hasNext()){
        prop.put("next-page", 1);
        prop.put("next-page_start", start+10);
        prop.put("next-page_tag", tagName);
    }
    prop.put("bookmarks", count);
    return prop;
    }

}
