//FeedReader_p.java
//------------
// part of YACY
//
// (C) 2007 Alexander Schier
//
// last change: $LastChangedDate:  $ by $LastChangedBy: $
// $LastChangedRevision: $
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

import java.net.MalformedURLException;

import de.anomic.http.httpRequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;
import de.anomic.xml.RSSReader;
import de.anomic.yacy.yacyURL;

// test url:
// http://localhost:8080/FeedReader_p.html?url=http://www.tagesthemen.de/xml/rss2

public class FeedReader_p {
    
    public static servletProperties respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final servletProperties prop = new servletProperties();
        
        prop.put("page", "0");
        if (post != null) {
            yacyURL url;
            try {
                url = new yacyURL(post.get("url"), null);
            } catch (final MalformedURLException e) {
                prop.put("page", "2");
                return prop;
            }
            
            // int maxitems=Integer.parseInt(post.get("max", "0"));
            // int offset=Integer.parseInt(post.get("offset", "0")); //offset to the first displayed item
            final RSSFeed feed = new RSSReader(url.toString()).getFeed();

            prop.putHTML("page_title", feed.getChannel().getTitle());
            if (feed.getChannel().getAuthor() == null) {
                prop.put("page_hasAuthor", "0");
            } else {
                prop.put("page_hasAuthor", "1");
                prop.putHTML("page_hasAuthor_author", feed.getChannel().getAuthor());
            }
            prop.putHTML("page_description", feed.getChannel().getDescription());

            int i = 0;
            for (final RSSMessage item: feed) {
                prop.putHTML("page_items_" + i + "_author", item.getAuthor());
                prop.putHTML("page_items_" + i + "_title", item.getTitle());
                prop.put("page_items_" + i + "_link", item.getLink());
                prop.put("page_items_" + i + "_description", item.getDescription());
                prop.put("page_items_" + i + "_date", item.getPubDate());
                i++;
            }
            prop.put("page_items", feed.size());
            prop.put("page", "1");
        }
    
        // return rewrite properties
        return prop;
    }
}
