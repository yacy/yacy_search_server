// yacydoc.java
// -----------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.01.2009 on http://yacy.net
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

import java.net.MalformedURLException;

import de.anomic.http.httpRequestHeader;
import de.anomic.index.indexURLReference;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public class yacydoc {
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        final serverObjects prop = new serverObjects();
        prop.put("dc_title", "");
        prop.put("dc_creator", "");
        prop.put("dc_description", "");
        prop.put("dc_subject", "");
        prop.put("dc_publisher", "");
        prop.put("dc_contributor", "");
        prop.put("dc_date", "");
        prop.put("dc_type", "");
        prop.put("dc_identifier", "");
        prop.put("dc_language", "");

        if (post == null) return prop;
        
        String urlstring = post.get("urlstring", "").trim();
        String urlhash = post.get("urlhash", "").trim();
        if (urlstring.length() == 0 && urlhash.length() == 0) return prop;

        if (urlstring.length() > 0 && urlhash.length() == 0) {
            try {
                urlhash = (new yacyURL(urlstring, null)).hash();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        if (urlhash == null || urlhash.length() == 0) return prop;
        
        final indexURLReference entry = sb.webIndex.getURL(urlhash, null, 0);
        if (entry == null) return prop;

        final indexURLReference.Components comp = entry.comp();
        if (comp.url() == null) {
            return prop;
        }
        final indexURLReference le = ((entry.referrerHash() == null) || (entry.referrerHash().length() != yacySeedDB.commonHashLength)) ? null : sb.webIndex.getURL(entry.referrerHash(), null, 0);
        
        prop.putHTML("dc_title", comp.dc_title());
        prop.putHTML("dc_creator", comp.dc_creator());
        prop.putHTML("dc_description", "");
        prop.putHTML("dc_subject", comp.dc_subject());
        prop.putHTML("dc_publisher", comp.url().toNormalform(false, true));
        prop.putHTML("dc_contributor", "");
        prop.putHTML("dc_date", entry.moddate().toString());
        prop.put("dc_type", entry.doctype());
        prop.putHTML("dc_identifier", urlhash);
        prop.putHTML("dc_language", entry.language());

        prop.putHTML("yacy_loaddate", entry.loaddate().toString());
        prop.putHTML("yacy_referrer_hash", (le == null) ? "" : le.hash());
        prop.putHTML("yacy_referrer_url", (le == null) ? "" : le.comp().url().toNormalform(false, true));
        prop.put("yacy_size", entry.size());
        prop.put("yacy_words",entry.wordCount());
        
        // return rewrite properties
        return prop;
    }

}
