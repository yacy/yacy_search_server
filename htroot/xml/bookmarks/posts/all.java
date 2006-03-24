// /xml/bookmarks/posts/all.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// last major change: 27.12.2005
// this file is contributed by Alexander Schier
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
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT
package xml.bookmarks.posts;
import java.util.Date;
import java.util.Iterator;

import de.anomic.data.bookmarksDB;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class all {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        boolean isAdmin=switchboard.verifyAuthentication(header, true);
        serverObjects prop = new serverObjects();
        Iterator it;
        if(post != null && post.containsKey("tag")){
            it=switchboard.bookmarksDB.getBookmarksIterator((String) post.get("tag"), isAdmin);
        }else{
            it=switchboard.bookmarksDB.getBookmarksIterator(isAdmin);
        }
        int count=0;
        bookmarksDB.Bookmark bookmark;
        Date date;
        while(it.hasNext()){
            bookmark=switchboard.bookmarksDB.getBookmark((String) it.next());
            prop.putNoHTML("posts_"+count+"_url", bookmark.getUrl());
            prop.putNoHTML("posts_"+count+"_title", bookmark.getTitle());
            prop.putNoHTML("posts_"+count+"_description", bookmark.getDescription());
            prop.putNoHTML("posts_"+count+"_md5", serverCodings.encodeMD5Hex(bookmark.getUrl()));
            date=new Date(bookmark.getTimeStamp());
            prop.putNoHTML("posts_"+count+"_time", bookmarksDB.dateToiso8601(date));
            prop.putNoHTML("posts_"+count+"_tags", bookmark.getTagsString().replaceAll(","," "));
            count++;
        }
        prop.put("posts", count);

        // return rewrite properties
        return prop;
    }
    
}



