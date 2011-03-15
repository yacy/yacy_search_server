// CrawlStartExpert_p.java
// (C) 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 02.12.2004 as IndexCreate_p.java on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2010-08-23 14:32:02 +0200 (Mo, 23 Aug 2010) $
// $LastChangedRevision: 7068 $
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

import net.yacy.cora.protocol.RequestHeader;
import de.anomic.crawler.CrawlProfile;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CrawlStartExpert_p {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        //final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        // define visible variables
        prop.put("starturl", /*(intranet) ? repository :*/ "http://");
        prop.put("proxyPrefetchDepth", env.getConfig("proxyPrefetchDepth", "0"));
        prop.put("crawlingDepth", Math.min(3, env.getConfigLong("crawlingDepth", 0)));
        prop.put("mustmatch", /*(intranet) ? repository + ".*" :*/ CrawlProfile.MATCH_ALL);
        prop.put("mustnotmatch", CrawlProfile.MATCH_NEVER);
        
        prop.put("crawlingIfOlderCheck", "0");
        prop.put("crawlingIfOlderUnitYearCheck", "0");
        prop.put("crawlingIfOlderUnitMonthCheck", "0");
        prop.put("crawlingIfOlderUnitDayCheck", "1");
        prop.put("crawlingIfOlderUnitHourCheck", "0");
        prop.put("crawlingIfOlderNumber", "7");
        
        final int crawlingDomFilterDepth = env.getConfigInt("crawlingDomFilterDepth", -1);
        prop.put("crawlingDomFilterCheck", (crawlingDomFilterDepth == -1) ? "0" : "1");
        prop.put("crawlingDomFilterDepth", (crawlingDomFilterDepth == -1) ? 1 : crawlingDomFilterDepth);
        final int crawlingDomMaxPages = env.getConfigInt("crawlingDomMaxPages", -1);
        prop.put("crawlingDomMaxCheck", (crawlingDomMaxPages == -1) ? "0" : "1");
        prop.put("crawlingDomMaxPages", (crawlingDomMaxPages == -1) ? 10000 : crawlingDomMaxPages);
        prop.put("crawlingQChecked", env.getConfigBool("crawlingQ", true) ? "1" : "0");
        prop.put("storeHTCacheChecked", env.getConfigBool("storeHTCache", true) ? "1" : "0");
        prop.put("indexingTextChecked", env.getConfigBool("indexText", true) ? "1" : "0");
        prop.put("indexingMediaChecked", env.getConfigBool("indexMedia", true) ? "1" : "0");
        prop.put("crawlOrderChecked", env.getConfigBool("crawlOrder", true) ? "1" : "0");
        
        final long LCbusySleep = env.getConfigLong(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, 100L);
        final int LCppm = (LCbusySleep == 0) ? 1000 : (int) (60000L / LCbusySleep);
        prop.put("crawlingSpeedMaxChecked", (LCppm >= 1000) ? "1" : "0");
        prop.put("crawlingSpeedCustChecked", ((LCppm > 10) && (LCppm < 1000)) ? "1" : "0");
        prop.put("crawlingSpeedMinChecked", (LCppm <= 10) ? "1" : "0");
        prop.put("customPPMdefault", ((LCppm > 10) && (LCppm < 1000)) ? Integer.toString(LCppm) : "");
        
        prop.put("xsstopwChecked", env.getConfigBool("xsstopw", true) ? "1" : "0");
        prop.put("xdstopwChecked", env.getConfigBool("xdstopw", true) ? "1" : "0");
        prop.put("xpstopwChecked", env.getConfigBool("xpstopw", true) ? "1" : "0");
        
        // return rewrite properties
        return prop;
    }
}
