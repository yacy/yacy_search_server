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
import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.order.Bitfield;
import de.anomic.kelondro.text.MetadataRowContainer;
import de.anomic.kelondro.text.Reference;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.ReferenceContainerCache;
import de.anomic.kelondro.text.ReferenceRow;
import de.anomic.kelondro.text.Word;
import de.anomic.kelondro.text.AbstractBlacklist;
import de.anomic.plasma.plasmaSearchAPI;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchRankingProcess;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class IndexControlRWIs_p {
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

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
            final String keystring = post.get("keystring", "").trim();
            String keyhash = post.get("keyhash", "").trim();
            prop.putHTML("keystring", keystring);
            prop.putHTML("keyhash", keyhash);

            // read values from checkboxes
            String[] urlx = post.getAll("urlhx.*");
            final boolean delurl    = post.containsKey("delurl");
            final boolean delurlref = post.containsKey("delurlref");

            if (post.containsKey("keystringsearch")) {
                keyhash = Word.word2hash(keystring);
                prop.put("keyhash", keyhash);
                final plasmaSearchRankingProcess ranking = plasmaSearchAPI.genSearchresult(prop, sb, keyhash, null);
                if (ranking.filteredCount() == 0) {
                    prop.put("searchresult", 1);
                    prop.putHTML("searchresult_word", keystring);
                }
            }
    
            if (post.containsKey("keyhashsearch")) {
                if (keystring.length() == 0 || !Word.word2hash(keystring).equals(keyhash)) {
                    prop.put("keystring", "&lt;not possible to compute word from hash&gt;");
                }
                final plasmaSearchRankingProcess ranking = plasmaSearchAPI.genSearchresult(prop, sb, keyhash, null);
                if (ranking.filteredCount() == 0) {
                    prop.put("searchresult", 2);
                    prop.putHTML("searchresult_wordhash", keyhash);
                }
            }
            
            // delete everything
            if (post.containsKey("deletecomplete") && post.containsKey("confirmDelete")) {
                sb.webIndex.clear();
                sb.crawlQueues.clear();
                sb.crawlStacker.clear();
                try {
                    sb.robots.clear();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
                post.remove("deletecomplete");
            }
    
            // delete word
            if (post.containsKey("keyhashdeleteall")) {
                if (delurl || delurlref) {
                    // generate an urlx array
                    ReferenceContainer index = null;
                    index = sb.webIndex.getReferences(keyhash, null);
                    final Iterator<ReferenceRow> en = index.entries();
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
                sb.webIndex.deleteAllReferences(keyhash);
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
                final Set<String> urlHashes = new HashSet<String>();
                for (int i = 0; i < urlx.length; i++) urlHashes.add(urlx[i]);
                sb.webIndex.removeReferences(keyhash, urlHashes);
                // this shall lead to a presentation of the list; so handle that the remaining program
                // thinks that it was called for a list presentation
                post.remove("keyhashdelete");
                post.put("urllist", "generated");
            }
            
            if (post.containsKey("urllist")) {
                if (keystring.length() == 0 || !Word.word2hash(keystring).equals(keyhash)) {
                    prop.put("keystring", "&lt;not possible to compute word from hash&gt;");
                }
                final Bitfield flags = plasmaSearchAPI.compileFlags(post);
                final int count = (post.get("lines", "all").equals("all")) ? -1 : post.getInt("lines", -1);
                final plasmaSearchRankingProcess ranking = plasmaSearchAPI.genSearchresult(prop, sb, keyhash, flags);
                plasmaSearchAPI.genURLList(prop, keyhash, keystring, ranking, flags, count);
            }

            // transfer to other peer
            if (post.containsKey("keyhashtransfer")) {
                if (keystring.length() == 0 || !Word.word2hash(keystring).equals(keyhash)) {
                    prop.put("keystring", "&lt;not possible to compute word from hash&gt;");
                }
                
                // find host & peer
                String host = post.get("host", ""); // get host from input field
                yacySeed seed = null;
                if (host.length() != 0) {
                    if (host.length() == 12) {
                        // the host string is a peer hash
                        seed = sb.webIndex.peers().getConnected(host);
                    } else {
                        // the host string can be a host name
                        seed = sb.webIndex.peers().lookupByName(host);
                    }
                } else {
                    host = post.get("hostHash", ""); // if input field is empty, get from select box
                    seed = sb.webIndex.peers().getConnected(host);
                }
                
                // prepare index
                ReferenceContainer index;
                final long starttime = System.currentTimeMillis();
                index = sb.webIndex.getReferences(keyhash, null);
                // built urlCache
                final Iterator<ReferenceRow> urlIter = index.entries();
                final HashMap<String, MetadataRowContainer> knownURLs = new HashMap<String, MetadataRowContainer>();
                final HashSet<String> unknownURLEntries = new HashSet<String>();
                Reference iEntry;
                MetadataRowContainer lurl;
                while (urlIter.hasNext()) {
                    iEntry = urlIter.next();
                    lurl = sb.webIndex.metadata().load(iEntry.urlHash(), null, 0);
                    if (lurl == null) {
                        unknownURLEntries.add(iEntry.urlHash());
                        urlIter.remove();
                    } else {
                        knownURLs.put(iEntry.urlHash(), lurl);
                    }
                }
                
                // make an indexContainerCache
                ReferenceContainerCache icc = new ReferenceContainerCache(index.rowdef);
                icc.addReferences(index);
                
                // transport to other peer
                final String gzipBody = sb.getConfig("indexControl.gzipBody","false");
                final int timeout = (int) sb.getConfigLong("indexControl.timeout",60000);
                final String error = yacyClient.transferIndex(
                             seed,
                             icc,
                             knownURLs,
                             "true".equalsIgnoreCase(gzipBody),
                             timeout);
                prop.put("result", (error == null) ? ("Successfully transferred " + knownURLs.size() + " words in " + ((System.currentTimeMillis() - starttime) / 1000) + " seconds, " + unknownURLEntries + " URL not found") : "error: " + error);
                index = null;
            }
    
            // generate list
            if (post.containsKey("keyhashsimilar")) {
                final Iterator<ReferenceContainer> containerIt = sb.webIndex.indexContainerSet(keyhash, false, true, 256).iterator();
                    ReferenceContainer container;
                    int i = 0;
                    int rows = 0, cols = 0;
                    prop.put("keyhashsimilar", "1");
                    while (containerIt.hasNext() && i < 256) {
                        container = containerIt.next();
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
                final String blacklist = post.get("blacklist", "");
                final Set<String> urlHashes = new HashSet<String>();
                if (post.containsKey("blacklisturls")) {
                    PrintWriter pw;
                    try {
                        final String[] supportedBlacklistTypes = env.getConfig("BlackLists.types", "").split(",");
                        pw = new PrintWriter(new FileWriter(new File(listManager.listsPath, blacklist), true));
                        yacyURL url;
                        for (int i=0; i<urlx.length; i++) {
                            urlHashes.add(urlx[i]);
                            final MetadataRowContainer e = sb.webIndex.metadata().load(urlx[i], null, 0);
                            sb.webIndex.metadata().remove(urlx[i]);
                            if (e != null) {
                                url = e.metadata().url();
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
                    } catch (final IOException e) {
                    }
                }
                
                if (post.containsKey("blacklistdomains")) {
                    PrintWriter pw;
                    try {
                        final String[] supportedBlacklistTypes = AbstractBlacklist.BLACKLIST_TYPES_STRING.split(",");
                        pw = new PrintWriter(new FileWriter(new File(listManager.listsPath, blacklist), true));
                        yacyURL url;
                        for (int i=0; i<urlx.length; i++) {
                            urlHashes.add(urlx[i]);
                            final MetadataRowContainer e = sb.webIndex.metadata().load(urlx[i], null, 0);
                            sb.webIndex.metadata().remove(urlx[i]);
                            if (e != null) {
                                url = e.metadata().url();
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
                    } catch (final IOException e) {
                    }
                }
                sb.webIndex.removeReferences(keyhash, urlHashes);
            }
        
            if (prop.getInt("searchresult", 0) == 3) plasmaSearchAPI.listHosts(prop, keyhash, sb);
        }
        

        // insert constants
        prop.putNum("wcount", sb.webIndex.size());
        // return rewrite properties
        return prop;
    }
    
}
