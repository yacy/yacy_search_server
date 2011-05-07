// MediaSnippet.java
// -----------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import de.anomic.crawler.CrawlProfile;
import de.anomic.data.MimeTable;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.WordTokenizer;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.util.ByteArray;


public class MediaSnippet implements Comparable<MediaSnippet>, Comparator<MediaSnippet> {
    public ContentDomain type;
    public DigestURI href, source;
    public String name, attr, mime;
    public long ranking;
    public int width, height;
    public long fileSize;

    public MediaSnippet(final ContentDomain type, final DigestURI href, final String mime, final String name, final long fileSize, final String attr, final long ranking, final DigestURI source) {
        this.type = type;
        this.href = href;
        this.mime = mime;
        this.fileSize = fileSize;
        this.source = source; // the web page where the media resource appeared
        this.name = name;
        this.attr = attr;
        this.width = -1;
        this.height = -1;
        int p = 0;
        if (attr != null && (p = attr.indexOf(" x ")) > 0) {
            this.width = Integer.parseInt(attr.substring(0, p).trim());
            this.height = Integer.parseInt(attr.substring(p + 3).trim());
        }
        this.ranking = ranking; // the smaller the better! small values should be shown first
        if ((this.name == null) || (this.name.length() == 0)) this.name = "_";
        if ((this.attr == null) || (this.attr.length() == 0)) this.attr = "_";
    }
    
    public MediaSnippet(final ContentDomain type, final DigestURI href, final String mime, final String name, final long fileSize, final int width, final int height, final long ranking, final DigestURI source) {
        this.type = type;
        this.href = href;
        this.mime = mime;
        this.fileSize = fileSize;
        this.source = source; // the web page where the media resource appeared
        this.name = name;
        this.attr = width + " x " + height;
        this.width = width;
        this.height = height;
        this.ranking = ranking; // the smaller the better! small values should be shown first
        if ((this.name == null) || (this.name.length() == 0)) this.name = "_";
        if ((this.attr == null) || (this.attr.length() == 0)) this.attr = "_";
    }
    
    @Override
    public int hashCode() {
        return ByteArray.hashCode(href.hash());
    }
    
