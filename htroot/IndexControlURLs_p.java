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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRotateIterator;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class IndexControlURLs_p {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        serverObjects prop = new serverObjects();
        
        if (post == null || env == null) {
            prop.put("urlstring", "");
            prop.put("urlhash", "");
            prop.put("result", "");
            prop.put("ucount", Integer.toString(sb.wordIndex.loadedURL.size()));
            prop.put("otherHosts", "");
            return prop; // be save
        }
        
        // default values
        String urlstring = post.get("urlstring", "").trim();
        String urlhash = post.get("urlhash", "").trim();

        if (!urlstring.startsWith("http://") &&
            !urlstring.startsWith("https://")) { urlstring = "http://" + urlstring; }

        prop.putHTML("urlstring", urlstring);
        prop.put("urlhash", urlhash);
        prop.put("result", " ");

        if (post.containsKey("urlhashdeleteall")) {
            //try {
                int i = sb.removeAllUrlReferences(urlhash, true);
                prop.put("result", "Deleted URL and " + i + " references from " + i + " word indexes.");
            //} catch (IOException e) {
            //    prop.put("result", "Deleted nothing because the url-hash could not be resolved");
            //}
        }

        if (post.containsKey("urlhashdelete")) {
            indexURLEntry entry = sb.wordIndex.loadedURL.load(urlhash, null);
            if (entry == null) {
                prop.put("result", "No Entry for URL hash " + urlhash + "; nothing deleted.");
            } else {
                urlstring = entry.comp().url().toNormalform(false, true);
                prop.put("urlstring", "");
                sb.urlRemove(urlhash);
                prop.putHTML("result", "Removed URL " + urlstring);
            }
        }

        if (post.containsKey("urldelete")) {
            try {
                urlhash = (new yacyURL(urlstring, null)).hash();
            } catch (MalformedURLException e) {
                urlhash = null;
            }
            if ((urlhash == null) || (urlstring == null)) {
                prop.put("result", "No input given; nothing deleted.");
            } else {
                sb.urlRemove(urlhash);
                prop.putHTML("result", "Removed URL " + urlstring);
            }
        }

        if (post.containsKey("urlstringsearch")) {
            try {
                yacyURL url = new yacyURL(urlstring, null);
                urlhash = url.hash();
                prop.put("urlhash", urlhash);
                indexURLEntry entry = sb.wordIndex.loadedURL.load(urlhash, null);
                if (entry == null) {
                    prop.putHTML("urlstring", "unknown url: " + urlstring);
                    prop.put("urlhash", "");
                } else {
                    prop.putAll(genUrlProfile(sb, entry, urlhash));
                }
            } catch (MalformedURLException e) {
                prop.putHTML("urlstring", "bad url: " + urlstring);
                prop.put("urlhash", "");
            }
        }

        if (post.containsKey("urlhashsearch")) {
            indexURLEntry entry = sb.wordIndex.loadedURL.load(urlhash, null);
            if (entry == null) {
                prop.put("result", "No Entry for URL hash " + urlhash);
            } else {
                prop.putHTML("urlstring", entry.comp().url().toNormalform(false, true));
                prop.putAll(genUrlProfile(sb, entry, urlhash));
            }
        }

        // generate list
        if (post.containsKey("urlhashsimilar")) {
            try {
                final Iterator entryIt = new kelondroRotateIterator(sb.wordIndex.loadedURL.entries(true, urlhash), new String(kelondroBase64Order.zero(urlhash.length()))); 
                StringBuffer result = new StringBuffer("Sequential List of URL-Hashes:<br>");
                indexURLEntry entry;
                int i = 0;
                int rows = 0, cols = 0;
                prop.put("urlhashsimilar", "1");
                while (entryIt.hasNext() && i < 256) {
                    entry = (indexURLEntry) entryIt.next();
                    if (entry == null) break;
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
        
        // insert constants
        prop.putNum("ucount", sb.wordIndex.loadedURL.size());
        // return rewrite properties
        return prop;
    }
    
    private static serverObjects genUrlProfile(plasmaSwitchboard switchboard, indexURLEntry entry, String urlhash) {
        serverObjects prop = new serverObjects();
        if (entry == null) {
            prop.put("genUrlProfile", "1");
            prop.put("genUrlProfile_urlhash", urlhash);
            return prop;
        }
        indexURLEntry.Components comp = entry.comp();
        String referrer = null;
        indexURLEntry le = (entry.referrerHash() == null) ? null : switchboard.wordIndex.loadedURL.load(entry.referrerHash(), null);
        if (le == null) {
            referrer = "<unknown>";
        } else {
            referrer = le.comp().url().toNormalform(false, true);
        }
        if (comp.url() == null) {
            prop.put("genUrlProfile", "1");
            prop.put("genUrlProfile_urlhash", urlhash);
            return prop;
        }
        prop.put("genUrlProfile", "2");
        prop.putHTML("genUrlProfile_urlNormalform", comp.url().toNormalform(false, true));
        prop.put("genUrlProfile_urlhash", urlhash);
        prop.put("genUrlProfile_urlDescr", comp.title());
        prop.put("genUrlProfile_moddate", entry.moddate());
        prop.put("genUrlProfile_loaddate", entry.loaddate());
        prop.putHTML("genUrlProfile_referrer", referrer);
        prop.put("genUrlProfile_doctype", ""+entry.doctype());
        prop.put("genUrlProfile_language", entry.language());
        prop.put("genUrlProfile_size", entry.size());
        prop.put("genUrlProfile_wordCount", entry.wordCount());
        return prop;
    }

}
