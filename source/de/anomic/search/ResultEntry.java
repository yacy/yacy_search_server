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

package de.anomic.search;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.util.ByteArray;

import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;

public class ResultEntry implements Comparable<ResultEntry>, Comparator<ResultEntry> {
    
    // payload objects
    private final URIMetadataRow urlentry;
    private final URIMetadataRow.Components urlcomps; // buffer for components
    private String alternative_urlstring;
    private String alternative_urlname;
    private final TextSnippet textSnippet;
    private final List<MediaSnippet> mediaSnippets;
    
    // statistic objects
    public long dbRetrievalTime, snippetComputationTime, ranking;
    
    public ResultEntry(final URIMetadataRow urlentry,
                       final Segment indexSegment,
                       yacySeedDB peers,
                       final TextSnippet textSnippet,
                       final List<MediaSnippet> mediaSnippets,
                       final long dbRetrievalTime, final long snippetComputationTime) {
        this.urlentry = urlentry;
        this.urlcomps = urlentry.metadata();
        this.alternative_urlstring = null;
        this.alternative_urlname = null;
        this.textSnippet = textSnippet;
        this.mediaSnippets = mediaSnippets;
        this.dbRetrievalTime = dbRetrievalTime;
        this.snippetComputationTime = snippetComputationTime;
        final String host = urlcomps.url().getHost();
        if (host != null && host.endsWith(".yacyh")) {
            // translate host into current IP
            int p = host.indexOf('.');
            final String hash = yacySeed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
            final yacySeed seed = peers.getConnected(hash);
            final String filename = urlcomps.url().getFile();
            String address = null;
            if ((seed == null) || ((address = seed.getPublicAddress()) == null)) {
                // seed is not known from here
                try {
                    indexSegment.termIndex().remove(
                        Word.words2hashesHandles(Condenser.getWords(
                            ("yacyshare " +
                             filename.replace('?', ' ') +
                             " " +
                             urlcomps.dc_title()), null).keySet()),
                             urlentry.hash());
                } catch (IOException e) {
                    Log.logException(e);
                }
                indexSegment.urlMetadata().remove(urlentry.hash()); // clean up
                throw new RuntimeException("index void");
            }
            alternative_urlstring = "http://" + address + "/" + host.substring(0, p) + filename;
            alternative_urlname = "http://share." + seed.getName() + ".yacy" + filename;
            if ((p = alternative_urlname.indexOf('?')) > 0) alternative_urlname = alternative_urlname.substring(0, p);
        }
    }
    @Override
    public int hashCode() {
        return ByteArray.hashCode(urlentry.hash());
    }
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof ResultEntry)) return false;
        ResultEntry other = (ResultEntry) obj;
        return Base64Order.enhancedCoder.equal(urlentry.hash(), other.urlentry.hash());
    }
    public byte[] hash() {
        return urlentry.hash();
    }
    public DigestURI url() {
        return urlcomps.url();
    }
    public Bitfield flags() {
        return urlentry.flags();
    }
    public String urlstring() {
        return (alternative_urlstring == null) ? urlcomps.url().toNormalform(false, true) : alternative_urlstring;
    }
    public String urlname() {
        return (alternative_urlname == null) ? MultiProtocolURI.unescape(urlcomps.url().toNormalform(false, true)) : alternative_urlname;
    }
    public String title() {
        return urlcomps.dc_title();
    }
    public String publisher() {
        // dc:publisher
        return urlcomps.dc_publisher();
    }
    public String creator() {
        // dc:creator, the author
        return urlcomps.dc_creator();
    }
    public String subject() {
        // dc:subject, keywords
        return urlcomps.dc_subject();
    }
    public TextSnippet textSnippet() {
        return this.textSnippet;
    }
    public List<MediaSnippet> mediaSnippets() {
        return this.mediaSnippets;
    }
    public Date modified() {
        return urlentry.moddate();
    }
    public int filesize() {
        return urlentry.size();
    }
    public int limage() {
        return urlentry.limage();
    }
    public int laudio() {
        return urlentry.laudio();
    }
    public int lvideo() {
        return urlentry.lvideo();
    }
    public int lapp() {
        return urlentry.lapp();
    }
    public float lat() {
        return urlentry.metadata().lat();
    }
    public float lon() {
        return urlentry.metadata().lon();
    }
    public WordReferenceVars word() {
        final Reference word = urlentry.word();
        assert word instanceof WordReferenceVars;
        return (WordReferenceVars) word;
    }
    public boolean hasTextSnippet() {
        return (this.textSnippet != null) && (!this.textSnippet.getErrorCode().fail());
    }
    public boolean hasMediaSnippets() {
        return (this.mediaSnippets != null) && (!this.mediaSnippets.isEmpty());
    }
    public String resource() {
        // generate transport resource
        if ((textSnippet == null) || (!textSnippet.exists())) {
            return urlentry.toString();
        }
        return urlentry.toString(textSnippet.getLineRaw());
    }
    public int compareTo(ResultEntry o) {
        return Base64Order.enhancedCoder.compare(this.urlentry.hash(), o.urlentry.hash());
    }
    public int compare(ResultEntry o1, ResultEntry o2) {
        return Base64Order.enhancedCoder.compare(o1.urlentry.hash(), o2.urlentry.hash());
    }
}
