// DCEntry.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.04.2009 on http://yacy.net
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


package net.yacy.document.content;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.text.Collator;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import org.apache.solr.common.params.MultiMapSolrParams;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Document;

public class DCEntry extends MultiMapSolrParams {

    private static final long    serialVersionUID = -2050291583515701559L;

    // use a collator to relax when distinguishing between lowercase und uppercase letters
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }
    public  static final DCEntry poison = new DCEntry();

    public DCEntry() {
        super(new TreeMap<String, String[]>((Collator) insensitiveCollator.clone()));
    }

    public DCEntry(
            DigestURL url,
            Date date,
            String title,
            String author,
            String body,
            double lat,
            double lon
            ) {
        super(new TreeMap<String, String[]>((Collator) insensitiveCollator.clone()));
        this.getMap().put("dc:identifier", new String[]{url.toNormalform(true)});
        this.getMap().put("dc:date", new String[]{ISO8601Formatter.FORMATTER.format(date)});
        this.getMap().put("dc:title", new String[]{title});
        this.getMap().put("dc:creator", new String[]{author});
        this.getMap().put("dc:description", new String[]{body});
        this.getMap().put("geo:lat", new String[]{Double.toString(lat)});
        this.getMap().put("geo:long", new String[]{Double.toString(lon)});
    }

    /*
    DC according to rfc 5013

    * dc_title
    * dc_creator
    * dc_subject
    * dc_description
    * dc_publisher
    dc_contributor
    dc_date
    dc_type
    * dc_format
    * dc_identifier
    * dc_source
    dc_language
    dc_relation
    dc_coverage
    dc_rights
         */
    public Date getDate() {
        String d = this.get("docdatetime");
        if (d == null) d = this.get("date");
        if (d == null) d = this.get("dc:date");
        if (d == null) d = this.get("last-modified");
        if (d == null) return null;
        if (d.isEmpty()) return null;
        try {
            Date x = ISO8601Formatter.FORMATTER.parse(d);
            Date now = new Date();
            return x.after(now) ? now : x;
        } catch (final ParseException e) {
            ConcurrentLog.logException(e);
            return new Date();
        }
    }

    /**
     * get Identifier (url) (so far only used for surrogate processing)
     * @param useRelationAsAlternative true = take relation if no identifier resolves to url
     * @return
     */
    public DigestURL getIdentifier(boolean useRelationAsAlternative) {
        // identifier may be included multiple times (with all kinds of syntax - example is from on record)
        // <dc:identifier>Astronomy and Astrophysics, 539, A99, 2012</dc:identifier>
        // <dc:identifier>http://hdl.handle.net/2104/8302</dc:identifier>
        // <dc:identifier>10.1051/0004-6361/201117940</dc:identifier>
        String u = this.get("url");
        
        if (u == null) {
            final String[] urls = this.getParams("dc:identifier");
            if (urls == null) {
                return useRelationAsAlternative ? getRelation() : null;
            }
            if (urls.length > 0) { // check best also with 1 in case it's not http urn
                // select one that fits
                u = bestU(urls);
            }
        }

        try {
            return new DigestURL(u);
        } catch (final MalformedURLException e) {
            if (useRelationAsAlternative) {
                DigestURL relation = this.getRelation();
                if (relation != null) return relation;
                ConcurrentLog.warn("DCEntry", "getIdentifier: url is bad, relation also: " + e.getMessage());
            }
            ConcurrentLog.warn("DCEntry", "getIdentifier: url is bad: " + e.getMessage());
            return null;
        }
    }

    public DigestURL getRelation() {
        String u = this.get("dc:relation");
        if (u == null) return null;
        String[] urls = u.split(";");
        if (urls.length > 1) {
            // select one that fits
            u = bestU(urls);
        }
        try {
            return new DigestURL(u);
        } catch (final MalformedURLException e) {
            ConcurrentLog.warn("DCEntry", "getRelation: url is bad: " + e.getMessage());
            return null;
        }
    }

    private static String bestU(String[] urls) {
        if (urls.length > 1) { // with only one ... no choice
            for (String uu: urls) {
                if (uu.startsWith("http://") && (uu.endsWith(".html") || uu.endsWith(".htm") || uu.endsWith(".pdf") || uu.endsWith(".doc") || uu.endsWith(".rss") || uu.endsWith(".xml"))) return uu;
            }
            for (String uu: urls) {
                if (uu.startsWith("http://")) return uu;
            }
            for (String uu: urls) {
                if (uu.startsWith("https://")) return uu;
            }
            for (String uu: urls) {
                if (uu.startsWith("ftp://")) return uu;
            }
        } // but check urn:/doi: resolve
        for (String uu: urls) {
            //doi identifier can be resolved through dx.doi.org:
            //   http://dx.doi.org/doi:10.12775/SIT.2010.010
            //or http://dx.doi.org/10.12775/SIT.2010.010
            if (uu.startsWith("DOI:") || uu.startsWith("doi:")) // saw it upper & lower-case
                return "http://dx.doi.org/" + uu;

            //urn identifier koennen ueber den resolver der d-nb aufgeloest werden:
            //http://nbn-resolving.de/urn:nbn:de:bsz:960-opus-1860
            if (uu.startsWith("urn:")) return "http://nbn-resolving.de/" + uu;
        }
        return urls[0];
    }

    //modified by copperdust; Ukraine, 2012
    public String getLanguage() {//final language computation
        String l = this.get("dc:language");//from document metainfo
        // OAI uses often 3-char languages (ISO639-2) convert to ISO639-1 2-char code)
        // TODO: implement complete list of ISO639-2/ISO639-3 language codes
        if (l != null && l.length() == 3) {
            if (l.startsWith("ger") || l.startsWith("deu")) l = "de";
            if (l.startsWith("eng")) l = "en";
            if (l.startsWith("rus")) l = "ru";
            if (l.startsWith("jpn")) l = "ja";
            if (l.startsWith("ita")) l = "it";
            if (l.startsWith("por")) l = "pt";
            if (l.startsWith("spa")) l = "es";
            if (l.startsWith("chi") || l.startsWith("zho")) l = "zh";
            if (l.startsWith("fre") || l.startsWith("fra")) l = "fr";
            if (l.startsWith("eus") || l.startsWith("baq")) l = "eu";
            if (l.startsWith("gre") || l.startsWith("ell")) l = "el";
            return l;
        }
        if (l == null) l = getIdentifier(true).language(); // determine from identifier-url.TLD
        if (l == null) return this.get("language");//from TLD
        return l;
    }

    public String getType() {
        String t = this.get("dc:type");
        if (t == null) return "";
        return t;
    }

    public String getFormat() {
        String t = this.get("dc:format");
        if (t == null) return "";
        return t;
    }

    public String getSource() {
        String t = this.get("dc:source");
        if (t == null) return "";
        return t;
    }

    public String getRights() {
        String t = this.get("dc:rights");
        if (t == null) return "";
        return t;
    }

    public String getTitle() {
        String t = this.get("title");
        if (t == null) t = this.get("dc:title");
        t = stripCDATA(t);
        if (t == null) return "";
        return t;
    }

    public String getPublisher() {
        String t = this.get("dc:publisher");
        t = stripCDATA(t);
        if (t == null) return "";
        return t;
    }

    public String getCreator() {
        String t = this.get("author");
        if (t == null) t = this.get("dc:creator");
        t = stripCDATA(t);
        if (t == null) return "";
        return t;
    }

    public List<String> getDescriptions() {
        String[] t = this.getParams("dc:description");
        List<String> descriptions = new ArrayList<String>();
        if (t == null) return descriptions;
        for (String s: t) descriptions.add(stripCDATA(s));
        return descriptions;
    }

    /**
     * return list of subjects (keywords)
     * @return string list or null
     */
    public String[] getSubject() {
        String t = this.get("categories");
        String[] tx;
        if (t != null) {
            t = stripCDATA(t);
            return t.split(";");
        }
        tx = this.getParams("dc:subject");
        
        if (tx != null) {
            for (int i = 0; i < tx.length; i++) {
                tx[i] = stripCDATA(tx[i]);
            }
        }
        return tx;
    }

    public double getLon() {
        String t = this.get("geo:long");
        if (t == null) t = this.get("geo:lon");
        t = stripCDATA(t);
        if (t == null) return 0.0d;
        return Double.parseDouble(t);
    }

    public double getLat() {
        String t = this.get("geo:lat");
        if (t == null) t = this.get("geo:lat");
        t = stripCDATA(t);
        if (t == null) return 0.0d;
        return Double.parseDouble(t);
    }

    private static String stripCDATA(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("<![CDATA[")) s = s.substring(9);
        if (s.endsWith("]]")) s = s.substring(0, s.length() - 2);
        return s;
    }

    public Document document() {
        HashSet<String> languages = new HashSet<String>();
        languages.add(getLanguage());
        List<String> t = new ArrayList<String>(1);
        t.add(getTitle());
        return new Document(
            getIdentifier(true),
            "text/html",
            "UTF-8",
            this,
            languages,
            getSubject(), // might be null
            t,
            getCreator(),
            getPublisher(),
            null,
            getDescriptions(),
            getLon(), getLat(),
            "",
            null,
            null,
            null,
            false,
            getDate());
    }

    public void writeXML(OutputStreamWriter os) throws IOException {
        Document doc = document();
        if (doc != null) {
            doc.writeXML(os, this.getDate());
        }
    }
}
