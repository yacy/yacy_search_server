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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import net.yacy.document.Document;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import de.anomic.crawler.retrieval.LoaderDispatcher;

public class MediaSnippet {
    public int type;
    public DigestURI href, source;
    public String name, attr;
    public int ranking;

    public MediaSnippet(final int type, final DigestURI href, final String name, final String attr, final int ranking, final DigestURI source) {
        this.type = type;
        this.href = href;
        this.source = source; // the web page where the media resource appeared
        this.name = name;
        this.attr = attr;
        this.ranking = ranking; // the smaller the better! small values should be shown first
        if ((this.name == null) || (this.name.length() == 0)) this.name = "_";
        if ((this.attr == null) || (this.attr.length() == 0)) this.attr = "_";
    }
    
    @Override
    public int hashCode() {
        return href.hashCode();
    }
    
    public static ArrayList<MediaSnippet> retrieveMediaSnippets(final DigestURI url, final TreeSet<byte[]> queryhashes, final int mediatype, final boolean fetchOnline, final int timeout, final boolean reindexing) {
        if (queryhashes.size() == 0) {
            Log.logFine("snippet fetch", "no query hashes given for url " + url);
            return new ArrayList<MediaSnippet>();
        }
        
        final Document document = LoaderDispatcher.retrieveDocument(url, fetchOnline, timeout, false, reindexing);
        final ArrayList<MediaSnippet> a = new ArrayList<MediaSnippet>();
        if (document != null) {
            if ((mediatype == QueryParams.CONTENTDOM_ALL) || (mediatype == QueryParams.CONTENTDOM_AUDIO)) a.addAll(computeMediaSnippets(document, queryhashes, QueryParams.CONTENTDOM_AUDIO));
            if ((mediatype == QueryParams.CONTENTDOM_ALL) || (mediatype == QueryParams.CONTENTDOM_VIDEO)) a.addAll(computeMediaSnippets(document, queryhashes, QueryParams.CONTENTDOM_VIDEO));
            if ((mediatype == QueryParams.CONTENTDOM_ALL) || (mediatype == QueryParams.CONTENTDOM_APP)) a.addAll(computeMediaSnippets(document, queryhashes, QueryParams.CONTENTDOM_APP));
            if ((mediatype == QueryParams.CONTENTDOM_ALL) || (mediatype == QueryParams.CONTENTDOM_IMAGE)) a.addAll(computeImageSnippets(document, queryhashes));
        }
        return a;
    }
    
    public static ArrayList<MediaSnippet> computeMediaSnippets(final Document document, final TreeSet<byte[]> queryhashes, final int mediatype) {
        
        if (document == null) return new ArrayList<MediaSnippet>();
        Map<DigestURI, String> media = null;
        if (mediatype == QueryParams.CONTENTDOM_AUDIO) media = document.getAudiolinks();
        else if (mediatype == QueryParams.CONTENTDOM_VIDEO) media = document.getVideolinks();
        else if (mediatype == QueryParams.CONTENTDOM_APP) media = document.getApplinks();
        if (media == null) return null;
        
        final Iterator<Map.Entry<DigestURI, String>> i = media.entrySet().iterator();
        Map.Entry<DigestURI, String> entry;
        DigestURI url;
        String desc;
        TreeSet<byte[]> s;
        final ArrayList<MediaSnippet> result = new ArrayList<MediaSnippet>();
        while (i.hasNext()) {
            entry = i.next();
            url = entry.getKey();
            desc = entry.getValue();
            s = TextSnippet.removeAppearanceHashes(url.toNormalform(false, false), queryhashes);
            if (s.size() == 0) {
                result.add(new MediaSnippet(mediatype, url, desc, null, 0, document.dc_source()));
                continue;
            }
            s = TextSnippet.removeAppearanceHashes(desc, s);
            if (s.size() == 0) {
                result.add(new MediaSnippet(mediatype, url, desc, null, 0, document.dc_source()));
                continue;
            }
        }
        return result;
    }
    
    public static ArrayList<MediaSnippet> computeImageSnippets(final Document document, final TreeSet<byte[]> queryhashes) {
        
        final TreeSet<ImageEntry> images = new TreeSet<ImageEntry>();
        images.addAll(document.getImages().values()); // iterates images in descending size order!
        // a measurement for the size of the images can be retrieved using the htmlFilterImageEntry.hashCode()
        
        final Iterator<ImageEntry> i = images.iterator();
        ImageEntry ientry;
        DigestURI url;
        String desc;
        TreeSet<byte[]> s;
        final ArrayList<MediaSnippet> result = new ArrayList<MediaSnippet>();
        while (i.hasNext()) {
            ientry = i.next();
            url = ientry.url();
            desc = ientry.alt();
            s = TextSnippet.removeAppearanceHashes(url.toNormalform(false, false), queryhashes);
            if (s.size() == 0) {
                final int ranking = ientry.hashCode();
                result.add(new MediaSnippet(QueryParams.CONTENTDOM_IMAGE, url, desc, ientry.width() + " x " + ientry.height(), ranking, document.dc_source()));
                continue;
            }
            s = TextSnippet.removeAppearanceHashes(desc, s);
            if (s.size() == 0) {
                final int ranking = ientry.hashCode();
                result.add(new MediaSnippet(QueryParams.CONTENTDOM_IMAGE, url, desc, ientry.width() + " x " + ientry.height(), ranking, document.dc_source()));
                continue;
            }
        }
        return result;
    }
    

    /*
    private static String computeMediaSnippet(Map<yacyURL, String> media, Set<String> queryhashes) {
        Iterator<Map.Entry<yacyURL, String>> i = media.entrySet().iterator();
        Map.Entry<yacyURL, String> entry;
        yacyURL url;
        String desc;
        Set<String> s;
        String result = "";
        while (i.hasNext()) {
            entry = i.next();
            url = entry.getKey();
            desc = entry.getValue();
            s = removeAppearanceHashes(url.toNormalform(false, false), queryhashes);
            if (s.size() == 0) {
                result += "<br /><a href=\"" + url + "\">" + ((desc.length() == 0) ? url : desc) + "</a>";
                continue;
            }
            s = removeAppearanceHashes(desc, s);
            if (s.size() == 0) {
                result += "<br /><a href=\"" + url + "\">" + ((desc.length() == 0) ? url : desc) + "</a>";
                continue;
            }
        }
        if (result.length() == 0) return null;
        return result.substring(6);
    }
    */
    
}
