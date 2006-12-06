// IndexControl_p.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../classes IndexControl_p.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.http.httpHeader;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverCodings;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class IndexControl_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        
        serverObjects prop = new serverObjects();

        if (post == null || env == null) {
            prop.put("keystring", "");
            prop.put("keyhash", "");
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            prop.put("result", "");
            prop.put("wcount", Integer.toString(switchboard.wordIndex.size()));
            prop.put("ucount", Integer.toString(switchboard.wordIndex.loadedURL.size()));
            prop.put("otherHosts", "");
            prop.put("indexDistributeChecked", (switchboard.getConfig("allowDistributeIndex", "true").equals("true")) ? "checked" : "");
            prop.put("indexDistributeWhileCrawling", (switchboard.getConfig("allowDistributeIndexWhileCrawling", "true").equals("true")) ? "checked" : "");
            prop.put("indexReceiveChecked", (switchboard.getConfig("allowReceiveIndex", "true").equals("true")) ? "checked" : "");
            prop.put("indexReceiveBlockBlacklistChecked", (switchboard.getConfig("indexReceiveBlockBlacklist", "true").equals("true")) ? "checked" : "");
            prop.put("peertags", serverCodings.set2string(yacyCore.seedDB.mySeed.getPeerTags(), ",", false));
            return prop; // be save
        }

        // default values
        String keystring = ((String) post.get("keystring", "")).trim();
        String keyhash = ((String) post.get("keyhash", "")).trim();
        String urlstring = ((String) post.get("urlstring", "")).trim();
        String urlhash = ((String) post.get("urlhash", "")).trim();

        if (!urlstring.startsWith("http://") &&
            !urlstring.startsWith("https://")) { urlstring = "http://" + urlstring; }

        prop.put("keystring", keystring);
        prop.put("keyhash", keyhash);
        prop.put("urlstring", urlstring);
        prop.put("urlhash", urlhash);
        prop.put("result", " ");

        // read values from checkboxes
        String[] urlx = post.getAll("urlhx.*");
        boolean delurl    = post.containsKey("delurl");
        boolean delurlref = post.containsKey("delurlref");
