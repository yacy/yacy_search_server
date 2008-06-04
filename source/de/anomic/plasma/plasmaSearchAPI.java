// plasmaSearchAPI.java
// -----------------------
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2008 on http://yacy.net
// 
// This is a part of YaCy, a peer-to-peer based web search engine
// 
// $LastChangedDate: 2007-11-14 01:15:28 +0000 (Mi, 14 Nov 2007) $
// $LastChangedRevision: 4216 $
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


package de.anomic.plasma;

import java.util.Date;
import java.util.Iterator;

import de.anomic.data.listManager;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexReferenceBlacklist;
import de.anomic.index.indexURLReference;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.yacy.yacyPeerActions;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class plasmaSearchAPI {
    // collection of static methods for a search servlet. Exists only to prevent that the same processes are defined more than once.
    

    public static kelondroBitfield compileFlags(serverObjects post) {
        kelondroBitfield b = new kelondroBitfield(4);
        if (post.get("allurl", "").equals("on")) return null;
        if (post.get("flags") != null) {
            if (post.get("flags","").length() == 0) return null;
            return new kelondroBitfield(4, (String) post.get("flags"));
        }
        if (post.get("description", "").equals("on")) b.set(indexRWIEntry.flag_app_dc_description, true);
        if (post.get("title", "").equals("on")) b.set(indexRWIEntry.flag_app_dc_title, true);
        if (post.get("creator", "").equals("on")) b.set(indexRWIEntry.flag_app_dc_creator, true);
        if (post.get("subject", "").equals("on")) b.set(indexRWIEntry.flag_app_dc_subject, true);
        if (post.get("url", "").equals("on")) b.set(indexRWIEntry.flag_app_dc_identifier, true);
        if (post.get("emphasized", "").equals("on")) b.set(indexRWIEntry.flag_app_emphasized, true);
        if (post.get("image", "").equals("on")) b.set(plasmaCondenser.flag_cat_hasimage, true);
        if (post.get("audio", "").equals("on")) b.set(plasmaCondenser.flag_cat_hasaudio, true);
        if (post.get("video", "").equals("on")) b.set(plasmaCondenser.flag_cat_hasvideo, true);
        if (post.get("app", "").equals("on")) b.set(plasmaCondenser.flag_cat_hasapp, true);
        if (post.get("indexof", "").equals("on")) b.set(plasmaCondenser.flag_cat_indexof, true);
        return b;
    }
    
    public static void listHosts(serverObjects prop, String startHash, yacyPeerActions peerActions) {
        // list known hosts
        yacySeed seed;
        int hc = 0;
        prop.put("searchresult_keyhash", startHash);
        Iterator<yacySeed> e = peerActions.dhtAction.getAcceptRemoteIndexSeeds(startHash);
        while (e.hasNext()) {
            seed = (yacySeed) e.next();
            if (seed != null) {
                prop.put("searchresult_hosts_" + hc + "_hosthash", seed.hash);
                prop.putHTML("searchresult_hosts_" + hc + "_hostname", seed.hash + " " + seed.get(yacySeed.NAME, "nameless"));
                hc++;
            }
        }
        prop.put("searchresult_hosts", hc);
    }

    public static plasmaSearchRankingProcess genSearchresult(serverObjects prop, plasmaSwitchboard sb, String keyhash, kelondroBitfield filter) {
        plasmaSearchQuery query = new plasmaSearchQuery(keyhash, -1, sb.getRanking(), filter);
        plasmaSearchRankingProcess ranked = new plasmaSearchRankingProcess(sb.webIndex, query, Integer.MAX_VALUE, 1);
        ranked.execQuery();
        
        if (ranked.filteredCount() == 0) {
            prop.put("searchresult", 2);
            prop.put("searchresult_wordhash", keyhash);
        } else {
            prop.put("searchresult", 3);
            prop.put("searchresult_allurl", ranked.filteredCount());
            prop.put("searchresult_description", ranked.flagCount()[indexRWIEntry.flag_app_dc_description]);
            prop.put("searchresult_title", ranked.flagCount()[indexRWIEntry.flag_app_dc_title]);
            prop.put("searchresult_creator", ranked.flagCount()[indexRWIEntry.flag_app_dc_creator]);
            prop.put("searchresult_subject", ranked.flagCount()[indexRWIEntry.flag_app_dc_subject]);
            prop.put("searchresult_url", ranked.flagCount()[indexRWIEntry.flag_app_dc_identifier]);
            prop.put("searchresult_emphasized", ranked.flagCount()[indexRWIEntry.flag_app_emphasized]);
            prop.put("searchresult_image", ranked.flagCount()[plasmaCondenser.flag_cat_hasimage]);
            prop.put("searchresult_audio", ranked.flagCount()[plasmaCondenser.flag_cat_hasaudio]);
            prop.put("searchresult_video", ranked.flagCount()[plasmaCondenser.flag_cat_hasvideo]);
            prop.put("searchresult_app", ranked.flagCount()[plasmaCondenser.flag_cat_hasapp]);
            prop.put("searchresult_indexof", ranked.flagCount()[plasmaCondenser.flag_cat_indexof]);
        }
        return ranked;
    }
    
    public static void genURLList(serverObjects prop, String keyhash, String keystring, plasmaSearchRankingProcess ranked, kelondroBitfield flags, int maxlines) {
        // search for a word hash and generate a list of url links
        prop.put("genUrlList_keyHash", keyhash);
        
        if (ranked.filteredCount() == 0) {
            prop.put("genUrlList", 1);
            prop.put("genUrlList_count", 0);
            prop.put("searchresult", 2);
        } else {
            prop.put("genUrlList", 2);
            prop.put("searchresult", 3);
            prop.put("genUrlList_flags", (flags == null) ? "" : flags.exportB64());
            prop.put("genUrlList_lines", maxlines);
            int i = 0;
            yacyURL url;
            indexURLReference entry;
            String us;
            long rn = -1;
            while ((ranked.size() > 0) && ((entry = ranked.bestURL(false)) != null)) {
                if ((entry == null) || (entry.comp() == null)) continue;
                url = entry.comp().url();
                if (url == null) continue;
                us = url.toNormalform(false, false);
                if (rn == -1) rn = entry.ranking();
                prop.put("genUrlList_urlList_"+i+"_urlExists", "1");
                prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxCount", i);
                prop.putHTML("genUrlList_urlList_"+i+"_urlExists_urlhxValue", entry.word().urlHash());
                prop.putHTML("genUrlList_urlList_"+i+"_urlExists_keyString", keystring);
                prop.put("genUrlList_urlList_"+i+"_urlExists_keyHash", keyhash);
                prop.putHTML("genUrlList_urlList_"+i+"_urlExists_urlString", us);
                prop.put("genUrlList_urlList_"+i+"_urlExists_urlStringShort", (us.length() > 40) ? (us.substring(0, 20) + "<br>" + us.substring(20,  40) + "...") : ((us.length() > 30) ? (us.substring(0, 20) + "<br>" + us.substring(20)) : us));
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_ranking", (entry.ranking() - rn));
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_domlength", yacyURL.domLengthEstimation(entry.hash()));
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_ybr", plasmaSearchRankingProcess.ybr(entry.hash()));
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_tf", 1000.0 * entry.word().termFrequency());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_authority", (ranked.getOrder() == null) ? -1 : ranked.getOrder().authority(entry.hash()));
                prop.put("genUrlList_urlList_"+i+"_urlExists_date", serverDate.formatShortDay(new Date(entry.word().lastModified())));
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_wordsintitle", entry.word().wordsintitle());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_wordsintext", entry.word().wordsintext());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_phrasesintext", entry.word().phrasesintext());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_llocal", entry.word().llocal());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_lother", entry.word().lother());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_hitcount", entry.word().hitcount());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_worddistance", 0);
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_pos", entry.word().posintext());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_phrase", entry.word().posofphrase());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_posinphrase", entry.word().posinphrase());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_urlcomps", entry.word().urlcomps());
                prop.putNum("genUrlList_urlList_"+i+"_urlExists_urllength", entry.word().urllength());
                prop.put("genUrlList_urlList_"+i+"_urlExists_props",
                        ((entry.word().flags().get(plasmaCondenser.flag_cat_indexof)) ? "appears on index page, " : "") +
                        ((entry.word().flags().get(plasmaCondenser.flag_cat_hasimage)) ? "contains images, " : "") +
                        ((entry.word().flags().get(plasmaCondenser.flag_cat_hasaudio)) ? "contains audio, " : "") +
                        ((entry.word().flags().get(plasmaCondenser.flag_cat_hasvideo)) ? "contains video, " : "") +
                        ((entry.word().flags().get(plasmaCondenser.flag_cat_hasapp)) ? "contains applications, " : "") +
                        ((entry.word().flags().get(indexRWIEntry.flag_app_dc_identifier)) ? "appears in url, " : "") +
                        ((entry.word().flags().get(indexRWIEntry.flag_app_dc_title)) ? "appears in title, " : "") +
                        ((entry.word().flags().get(indexRWIEntry.flag_app_dc_creator)) ? "appears in author, " : "") +
                        ((entry.word().flags().get(indexRWIEntry.flag_app_dc_subject)) ? "appears in subject, " : "") +
                        ((entry.word().flags().get(indexRWIEntry.flag_app_dc_description)) ? "appears in description, " : "") +
                        ((entry.word().flags().get(indexRWIEntry.flag_app_emphasized)) ? "appears emphasized, " : "") +
                        ((yacyURL.probablyRootURL(entry.word().urlHash())) ? "probably root url" : "")
                );
                if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_DHT, url)) {
                    prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxChecked", "1");
                }
                i++;
                if ((maxlines >= 0) && (i >= maxlines)) break;
            }
            Iterator<String> iter = ranked.miss(); // iterates url hash strings
            while (iter.hasNext()) {
                us = (String) iter.next();
                prop.put("genUrlList_urlList_"+i+"_urlExists", "0");
                prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxCount", i);
                prop.putHTML("genUrlList_urlList_"+i+"_urlExists_urlhxValue", us);
                i++;
            }
            prop.put("genUrlList_urlList", i);
            prop.putHTML("genUrlList_keyString", keystring);
            prop.put("genUrlList_count", i);
            putBlacklists(prop, listManager.getDirListing(listManager.listsPath));
        }
    }
    
    public static void putBlacklists(serverObjects prop, String[] lists) {
        prop.put("genUrlList_blacklists", lists.length);
        for (int i=0; i<lists.length; i++)
            prop.put("genUrlList_blacklists_" + i + "_name", lists[i]);
    }
}
