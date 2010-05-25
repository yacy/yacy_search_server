//FeedReader_p.java
//------------
// part of YACY
//
// (C) 2007 Alexander Schier
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

import java.io.IOException;
import java.net.MalformedURLException;

import net.yacy.cora.document.Hit;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSReader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import de.anomic.http.server.RequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

// test url:
// http://localhost:8080/FeedReader_p.html?url=http://www.tagesthemen.de/xml/rss2

public class FeedReader_p {
    
    public static servletProperties respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        
        prop.put("page", "0");
        if (post != null) {
            DigestURI url;
            try {
                url = new DigestURI(post.get("url"), null);
            } catch (final MalformedURLException e) {
                prop.put("page", "2");
                return prop;
            }
            
            // int maxitems=Integer.parseInt(post.get("max", "0"));
            // int offset=Integer.parseInt(post.get("offset", "0")); //offset to the first displayed item
            try {
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
                for (final Hit item: feed) {
                    prop.putHTML("page_items_" + i + "_author", item.getAuthor());
                    prop.putHTML("page_items_" + i + "_title", item.getTitle());
                    prop.putHTML("page_items_" + i + "_link", item.getLink());
                    prop.putHTML("page_items_" + i + "_description", item.getDescription());
                    prop.putHTML("page_items_" + i + "_date", item.getPubDate());
                    i++;
                }
                prop.put("page_items", feed.size());
                prop.put("page", "1");
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    
        // return rewrite properties
        return prop;
    }
}
