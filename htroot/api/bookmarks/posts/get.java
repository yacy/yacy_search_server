

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.BookmarksDB;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class get {
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard switchboard = (Switchboard) env;
        final boolean isAdmin=switchboard.verifyAuthentication(header);
        final serverObjects prop = new serverObjects();
        String tag = null;
        final String date;
        //String url=""; //urlfilter not yet implemented

        if (post != null && post.containsKey("tag")) {
            tag = post.get("tag");
        }
        if (post != null && post.containsKey("date")) {
            date = post.get("date");
        } else {
            date = ISO8601Formatter.FORMATTER.format();
        }

        // if an extended xml should be used
        final boolean extendedXML = (post != null && post.containsKey("extendedXML"));

        int count=0;

        Date parsedDate = null;
        try {
            parsedDate = ISO8601Formatter.FORMATTER.parse(date);
        } catch (final ParseException e) {
            parsedDate = new Date();
        }

        final List<String> bookmark_hashes = switchboard.bookmarksDB.getDate(Long.toString(parsedDate.getTime())).getBookmarkList();
        BookmarksDB.Bookmark bookmark = null;
        for (final String bookmark_hash : bookmark_hashes){
            try {
                bookmark=switchboard.bookmarksDB.getBookmark(bookmark_hash);
                if (ISO8601Formatter.FORMATTER.format(new Date(bookmark.getTimeStamp())).equals(date) &&
                        tag==null || bookmark.getTags().contains(tag) &&
                        isAdmin || bookmark.getPublic()){
                    prop.putHTML("posts_"+count+"_url", bookmark.getUrl());
                    prop.putHTML("posts_"+count+"_title", bookmark.getTitle());
                    prop.putHTML("posts_"+count+"_description", bookmark.getDescription());
                    prop.put("posts_"+count+"_md5", Digest.encodeMD5Hex(bookmark.getUrl()));
                    prop.put("posts_"+count+"_time", date);
                    prop.putHTML("posts_"+count+"_tags", bookmark.getTagsString().replaceAll(","," "));

                    // additional XML tags
                    prop.put("posts_"+count+"_isExtended",extendedXML ? "1" : "0");
                    if (extendedXML) {
                        prop.put("posts_"+count+"_isExtended_private", Boolean.toString(!bookmark.getPublic()));
                    }
                    count++;
                }
            } catch (final IOException e) {
            }
        }
        prop.put("posts", count);

        // return rewrite properties
        return prop;
    }

}
