// IndexControlRWIs_p.java
// -----------------------
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.urlPattern.abstractURLPattern;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class IndexControlRWIs_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();

        prop.putHTML("keystring", "");
        prop.put("keyhash", "");
        prop.put("result", "");

        // switch off all optional forms/lists
        prop.put("searchresult", 0);
        prop.put("keyhashsimilar", 0);
        prop.put("genUrlList", 0);
        
        if (post != null) {
            // default values
            String keystring = post.get("keystring", "").trim();
            String keyhash = post.get("keyhash", "").trim();
            int sortorder = post.getInt("ordering", 0);
            prop.putHTML("keystring", keystring);
            prop.put("keyhash", keyhash);

            // read values from checkboxes
            String[] urlx = post.getAll("urlhx.*");
            boolean delurl    = post.containsKey("delurl");
            boolean delurlref = post.containsKey("delurlref");

            if (post.containsKey("keystringsearch")) {
                keyhash = plasmaCondenser.word2hash(keystring);
                prop.put("keyhash", keyhash);
                final plasmaWordIndex.Finding finding = genSearchresult(prop, sb, keyhash, null, false, sortorder);
                if (finding.size() == 0) {
                    prop.put("searchresult", 1);
                    prop.put("searchresult_word", keystring);
                }
            }
    
            if (post.containsKey("keyhashsearch")) {
                if (keystring.length() == 0 || !plasmaCondenser.word2hash(keystring).equals(keyhash)) {
                    prop.put("keystring", "&lt;not possible to compute word from hash&gt;");
                }
                final plasmaWordIndex.Finding finding = genSearchresult(prop, sb, keyhash, null, false, sortorder);
                if (finding.size() == 0) {
                    prop.put("searchresult", 2);
                    prop.put("searchresult_wordhash", keyhash);
                }
            }

            // delete word
            if (post.containsKey("keyhashdeleteall")) {
                if (delurl || delurlref) {
                    // generate an urlx array
                    indexContainer index = null;
                    index = sb.wordIndex.getContainer(keyhash, null);
                    Iterator en = index.entries();
                    int i = 0;
                    urlx = new String[index.size()];
                    while (en.hasNext()) {
                        urlx[i++] = ((indexRWIEntry) en.next()).urlHash();
                    }
                    index = null;
                }
                if (delurlref) {
                    for (int i = 0; i < urlx.length; i++) sb.removeAllUrlReferences(urlx[i], true);
                }
                if (delurl || delurlref) {
                    for (int i = 0; i < urlx.length; i++) {
                        sb.urlRemove(urlx[i]);
                    }
                }
                sb.wordIndex.deleteContainer(keyhash);
                post.remove("keyhashdeleteall");
                post.put("urllist", "generated");
            }
    
            // delete selected URLs
            if (post.containsKey("keyhashdelete")) {
                if (delurlref) {
                    for (int i = 0; i < urlx.length; i++) sb.removeAllUrlReferences(urlx[i], true);
                }
                if (delurl || delurlref) {
                    for (int i = 0; i < urlx.length; i++) {
                        sb.urlRemove(urlx[i]);
                    }
                }
                Set urlHashes = new HashSet();
                for (int i = 0; i < urlx.length; i++) urlHashes.add(urlx[i]);
                sb.wordIndex.removeEntries(keyhash, urlHashes);
                // this shall lead to a presentation of the list; so handle that the remaining program
                // thinks that it was called for a list presentation
                post.remove("keyhashdelete");
                post.put("urllist", "generated");
            }
            
            if (post.containsKey("urllist")) {
                if (keystring.length() == 0 || !plasmaCondenser.word2hash(keystring).equals(keyhash)) {
                    prop.put("keystring", "&lt;not possible to compute word from hash&gt;");
                }
                kelondroBitfield flags = compileFlags(post);
                int count = (post.get("lines", "all").equals("all")) ? -1 : post.getInt("lines", -1);
                final plasmaWordIndex.Finding finding = genSearchresult(prop, sb, keyhash, flags, true, sortorder);
                genURLList(prop, keyhash, keystring, finding, flags, count, sortorder);
            }

            // transfer to other peer
            if (post.containsKey("keyhashtransfer")) {
                if (keystring.length() == 0 || !plasmaCondenser.word2hash(keystring).equals(keyhash)) {
                    prop.put("keystring", "&lt;not possible to compute word from hash&gt;");
                }
                
                // find host & peer
                String host = post.get("host", ""); // get host from input field
                yacySeed seed = null;
                if (host.length() != 0) {
                    if (host.length() == 12) {
                        // the host string is a peer hash
                        seed = yacyCore.seedDB.getConnected(host);
                    } else {
                        // the host string can be a host name
                        seed = yacyCore.seedDB.lookupByName(host);
                    }
                } else {
                    host = post.get("hostHash", ""); // if input field is empty, get from select box
                    seed = yacyCore.seedDB.getConnected(host);
                }
                
                // prepare index
                indexContainer index;
                String result;
                long starttime = System.currentTimeMillis();
                index = sb.wordIndex.getContainer(keyhash, null);
                // built urlCache
                Iterator urlIter = index.entries();
                HashMap knownURLs = new HashMap();
                HashSet unknownURLEntries = new HashSet();
                indexRWIEntry iEntry;
                indexURLEntry lurl;
                while (urlIter.hasNext()) {
                    iEntry = (indexRWIEntry) urlIter.next();
                    lurl = sb.wordIndex.loadedURL.load(iEntry.urlHash(), null);
                    if (lurl == null) {
                        unknownURLEntries.add(iEntry.urlHash());
                        urlIter.remove();
                    } else {
                        knownURLs.put(iEntry.urlHash(), lurl);
                    }
                }
                
                // transport to other peer
                String gzipBody = sb.getConfig("indexControl.gzipBody","false");
                int timeout = (int) sb.getConfigLong("indexControl.timeout",60000);
                HashMap resultObj = yacyClient.transferIndex(
                             seed,
                             new indexContainer[]{index},
                             knownURLs,
                             "true".equalsIgnoreCase(gzipBody),
                             timeout);
                result = (String) resultObj.get("result");
                prop.put("result", (result == null) ? ("Successfully transferred " + knownURLs.size() + " words in " + ((System.currentTimeMillis() - starttime) / 1000) + " seconds, " + unknownURLEntries + " URL not found") : result);
                index = null;
            }
    
            // generate list
            if (post.containsKey("keyhashsimilar")) {
                final Iterator containerIt = sb.wordIndex.indexContainerSet(keyhash, false, true, 256).iterator();
                    indexContainer container;
                    int i = 0;
                    int rows = 0, cols = 0;
                    prop.put("keyhashsimilar", "1");
                    while (containerIt.hasNext() && i < 256) {
                        container = (indexContainer) containerIt.next();
                        prop.put("keyhashsimilar_rows_"+rows+"_cols_"+cols+"_wordHash", container.getWordHash());
                        cols++;
                        if (cols==8) {
                            prop.put("keyhashsimilar_rows_"+rows+"_cols", cols);
                            cols = 0;
                            rows++;
                        }
                        i++;
                    }
                    prop.put("keyhashsimilar_rows_"+rows+"_cols", cols);
                    prop.put("keyhashsimilar_rows", rows + 1);
                    prop.put("result", "");
            }
            
            if (post.containsKey("blacklist")) {
                String blacklist = post.get("blacklist", "");
                Set urlHashes = new HashSet();
                if (post.containsKey("blacklisturls")) {
                    PrintWriter pw;
                    try {
                        String[] supportedBlacklistTypes = env.getConfig("BlackLists.types", "").split(",");
                        pw = new PrintWriter(new FileWriter(new File(listManager.listsPath, blacklist), true));
                        yacyURL url;
                        for (int i=0; i<urlx.length; i++) {
                            urlHashes.add(urlx[i]);
                            indexURLEntry e = sb.wordIndex.loadedURL.load(urlx[i], null);
                            sb.wordIndex.loadedURL.remove(urlx[i]);
                            if (e != null) {
                                url = e.comp().url();
                                pw.println(url.getHost() + "/" + url.getFile());
                                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                                    if (listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists", blacklist)) {
                                        plasmaSwitchboard.urlBlacklist.add(
                                                supportedBlacklistTypes[blTypes],
                                                url.getHost(),
                                                url.getFile());
                                    }                
                                }
                            }
                        }
                        pw.close();
                    } catch (IOException e) {
                    }
                }
                
                if (post.containsKey("blacklistdomains")) {
                    PrintWriter pw;
                    try {
                        String[] supportedBlacklistTypes = abstractURLPattern.BLACKLIST_TYPES_STRING.split(",");
                        pw = new PrintWriter(new FileWriter(new File(listManager.listsPath, blacklist), true));
                        yacyURL url;
                        for (int i=0; i<urlx.length; i++) {
                            urlHashes.add(urlx[i]);
                            indexURLEntry e = sb.wordIndex.loadedURL.load(urlx[i], null);
                            sb.wordIndex.loadedURL.remove(urlx[i]);
                            if (e != null) {
                                url = e.comp().url();
                                pw.println(url.getHost() + "/.*");
                                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                                    if (listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists", blacklist)) {
                                        plasmaSwitchboard.urlBlacklist.add(
                                                supportedBlacklistTypes[blTypes],
                                                url.getHost(), ".*");
                                    }                
                                }
                            }
                        }
                        pw.close();
                    } catch (IOException e) {
                    }
                }
                sb.wordIndex.removeEntries(keyhash, urlHashes);
            }
        
            if (prop.getInt("searchresult", 0) == 3) listHosts(prop, keyhash);
        }
        

        // insert constants
        prop.putNum("wcount", sb.wordIndex.size());
        // return rewrite properties
        return prop;
    }
    
    private static kelondroBitfield compileFlags(serverObjects post) {
        kelondroBitfield b = new kelondroBitfield(4);
        if (post.get("allurl", "").equals("on")) {
            for (int i = 0; i < 32; i++) {b.set(i, true);}
            return b;
        }
        if (post.get("flags") != null) return new kelondroBitfield(4, (String) post.get("flags"));
        if (post.get("reference", "").equals("on")) b.set(indexRWIEntry.flag_app_reference, true);
        if (post.get("description", "").equals("on")) b.set(indexRWIEntry.flag_app_descr, true);
        if (post.get("author", "").equals("on")) b.set(indexRWIEntry.flag_app_author, true);
        if (post.get("tag", "").equals("on")) b.set(indexRWIEntry.flag_app_tags, true);
        if (post.get("url", "").equals("on")) b.set(indexRWIEntry.flag_app_url, true);
        if (post.get("emphasized", "").equals("on")) b.set(indexRWIEntry.flag_app_emphasized, true);
        if (post.get("image", "").equals("on")) b.set(plasmaCondenser.flag_cat_hasimage, true);
        if (post.get("audio", "").equals("on")) b.set(plasmaCondenser.flag_cat_hasaudio, true);
        if (post.get("video", "").equals("on")) b.set(plasmaCondenser.flag_cat_hasvideo, true);
        if (post.get("app", "").equals("on")) b.set(plasmaCondenser.flag_cat_hasapp, true);
        if (post.get("indexof", "").equals("on")) b.set(plasmaCondenser.flag_cat_indexof, true);
        return b;
    }
    
    private static void listHosts(serverObjects prop, String startHash) {
        // list known hosts
        yacySeed seed;
        int hc = 0;
        prop.put("searchresult_keyhash", startHash);
        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) {
            Iterator e = yacyCore.dhtAgent.getAcceptRemoteIndexSeeds(startHash);
            while (e.hasNext()) {
                seed = (yacySeed) e.next();
                if (seed != null) {
                    prop.put("searchresult_hosts_" + hc + "_hosthash", seed.hash);
                    prop.putHTML("searchresult_hosts_" + hc + "_hostname", seed.hash + " " + seed.get(yacySeed.NAME, "nameless"));
                    hc++;
                }
            }
            prop.put("searchresult_hosts", hc);
        } else {
            prop.put("searchresult_hosts", "0");
        }
    }

    private static plasmaWordIndex.Finding genSearchresult(serverObjects prop, plasmaSwitchboard sb, String keyhash, kelondroBitfield filter, boolean urlfetch, int sortorder) {
        final plasmaWordIndex.Finding finding = sb.wordIndex.retrieveURLs(keyhash, filter, false, -1, urlfetch, sortorder);
        if (finding.size() == 0) {
            prop.put("searchresult", 2);
            prop.put("searchresult_wordhash", keyhash);
        } else {
            prop.put("searchresult", 3);
            prop.put("searchresult_allurl", finding.size());
            prop.put("searchresult_reference", finding.flagcount()[indexRWIEntry.flag_app_reference]);
            prop.put("searchresult_description", finding.flagcount()[indexRWIEntry.flag_app_descr]);
            prop.put("searchresult_author", finding.flagcount()[indexRWIEntry.flag_app_author]);
            prop.put("searchresult_tag", finding.flagcount()[indexRWIEntry.flag_app_tags]);
            prop.put("searchresult_url", finding.flagcount()[indexRWIEntry.flag_app_url]);
            prop.put("searchresult_emphasized", finding.flagcount()[indexRWIEntry.flag_app_emphasized]);
            prop.put("searchresult_image", finding.flagcount()[plasmaCondenser.flag_cat_hasimage]);
            prop.put("searchresult_audio", finding.flagcount()[plasmaCondenser.flag_cat_hasaudio]);
            prop.put("searchresult_video", finding.flagcount()[plasmaCondenser.flag_cat_hasvideo]);
            prop.put("searchresult_app", finding.flagcount()[plasmaCondenser.flag_cat_hasapp]);
            prop.put("searchresult_indexof", finding.flagcount()[plasmaCondenser.flag_cat_indexof]);
        }
        return finding;
    }
    
    private static void genURLList(serverObjects prop, String keyhash, String keystring, plasmaWordIndex.Finding finding, kelondroBitfield flags, int maxlines, int ordering) {
        // search for a word hash and generate a list of url links
        prop.put("genUrlList_keyHash", keyhash);
        
        if (finding.size() == 0) {
            prop.put("genUrlList", 1);
            prop.put("genUrlList_count", 0);
            prop.put("searchresult", 2);
        } else {
            prop.put("genUrlList", 2);
            prop.put("searchresult", 3);
            prop.put("genUrlList_flags", flags.exportB64());
            prop.put("genUrlList_lines", maxlines);
            prop.put("genUrlList_ordering", ordering);
            int i = 0;
            yacyURL url;
            Iterator iter = finding.hit();
            plasmaWordIndex.Item entry;
            String us;
            while (iter.hasNext()) {
                entry = (plasmaWordIndex.Item) iter.next();
                us = entry.url().comp().url().toNormalform(false, false);
                prop.put("genUrlList_urlList_"+i+"_urlExists", "1");
                prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxCount", i);
                prop.putHTML("genUrlList_urlList_"+i+"_urlExists_urlhxValue", entry.index().urlHash());
                prop.putHTML("genUrlList_urlList_"+i+"_urlExists_keyString", keystring);
                prop.put("genUrlList_urlList_"+i+"_urlExists_keyHash", keyhash);
                prop.putHTML("genUrlList_urlList_"+i+"_urlExists_urlString", us);
                prop.putHTML("genUrlList_urlList_"+i+"_urlExists_urlStringShort", (us.length() > 60) ? (us.substring(0, 60) + "...") : us);
                prop.put("genUrlList_urlList_"+i+"_urlExists_pos", entry.index().posintext());
                prop.put("genUrlList_urlList_"+i+"_urlExists_phrase", entry.index().posofphrase());
                prop.put("genUrlList_urlList_"+i+"_urlExists_urlcomps", entry.index().urlcomps());
                prop.put("genUrlList_urlList_"+i+"_urlExists_urllength", entry.index().urllength());
                prop.put("genUrlList_urlList_"+i+"_urlExists_props",
                        ((entry.index().flags().get(plasmaCondenser.flag_cat_hasimage)) ? "contains images, " : "") +
                        ((entry.index().flags().get(plasmaCondenser.flag_cat_hasaudio)) ? "contains audio, " : "") +
                        ((entry.index().flags().get(plasmaCondenser.flag_cat_hasvideo)) ? "contains video, " : "") +
                        ((entry.index().flags().get(plasmaCondenser.flag_cat_hasapp)) ? "contains applications, " : "") +
                        ((entry.index().flags().get(indexRWIEntry.flag_app_url)) ? "appears in url, " : "") +
                        ((entry.index().flags().get(indexRWIEntry.flag_app_descr)) ? "appears in description, " : "") +
                        ((entry.index().flags().get(indexRWIEntry.flag_app_author)) ? "appears in author, " : "") +
                        ((entry.index().flags().get(indexRWIEntry.flag_app_tags)) ? "appears in tags, " : "") +
                        ((entry.index().flags().get(indexRWIEntry.flag_app_reference)) ? "appears in reference, " : "") +
                        ((entry.index().flags().get(indexRWIEntry.flag_app_emphasized)) ? "appears emphasized" : "")
                );
                prop.put("genUrlList_urlList_"+i+"_urlExists_phrase", entry.index().posofphrase());
                prop.put("genUrlList_urlList_"+i+"_urlExists_phrase", entry.index().posofphrase());
                try {
                    url = new yacyURL(us, null);
                } catch (MalformedURLException e) {
                    url = null;
                }
                if (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_DHT, url)) {
                    prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxChecked", "1");
                }
                i++;
                if ((maxlines >= 0) && (i >= maxlines)) break;
            }
            iter = finding.miss().iterator();
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
    
    private static void putBlacklists(serverObjects prop, String[] lists) {
        prop.put("genUrlList_blacklists", lists.length);
        for (int i=0; i<lists.length; i++)
            prop.put("genUrlList_blacklists_" + i + "_name", lists[i]);
    }
}
