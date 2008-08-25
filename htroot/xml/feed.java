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

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;

public class feed {
 
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        // insert default values
        final serverObjects prop = new serverObjects();
        prop.put("channel_title", "");
        prop.put("channel_description", "");
        prop.put("channel_pubDate", "");
        prop.put("item", "0");

        if ((post == null) || (env == null)) return prop;
        final boolean authorized = sb.verifyAuthentication(header, false);

        final String channelNames = post.get("set");
        if (channelNames == null) return prop;
        final String[] channels = channelNames.split(","); // several channel names can be given and separated by comma

        int messageCount = 0;
        int messageMaxCount = Math.min(post.getInt("count", 100), 1000);

        RSSFeed feed;
        channelIteration: for (int channelIndex = 0; channelIndex < channels.length; channelIndex++) {
            // prevent that unauthorized access to this servlet get results from private data
            if ((!authorized) && (RSSFeed.privateChannels.contains(channels[channelIndex]))) continue channelIteration; // allow only public channels if not authorized

            // read the channel
            feed = RSSFeed.channels(channels[channelIndex]);
            if ((feed == null) || (feed.size() == 0)) continue channelIteration;

            RSSMessage message = feed.getChannel();
            if (message != null) {
                prop.putHTML("channel_title", message.getTitle(), true);
                prop.putHTML("channel_description", message.getDescription(), true);
                prop.put("channel_pubDate", message.getPubDate());
            }
            while ((messageMaxCount > 0) && (feed.size() > 0)) {
                message = feed.pollMessage();
                if (message == null) continue;

                // create RSS entry
                prop.putHTML("item_" + messageCount + "_title", channels[channelIndex] + ": " + message.getTitle(), true);
                prop.putHTML("item_" + messageCount + "_description", message.getDescription(), true);
                prop.putHTML("item_" + messageCount + "_link", message.getLink(), true);
                prop.put("item_" + messageCount + "_pubDate", message.getPubDate());
                prop.put("item_" + messageCount + "_guid", message.getGuid());
                messageCount++;
                messageMaxCount--;
            }
            if (messageMaxCount == 0) break channelIteration;
        }
        prop.put("item", messageCount);

        // return rewrite properties
        return prop;
    }
 
}
