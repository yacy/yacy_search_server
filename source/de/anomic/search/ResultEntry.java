// ResultEntry.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.10.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

package de.anomic.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import de.anomic.document.Condenser;
import de.anomic.document.Word;
import de.anomic.kelondro.order.Bitfield;
import de.anomic.kelondro.text.Reference;
import de.anomic.kelondro.text.Segment;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.text.referencePrototype.WordReferenceVars;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public class ResultEntry {
    
    // payload objects
    private final URLMetadataRow urlentry;
    private final URLMetadataRow.Components urlcomps; // buffer for components
    private String alternative_urlstring;
    private String alternative_urlname;
    private final TextSnippet textSnippet;
    private final ArrayList<MediaSnippet> mediaSnippets;
    
    // statistic objects
    public long dbRetrievalTime, snippetComputationTime;
    
    public ResultEntry(final URLMetadataRow urlentry,
                       final Segment indexSegment,
                       yacySeedDB peers,
                       final TextSnippet textSnippet,
                       final ArrayList<MediaSnippet> mediaSnippets,
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
        if (host.endsWith(".yacyh")) {
            // translate host into current IP
            int p = host.indexOf(".");
            final String hash = yacySeed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
            final yacySeed seed = peers.getConnected(hash);
            final String filename = urlcomps.url().getFile();
            String address = null;
            if ((seed == null) || ((address = seed.getPublicAddress()) == null)) {
                // seed is not known from here
                try {
                    indexSegment.termIndex().remove(
                        Word.words2hashes(Condenser.getWords(
                            ("yacyshare " +
                             filename.replace('?', ' ') +
                             " " +
                             urlcomps.dc_title())).keySet()),
                             urlentry.hash());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                indexSegment.urlMetadata().remove(urlentry.hash()); // clean up
                throw new RuntimeException("index void");
            }
            alternative_urlstring = "http://" + address + "/" + host.substring(0, p) + filename;
            alternative_urlname = "http://share." + seed.getName() + ".yacy" + filename;
            if ((p = alternative_urlname.indexOf("?")) > 0) alternative_urlname = alternative_urlname.substring(0, p);
        }
    }
    public int hashCode() {
        return urlentry.hash().hashCode();
    }
    public String hash() {
        return urlentry.hash();
    }
    public yacyURL url() {
        return urlcomps.url();
    }
    public Bitfield flags() {
        return urlentry.flags();
    }
    public String urlstring() {
        return (alternative_urlstring == null) ? urlcomps.url().toNormalform(false, true) : alternative_urlstring;
    }
    public String urlname() {
        return (alternative_urlname == null) ? yacyURL.unescape(urlcomps.url().toNormalform(false, true)) : alternative_urlname;
    }
    public String title() {
        return urlcomps.dc_title();
    }
    public TextSnippet textSnippet() {
        return this.textSnippet;
    }
    public ArrayList<MediaSnippet> mediaSnippets() {
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
    public WordReferenceVars word() {
        final Reference word = urlentry.word();
        assert word instanceof WordReferenceVars;
        return (WordReferenceVars) word;
    }
    public boolean hasTextSnippet() {
        return (this.textSnippet != null) && (this.textSnippet.getErrorCode() < 11);
    }
    public boolean hasMediaSnippets() {
        return (this.mediaSnippets != null) && (this.mediaSnippets.size() > 0);
    }
    public String resource() {
        // generate transport resource
        if ((textSnippet == null) || (!textSnippet.exists())) {
            return urlentry.toString();
        }
        return urlentry.toString(textSnippet.getLineRaw());
    }
}
