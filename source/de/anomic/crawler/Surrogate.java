// Surrogate.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.04.2009 on http://yacy.net
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


package de.anomic.crawler;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import de.anomic.kelondro.util.DateFormatter;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.yacy.yacyURL;

public class Surrogate extends HashMap<String, String> {
    private static final long serialVersionUID = -2050291583515701559L;

    public Surrogate() {
        super();
    }
    public Date date() {
        String d = this.get("date");
        if (d == null) d = this.get("docdatetime");
        if (d == null) return null;
        try {
            return DateFormatter.parseISO8601(d);
        } catch (ParseException e) {
            e.printStackTrace();
            return new Date();
        }
    }
    public yacyURL url() {
        String u = this.get("url");
        if (u == null) return null;
        try {
            return new yacyURL(u, null);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }
    public String language() {
        String l = this.get("language");
        if (l == null) return "en"; else return l;
    }
    public String title() {
        String t = this.get("title");
        return stripCDATA(t);
    }
    public String body() {
        String t = this.get("body");
        return stripCDATA(t);
    }
    public String[] categories() {
        String t = this.get("categories");
        t = stripCDATA(t);
        return t.split(";");
    }
    private String stripCDATA(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("<![CDATA[")) s = s.substring(9);
        if (s.endsWith("]]")) s = s.substring(0, s.length() - 2);
        return s;
    }
    public plasmaParserDocument document() {
        HashSet<String> languages = new HashSet<String>();
        languages.add(language());
        
        return new plasmaParserDocument(url(), "text/html", "utf-8", languages,
                                categories(), title(), "",
                                null, "",
                                body().getBytes(), null, null);
    }
}