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

    public DigestURL getIdentifier(boolean useRelationAsAlternative) {
        String u = this.get("url");
        if (u == null) u = this.get("dc:identifier");
        if (u == null) return useRelationAsAlternative ? getRelation() : null;
        String[] urls = u.split(";");
        if (urls.length > 1) {
            // select one that fits
            u = bestU(urls);
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
        for (String uu: urls) {
            if (uu.startsWith("http://") && (uu.endsWith(".html") || uu.endsWith(".htm") || uu.endsWith(".pdf") || uu.endsWith(".doc") || uu.endsWith(".rss") || uu.endsWith(".xml"))) return uu;
        }
        for (String uu: urls) {
            if (uu.startsWith("http://")) return uu;
        }
        for (String uu: urls) {
            if (uu.startsWith("ftp://")) return uu;
        }
        for (String uu: urls) {
            //urn identifier koennen ueber den resolver der d-nb aufgeloest werden:
            //http://nbn-resolving.de/urn:nbn:de:bsz:960-opus-1860
            if (uu.startsWith("urn:")) return "http://nbn-resolving.de/" + uu;
        }
        return urls[0];
    }

    //modified by copperdust; Ukraine, 2012
    public String getLanguage() {//final language computation
        String l = this.get("dc:language");//from document metainfo
        if (l == null) l = getIdentifier(true).language();//from symbolic frequency table
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

    public String[] getSubject() {
        String t = this.get("categories");
        if (t == null) t = this.get("dc:subject");
        t = stripCDATA(t);
        if (t == null) return new String[]{};
        return t.split(";");
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
            getSubject(),
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