    @Override
    public String toString() {
        return UTF8.String(href.hash());
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof MediaSnippet)) return false;
        MediaSnippet other = (MediaSnippet) obj;
        return Base64Order.enhancedCoder.equal(this.href.hash(), other.href.hash());
    }
    
    public int compareTo(MediaSnippet o) {
        return Base64Order.enhancedCoder.compare(this.href.hash(), o.href.hash());
    }
    
    public int compare(MediaSnippet o1, MediaSnippet o2) {
        return o1.compareTo(o2);
    }
    
    public static List<MediaSnippet> retrieveMediaSnippets(final DigestURI url, final HandleSet queryhashes, final ContentDomain mediatype, final CrawlProfile.CacheStrategy cacheStrategy, final int timeout, final boolean reindexing) {
        if (queryhashes.isEmpty()) {
            Log.logFine("snippet fetch", "no query hashes given for url " + url);
            return new ArrayList<MediaSnippet>();
        }
        
        Document document;
        try {
            document = Document.mergeDocuments(url, null, Switchboard.getSwitchboard().loader.loadDocuments(Switchboard.getSwitchboard().loader.request(url, false, reindexing), cacheStrategy, timeout, Long.MAX_VALUE));
        } catch (IOException e) {
            Log.logFine("snippet fetch", "load error: " + e.getMessage());
            return new ArrayList<MediaSnippet>();
        } catch (Parser.Failure e) {
            Log.logFine("snippet fetch", "parser error: " + e.getMessage());
            return new ArrayList<MediaSnippet>();
        }
        final ArrayList<MediaSnippet> a = new ArrayList<MediaSnippet>();
        if (document != null) {
            if ((mediatype == ContentDomain.ALL) || (mediatype == ContentDomain.AUDIO)) a.addAll(computeMediaSnippets(url, document, queryhashes, ContentDomain.AUDIO));
            if ((mediatype == ContentDomain.ALL) || (mediatype == ContentDomain.VIDEO)) a.addAll(computeMediaSnippets(url, document, queryhashes, ContentDomain.VIDEO));
            if ((mediatype == ContentDomain.ALL) || (mediatype == ContentDomain.APP)) a.addAll(computeMediaSnippets(url, document, queryhashes, ContentDomain.APP));
            if ((mediatype == ContentDomain.ALL) || (mediatype == ContentDomain.IMAGE)) a.addAll(computeImageSnippets(url, document, queryhashes));
        }
        return a;
    }
    
    public static List<MediaSnippet> computeMediaSnippets(final DigestURI source, final Document document, final HandleSet queryhashes, final ContentDomain mediatype) {
        
        if (document == null) return new ArrayList<MediaSnippet>();
        Map<MultiProtocolURI, String> media = null;
        if (mediatype == ContentDomain.AUDIO) media = document.getAudiolinks();
        else if (mediatype == ContentDomain.VIDEO) media = document.getVideolinks();
        else if (mediatype == ContentDomain.APP) media = document.getApplinks();
        if (media == null) return null;
        
        final Iterator<Map.Entry<MultiProtocolURI, String>> i = media.entrySet().iterator();
        Map.Entry<MultiProtocolURI, String> entry;
        DigestURI url;
        String desc;
        final List<MediaSnippet> result = new ArrayList<MediaSnippet>();
        while (i.hasNext()) {
            entry = i.next();
            url = new DigestURI(entry.getKey());
            desc = entry.getValue();
            int ranking = removeAppearanceHashes(url.toNormalform(false, false), queryhashes).size() +
                           removeAppearanceHashes(desc, queryhashes).size();
            if (ranking < 2 * queryhashes.size()) {
                result.add(new MediaSnippet(mediatype, url, MimeTable.url2mime(url), desc, document.getTextLength(), null, ranking, source));
            }
        }
        return result;
    }
    
    public static List<MediaSnippet> computeImageSnippets(final DigestURI source, final Document document, final HandleSet queryhashes) {
        
        final SortedSet<ImageEntry> images = new TreeSet<ImageEntry>();
        images.addAll(document.getImages().values()); // iterates images in descending size order!
        // a measurement for the size of the images can be retrieved using the htmlFilterImageEntry.hashCode()
        
        final Iterator<ImageEntry> i = images.iterator();
        ImageEntry ientry;
        DigestURI url;
        String desc;
        final List<MediaSnippet> result = new ArrayList<MediaSnippet>();
        while (i.hasNext()) {
            ientry = i.next();
            url = new DigestURI(ientry.url());
            String u = url.toString();
            if (u.indexOf(".ico") >= 0 || u.indexOf("favicon") >= 0) continue;
            if (ientry.height() > 0 && ientry.height() < 32) continue;
            if (ientry.width() > 0 && ientry.width() < 32) continue;
            desc = ientry.alt();
            int appcount = queryhashes.size()  * 2 - 
                           removeAppearanceHashes(url.toNormalform(false, false), queryhashes).size() -
                           removeAppearanceHashes(desc, queryhashes).size();
            final long ranking = Long.MAX_VALUE - (ientry.height() + 1) * (ientry.width() + 1) * (appcount + 1);  
            result.add(new MediaSnippet(ContentDomain.IMAGE, url, MimeTable.url2mime(url), desc, ientry.fileSize(), ientry.width(), ientry.height(), ranking, source));
        }
        return result;
    }
    
    /**
     * removed all word hashes that can be computed as tokens from a given sentence from a given hash set
     * @param sentence
     * @param queryhashes
     * @return the given hash set minus the hashes from the tokenization of the given sentence
     */
    private static HandleSet removeAppearanceHashes(final String sentence, final HandleSet queryhashes) {
        // remove all hashes that appear in the sentence
        if (sentence == null) return queryhashes;
        final SortedMap<byte[], Integer> hs = WordTokenizer.hashSentence(sentence, null);
        final Iterator<byte[]> j = queryhashes.iterator();
        byte[] hash;
        Integer pos;
        final HandleSet remaininghashes = new HandleSet(queryhashes.row().primaryKeyLength, queryhashes.comparator(), queryhashes.size());
        while (j.hasNext()) {
            hash = j.next();
            pos = hs.get(hash);
            if (pos == null) {
                try {
                    remaininghashes.put(hash);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                }
            }
        }
        return remaininghashes;
    }
    
}
