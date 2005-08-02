// IndexControl_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 02.05.2004
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
// javac -classpath .:../Classes IndexControl_p.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;

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

        if ((post == null) || (env == null)) {
            prop.put("keystring", "");
            prop.put("keyhash", "");
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            prop.put("result", "");
            prop.put("wcount", "" + switchboard.wordIndex.size());
            prop.put("ucount", "" + switchboard.urlPool.loadedURL.size());
            prop.put("otherHosts", "");
            prop.put("indexDistributeChecked", (switchboard.getConfig("allowDistributeIndex", "true").equals("true")) ? "checked" : "");
            prop.put("indexReceiveChecked", (switchboard.getConfig("allowReceiveIndex", "true").equals("true")) ? "checked" : "");
            prop.put("indexReceiveBlockBlacklistChecked", (switchboard.getConfig("indexReceiveBlockBlacklist", "true").equals("true")) ? "checked" : "");
            return prop; // be save
        }
        
        // default values
        String keystring = ((String) post.get("keystring")).trim();
        String keyhash = ((String) post.get("keyhash")).trim();
        String urlstring = ((String) post.get("urlstring")).trim();
        String urlhash = ((String) post.get("urlhash")).trim();
        
        if ((!(urlstring.startsWith("http://"))) && (!(urlstring.startsWith("https://")))) urlstring = "http://" + urlstring;
        
        prop.put("keystring", keystring);
        prop.put("keyhash", keyhash);
        prop.put("urlstring", urlstring);
        prop.put("urlhash", urlhash);
        prop.put("result", "");
        
        // read values from checkboxes
        String[] urlx = post.getAll("urlhx.*");
        boolean delurl    = post.containsKey("delurl");
        boolean delurlref = post.containsKey("delurlref");
        //System.out.println("DEBUG CHECK: " + ((delurl) ? "delurl" : "") + " " + ((delurlref) ? "delurlref" : ""));
        
        if (post.containsKey("setIndexTransmission")) {
            boolean allowDistributeIndex = ((String) post.get("indexDistribute", "")).equals("on");
            switchboard.setConfig("allowDistributeIndex", (allowDistributeIndex) ? "true" : "false");
            if (allowDistributeIndex) switchboard.indexDistribution.enable(); else switchboard.indexDistribution.disable(); 
            boolean allowReceiveIndex = ((String) post.get("indexReceive", "")).equals("on");
            switchboard.setConfig("allowReceiveIndex", (allowReceiveIndex) ? "true" : "false");
            yacyCore.seedDB.mySeed.setFlagAcceptRemoteIndex(allowReceiveIndex);
            boolean indexReceiveBlockBlacklist = ((String) post.get("indexReceiveBlockBlacklist", "")).equals("on");
            switchboard.setConfig("indexReceiveBlockBlacklist", (indexReceiveBlockBlacklist) ? "true" : "false");
        }
        
        if (post.containsKey("keyhashdeleteall")) {
            if ((delurl) || (delurlref)) {
                // generate an urlx array
                try {
                    HashSet keyhashes = new HashSet();
                    keyhashes.add(keyhash);
                    plasmaWordIndexEntity index = switchboard.searchManager.searchHashes(keyhashes, 10000);
                    Enumeration en = index.elements(true);
                    int i = 0;
                    urlx = new String[index.size()];
                    while (en.hasMoreElements()) urlx[i++] = ((plasmaWordIndexEntry) en.nextElement()).getUrlHash();
		    index.close();
                } catch (IOException e) {
                    urlx = new String[0];
                }
            }
            if (delurlref) for (int i = 0; i < urlx.length; i++) switchboard.removeAllUrlReferences(urlx[i], true);
            if ((delurl) || (delurlref)) for (int i = 0; i < urlx.length; i++) switchboard.urlPool.loadedURL.remove(urlx[i]);
            switchboard.wordIndex.deleteIndex(keyhash);
            post.remove("keyhashdeleteall");
            if ((keystring.length() > 0) && (plasmaWordIndexEntry.word2hash(keystring).equals(keyhash)))
                post.put("keystringsearch", "generated");
            else
                post.put("keyhashsearch", "generated");
        }
        
        if (post.containsKey("keyhashdelete")) {
            if (delurlref) for (int i = 0; i < urlx.length; i++) switchboard.removeAllUrlReferences(urlx[i], true);
            if ((delurl) || (delurlref)) for (int i = 0; i < urlx.length; i++) switchboard.urlPool.loadedURL.remove(urlx[i]);
            switchboard.wordIndex.removeEntries(keyhash, urlx, true);
            // this shall lead to a presentation of the list; so handle that the remaining program
            // thinks that it was called for a list presentation
            post.remove("keyhashdelete");
            if ((keystring.length() > 0) && (plasmaWordIndexEntry.word2hash(keystring).equals(keyhash)))
                post.put("keystringsearch", "generated");
            else
                post.put("keyhashsearch", "generated");
            //prop.put("result", "Delete of relation of url hashes " + result + " to key hash " + keyhash);
        }
        
        if (post.containsKey("urlhashdeleteall")) {
            int i = switchboard.removeAllUrlReferences(urlhash, true);
            prop.put("result", "Deleted URL and " + i + " references from " + i + " word indexes.");
        }
        
        if (post.containsKey("urlhashdelete")) {
            plasmaCrawlLURL.Entry entry = switchboard.urlPool.loadedURL.getEntry(urlhash);
            URL url = entry.url();
            if (url == null) {
                prop.put("result", "No Entry for URL hash " + urlhash + "; nothing deleted.");
            } else {
                urlstring = htmlFilterContentScraper.urlNormalform(url);
                prop.put("urlstring", "");
                switchboard.urlPool.loadedURL.remove(urlhash);
                prop.put("result", "Removed URL " + urlstring);
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
            if ((keystring.length() == 0) || (!(plasmaWordIndexEntry.word2hash(keystring).equals(keyhash))))
                prop.put("keystring", "<not possible to compute word from hash>");
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            prop.put("result", genUrlList(switchboard, keyhash, ""));
        }
        
        if (post.containsKey("keyhashtransfer")) {
            if ((keystring.length() == 0) || (!(plasmaWordIndexEntry.word2hash(keystring).equals(keyhash))))
                prop.put("keystring", "<not possible to compute word from hash>");
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            plasmaWordIndexEntity[] indexes = new plasmaWordIndexEntity[1];
            String result;
            long starttime = System.currentTimeMillis();
            indexes[0] = switchboard.wordIndex.getEntity(keyhash, true);
            result = yacyClient.transferIndex(yacyCore.seedDB.getConnected(post.get("hostHash", "")), indexes, switchboard.urlPool.loadedURL);
            prop.put("result", (result == null) ? ("Successfully transferred " + indexes[0].size() + " words in " + ((System.currentTimeMillis() - starttime) / 1000) + " seconds") : result);
	    try {indexes[0].close();} catch (IOException e) {}
        }
        
	if (post.containsKey("keyhashsimilar")) {
                Iterator hashIt = switchboard.wordIndex.wordHashes(keyhash, true, true);
                String result = "Sequential List of Word-Hashes:<br>";
                String hash;
                int i = 0;
                while ((hashIt.hasNext()) && (i < 256)) {
                    hash = (String) hashIt.next();
                    result += "<a href=\"/IndexControl_p.html?" + 
                    "keystring=" +
                    "&keyhash=" + hash +
                    "&urlhash=" +
                    "&urlstring=" +
                    "&keyhashsearch=" +
                    "\" class=\"tt\">" + hash + "</a> " + (((i + 1) % 8 == 0) ? "<br>" : "");
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
                prop.put("urlstring", "wrong url: " + urlstring);
                prop.put("urlhash", "");
            }
        }
        
        if (post.containsKey("urlhashsearch")) {
            plasmaCrawlLURL.Entry entry = switchboard.urlPool.loadedURL.getEntry(urlhash);
            URL url = entry.url();
            if (url == null) {
                prop.put("result", "No Entry for URL hash " + urlhash);
            } else {
                urlstring = url.toString();
                prop.put("urlstring", urlstring);
                prop.put("result", genUrlProfile(switchboard, entry, urlhash));
            }
        }

	if (post.containsKey("urlhashsimilar")) {
            try {
                Iterator hashIt = switchboard.urlPool.loadedURL.urlHashes(urlhash, true);
                String result = "Sequential List of URL-Hashes:<br>";
		String hash;
		int i = 0;
                while ((hashIt.hasNext()) && (i < 256)) {
		    hash = (String) hashIt.next();
                    result += "<a href=\"/IndexControl_p.html?" + 
                    "keystring=" +
                    "&keyhash=" +
                    "&urlhash=" + hash +
                    "&urlstring=" + 
                    "&urlhashsearch=" +
                    "\" class=\"tt\">" + hash + "</a> " + (((i + 1) % 8 == 0) ? "<br>" : "");
                    i++;
		}
                prop.put("result", result);
            } catch (IOException e) {
                prop.put("result", "Error: " + e.getMessage());
            }
        }

        //List known hosts
	yacySeed seed;
        int hc = 0;
	if ((yacyCore.seedDB != null) && (yacyCore.seedDB.sizeConnected() > 0)) {
	    Enumeration e = yacyCore.dhtAgent.getAcceptRemoteIndexSeeds(keyhash);
	    while (e.hasMoreElements()) {
		seed = (yacySeed) e.nextElement();
                if (seed != null) {
                    prop.put("hosts_" + hc + "_hosthash", seed.hash);
                    prop.put("hosts_" + hc + "_hostname", /*seed.hash + " " +*/ seed.get("Name", "nameless"));
                    hc++;
                }
	    }
            prop.put("hosts", "" + hc);
	} else {
            prop.put("hosts", "0");
	}
        
        // insert constants
        prop.put("wcount", "" + switchboard.wordIndex.size());
        prop.put("ucount", "" + switchboard.urlPool.loadedURL.size());
	prop.put("indexDistributeChecked", (switchboard.getConfig("allowDistributeIndex", "true").equals("true")) ? "checked" : "");
        prop.put("indexReceiveChecked", (switchboard.getConfig("allowReceiveIndex", "true").equals("true")) ? "checked" : "");
        prop.put("indexReceiveBlockBlacklistChecked", (switchboard.getConfig("indexReceiveBlockBlacklist", "true").equals("true")) ? "checked" : "");
        // return rewrite properties
	return prop;
    }

    public static String genUrlProfile(plasmaSwitchboard switchboard, plasmaCrawlLURL.Entry entry, String urlhash) {
        if (entry == null) return "No entry found for URL-hash " + urlhash;
        URL url = entry.url();
        if (url == null) return "No entry found for URL-hash " + urlhash;
        String result = "<table>" +
        "<tr><td class=\"small\">URL String</td><td class=\"tt\">" + htmlFilterContentScraper.urlNormalform(url) + "</td></tr>" +
        "<tr><td class=\"small\">Hash</td><td class=\"tt\">" + urlhash + "</td></tr>" +
        "<tr><td class=\"small\">Description</td><td class=\"tt\">" + entry.descr() + "</td></tr>" +
        "<tr><td class=\"small\">Modified-Date</td><td class=\"tt\">" + entry.moddate() + "</td></tr>" +
        "<tr><td class=\"small\">Loaded-Date</td><td class=\"tt\">" + entry.loaddate() + "</td></tr>" +
        "<tr><td class=\"small\">Referrer</td><td class=\"tt\">" + switchboard.urlPool.loadedURL.getEntry(entry.referrerHash()).url() + "</td></tr>" +
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
        try {
            HashSet keyhashes = new HashSet();
            keyhashes.add(keyhash);
            plasmaWordIndexEntity index = switchboard.searchManager.searchHashes(keyhashes, 10000);
            String result = "";
            if (index.size() == 0) {
                result = "No URL entries related to this word hash <span class=\"tt\">" + keyhash + "</span>.";
            } else {
                Enumeration en = index.elements(true);
                plasmaWordIndexEntry ie;
                result = "URL entries related to this word hash <span class=\"tt\">" + keyhash + "</span>:<br>";
                result += "<form action=\"IndexControl_p.html\" method=\"post\" enctype=\"multipart/form-data\">";
                String us, uh;
                int i = 0;
                while (en.hasMoreElements()) {
                    ie = (plasmaWordIndexEntry) en.nextElement();
                    uh = ie.getUrlHash();
                    result +=
                    "<input type=\"checkbox\" name=\"urlhx" + i++ + "\" value=\"" + uh + "\" align=\"top\">";
                    if (switchboard.urlPool.loadedURL.exists(uh)) {
                        us = switchboard.urlPool.loadedURL.getEntry(uh).url().toString();
                        result +=
                        "<a href=\"/IndexControl_p.html?" + "keystring=" + keystring +
                        "&keyhash=" + keyhash + "&urlhash=" + uh + "&urlstringsearch=" + "&urlstring=" + us +
                        "\" class=\"tt\">" + uh + "</a><span class=\"tt\">&nbsp;" + us + "</span><br>";
                    } else {
                        result +=
                        "<span class=\"tt\">" + uh + "&nbsp;&lt;unresolved URL Hash&gt;</span><br>";
                    }
                }
                result +=
                "<input type=\"hidden\" name=\"keystring\" value=\"" + keystring + "\">" +
                "<input type=\"hidden\" name=\"keyhash\" value=\"" + keyhash + "\">" +
                "<input type=\"hidden\" name=\"urlstring\" value=\"\">" +
                "<input type=\"hidden\" name=\"urlhash\" value=\"\">" +
                "<br><fieldset><legend>Reference Deletion</legend><table border=\"0\" cellspacing=\"5\" cellpadding=\"5\"><tr valign=\"top\"><td><br><br>" +
                "<input type=\"submit\" value=\"Delete reference to selected URLs\" name=\"keyhashdelete\"><br><br>" +
                "<input type=\"submit\" value=\"Delete reference to ALL URLs\" name=\"keyhashdeleteall\"><span class=\"small\"><br>&nbsp;&nbsp;(= delete Word)</span>" +
                "</td><td width=\"150\">" +
                "<center><input type=\"checkbox\" name=\"delurl\" value=\"\" align=\"top\" checked></center><br>" +
                "<span class=\"small\">delete also the referenced URL itself (reasonable and recommended, may produce unresolved references at other word indexes but they do not harm)</span>" +
                "</td><td width=\"150\">" +
                "<center><input type=\"checkbox\" name=\"delurlref\" value=\"\" align=\"top\"></center><br>" +
                "<span class=\"small\">for every resolveable and deleted URL reference, delete the same reference at every other word where the reference exists (very extensive, but prevents further unresolved references)</span>" +
                "</td></tr></table></fieldset></form>";
            }
	    index.close();
            return result;
        }  catch (IOException e) {
            return "";
        }
    }
    
}
