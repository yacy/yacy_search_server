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
import java.util.Arrays;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class yacydoc {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();
        final Segment segment = sb.index;
        final boolean html = post != null && post.containsKey("html");
        prop.setLocalized(html);

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
        prop.put("collection", "");
        prop.put("geo_lat", "");
        prop.put("geo_long", "");

        prop.put("yacy_urlhash", "");
        prop.put("yacy_loaddate", "");
        prop.put("yacy_referrer_hash", "");
        prop.put("yacy_referrer_url", "");
        prop.put("yacy_size", "");
        prop.put("yacy_words", "");
        prop.put("yacy_citations", "");
        prop.put("yacy_inbound", "");
        prop.put("yacy_outbound", "");

        if (post == null) return prop;

        final String urlstring = post.get("url", "").trim();
        String urlhash = post.get("urlhash", "").trim();
        if (urlstring.isEmpty() && urlhash.isEmpty()) return prop;

        if (urlstring.length() > 0 && urlhash.isEmpty()) {
            try {
                final DigestURL url = new DigestURL(urlstring);
                urlhash = ASCII.String(url.hash());
            } catch (final MalformedURLException e) {
                ConcurrentLog.logException(e);
            }
        }
        if (urlhash == null || urlhash.isEmpty()) return prop;

        final URIMetadataNode entry = segment.fulltext().getMetadata(urlhash.getBytes());
        if (entry == null) return prop;

        if (entry.url() == null) {
            return prop;
        }
        final URIMetadataNode le = (entry.referrerHash() == null || entry.referrerHash().length != Word.commonHashLength) ? null : segment.fulltext().getMetadata(entry.referrerHash());

        prop.putXML("dc_title", entry.dc_title());
        prop.putXML("dc_creator", entry.dc_creator());
        prop.putXML("dc_description", ""); // this is the fulltext part in the surrogate
        prop.putXML("dc_subject", entry.dc_subject());
        prop.putXML("dc_publisher", entry.dc_publisher());
        prop.putXML("dc_contributor", "");
        prop.putXML("dc_date", ISO8601Formatter.FORMATTER.format(entry.moddate()));
        prop.putXML("dc_type", String.valueOf(entry.doctype()));
        prop.putXML("dc_identifier", entry.url().toNormalform(true));
        prop.putXML("dc_language", entry.language());
        prop.putXML("collection", Arrays.toString(entry.collections()));
        prop.put("geo_lat", entry.lat());
        prop.put("geo_long", entry.lon());

        prop.put("yacy_urlhash", entry.url().hash());
        prop.putXML("yacy_loaddate", entry.loaddate().toString());
        prop.putXML("yacy_referrer_hash", (le == null) ? "" : ASCII.String(le.hash()));
        prop.putXML("yacy_referrer_url", (le == null) ? "" : le.url().toNormalform(true));
        prop.put("yacy_size", entry.size());
        prop.put("yacy_words", entry.wordCount());
        prop.put("yacy_citations", sb.index.connectedCitation() ? sb.index.urlCitation().count(entry.hash()) : 0);
        prop.put("yacy_inbound", entry.llocal());
        prop.put("yacy_outbound", entry.lother());

        // return rewrite properties
        return prop;
    }

}
