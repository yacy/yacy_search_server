// feed.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 25.04.2008 on http://yacy.net
// 
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package xml;

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;

public class feed {
 
 public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
     //plasmaSwitchboard sb = (plasmaSwitchboard) env;
     
     // insert default values
     serverObjects prop = new serverObjects();
     prop.put("channel_title", "");
     prop.put("channel_description", "");
     prop.put("channel_pubDate", "");
     prop.put("item", "0");
     
     if ((post == null) || (env == null)) return prop;
     //boolean authorized = sb.adminAuthenticated(header) >= 2;
     
     String channelName = post.get("set");
     if (channelName == null) return prop;
     
     RSSFeed feed = RSSFeed.channels(channelName);
     if ((feed == null) || (feed.size() == 0)) return prop;
     int count = post.getInt("count", 100);
     
     RSSMessage message = feed.getChannel();
     if (message != null) {
         prop.put("channel_title", message.getTitle());
         prop.put("channel_description", message.getDescription());
         prop.put("channel_pubDate", message.getPubDate());
     }
     int c = 0;
     while ((count > 0) && (feed.size() > 0)) {
         message = feed.pollMessage();
         if (message == null) continue;
         
         // create RSS entry
         prop.putHTML("item_" + c + "_title", message.getTitle());
         prop.putHTML("item_" + c + "_description", message.getDescription());
         prop.putHTML("item_" + c + "_link", message.getLink());
         prop.put("item_" + c + "_pubDate", message.getPubDate());
         prop.put("item_" + c + "_guid", message.getGuid());
         c++;
         count--;
     }
     prop.put("item", c);

     // return rewrite properties
     return prop;
 }
 
}
