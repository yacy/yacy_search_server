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
import java.util.List;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;


public class ResultEntry implements Comparable<ResultEntry>, Comparator<ResultEntry> {

    // payload objects
    private final URIMetadataNode urlentry;
    private String alternative_urlstring;
    private String alternative_urlname;
    private final TextSnippet textSnippet;
    private final List<MediaSnippet> mediaSnippets;
    private final Segment indexSegment;

    // statistic objects
    public long snippetComputationTime;

    public ResultEntry(final URIMetadataNode urlentry,
                       final Segment indexSegment,
                       SeedDB peers,
                       final TextSnippet textSnippet,
                       final List<MediaSnippet> mediaSnippets,
                       final long snippetComputationTime) {
        this.urlentry = urlentry;
        this.urlentry.setField(CollectionSchema.text_t.getSolrFieldName(), ""); // clear the text field which eats up most of the space; it was used for snippet computation which is in a separate field here
        this.indexSegment = indexSegment;
        this.alternative_urlstring = null;
        this.alternative_urlname = null;
        this.textSnippet = textSnippet;
        this.mediaSnippets = mediaSnippets;
        this.snippetComputationTime = snippetComputationTime;
        final String host = urlentry.url().getHost();
        if (host != null && host.endsWith(".yacyh")) {
            // translate host into current IP
            int p = host.indexOf('.');
            final String hash = Seed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
            final Seed seed = peers.getConnected(hash);
            final String path = urlentry.url().getFile();
            String address = null;
            if ((seed == null) || ((address = seed.getPublicAddress()) == null)) {
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
            this.hashCache = ByteArray.hashCode(this.urlentry.hash());
        }
        return this.hashCache;
    }
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof ResultEntry)) return false;
        ResultEntry other = (ResultEntry) obj;
        return Base64Order.enhancedCoder.equal(this.urlentry.hash(), other.urlentry.hash());
    }
    public URIMetadataNode getNode() {
        return this.urlentry;
    }
    public byte[] hash() {
        return this.urlentry.hash();
    }
    public DigestURL url() {
        return this.urlentry.url();
    }
    public Bitfield flags() {
        return this.urlentry.flags();
    }
    public String urlstring() {
        return (this.alternative_urlstring == null) ? this.urlentry.url().toNormalform(true) : this.alternative_urlstring;
    }
    public String urlname() {
        return (this.alternative_urlname == null) ? MultiProtocolURL.unescape(this.urlentry.url().toNormalform(true)) : this.alternative_urlname;
    }
    public String title() {
        String titlestr = this.urlentry.dc_title();
        // if title is empty use filename as title
        if (titlestr.isEmpty()) { // if url has no filename, title is still empty (e.g. "www.host.com/" )
            titlestr = this.urlentry.url() != null ? this.urlentry.url().getFileName() : "";
        }
        return titlestr;
    }
    public String publisher() {
        // dc:publisher
        return this.urlentry.dc_publisher();
    }
    public String creator() {
        // dc:creator, the author
        return this.urlentry.dc_creator();
    }
    public String subject() {
        // dc:subject, keywords
        return this.urlentry.dc_subject();
    }
    public TextSnippet textSnippet() {
        return this.textSnippet;
    }
    public List<MediaSnippet> mediaSnippets() {
        return this.mediaSnippets;
    }
    public Date modified() {
        return this.urlentry.moddate();
    }
    public int filesize() {
        return this.urlentry.size();
    }
    public int referencesCount() {
        // urlCitationIndex index might be null (= configuration option)
    	return this.indexSegment.connectedCitation() ? this.indexSegment.urlCitation().count(this.urlentry.hash()) : 0;
    }
    public int llocal() {
    	return this.urlentry.llocal();
    }
    public int lother() {
    	return this.urlentry.lother();
    }
    public int limage() {
        return this.urlentry.limage();
    }
    public int laudio() {
        return this.urlentry.laudio();
    }
    public int lvideo() {
        return this.urlentry.lvideo();
    }
    public int lapp() {
        return this.urlentry.lapp();
    }
    public double lat() {
        return this.urlentry.lat();
    }
    public double lon() {
        return this.urlentry.lon();
    }
    public WordReference word() {
        final Reference word = this.urlentry.word();
        if (word == null) return null;
        if (word instanceof WordReferenceVars) return (WordReferenceVars) word;
        if (word instanceof WordReferenceRow) return (WordReferenceRow) word;
        assert word instanceof WordReferenceRow || word instanceof WordReferenceVars : word == null ? "word = null" : "type = " + word.getClass().getCanonicalName();
        return null;
    }
    public boolean hasTextSnippet() {
        return (this.textSnippet != null) && (!this.textSnippet.getErrorCode().fail());
    }
    public boolean hasMediaSnippets() {
        return (this.mediaSnippets != null) && (!this.mediaSnippets.isEmpty());
    }
    public String resource() {
        // generate transport resource
        if ((this.textSnippet == null) || (!this.textSnippet.exists())) {
            return this.urlentry.toString();
        }
        return this.urlentry.toString(this.textSnippet.getLineRaw());
    }
    @Override
    public int compareTo(ResultEntry o) {
        return Base64Order.enhancedCoder.compare(this.urlentry.hash(), o.urlentry.hash());
    }
    @Override
    public int compare(ResultEntry o1, ResultEntry o2) {
        return Base64Order.enhancedCoder.compare(o1.urlentry.hash(), o2.urlentry.hash());
    }
    public long ranking() {
        return this.urlentry.ranking();
    }
}
