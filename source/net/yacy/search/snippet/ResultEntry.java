// ResultEntry.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.10.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.search.snippet;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Condenser;
import net.yacy.document.parser.pdfParser;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;


public class ResultEntry extends URIMetadataNode implements Comparable<ResultEntry>, Comparator<ResultEntry> {

    // payload objects
    private String alternative_urlstring;
    private String alternative_urlname;
    private final TextSnippet textSnippet;
    private final Segment indexSegment;

    public ResultEntry(final URIMetadataNode urlentry,
                       final Segment indexSegment,
                       SeedDB peers,
                       final TextSnippet textSnippet) {
        super(urlentry);
        this.removeFields(CollectionSchema.text_t.getSolrFieldName()); // clear the text field which eats up most of the space; it was used for snippet computation which is in a separate field here
        this.indexSegment = indexSegment;
        this.alternative_urlstring = null;
        this.alternative_urlname = null;
        this.textSnippet = textSnippet;
        final String host = urlentry.url().getHost();
        if (host != null && host.endsWith(".yacyh")) {
            // translate host into current IP
            int p = host.indexOf('.');
            final String hash = Seed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
            final Seed seed = peers.getConnected(hash);
            final String path = urlentry.url().getFile();
            String address = null;
            if ((seed == null) || ((address = seed.getPublicAddress(seed.getIP())) == null)) {
                // seed is not known from here
                try {
                    if (indexSegment.termIndex() != null) indexSegment.termIndex().remove(
                        Word.words2hashesHandles(Condenser.getWords(
                            ("yacyshare " +
                             path.replace('?', ' ') +
                             " " +
                             urlentry.dc_title()), null).keySet()),
                             urlentry.hash());
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
                indexSegment.fulltext().remove(urlentry.hash()); // clean up
                throw new RuntimeException("index void");
            }
            this.alternative_urlstring = "http://" + address + "/" + host.substring(0, p) + path;
            this.alternative_urlname = "http://share." + seed.getName() + ".yacy" + path;
            if ((p = this.alternative_urlname.indexOf('?')) > 0) this.alternative_urlname = this.alternative_urlname.substring(0, p);
        }
    }
    private int hashCache = Integer.MIN_VALUE; // if this is used in a compare method many times, a cache is useful
    @Override
    public int hashCode() {
        if (this.hashCache == Integer.MIN_VALUE) {
            this.hashCache = ByteArray.hashCode(this.hash());
        }
        return this.hashCache;
    }
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof ResultEntry)) return false;
        ResultEntry other = (ResultEntry) obj;
        return Base64Order.enhancedCoder.equal(this.hash(), other.hash());
    }

    public String urlstring() {
        if (this.alternative_urlstring != null) return this.alternative_urlstring;
        
        if (!pdfParser.individualPages) return this.url().toNormalform(true);
        if (!"pdf".equals(MultiProtocolURL.getFileExtension(this.url().getFileName()).toLowerCase())) return this.url().toNormalform(true);
        // for pdf links we rewrite the url
        // this is a special treatment of pdf files which can be splitted into subpages
        String pageprop = pdfParser.individualPagePropertyname;
        String resultUrlstring = this.url().toNormalform(true);
        int p = resultUrlstring.lastIndexOf(pageprop + "=");
        if (p > 0) {
          return resultUrlstring.substring(0, p - 1) + "#page=" + resultUrlstring.substring(p + pageprop.length() + 1);
        }
        return resultUrlstring;
    }
    public String urlname() {
        return (this.alternative_urlname == null) ? MultiProtocolURL.unescape(urlstring()) : this.alternative_urlname;
    }
    public String title() {
        String titlestr = this.dc_title();
        // if title is empty use filename as title
        if (titlestr.isEmpty()) { // if url has no filename, title is still empty (e.g. "www.host.com/" )
            titlestr = this.url() != null ? this.url().getFileName() : "";
        }
        return titlestr;
    }
    public TextSnippet textSnippet() {
        return this.textSnippet;
    }
    public Date[] events() {
        return this.datesInContent();
    }
    public int referencesCount() {
        // urlCitationIndex index might be null (= configuration option)
    	return this.indexSegment.connectedCitation() ? this.indexSegment.urlCitation().count(this.hash()) : 0;
    }
    public boolean hasTextSnippet() {
        return (this.textSnippet != null) && (!this.textSnippet.getErrorCode().fail());
    }
    public String resource() {
        // generate transport resource
        if ((this.textSnippet == null) || (!this.textSnippet.exists())) {
            return this.toString();
        }
        return this.toString(this.textSnippet.getLineRaw());
    }
    @Override
    public int compareTo(ResultEntry o) {
        return Base64Order.enhancedCoder.compare(this.hash(), o.hash());
    }
    @Override
    public int compare(ResultEntry o1, ResultEntry o2) {
        return Base64Order.enhancedCoder.compare(o1.hash(), o2.hash());
    }
}
