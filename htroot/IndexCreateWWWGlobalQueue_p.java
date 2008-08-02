// IndexCreateWWWCrawlQueue_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 04.07.2005
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

// You must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import de.anomic.crawler.CrawlEntry;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.NoticedURL;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;

public class IndexCreateWWWGlobalQueue_p {
    
    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(final Date date) {
        if (date == null) return "";
        return dayFormatter.format(date);
    }
    
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
 
        int showLimit = 100;
        if (post != null) {
            if (post.containsKey("limit")) {
                try {
                    showLimit = Integer.valueOf(post.get("limit")).intValue();
                } catch (final NumberFormatException e) {}
            }            
            
            if (post.containsKey("clearcrawlqueue")) {
                final int c = sb.crawlQueues.noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT);
                sb.crawlQueues.noticeURL.clear(NoticedURL.STACK_TYPE_LIMIT);
                try { sb.cleanProfiles(); } catch (final InterruptedException e) { /* Ignore this */}
                /*
                int c = 0;
                while (switchboard.urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT) > 0) {
                    urlHash = switchboard.urlPool.noticeURL.pop(plasmaCrawlNURL.STACK_TYPE_LIMIT).hash();
                    if (urlHash != null) { switchboard.urlPool.noticeURL.remove(urlHash); c++; }
                }
                */
                prop.put("info", "3");//crawling queue cleared
                prop.putNum("info_numEntries", c);
            } else if (post.containsKey("deleteEntry")) {
                final String urlHash = post.get("deleteEntry");
                sb.crawlQueues.noticeURL.removeByURLHash(urlHash);
                prop.put("LOCATION","");
                return prop;
            }
        }

        int stackSize = sb.crawlQueues.noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT);
        if (stackSize == 0) {
            prop.put("crawler-queue", "0");
        } else {
            prop.put("crawler-queue", "1");
            final ArrayList<CrawlEntry> crawlerList = sb.crawlQueues.noticeURL.top(NoticedURL.STACK_TYPE_LIMIT, showLimit);
            
            CrawlEntry urle;
            boolean dark = true;
            yacySeed initiator;
            String profileHandle;
            CrawlProfile.entry profileEntry;
            int i, showNum = 0;
            for (i = 0; (i < crawlerList.size()) && (showNum < showLimit); i++) {
                urle = crawlerList.get(i);
                if ((urle != null)&&(urle.url()!=null)) {
                    initiator = sb.webIndex.seedDB.getConnected(urle.initiator());
                    profileHandle = urle.profileHandle();
                    profileEntry = (profileHandle == null) ? null : sb.webIndex.profilesActiveCrawls.getEntry(profileHandle);
                    prop.put("crawler-queue_list_"+showNum+"_dark", dark ? "1" : "0");
                    prop.putHTML("crawler-queue_list_"+showNum+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()) );
                    prop.put("crawler-queue_list_"+showNum+"_profile", ((profileEntry == null) ? "unknown" : profileEntry.name()));
                    prop.put("crawler-queue_list_"+showNum+"_depth", urle.depth());
                    prop.put("crawler-queue_list_"+showNum+"_modified", daydate(urle.loaddate()) );
                    prop.putHTML("crawler-queue_list_"+showNum+"_anchor", urle.name());
                    prop.putHTML("crawler-queue_list_"+showNum+"_url", urle.url().toNormalform(false, true));
                    prop.put("crawler-queue_list_"+showNum+"_hash", urle.url().hash());
                    dark = !dark;
                    showNum++;
                } else {
                    stackSize--;
                }
            }
            prop.putNum("crawler-queue_show-num", showNum); //showin sjow-num most recent
            prop.putNum("crawler-queue_num", stackSize);//num Entries
            prop.putNum("crawler-queue_list", showNum);
        }

        // return rewrite properties
        return prop;
    }
}
