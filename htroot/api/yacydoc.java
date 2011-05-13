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

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.Log;

import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class yacydoc {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        
        final serverObjects prop = new serverObjects();
        final Segment segment;
        boolean html = post != null && post.containsKey("html");
        prop.setLocalized(html);
        boolean authorized = sb.verifyAuthentication(header, false);
        if (post != null && post.containsKey("segment") && authorized) {
            segment = sb.indexSegments.segment(post.get("segment"));
        } else {
            segment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }
        
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
        
        String urlstring = post.get("url", "").trim();
        String urlhash = post.get("urlhash", "").trim();
        if (urlstring.length() == 0 && urlhash.length() == 0) return prop;

        if (urlstring.length() > 0 && urlhash.length() == 0) {
            try {
                DigestURI url = new DigestURI(urlstring);
                urlhash = UTF8.String(url.hash());
            } catch (MalformedURLException e) {
                Log.logException(e);
            }
        }
        if (urlhash == null || urlhash.length() == 0) return prop;
        
        final URIMetadataRow entry = segment.urlMetadata().load(urlhash.getBytes());
        if (entry == null) return prop;

        final URIMetadataRow.Components metadata = entry.metadata();
        if (metadata.url() == null) {
            return prop;
        }
        final URIMetadataRow le = (entry.referrerHash() == null || entry.referrerHash().length != Word.commonHashLength) ? null : segment.urlMetadata().load(entry.referrerHash());
        
        prop.putXML("dc_title", metadata.dc_title());
        prop.putXML("dc_creator", metadata.dc_creator());
        prop.putXML("dc_description", ""); // this is the fulltext part in the surrogate
        prop.putXML("dc_subject", metadata.dc_subject());
        prop.putXML("dc_publisher", metadata.dc_publisher());
        prop.putXML("dc_contributor", "");
        prop.putXML("dc_date", ISO8601Formatter.FORMATTER.format(entry.moddate()));
        prop.putXML("dc_type", String.valueOf(entry.doctype()));
        prop.putXML("dc_identifier", metadata.url().toNormalform(false, true));
        prop.putXML("dc_language", UTF8.String(entry.language()));
        prop.put("geo_lat", metadata.lat());
        prop.put("geo_long", metadata.lon());

        prop.put("yacy_urlhash", metadata.url().hash());
        prop.putXML("yacy_loaddate", entry.loaddate().toString());
        prop.putXML("yacy_referrer_hash", (le == null) ? "" : UTF8.String(le.hash()));
        prop.putXML("yacy_referrer_url", (le == null) ? "" : le.metadata().url().toNormalform(false, true));
        prop.put("yacy_size", entry.size());
        prop.put("yacy_words",entry.wordCount());
        
        // return rewrite properties
        return prop;
    }

}
