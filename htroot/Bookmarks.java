// Bookmarks_p.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.BookmarkHelper;
import net.yacy.data.BookmarksDB;
import net.yacy.data.ListManager;
import net.yacy.data.UserDB;
import net.yacy.data.BookmarksDB.Bookmark;
import net.yacy.data.BookmarksDB.Tag;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.peers.NewsPool;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class Bookmarks {

	private static final serverObjects prop = new serverObjects();
	private static Switchboard sb = null;
	private static UserDB.Entry user = null;
	private static boolean isAdmin = false;

	final static int SORT_ALPHA = 1;
	final static int SORT_SIZE = 2;
	final static int SHOW_ALL = -1;
	final static boolean TAGS = false;
	final static boolean FOLDERS = true;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

    	int max_count = 10;
    	int start=0;
    	int display = 0;
    	String tagName = "";
    	String username="";

    	prop.clear();
    	sb = (Switchboard) env;
    	user = sb.userDB.getUser(header);
    	isAdmin = (sb.verifyAuthentication(header) || user!= null && user.hasRight(UserDB.AccessRight.BOOKMARK_RIGHT));

    	// set user name
    	if (user != null) {
            username=user.getUserName();
        } else if(isAdmin) {
            username="admin";
        }
    	prop.putHTML("user", username);

    	//redirect to userpage
    	/*
    	if(username!="" &&(post == null || !post.containsKey("user") && !post.containsKey("mode")))
        prop.put("LOCATION", "/Bookmarks.html?user="+username);
    	*/

    	// set peer address
    	prop.put("address", sb.peers.mySeed().getPublicAddress());

    	//defaultvalues
    	if(isAdmin) {
            prop.put("mode", "1");
            prop.put("admin", "1");
            prop.put("display", "0");
        } else {
            prop.put("mode", "0");
            prop.put("admin", "0");
            prop.put("display", "0");
        }
    	prop.put("mode_edit", "0");
    	prop.put("mode_title", "");
    	prop.put("mode_description", "");
    	prop.put("mode_url", "");
    	prop.put("mode_tags", "");
    	prop.put("mode_path", "");
    	prop.put("mode_public", "1"); //1=is public
    	prop.put("mode_feed", "0"); //no newsfeed

    	if (post != null) {
            if (!isAdmin) {
                if(post.containsKey("login")){
                	prop.authenticationRequired();
                }
            } else if (post.containsKey("mode")) {
                final String mode=post.get("mode");
                if ("add".equals(mode)) {
                    prop.put("mode", "2");
                    prop.put("display", "1");
                    display = 1;
                } else if ("importxml".equals(mode)){
                    prop.put("mode", "3");
                    prop.put("display", "1");
                    display = 1;
                }
            } else if(post.containsKey("add")) { //add an Entry
                final String url=post.get("url");
                final String title=post.get("title");
                final String description=post.get("description");
                String tagsString = post.get("tags");
                String pathString = post.get("path");
                if(pathString == null || "".equals(pathString)){
                    pathString="/unsorted"; //default folder
                }
                tagsString = tagsString + "," + pathString;
                final Set<String> tags=ListManager.string2set(BookmarkHelper.cleanTagsString(tagsString));
                final BookmarksDB.Bookmark bookmark = sb.bookmarksDB.createBookmark(url, username);

                if (bookmark != null) {
                    bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_TITLE, title);
                    bookmark.setProperty(BookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);

                    if (user!=null) {
                        bookmark.setOwner(user.getUserName());
                    }

                    if ("public".equals(post.get("public"))) {
                        bookmark.setPublic(true);
                        publishNews(url, title, description, tagsString);
                    } else {
                        bookmark.setPublic(false);
                    }

                    if(post.containsKey("feed") && ("true".equals(post.get("feed")))){
                        bookmark.setFeed(true);
                    } else {
                        bookmark.setFeed(false);
                    }

                    bookmark.setTags(tags, true);
                    sb.bookmarksDB.saveBookmark(bookmark);
                }

            } else if (post.containsKey("edit")) {
                final String urlHash = post.get("edit");
                prop.put("mode", "2");
                prop.put("display", "1");
                display = 1;
                ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
                if (urlHash.isEmpty()) {
                    prop.put("mode_edit", "0"); // create mode
                    prop.putHTML("mode_title", post.get("title"));
                    prop.putHTML("mode_description", post.get("description"));
                    prop.putHTML("mode_url", post.get("url"));
                    prop.putHTML("mode_tags", post.get("tags"));
                    prop.putHTML("mode_path", post.get("path"));
                    prop.put("mode_public", "0");
                    prop.put("mode_feed", "0");
                } else {
                    BookmarksDB.Bookmark bookmark = null;
                    try {
                        bookmark = sb.bookmarksDB.getBookmark(urlHash);
                    } catch (final IOException e1) {
                    }
                    if (bookmark == null) {
                        // try to get the bookmark from the LURL database
                        final URIMetadataNode urlentry = sb.index.fulltext().getMetadata(ASCII.getBytes(urlHash));
                        if (urlentry != null) try {
                            final Document document = Document.mergeDocuments(urlentry.url(), null, sb.loader.loadDocuments(sb.loader.request(urlentry.url(), true, false), CacheStrategy.IFEXIST, Integer.MAX_VALUE, null, agent));
                            prop.put("mode_edit", "0"); // create mode
                            prop.put("mode_url", urlentry.url().toNormalform(false));
                            prop.putHTML("mode_title", urlentry.dc_title());
                            prop.putHTML("mode_description", (document == null) ? urlentry.dc_title(): document.dc_title());
                            prop.putHTML("mode_author", urlentry.dc_creator());
                            prop.putHTML("mode_tags", (document == null) ? urlentry.dc_subject() : document.dc_subject(','));
                            prop.putHTML("mode_path","");
                            prop.put("mode_public", "0");
                            prop.put("mode_feed", "0"); //TODO: check if it IS a feed
                        } catch (final IOException e) {ConcurrentLog.logException(e);} catch (final Parser.Failure e) {ConcurrentLog.logException(e);}
                    } else {
                        // get from the bookmark database
                        prop.put("mode_edit", "1"); // edit mode
                        prop.putHTML("mode_title", bookmark.getTitle());
                        prop.putHTML("mode_description", bookmark.getDescription());
                        prop.put("mode_url", bookmark.getUrl()); //TODO: XSS protection - how is this stored?
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
                final boolean isPublic = "public".equals(post.get("public"));

                String tags = post.get("tags");
                if("".equals(tags)){
                    tags="unsorted";
                }

                ConcurrentLog.info("BOOKMARKS", "Trying to import bookmarks from HTML-file");

                try {
                    final File file = new File(post.get("htmlfile"));
                    BookmarkHelper.importFromBookmarks(sb.bookmarksDB, new DigestURL(file), post.get("htmlfile$file"), tags, isPublic);
                } catch (final MalformedURLException e) {}

                ConcurrentLog.info("BOOKMARKS", "success!!");

            } else if (post.containsKey("xmlfile")) {

                final boolean isPublic = "public".equals(post.get("public"));
                BookmarkHelper.importFromXML(sb.bookmarksDB, post.get("xmlfile$file"), isPublic);

            } else if (post.containsKey("delete")) {

                final String urlHash=post.get("delete");
                sb.bookmarksDB.removeBookmark(urlHash);

            }

            if (post.containsKey("tag")) {
                tagName = post.get("tag");
            }

            if (post.containsKey("start")) {
                start = post.getInt("start", 0);
            }

            if (post.containsKey("num")) {
                max_count = post.getInt("num", 10);
            }
    	} // END if(post != null)

    	if (display == 0) {

	    	//-----------------------
	    	// create tag list
	    	//-----------------------
	    	printTagList("taglist", tagName, SORT_SIZE, 25, false);
	    	printTagList("optlist", tagName, SORT_ALPHA, SHOW_ALL, true);

	    	//-----------------------
	    	// create bookmark list
	    	//-----------------------
	    	int count = 0;
	        Iterator<String> it = null;

                prop.put("display_num-bookmarks", sb.bookmarksDB.bookmarksSize());

	       	if(!"".equals(tagName)){
                    prop.put("display_selected", "");
                    it = sb.bookmarksDB.getBookmarksIterator(tagName, isAdmin);
	       	} else {
                    prop.put("display_selected", " selected=\"selected\"");
                    it = sb.bookmarksDB.getBookmarksIterator(isAdmin);
	       	}

	       	//skip the first entries (display next page)
	       	count = 0;
	       	while(count < start && it.hasNext()){
                    it.next();
                    count++;
	       	}

	       	count = 0;
	       	while(count < max_count && it.hasNext()) {
                    Bookmark bookmark = null;
                    try {
                        bookmark = sb.bookmarksDB.getBookmark(it.next());
                    } catch (final IOException e) {
                    }

                    if (bookmark != null){
                        if (bookmark.getFeed() && isAdmin) {
                            prop.put("display_bookmarks_"+count+"_link", "/FeedReader_p.html?url="+bookmark.getUrl());
                        } else {
                            prop.put("display_bookmarks_"+count+"_link",bookmark.getUrl());
                        }
                        prop.putHTML("display_bookmarks_"+count+"_title", bookmark.getTitle());
                        prop.putHTML("display_bookmarks_"+count+"_description", bookmark.getDescription());
                        prop.put("display_bookmarks_"+count+"_date", ISO8601Formatter.FORMATTER.format(new Date(bookmark.getTimeStamp())));
                        prop.put("display_bookmarks_"+count+"_rfc822date", HeaderFramework.formatRFC1123(new Date(bookmark.getTimeStamp())));
                        prop.put("display_bookmarks_"+count+"_public", (bookmark.getPublic() ? "1" : "0"));

                        //List Tags.
                        final Set<String> tags = bookmark.getTags();
                        int tagCount=0;
                        for (final String tag : tags) {
                            if (tag.length() > 0 && tag.charAt(0) != '/') {
                                prop.putHTML("display_bookmarks_" + count + "_tags_" + tagCount + "_tag", tag);
                                tagCount++;
                            }
                        }
                        prop.put("display_bookmarks_"+count+"_tags", tagCount);
                        prop.put("display_bookmarks_"+count+"_hash", bookmark.getUrlHash());
                        count++;
                    }
	       	}

	       	prop.putHTML("display_tag", tagName);
	       	prop.put("display_start", start);

	       	if (it.hasNext()) {
                    prop.put("display_next-page", "1");
                    prop.put("display_next-page_start", start+max_count);
                    prop.putHTML("display_next-page_tag", tagName);
                    prop.put("display_next-page_num", max_count);
	       	}
	       	if (start >= max_count) {
                    start = start-max_count;
                    if (start <0){
                        start = 0;
                    }
                    prop.put("display_prev-page", "1");
                    prop.put("display_prev-page_start", start);
                    prop.putHTML("display_prev-page_tag", tagName);
                    prop.put("display_prev-page_num", max_count);
	       	}
	       	prop.put("display_bookmarks", count);


	    	//-----------------------
	    	// create folder list
	    	//-----------------------

	       	count = 0;
	       	count = recurseFolders(BookmarkHelper.getFolderList("/", sb.bookmarksDB.getTagIterator(isAdmin)), "/", 0, true, "");
	       	prop.put("display_folderlist", count);
    	}
       	return prop;    // return from serverObjects respond()
    }

    private static void printTagList(final String id, final String tagName, final int comp, final int max, final boolean opt){

    	if (sb.bookmarksDB == null) {
    	    prop.put("display_"+id, 0);
    	    return;
    	}

    	int count=0;
        final Iterator<BookmarksDB.Tag> it;

        if ("".equals(tagName)) {
            it = sb.bookmarksDB.getTagIterator(isAdmin, comp, max);
        } else {
            it = sb.bookmarksDB.getTagIterator(tagName, isAdmin, comp, max);
        }

       	while(it.hasNext()){
            final Tag tag = it.next();
            if (!tag.getTagName().startsWith("/") && !"".equals(tag.getTagName())) {
                prop.putHTML("display_"+id+"_"+count+"_name", tag.getFriendlyName());
                prop.putHTML("display_"+id+"_"+count+"_tag", tag.getTagName());
                prop.put("display_"+id+"_"+count+"_num", tag.size());
                if (opt) {
                    if (tag.getFriendlyName().equals(tagName)){
                        prop.put("display_"+id+"_"+count+"_selected", " selected=\"selected\"");
                    } else {
                        prop.put("display_"+id+"_"+count+"_selected", "");
                    }
                } else {
                    // font-size is pseudo-rounded to 2 decimals
                    prop.put("display_"+id+"_"+count+"_size", Math.round((1.1f+Math.log(tag.size())/4f)*100.0f)/100.0f);
                }
                count++;
            }
       	}
       	prop.put("display_"+id, count);
    }

    private static int recurseFolders(final Iterator<String> it, String root, int count, final boolean next, final String prev) {

        final String fn = (next) ? it.next() : prev;

    	if("\uffff".equals(fn)) {
            int i = prev.replaceAll("[^/]","").length();
            while( i>0 ){
                prop.put("display_folderlist_"+count+"_folder", "</ul></li>");
                count++;
                i--;
            }
            return count;
    	}

    	if (fn.startsWith(("/".equals(root) ? root : root + "/"))) {
            prop.put("display_folderlist_"+count+"_folder", "<li>"+fn.replaceFirst(root+"/*","")+"<ul class=\"folder\">");
            count++;
            final Iterator<String> bit = sb.bookmarksDB.getBookmarksIterator(fn, isAdmin);

            while (bit.hasNext()) {
                Bookmark bookmark = null;
                try {
                    bookmark = sb.bookmarksDB.getBookmark(bit.next());
                } catch (final IOException e) {
                }
        		if (bookmark == null) break;
                prop.put("display_folderlist_" + count + "_folder", "<li><a href=\"" + bookmark.getUrl() + "\" title=\"" + bookmark.getDescription() + "\">" + bookmark.getTitle() + "</a></li>");
                count++;
            }

            if (it.hasNext()) {
                count = recurseFolders(it, fn, count, true, fn);
            }

    	} else {
            prop.put("display_folderlist_"+count+"_folder", "</ul></li>");
            count++;
            root = root.replaceAll("(/.[^/]*$)", "");
            if ("".equals(root)) {
                root = "/";
            }
            count = recurseFolders(it, root, count, false, fn);
    	}
    	return count;
    }

    private static void publishNews(final String url, final String title, final String description, final String tagsString) {
    	// create a news message
        if (sb.isRobinsonMode()) return;
    	final Map<String, String> map = new HashMap<String, String>();
    	map.put("url", url.replace(',', '|'));
    	map.put("title", title.replace(',', ' '));
    	map.put("description", description.replace(',', ' '));
    	map.put("tags", tagsString.replace(',', ' '));
    	sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_BOOKMARK_ADD, map);
    }

}
