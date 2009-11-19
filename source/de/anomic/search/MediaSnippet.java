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
import net.yacy.repository.LoaderDispatcher;


public class MediaSnippet {
    public ContentDomain type;
    public DigestURI href, source;
    public String name, attr;
    public int ranking;

    public MediaSnippet(final ContentDomain type, final DigestURI href, final String name, final String attr, final int ranking, final DigestURI source) {
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
    
    public static ArrayList<MediaSnippet> retrieveMediaSnippets(final DigestURI url, final TreeSet<byte[]> queryhashes, final ContentDomain mediatype, final boolean fetchOnline, final int timeout, final boolean reindexing) {
        if (queryhashes.size() == 0) {
            Log.logFine("snippet fetch", "no query hashes given for url " + url);
            return new ArrayList<MediaSnippet>();
        }
        
        final Document document = LoaderDispatcher.retrieveDocument(url, fetchOnline, timeout, false, reindexing);
        final ArrayList<MediaSnippet> a = new ArrayList<MediaSnippet>();
        if (document != null) {
            if ((mediatype == ContentDomain.ALL) || (mediatype == ContentDomain.AUDIO)) a.addAll(computeMediaSnippets(document, queryhashes, ContentDomain.AUDIO));
            if ((mediatype == ContentDomain.ALL) || (mediatype == ContentDomain.VIDEO)) a.addAll(computeMediaSnippets(document, queryhashes, ContentDomain.VIDEO));
            if ((mediatype == ContentDomain.ALL) || (mediatype == ContentDomain.APP)) a.addAll(computeMediaSnippets(document, queryhashes, ContentDomain.APP));
            if ((mediatype == ContentDomain.ALL) || (mediatype == ContentDomain.IMAGE)) a.addAll(computeImageSnippets(document, queryhashes));
        }
        return a;
    }
    
    public static ArrayList<MediaSnippet> computeMediaSnippets(final Document document, final TreeSet<byte[]> queryhashes, final ContentDomain mediatype) {
        
        if (document == null) return new ArrayList<MediaSnippet>();
        Map<DigestURI, String> media = null;
        if (mediatype == ContentDomain.AUDIO) media = document.getAudiolinks();
        else if (mediatype == ContentDomain.VIDEO) media = document.getVideolinks();
        else if (mediatype == ContentDomain.APP) media = document.getApplinks();
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
            String u = url.toString();
            if (u.indexOf(".ico") >= 0 || u.indexOf("favicon") >= 0) continue;
            if (ientry.height() > 0 && ientry.height() < 64) continue;
            if (ientry.width() > 0 && ientry.width() < 64) continue;
            desc = ientry.alt();
            int appcount = 0;
            s = TextSnippet.removeAppearanceHashes(url.toNormalform(false, false), queryhashes);
            appcount += queryhashes.size() - s.size();
            // if the resulting set is empty, then _all_ words from the query appeared in the url
            s = TextSnippet.removeAppearanceHashes(desc, s);
            appcount += queryhashes.size() - s.size();
            // if the resulting set is empty, then _all_ search words appeared in the description
            final int ranking = /*(ientry.hashCode() / queryhashes.size() / 2) */ ientry.height() * ientry.width() * appcount * 10000 /* 0x7FFF0000)*/;
            result.add(new MediaSnippet(ContentDomain.IMAGE, url, desc, ientry.width() + " x " + ientry.height(), ranking, document.dc_source()));
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