//      System.out.println("DEBUG CHECK: " + ((delurl) ? "delurl" : "") + " " + ((delurlref) ? "delurlref" : ""));

        // DHT control
        if (post.containsKey("setIndexTransmission")) {
            if (post.get("indexDistribute", "").equals("on")) {
                switchboard.setConfig("allowDistributeIndex", "true");
            } else {
                switchboard.setConfig("allowDistributeIndex", "false");
            }

            if (post.get("indexDistributeWhileCrawling","").equals("on")) {
                switchboard.setConfig("allowDistributeIndexWhileCrawling", "true");
            } else {
                switchboard.setConfig("allowDistributeIndexWhileCrawling", "false");
            }

            if (post.get("indexReceive", "").equals("on")) {
                switchboard.setConfig("allowReceiveIndex", "true");
                yacyCore.seedDB.mySeed.setFlagAcceptRemoteIndex(true);
            } else {
                switchboard.setConfig("allowReceiveIndex", "false");
                yacyCore.seedDB.mySeed.setFlagAcceptRemoteIndex(false);
            }

            if (post.get("indexReceiveBlockBlacklist", "").equals("on")) {
                switchboard.setConfig("indexReceiveBlockBlacklist", "true");
            } else {
                switchboard.setConfig("indexReceiveBlockBlacklist", "false");
            }
            
            if (post.containsKey("peertags")) {
                yacyCore.seedDB.mySeed.setPeerTags(serverCodings.string2set((String) post.get("peertags"), ","));
            }
        }

        // delete word
        if (post.containsKey("keyhashdeleteall")) {
            if (delurl || delurlref) {
                // generate an urlx array
                indexContainer index = null;
                index = switchboard.wordIndex.getContainer(keyhash, null, -1);
                Iterator en = index.entries();
                int i = 0;
                urlx = new String[index.size()];
                while (en.hasNext()) {
                    urlx[i++] = ((indexRWIEntry) en.next()).urlHash();
                }
                index = null;
            }
            if (delurlref) {
                for (int i = 0; i < urlx.length; i++) switchboard.removeAllUrlReferences(urlx[i], true);
            }
            if (delurl || delurlref) {
                for (int i = 0; i < urlx.length; i++) {
                    switchboard.wordIndex.loadedURL.remove(urlx[i]);
                }
            }
            switchboard.wordIndex.deleteContainer(keyhash);
            post.remove("keyhashdeleteall");
            if (keystring.length() > 0 &&
                plasmaCondenser.word2hash(keystring).equals(keyhash)) {
                post.put("keystringsearch", "generated");
            } else {
                post.put("keyhashsearch", "generated");
            }
        }

        // delete selected URLs
        if (post.containsKey("keyhashdelete")) {
            if (delurlref) {
                for (int i = 0; i < urlx.length; i++) switchboard.removeAllUrlReferences(urlx[i], true);
            }
            if (delurl || delurlref) {
                for (int i = 0; i < urlx.length; i++) {
                    switchboard.wordIndex.loadedURL.remove(urlx[i]);
                }
            }
            Set urlHashes = new HashSet();
            for (int i = 0; i < urlx.length; i++) urlHashes.add(urlx[i]);
            switchboard.wordIndex.removeEntries(keyhash, urlHashes);
            // this shall lead to a presentation of the list; so handle that the remaining program
            // thinks that it was called for a list presentation
            post.remove("keyhashdelete");
            if (keystring.length() > 0 && plasmaCondenser.word2hash(keystring).equals(keyhash)) {
                post.put("keystringsearch", "generated");
            } else {
                post.put("keyhashsearch", "generated");
//              prop.put("result", "Delete of relation of url hashes " + result + " to key hash " + keyhash);
            }
        }

        if (post.containsKey("urlhashdeleteall")) {
            //try {
                int i = switchboard.removeAllUrlReferences(urlhash, true);
                prop.put("result", "Deleted URL and " + i + " references from " + i + " word indexes.");
            //} catch (IOException e) {
            //    prop.put("result", "Deleted nothing because the url-hash could not be resolved");
            //}
        }

        if (post.containsKey("urlhashdelete")) {
            indexURLEntry entry = switchboard.wordIndex.loadedURL.load(urlhash, null);
            if (entry == null) {
                prop.put("result", "No Entry for URL hash " + urlhash + "; nothing deleted.");
            } else {
                urlstring = entry.comp().url().toNormalform();
                prop.put("urlstring", "");
                switchboard.wordIndex.loadedURL.remove(urlhash);
                prop.put("result", "Removed URL " + urlstring);
            }
        }

        if (post.containsKey("keystringsearch")) {
            keyhash = plasmaCondenser.word2hash(keystring);
            prop.put("keyhash", keyhash);
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            prop.putAll(genUrlList(switchboard, keyhash, keystring));
        }

        if (post.containsKey("keyhashsearch")) {
            if (keystring.length() == 0 || !plasmaCondenser.word2hash(keystring).equals(keyhash)) {
                prop.put("keystring", "<not possible to compute word from hash>");
            }
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            prop.putAll(genUrlList(switchboard, keyhash, ""));
        }

        // transfer to other peer
        if (post.containsKey("keyhashtransfer")) {
            if (keystring.length() == 0 || !plasmaCondenser.word2hash(keystring).equals(keyhash)) {
                prop.put("keystring", "<not possible to compute word from hash>");
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
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            indexContainer index;
            String result;
            long starttime = System.currentTimeMillis();
            index = switchboard.wordIndex.getContainer(keyhash, null, -1);
            // built urlCache
            Iterator urlIter = index.entries();
            HashMap knownURLs = new HashMap();
            HashSet unknownURLEntries = new HashSet();
            indexRWIEntry iEntry;
            indexURLEntry lurl;
            while (urlIter.hasNext()) {
                iEntry = (indexRWIEntry) urlIter.next();
                lurl = switchboard.wordIndex.loadedURL.load(iEntry.urlHash(), null);
                if (lurl == null) {
                    unknownURLEntries.add(iEntry.urlHash());
                    urlIter.remove();
                } else {
                    knownURLs.put(iEntry.urlHash(), lurl);
                }
            }
            
            // transport to other peer
            String gzipBody = switchboard.getConfig("indexControl.gzipBody","false");
            int timeout = (int) switchboard.getConfigLong("indexControl.timeout",60000);
            HashMap resultObj = yacyClient.transferIndex(
                         seed,
                         new indexContainer[]{index},
                         knownURLs,
                         "true".equalsIgnoreCase(gzipBody),
                         timeout);
            result = (String) resultObj.get("result");
            prop.put("result", (result == null) ? ("Successfully transferred " + index.size() + " words in " + ((System.currentTimeMillis() - starttime) / 1000) + " seconds") : result);
            index = null;
        }

        // generate list
        if (post.containsKey("keyhashsimilar")) {
            final Iterator containerIt = switchboard.wordIndex.indexContainerSet(keyhash, false, true, 256).iterator();
                indexContainer container;
                int i = 0;
                int rows = 0, cols = 0;
                prop.put("keyhashsimilar", 1);
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

        if (post.containsKey("urlstringsearch")) {
            try {
                URL url = new URL(urlstring);
                urlhash = plasmaURL.urlHash(url);
                prop.put("urlhash", urlhash);
                indexURLEntry entry = switchboard.wordIndex.loadedURL.load(urlhash, null);
                if (entry == null) {
                    prop.put("urlstring", "unknown url: " + urlstring);
                    prop.put("urlhash", "");
                } else {
                    prop.putAll(genUrlProfile(switchboard, entry, urlhash));
                }
            } catch (MalformedURLException e) {
                prop.put("urlstring", "bad url: " + urlstring);
                prop.put("urlhash", "");
            }
        }

        if (post.containsKey("urlhashsearch")) {
            indexURLEntry entry = switchboard.wordIndex.loadedURL.load(urlhash, null);
            if (entry == null) {
                prop.put("result", "No Entry for URL hash " + urlhash);
            } else {
                prop.put("urlstring", entry.comp().url().toNormalform());
                prop.putAll(genUrlProfile(switchboard, entry, urlhash));
            }
        }

        // generate list
        if (post.containsKey("urlhashsimilar")) {
            try {
                final Iterator entryIt = switchboard.wordIndex.loadedURL.entries(true, true, urlhash); 
                StringBuffer result = new StringBuffer("Sequential List of URL-Hashes:<br>");
                indexURLEntry entry;
                int i = 0;
                int rows = 0, cols = 0;
                prop.put("urlhashsimilar", 1);
                while (entryIt.hasNext() && i < 256) {
                    entry = (indexURLEntry) entryIt.next();
                    prop.put("urlhashsimilar_rows_"+rows+"_cols_"+cols+"_urlHash", entry.hash());
                    cols++;
                    if (cols==8) {
                        prop.put("urlhashsimilar_rows_"+rows+"_cols", cols);
                        cols = 0;
                        rows++;
                    }
                    i++;
                }
                prop.put("urlhashsimilar_rows", rows);
                prop.put("result", result.toString());
            } catch (IOException e) {
                prop.put("result", "No Entries for URL hash " + urlhash);
            }
        }

        // list known hosts
        yacySeed seed;
        int hc = 0;
        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) {
            Enumeration e = yacyCore.dhtAgent.getAcceptRemoteIndexSeeds(keyhash);
            while (e.hasMoreElements()) {
                seed = (yacySeed) e.nextElement();
                if (seed != null) {
                    prop.put("hosts_" + hc + "_hosthash", seed.hash);
                    prop.put("hosts_" + hc + "_hostname", seed.hash + " " + seed.get(yacySeed.NAME, "nameless"));
                    hc++;
                }
            }
            prop.put("hosts", Integer.toString(hc));
        } else {
            prop.put("hosts", "0");
        }

        // insert constants
        prop.put("wcount", Integer.toString(switchboard.wordIndex.size()));
        prop.put("ucount", Integer.toString(switchboard.wordIndex.loadedURL.size()));
        prop.put("indexDistributeChecked", (switchboard.getConfig("allowDistributeIndex", "true").equals("true")) ? "checked" : "");
        prop.put("indexDistributeWhileCrawling", (switchboard.getConfig("allowDistributeIndexWhileCrawling", "true").equals("true")) ? "checked" : "");
        prop.put("indexReceiveChecked", (switchboard.getConfig("allowReceiveIndex", "true").equals("true")) ? "checked" : "");
        prop.put("indexReceiveBlockBlacklistChecked", (switchboard.getConfig("indexReceiveBlockBlacklist", "true").equals("true")) ? "checked" : "");
        prop.put("peertags", serverCodings.set2string(yacyCore.seedDB.mySeed.getPeerTags(), ",", false));
        // return rewrite properties
        return prop;
    }

    public static serverObjects genUrlProfile(plasmaSwitchboard switchboard, indexURLEntry entry, String urlhash) {
        serverObjects prop = new serverObjects();
        if (entry == null) {
            prop.put("genUrlProfile", 1);
            prop.put("genUrlProfile_urlhash", urlhash);
            return prop;
        }
        indexURLEntry.Components comp = entry.comp();
        String referrer = null;
        indexURLEntry le = switchboard.wordIndex.loadedURL.load(entry.referrerHash(), null);
        if (le == null) {
            referrer = "<unknown>";
        } else {
            referrer = le.comp().url().toNormalform();
        }
        if (comp.url() == null) {
            prop.put("genUrlProfile", 1);
            prop.put("genUrlProfile_urlhash", urlhash);
            return prop;
        }
        prop.put("genUrlProfile", 2);
        prop.put("genUrlProfile_urlNormalform", comp.url().toNormalform());
        prop.put("genUrlProfile_urlhash", urlhash);
        prop.put("genUrlProfile_urlDescr", comp.descr());
        prop.put("genUrlProfile_moddate", entry.moddate());
        prop.put("genUrlProfile_loaddate", entry.loaddate());
        prop.put("genUrlProfile_referrer", referrer);
        prop.put("genUrlProfile_doctype", ""+entry.doctype());
        prop.put("genUrlProfile_language", entry.language());
        prop.put("genUrlProfile_size", entry.size());
        prop.put("genUrlProfile_wordCount", entry.wordCount());
        return prop;
    }

    public static serverObjects genUrlList(plasmaSwitchboard switchboard, String keyhash, String keystring) {
        // search for a word hash and generate a list of url links
        serverObjects prop = new serverObjects();
        indexContainer index = null;
        try {
            index = switchboard.wordIndex.getContainer(keyhash, null, -1);

            prop.put("genUrlList_keyHash", keyhash);
            
            if ((index == null) || (index.size() == 0)) {
                prop.put("genUrlList", 1);
                prop.put("genUrlList_count", 0);
            } else {
                final Iterator en = index.entries();
                prop.put("genUrlList", 2);
                String us;
                String uh[] = new String[2];
                int i = 0;

                final TreeMap tm = new TreeMap();
                indexRWIEntry xi;
                while (en.hasNext()) {
                    xi = (indexRWIEntry) en.next();
                    uh = new String[]{xi.urlHash(), Integer.toString(xi.posintext())};
                    indexURLEntry le = switchboard.wordIndex.loadedURL.load(uh[0], null);
                    if (le == null) {
                        tm.put(uh[0], uh);
                    } else {
                        us = le.comp().url().toNormalform();
                        tm.put(us, uh);

                    }
                }

                URL url;
                final Iterator iter = tm.keySet().iterator();
                while (iter.hasNext()) {
                    us = iter.next().toString();
                    uh = (String[]) tm.get(us);
                    if (us.equals(uh[0])) {
                        prop.put("genUrlList_urlList_"+i+"_urlExists", 0);
                        prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxCount", i);
                        prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxValue", uh[0]);
                    } else {
                        prop.put("genUrlList_urlList_"+i+"_urlExists", 1);
                        prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxCount", i);
                        prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxValue", uh[0]);
                        prop.put("genUrlList_urlList_"+i+"_urlExists_keyString", keystring);
                        prop.put("genUrlList_urlList_"+i+"_urlExists_keyHash", keyhash);
                        prop.put("genUrlList_urlList_"+i+"_urlExists_urlString", us);
                        prop.put("genUrlList_urlList_"+i+"_urlExists_pos", uh[1]);
                        url = new URL(us);
                        if (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_DHT, url)) {
                            prop.put("genUrlList_urlList_"+i+"_urlExists_urlhxChecked", 1);
                        }
                    }
                    i++;
                }
                prop.put("genUrlList_urlList", i);
                prop.put("genUrlList_keyString", keystring);
                prop.put("genUrlList_count", i);
            }
            index = null;
            return prop;
        }  catch (IOException e) {
            return prop;
        } finally {
            if (index != null) index = null;
        }
    }

}
