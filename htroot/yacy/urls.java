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

public class urls {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
     
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        
        // insert default values
        prop.putASIS("iam", yacyCore.seedDB.mySeed.hash);
        prop.putASIS("response", "rejected - insufficient call parameters");
        prop.putASIS("channel_title", "");
        prop.putASIS("channel_description", "");
        prop.putASIS("channel_pubDate", "");
        prop.put("item", 0);
        
        if (post == null) return prop;
        
        if (post.get("call", "").equals("remotecrawl")) {
            // perform a remote crawl url handover
            int stackType = plasmaCrawlNURL.STACK_TYPE_LIMIT;
            //int stackType = plasmaCrawlNURL.STACK_TYPE_CORE;
            int count = Math.min(100, post.getInt("count", 0));
            int c = 0;
            plasmaCrawlEntry entry;
            while ((count > 0) && (sb.noticeURL.stackSize(stackType) > 0)) {
                try {
                    entry = sb.noticeURL.pop(stackType, false);
                } catch (IOException e) {
                    break;
                }
                if (entry == null) break;
                prop.put("item_" + c + "_title", "");
                prop.put("item_" + c + "_link", entry.url().toNormalform(true, false));
                prop.put("item_" + c + "_description", entry.name());
                prop.put("item_" + c + "_author", "");
                prop.put("item_" + c + "_pubDate", serverDate.shortSecondTime(entry.appdate()));
                prop.put("item_" + c + "_guid", entry.url().hash());
                c++;
                count--;
            }
            prop.put("item", c);
            prop.put("response", "ok");
        }

        // return rewrite properties
        return prop;
    }
    
}
