// IndexCreateParserErrors_p.java
// -------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
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


import java.util.ArrayList;
import java.util.Date;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.CrawlStacker;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexCreateParserErrors_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        prop.put("rejected", "0");
        int showRejectedCount = 100;

        if (post != null) {

            if (post.containsKey("clearRejected")) {
                sb.crawlQueues.errorURL.clearStack();
            }
            if (post.containsKey("moreRejected")) {
                showRejectedCount = post.getInt("showRejected", 10);
            }
        }
        boolean dark;


        prop.put("indexing-queue", "0"); //is empty

        // failure cases
        if (sb.crawlQueues.errorURL.stackSize() != 0) {
            if (showRejectedCount > sb.crawlQueues.errorURL.stackSize()) showRejectedCount = sb.crawlQueues.errorURL.stackSize();
            prop.put("rejected", "1");
            prop.putNum("rejected_num", sb.crawlQueues.errorURL.stackSize());
            if (showRejectedCount != sb.crawlQueues.errorURL.stackSize()) {
                prop.put("rejected_only-latest", "1");
                prop.putNum("rejected_only-latest_num", showRejectedCount);
                prop.put("rejected_only-latest_newnum", ((int) (showRejectedCount * 1.5)));
            }else{
                prop.put("rejected_only-latest", "0");
            }
            dark = true;
            DigestURL url;
            int j=0;
            ArrayList<CollectionConfiguration.FailDoc> l = sb.crawlQueues.errorURL.list(showRejectedCount);
            CollectionConfiguration.FailDoc entry;
            for (int i = l.size() - 1; i >= 0; i--) {
                entry = l.get(i);
                if (entry == null) continue;
                url = entry.getDigestURL();
                if (url == null) continue;
                
                prop.putHTML("rejected_list_"+j+"_time", GenericFormatter.SIMPLE_FORMATTER.format(new Date()));
                prop.putHTML("rejected_list_"+j+"_url", url.toNormalform(false));
                
                String cause = entry.getFailReason();
                if (cause == null) {
                    // well that should not really happen but occurs in a specific combination of running crawls for same domains with different url filters
                    prop.put("rejected_list_"+j+"_failreason", "no fail reason given");
                } else if (cause.startsWith(CrawlStacker.ERROR_NO_MATCH_MUST_MATCH_FILTER)) {
                    prop.put("rejected_list_"+j+"_failreason", "(<a href=\"/RegexTest.html?text=" + url.toNormalform(false) +
                            "&regex=" + cause.substring(CrawlStacker.ERROR_NO_MATCH_MUST_MATCH_FILTER.length()) + "\">test</a>) " + cause);
                } else if (cause.startsWith(CrawlStacker.ERROR_MATCH_WITH_MUST_NOT_MATCH_FILTER)) {
                    prop.put("rejected_list_"+j+"_failreason", "(<a href=\"/RegexTest.html?text=" + url.toNormalform(false) +
                            "&regex=" + cause.substring(CrawlStacker.ERROR_MATCH_WITH_MUST_NOT_MATCH_FILTER.length()) + "\">test</a>) " + cause);
                } else {
                    prop.putHTML("rejected_list_"+j+"_failreason", cause);
                }
                prop.put("rejected_list_"+j+"_dark", dark ? "1" : "0");
                dark = !dark;
                j++;
            }
            prop.put("rejected_list", j);
        }

        // return rewrite properties
        return prop;
    }
}
