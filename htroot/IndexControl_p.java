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
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaWordIndexEntity;
import de.anomic.plasma.plasmaWordIndexEntry;
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
            prop.put("ucount", Integer.toString(switchboard.urlPool.loadedURL.size()));
            prop.put("otherHosts", "");
            prop.put("indexDistributeChecked", (switchboard.getConfig("allowDistributeIndex", "true").equals("true")) ? "checked" : "");
            prop.put("indexDistributeWhileCrawling", (switchboard.getConfig("allowDistributeIndexWhileCrawling", "true").equals("true")) ? "checked" : "");
            prop.put("indexReceiveChecked", (switchboard.getConfig("allowReceiveIndex", "true").equals("true")) ? "checked" : "");
            prop.put("indexReceiveBlockBlacklistChecked", (switchboard.getConfig("indexReceiveBlockBlacklist", "true").equals("true")) ? "checked" : "");
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
        prop.put("result", "");

        // read values from checkboxes
        String[] urlx = post.getAll("urlhx.*");
        boolean delurl    = post.containsKey("delurl");
        boolean delurlref = post.containsKey("delurlref");
//      System.out.println("DEBUG CHECK: " + ((delurl) ? "delurl" : "") + " " + ((delurlref) ? "delurlref" : ""));

        // DHT control
        if (post.containsKey("setIndexTransmission")) {
            if (post.get("indexDistribute", "").equals("on")) {
                switchboard.setConfig("allowDistributeIndex", "true");
                switchboard.indexDistribution.enable();
            } else {
                switchboard.setConfig("allowDistributeIndex", "false");
                switchboard.indexDistribution.disable();
            }

            if (post.containsKey("indexDistributeWhileCrawling")) {
                switchboard.setConfig("allowDistributeIndexWhileCrawling", "true");
                switchboard.indexDistribution.enableWhileCrawling();
            } else {
                switchboard.setConfig("allowDistributeIndexWhileCrawling", "false");
                switchboard.indexDistribution.disableWhileCrawling();
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
        }

        // delete word
        if (post.containsKey("keyhashdeleteall")) {
            if (delurl || delurlref) {
                // generate an urlx array
                plasmaWordIndexEntity index = null;
                try {
                    index = switchboard.wordIndex.getEntity(keyhash, true, -1);
                    Iterator en = index.elements(true);
                    int i = 0;
                    urlx = new String[index.size()];
                    while (en.hasNext()) {
                        urlx[i++] = ((plasmaWordIndexEntry) en.next()).getUrlHash();
                    }
                    index.close();
                    index = null;
                } catch (IOException e) {
                    urlx = new String[0];
                } finally {
                    if (index != null) try { index.close(); } catch (Exception e) {}
                }
            }
            if (delurlref) {
                for (int i = 0; i < urlx.length; i++) switchboard.removeAllUrlReferences(urlx[i], true);
            }
            if (delurl || delurlref) {
                for (int i = 0; i < urlx.length; i++) {
                    switchboard.urlPool.loadedURL.remove(urlx[i]);
                }
            }
            switchboard.wordIndex.deleteIndex(keyhash);
            post.remove("keyhashdeleteall");
            if (keystring.length() > 0 &&
                plasmaWordIndexEntry.word2hash(keystring).equals(keyhash)) {
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
                    switchboard.urlPool.loadedURL.remove(urlx[i]);
                }
            }
            switchboard.wordIndex.removeEntries(keyhash, urlx, true);
            // this shall lead to a presentation of the list; so handle that the remaining program
            // thinks that it was called for a list presentation
            post.remove("keyhashdelete");
            if (keystring.length() > 0 &&
                plasmaWordIndexEntry.word2hash(keystring).equals(keyhash)) {
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
            try {
                plasmaCrawlLURL.Entry entry = switchboard.urlPool.loadedURL.getEntry(urlhash);
                URL url = entry.url();
                urlstring = htmlFilterContentScraper.urlNormalform(url);
                prop.put("urlstring", "");
                switchboard.urlPool.loadedURL.remove(urlhash);
                prop.put("result", "Removed URL " + urlstring);
            } catch (IOException e) {
                prop.put("result", "No Entry for URL hash " + urlhash + "; nothing deleted.");
            }
        }

        if (post.containsKey("keystringsearch")) {
            keyhash = plasmaWordIndexEntry.word2hash(keystring);
            prop.put("keyhash", keyhash);
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            prop.put("result", genUrlList(switchboard, keyhash, keystring));
        }

        if (post.containsKey("keyhashsearch")) {
            if (keystring.length() == 0 ||
                !plasmaWordIndexEntry.word2hash(keystring).equals(keyhash)) {
                prop.put("keystring", "<not possible to compute word from hash>");
            }
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            prop.put("result", genUrlList(switchboard, keyhash, ""));
        }

        // transfer to other peer
        if (post.containsKey("keyhashtransfer")) {
            if (keystring.length() == 0 ||
                !plasmaWordIndexEntry.word2hash(keystring).equals(keyhash)) {
                prop.put("keystring", "<not possible to compute word from hash>");
            }
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            plasmaWordIndexEntity[] indexes = new plasmaWordIndexEntity[1];
            String result;
            long starttime = System.currentTimeMillis();
            indexes[0] = switchboard.wordIndex.getEntity(keyhash, true, -1);
            // built urlCache
            Iterator urlIter = indexes[0].elements(true);
            HashMap knownURLs = new HashMap();
            HashSet unknownURLEntries = new HashSet();
            plasmaWordIndexEntry indexEntry;
            plasmaCrawlLURL.Entry lurl;
            while (urlIter.hasNext()) {
                indexEntry = (plasmaWordIndexEntry) urlIter.next();
                try {
                    lurl = switchboard.urlPool.loadedURL.getEntry(indexEntry.getUrlHash());
                    if (lurl.toString() == null) {
                        switchboard.urlPool.loadedURL.remove(indexEntry.getUrlHash());
                        unknownURLEntries.add(indexEntry.getUrlHash());
                    } else {
                        knownURLs.put(indexEntry.getUrlHash(), lurl);
                    }
                } catch (IOException e) {
                    unknownURLEntries.add(indexEntry.getUrlHash());
                }
            }
            // now delete all entries that have no url entry
            Iterator hashIter = unknownURLEntries.iterator();
            while (hashIter.hasNext()) {
                try {
                    indexes[0].removeEntry((String) hashIter.next(), false);
                } catch (IOException e) {}
            }
            // use whats remaining           
            String gzipBody = switchboard.getConfig("indexControl.gzipBody","false");
            int timeout = (int) switchboard.getConfigLong("indexControl.timeout",60000);
            result = yacyClient.transferIndex (
                         yacyCore.seedDB.getConnected(post.get("hostHash", "")),
                         indexes,
                         knownURLs,
                         "true".equalsIgnoreCase(gzipBody),
                         timeout);
            prop.put("result", (result == null) ? ("Successfully transferred " + indexes[0].size() + " words in " + ((System.currentTimeMillis() - starttime) / 1000) + " seconds") : result);
            try {indexes[0].close();} catch (IOException e) {}
        }

        // generate list
        if (post.containsKey("keyhashsimilar")) {
            final Iterator hashIt = switchboard.wordIndex.wordHashes(keyhash, true, true);
            StringBuffer result = new StringBuffer("Sequential List of Word-Hashes:<br>");
            String hash;
            int i = 0;
            while (hashIt.hasNext() && i < 256) {
                hash = (String) hashIt.next();
                result.append("<a href=\"/IndexControl_p.html?")
                      .append("keyhash=").append(hash).append("&keyhashsearch=")
                      .append("\" class=\"tt\">").append(hash).append("</a> ")
                      .append(((i + 1) % 8 == 0) ? "<br>" : "");
                i++;
            }
            prop.put("result", result);
        }

        if (post.containsKey("urlstringsearch")) {
            try {
                URL url = new URL(urlstring);
            urlhash = plasmaURL.urlHash(url);
            prop.put("urlhash", urlhash);
                plasmaCrawlLURL.Entry entry = switchboard.urlPool.loadedURL.getEntry(urlhash);
                prop.put("result", genUrlProfile(switchboard, entry, urlhash));
            } catch (MalformedURLException e) {
                prop.put("urlstring", "bad url: " + urlstring);
                prop.put("urlhash", "");
            } catch (IOException e) {
                prop.put("urlstring", "unknown url: " + urlstring);
                prop.put("urlhash", "");
            }
        }

        if (post.containsKey("urlhashsearch")) {
            try {
                plasmaCrawlLURL.Entry entry = switchboard.urlPool.loadedURL.getEntry(urlhash);
                URL url = entry.url();
                urlstring = url.toString();
                prop.put("urlstring", urlstring);
                prop.put("result", genUrlProfile(switchboard, entry, urlhash));
            } catch (IOException e) {
                prop.put("result", "No Entry for URL hash " + urlhash);
            }
        }

        // generate list
        if (post.containsKey("urlhashsimilar")) {
            final Iterator hashIt = switchboard.urlPool.loadedURL.urlHashes(urlhash, true);
            StringBuffer result = new StringBuffer("Sequential List of URL-Hashes:<br>");
            String hash;
            int i = 0;
            while (hashIt.hasNext() && i < 256) {
                hash = (String) hashIt.next();
                result.append("<a href=\"/IndexControl_p.html?")
                .append("urlhash=").append(hash).append("&urlhashsearch=")
                .append("\" class=\"tt\">").append(hash).append("</a> ")
                .append(((i + 1) % 8 == 0) ? "<br>" : "");
                i++;
            }
            prop.put("result", result.toString());
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
                    prop.put("hosts_" + hc + "_hostname", /*seed.hash + " " +*/ seed.get(yacySeed.NAME, "nameless"));
                    hc++;
                }
            }
            prop.put("hosts", Integer.toString(hc));
        } else {
            prop.put("hosts", "0");
        }

        // insert constants
        prop.put("wcount", Integer.toString(switchboard.wordIndex.size()));
        prop.put("ucount", Integer.toString(switchboard.urlPool.loadedURL.size()));
        prop.put("indexDistributeChecked", (switchboard.getConfig("allowDistributeIndex", "true").equals("true")) ? "checked" : "");
        prop.put("indexDistributeWhileCrawling", (switchboard.getConfig("allowDistributeIndexWhileCrawling", "true").equals("true")) ? "checked" : "");
        prop.put("indexReceiveChecked", (switchboard.getConfig("allowReceiveIndex", "true").equals("true")) ? "checked" : "");
        prop.put("indexReceiveBlockBlacklistChecked", (switchboard.getConfig("indexReceiveBlockBlacklist", "true").equals("true")) ? "checked" : "");
        // return rewrite properties
        return prop;
    }

    public static String genUrlProfile(plasmaSwitchboard switchboard, plasmaCrawlLURL.Entry entry, String urlhash) {
        if (entry == null) { return "No entry found for URL-hash " + urlhash; }
        URL url = entry.url();
        String referrer = null;
        try {
            referrer = switchboard.urlPool.loadedURL.getEntry(entry.referrerHash()).url().toString();
        } catch (IOException e) {
            referrer = "<unknown>";
        }
        if (url == null) { return "No entry found for URL-hash " + urlhash; }
        String result = "<table>" +
        "<tr><td class=\"small\">URL String</td><td class=\"tt\">" + htmlFilterContentScraper.urlNormalform(url) + "</td></tr>" +
        "<tr><td class=\"small\">Hash</td><td class=\"tt\">" + urlhash + "</td></tr>" +
        "<tr><td class=\"small\">Description</td><td class=\"tt\">" + entry.descr() + "</td></tr>" +
        "<tr><td class=\"small\">Modified-Date</td><td class=\"tt\">" + entry.moddate() + "</td></tr>" +
        "<tr><td class=\"small\">Loaded-Date</td><td class=\"tt\">" + entry.loaddate() + "</td></tr>" +
        "<tr><td class=\"small\">Referrer</td><td class=\"tt\">" + referrer + "</td></tr>" +
        "<tr><td class=\"small\">Doctype</td><td class=\"tt\">" + entry.doctype() + "</td></tr>" +
        "<tr><td class=\"small\">Copy-Count</td><td class=\"tt\">" + entry.copyCount() + "</td></tr>" +
        "<tr><td class=\"small\">Local-Flag</td><td class=\"tt\">" + entry.local() + "</td></tr>" +
        "<tr><td class=\"small\">Quality</td><td class=\"tt\">" + entry.quality() + "</td></tr>" +
        "<tr><td class=\"small\">Language</td><td class=\"tt\">" + entry.language() + "</td></tr>" +
        "<tr><td class=\"small\">Size</td><td class=\"tt\">" + entry.size() + "</td></tr>" +
        "<tr><td class=\"small\">Words</td><td class=\"tt\">" + entry.wordCount() + "</td></tr>" +
        "</table><br>";
        result +=
        "<form action=\"IndexControl_p.html\" method=\"post\" enctype=\"multipart/form-data\">" +
        "<input type=\"hidden\" name=\"keystring\" value=\"\">" +
        "<input type=\"hidden\" name=\"keyhash\" value=\"\">" +
        "<input type=\"hidden\" name=\"urlstring\" value=\"\">" +
        "<input type=\"hidden\" name=\"urlhash\" value=\"" + urlhash + "\">" +
        "<input type=\"submit\" value=\"Delete URL\" name=\"urlhashdelete\"><br>" +
        "<span class=\"small\">&nbsp;this may produce unresolved references at other word indexes but they do not harm</span><br><br>" +
        "<input type=\"submit\" value=\"Delete URL and remove all references from words\" name=\"urlhashdeleteall\"><br>" +
        "<span class=\"small\">&nbsp;delete the reference to this url at every other word where the reference exists (very extensive, but prevents unresolved references)</span><br>" +
        "</form>";
        return result;
    }

    public static String genUrlList(plasmaSwitchboard switchboard, String keyhash, String keystring) {
        // search for a word hash and generate a list of url links
        plasmaWordIndexEntity index = null;
        try {
            index = switchboard.wordIndex.getEntity(keyhash, true, -1);

            final StringBuffer result = new StringBuffer(1024);
            if (index.size() == 0) {
                result.append("No URL entries related to this word hash <span class=\"tt\">").append(keyhash).append("</span>.");
            } else {
                final Iterator en = index.elements(true);
                result.append("URL entries related to this word hash <span class=\"tt\">").append(keyhash).append("</span><br><br>");
                result.append("<form action=\"IndexControl_p.html\" method=\"post\" enctype=\"multipart/form-data\">");
                String us, uh;
                int i = 0;

                final TreeMap tm = new TreeMap();
                while (en.hasNext()) {
                    uh = ((plasmaWordIndexEntry)en.next()).getUrlHash();
                    try {
                        us = switchboard.urlPool.loadedURL.getEntry(uh).url().toString();
                        tm.put(us, uh);
                    } catch (IOException e) {
                        tm.put(uh, uh);
                    }
                }

                final Iterator iter = tm.keySet().iterator();
                result.ensureCapacity((tm.size() + 2) * 384);
                while (iter.hasNext()) {
                    us = iter.next().toString();
                    uh = (String)tm.get(us);
                    result.append("<input type=\"checkbox\" name=\"urlhx").append(i++).append("\" value=\"").append(uh).append("\" align=\"top\">");
                    if (us.equals(uh)) {
                        result.append("<span class=\"tt\">").append(uh).append("&nbsp;&lt;unresolved URL Hash&gt;</span><br>");
                    } else {
                        result.append("<a href=\"/IndexControl_p.html?").append("keystring=").append(keystring)
                              .append("&keyhash=").append(keyhash).append("&urlhash=").append(uh)
                              .append("&urlstringsearch=").append("&urlstring=").append(us).append("\" class=\"tt\">")
                              .append(uh).append("</a><span class=\"tt\">&nbsp;").append(us).append("</span><br>");
                    }
                }
                result.append("<input type=\"hidden\" name=\"keystring\" value=\"").append(keystring).append("\">")
                      .append("<input type=\"hidden\" name=\"keyhash\" value=\"").append(keyhash).append("\">")
                      .append("<input type=\"hidden\" name=\"urlstring\" value=\"\">")
                      .append("<input type=\"hidden\" name=\"urlhash\" value=\"\">")
                      .append("<br><fieldset><legend>Reference Deletion</legend><table border=\"0\" cellspacing=\"5\" cellpadding=\"5\"><tr valign=\"top\"><td><br><br>")
                      .append("<input type=\"submit\" value=\"Delete reference to selected URLs\" name=\"keyhashdelete\"><br><br>")
                      .append("<input type=\"submit\" value=\"Delete reference to ALL URLs\" name=\"keyhashdeleteall\"><span class=\"small\"><br>&nbsp;&nbsp;(= delete Word)</span>")
                      .append("</td><td width=\"150\">")
                      .append("<center><input type=\"checkbox\" name=\"delurl\" value=\"\" align=\"top\" checked></center><br>")
                      .append("<span class=\"small\">delete also the referenced URL itself (reasonable and recommended, may produce unresolved references at other word indexes but they do not harm)</span>")
                      .append("</td><td width=\"150\">")
                      .append("<center><input type=\"checkbox\" name=\"delurlref\" value=\"\" align=\"top\"></center><br>")
                      .append("<span class=\"small\">for every resolveable and deleted URL reference, delete the same reference at every other word where the reference exists (very extensive, but prevents further unresolved references)</span>")
                      .append("</td></tr></table></fieldset></form><br>");
            }
            index.close();
            index = null;
            return result.toString();
        }  catch (IOException e) {
            return "";
        } finally {
            if (index != null) try { index.close(); index = null; } catch (Exception e) {};
        }
    }

}