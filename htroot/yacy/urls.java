// urls.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.08.2007 on http://yacy.net
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

import java.io.IOException;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlEntry;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNetwork;

public class urls {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        // insert default values
        serverObjects prop = new serverObjects();
        prop.put("iam", yacyCore.seedDB.mySeed().hash);
        prop.put("response", "rejected - insufficient call parameters");
        prop.put("channel_title", "");
        prop.put("channel_description", "");
        prop.put("channel_pubDate", "");
        prop.put("item", "0");
        
        if ((post == null) || (env == null)) return prop;
        if (!yacyNetwork.authentifyRequest(post, env)) return prop;
        
        if (post.get("call", "").equals("remotecrawl")) {
            // perform a remote crawl url handover
            int stackType = plasmaCrawlNURL.STACK_TYPE_LIMIT;
            //int stackType = plasmaCrawlNURL.STACK_TYPE_CORE;
            int count = Math.min(100, post.getInt("count", 0));
            int c = 0;
            plasmaCrawlEntry entry;
            while ((count > 0) && (sb.crawlQueues.noticeURL.stackSize(stackType) > 0)) {
                try {
                    entry = sb.crawlQueues.noticeURL.pop(stackType, false);
                } catch (IOException e) {
                    break;
                }
                if (entry == null) break;
                // place url to notice-url db
                sb.crawlQueues.delegatedURL.push(sb.crawlQueues.delegatedURL.newEntry(entry.url(), "client=____________"));
                // create RSS entry
                prop.put("item_" + c + "_title", "");
                prop.putHTML("item_" + c + "_link", entry.url().toNormalform(true, false));
                prop.putHTML("item_" + c + "_description", entry.name());
                prop.put("item_" + c + "_author", "");
                prop.put("item_" + c + "_pubDate", serverDate.shortSecondTime(entry.appdate()));
                prop.put("item_" + c + "_guid", entry.url().hash());
                c++;
                count--;
            }
            prop.put("item", c);
            prop.putHTML("response", "ok");
        }

        // return rewrite properties
        return prop;
    }
    
}
/*
from http://88.64.186.183:9999/yacy/urls.xml?count=10&call=remotecrawl

<?xml version="1.0"?>

<!-- this is not exactly rss format, but similar -->
<rss>

<!-- YaCy standard response header -->
<yacy version="0.5540423">
<iam>c_32kgI-4HTE</iam>
<uptime>3226</uptime>
<mytime>20071128030353</mytime>
<response>ok</response>
</yacy>

<!-- rss standard channel -->
<channel>
<title></title>
<description></description>
<pubDate></pubDate>
<!-- urll items -->
<item>
<title></title>
<link>http://publish.vx.roo.com/australian/ithomepagemini/</link>
<description>sub</description>
<author></author>
<pubDate>20071126173629</pubDate>
<guid>mlD2rBhnfuoY</guid>

</item>
<item>
<title></title>
<link>http://www.news.com.au/story/0%2C23599%2C22835669-2%2C00.html</link>
<description></description>
<author></author>
<pubDate>20071128014306</pubDate>
<guid>qT1GjNRe_5SQ</guid>
</item>
<item>
<title></title>
<link>http://www.news.com.au/perthnow/story/0%2C21598%2C22835663-2761%2C00.html</link>
<description>Driver injured: Willagee crash witnesses sought</description>

<author></author>
<pubDate>20071128014306</pubDate>
<guid>yGMa4uRe_5SQ</guid>
</item>
<item>
<title></title>
<link>http://www.news.com.au/travel/story/0%2C26058%2C22835185-5014090%2C00.html</link>
<description></description>
<author></author>
<pubDate>20071128014306</pubDate>
<guid>qfob36Re_5SQ</guid>
</item>

<item>
<title></title>
<link>http://www.news.com.au/story/0%2C23599%2C22835311-421%2C00.html</link>
<description></description>
<author></author>
<pubDate>20071128014306</pubDate>
<guid>YBLVBNRe_5SQ</guid>
</item>
<item>
<title></title>
<link>http://www.thirdwayblog.com/wp-content/uploads/</link>
<description>sub</description>

<author></author>
<pubDate>20071128010343</pubDate>
<guid>9rnz2MUqGq6Z</guid>
</item>
<item>
<title></title>
<link>http://www.parliament.gr/kouselas/koino_dra/koino_docs/</link>
<description>sub</description>
<author></author>
<pubDate>20071128010343</pubDate>
<guid>hSTvg-u6LxcB</guid>

</item>
<item>
<title></title>
<link>http://upload.wikimedia.org/wikipedia/el/f/f1/</link>
<description>sub</description>
<author></author>
<pubDate>20071128010343</pubDate>
<guid>F-3WVJBs-F4R</guid>
</item>
<item>
<title></title>
<link>http://www.logiprint.nl/nl/Briefpapier_drukken_Eindhoven.html</link>
<description>Briefpapier drukken Eindhoven</description>
<author></author>
<pubDate>20071011104246</pubDate>

<guid>bmBv8j07Ta7B</guid>
</item>
</channel>
</rss>
*/