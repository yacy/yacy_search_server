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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIRowEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSearchAPI;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchRankingProcess;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.urlPattern.abstractURLPattern;
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
        
        // clean up all search events
        plasmaSearchEvent.cleanupEvents(true);
        
        if (post != null) {
            // default values
            String keystring = post.get("keystring", "").trim();
            String keyhash = post.get("keyhash", "").trim();
            prop.putHTML("keystring", keystring);
            prop.put("keyhash", keyhash);

            // read values from checkboxes
            String[] urlx = post.getAll("urlhx.*");
            boolean delurl    = post.containsKey("delurl");
            boolean delurlref = post.containsKey("delurlref");

            if (post.containsKey("keystringsearch")) {
                keyhash = plasmaCondenser.word2hash(keystring);
                prop.put("keyhash", keyhash);
                final plasmaSearchRankingProcess ranking = plasmaSearchAPI.genSearchresult(prop, sb, keyhash, null);
                if (ranking.filteredCount() == 0) {
                    prop.put("searchresult", 1);
                    prop.put("searchresult_word", keystring);
                }
            }
    
            if (post.containsKey("keyhashsearch")) {
                if (keystring.length() == 0 || !plasmaCondenser.word2hash(keystring).equals(keyhash)) {
                    prop.put("keystring", "&lt;not possible to compute word from hash&gt;");
                }
                final plasmaSearchRankingProcess ranking = plasmaSearchAPI.genSearchresult(prop, sb, keyhash, null);
                if (ranking.filteredCount() == 0) {
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
                    Iterator<indexRWIRowEntry> en = index.entries();
                    int i = 0;
                    urlx = new String[index.size()];
                    while (en.hasNext()) {
                        urlx[i++] = en.next().urlHash();
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
                Set<String> urlHashes = new HashSet<String>();
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
                kelondroBitfield flags = plasmaSearchAPI.compileFlags(post);
                int count = (post.get("lines", "all").equals("all")) ? -1 : post.getInt("lines", -1);
                final plasmaSearchRankingProcess ranking = plasmaSearchAPI.genSearchresult(prop, sb, keyhash, flags);
                plasmaSearchAPI.genURLList(prop, keyhash, keystring, ranking, flags, count);
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
                Iterator<indexRWIRowEntry> urlIter = index.entries();
                HashMap<String, indexURLEntry> knownURLs = new HashMap<String, indexURLEntry>();
                HashSet<String> unknownURLEntries = new HashSet<String>();
                indexRWIEntry iEntry;
                indexURLEntry lurl;
                while (urlIter.hasNext()) {
                    iEntry = urlIter.next();
                    lurl = sb.wordIndex.loadedURL.load(iEntry.urlHash(), null, 0);
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
                HashMap<String, Object> resultObj = yacyClient.transferIndex(
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
                final Iterator<indexContainer> containerIt = sb.wordIndex.indexContainerSet(keyhash, false, true, 256).iterator();
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
                Set<String> urlHashes = new HashSet<String>();
                if (post.containsKey("blacklisturls")) {
                    PrintWriter pw;
                    try {
                        String[] supportedBlacklistTypes = env.getConfig("BlackLists.types", "").split(",");
                        pw = new PrintWriter(new FileWriter(new File(listManager.listsPath, blacklist), true));
                        yacyURL url;
                        for (int i=0; i<urlx.length; i++) {
                            urlHashes.add(urlx[i]);
                            indexURLEntry e = sb.wordIndex.loadedURL.load(urlx[i], null, 0);
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
                            indexURLEntry e = sb.wordIndex.loadedURL.load(urlx[i], null, 0);
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
        
            if (prop.getInt("searchresult", 0) == 3) plasmaSearchAPI.listHosts(prop, keyhash);
        }
        

        // insert constants
        prop.putNum("wcount", sb.wordIndex.size());
        // return rewrite properties
        return prop;
    }
    
}
