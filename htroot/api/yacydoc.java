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
import de.anomic.kelondro.text.MetadataRowContainer;
import de.anomic.kelondro.text.URLMetadata;
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
        
        final MetadataRowContainer entry = sb.webIndex.getURL(urlhash, null, 0);
        if (entry == null) return prop;

        final URLMetadata metadata = entry.metadata();
        if (metadata.url() == null) {
            return prop;
        }
        final MetadataRowContainer le = ((entry.referrerHash() == null) || (entry.referrerHash().length() != yacySeedDB.commonHashLength)) ? null : sb.webIndex.getURL(entry.referrerHash(), null, 0);
        
        prop.putXML("dc_title", metadata.dc_title());
        prop.putXML("dc_creator", metadata.dc_creator());
        prop.putXML("dc_description", "");
        prop.putXML("dc_subject", metadata.dc_subject());
        prop.putXML("dc_publisher", metadata.url().toNormalform(false, true));
        prop.putXML("dc_contributor", "");
        prop.putXML("dc_date", entry.moddate().toString());
        prop.putXML("dc_type", "" + entry.doctype());
        prop.putXML("dc_identifier", urlhash);
        prop.putXML("dc_language", entry.language());

        prop.putXML("yacy_loaddate", entry.loaddate().toString());
        prop.putXML("yacy_referrer_hash", (le == null) ? "" : le.hash());
        prop.putXML("yacy_referrer_url", (le == null) ? "" : le.metadata().url().toNormalform(false, true));
        prop.put("yacy_size", entry.size());
        prop.put("yacy_words",entry.wordCount());
        
        // return rewrite properties
        return prop;
    }

}
