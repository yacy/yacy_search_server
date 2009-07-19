// CrawlStartExpert_p.java
// (C) 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 02.12.2004 as IndexCreate_p.java on http://yacy.net
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

import de.anomic.crawler.CrawlProfile;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CrawlStart_p {
    
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
	    final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        // define visible variables
        String a = sb.peers.mySeed().getPublicAddress();
        boolean intranet = sb.getConfig(SwitchboardConstants.NETWORK_NAME, "").equals("intranet");
        String repository = "http://" + ((a == null) ? "localhost:" + sb.getConfig("port", "8080") : a) + "/repository/";
        prop.put("starturl", (intranet) ? repository : "http://");
        prop.put("proxyPrefetchDepth", env.getConfig("proxyPrefetchDepth", "0"));
        prop.put("crawlingDepth", env.getConfig("crawlingDepth", "0"));
        prop.put("mustmatch", (intranet) ? repository + ".*" : CrawlProfile.MATCH_ALL);
        prop.put("mustnotmatch", CrawlProfile.MATCH_NEVER);
        
        prop.put("crawlingIfOlderCheck", "0");
        prop.put("crawlingIfOlderUnitYearCheck", "0");
        prop.put("crawlingIfOlderUnitMonthCheck", "1");
        prop.put("crawlingIfOlderUnitDayCheck", "0");
        prop.put("crawlingIfOlderUnitHourCheck", "0");
        prop.put("crawlingIfOlderNumber", "3");
        
        final int crawlingDomFilterDepth = (int) env.getConfigLong("crawlingDomFilterDepth", -1);
        prop.put("crawlingDomFilterCheck", (crawlingDomFilterDepth == -1) ? "0" : "1");
        prop.put("crawlingDomFilterDepth", (crawlingDomFilterDepth == -1) ? 1 : crawlingDomFilterDepth);
        final int crawlingDomMaxPages = (int) env.getConfigLong("crawlingDomMaxPages", -1);
        prop.put("crawlingDomMaxCheck", (crawlingDomMaxPages == -1) ? "0" : "1");
        prop.put("crawlingDomMaxPages", (crawlingDomMaxPages == -1) ? 10000 : crawlingDomMaxPages);
        prop.put("crawlingQChecked", env.getConfig("crawlingQ", "").equals("true") ? "1" : "0");
        prop.put("storeHTCacheChecked", env.getConfig("storeHTCache", "").equals("true") ? "1" : "0");
        prop.put("indexingTextChecked", env.getConfig("indexText", "").equals("true") ? "1" : "0");
        prop.put("indexingMediaChecked", env.getConfig("indexMedia", "").equals("true") ? "1" : "0");
        prop.put("crawlOrderChecked", env.getConfig("crawlOrder", "").equals("true") ? "1" : "0");
        
        final long LCbusySleep = Integer.parseInt(env.getConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL_BUSYSLEEP, "100"));
        final int LCppm = (LCbusySleep == 0) ? 1000 : (int) (60000L / LCbusySleep);
        prop.put("crawlingSpeedMaxChecked", (LCppm >= 1000) ? "1" : "0");
        prop.put("crawlingSpeedCustChecked", ((LCppm > 10) && (LCppm < 1000)) ? "1" : "0");
        prop.put("crawlingSpeedMinChecked", (LCppm <= 10) ? "1" : "0");
        prop.put("customPPMdefault", ((LCppm > 10) && (LCppm < 1000)) ? Integer.toString(LCppm) : "");
        
        prop.put("xsstopwChecked", env.getConfig("xsstopw", "").equals("true") ? "1" : "0");
        prop.put("xdstopwChecked", env.getConfig("xdstopw", "").equals("true") ? "1" : "0");
        prop.put("xpstopwChecked", env.getConfig("xpstopw", "").equals("true") ? "1" : "0");
        
        // return rewrite properties
        return prop;
    }
}
