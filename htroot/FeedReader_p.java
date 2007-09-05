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

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;
import de.anomic.xml.rssReader;
import de.anomic.yacy.yacyURL;

// test url:
// http://localhost:8080/FeedReader_p.html?url=http://www.tagesthemen.de/xml/rss2

public class FeedReader_p {
    
    public static servletProperties respond(httpHeader header, serverObjects post, serverSwitch env) {
        servletProperties prop = new servletProperties();
        
        prop.put("page", 0);
        if (post != null) {
            yacyURL url;
            try {
                url = new yacyURL((String) post.get("url"), null);
            } catch (MalformedURLException e) {
                prop.put("page", 2);
                return prop;
            }
            
            // int maxitems=Integer.parseInt(post.get("max", "0"));
            // int offset=Integer.parseInt(post.get("offset", "0")); //offset to the first displayed item
            rssReader parser = new rssReader(url.toString());

            prop.put("page_title", parser.getChannel().getTitle());
            if (parser.getChannel().getAuthor() == null) {
                prop.put("page_hasAuthor", 0);
            } else {
                prop.put("page_hasAuthor", 1);
                prop.put("page_hasAuthor_author", parser.getChannel().getAuthor());
            }
            prop.put("page_description", parser.getChannel().getDescription());

            for (int i = 0; i < parser.items(); i++) {
                rssReader.Item item = parser.getItem(i);
                prop.put("page_items_" + i + "_author", item.getAuthor());
                prop.put("page_items_" + i + "_title", item.getTitle());
                prop.put("page_items_" + i + "_link", item.getLink());
                prop.putASIS("page_items_" + i + "_description", item.getDescription());
                prop.put("page_items_" + i + "_date", item.getPubDate());
            }
            prop.put("page_items", parser.items());
            prop.put("page", 1);
        }
    
        // return rewrite properties
        return prop;
    }
}
