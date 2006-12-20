// plasmaSearchRankingProfile.java 
// -------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created: 05.02.2006
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIEntryNew;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;

public class plasmaSearchRankingProfile {

    // pre-sort attributes
    public static final String DOMLENGTH     = "domlength";
    public static final String YBR           = "ybr";
    public static final String DATE          = "date";
    public static final String WORDSINTITLE  = "wordsintitle";
    public static final String WORDSINTEXT   = "wordsintext";
    public static final String PHRASESINTEXT = "phrasesintext";
    public static final String LLOCAL        = "llocal";
    public static final String LOTHER        = "lother";
    public static final String URLLENGTH     = "urllength";
    public static final String URLCOMPS      = "urlcomps";
    public static final String HITCOUNT      = "hitcount";
    public static final String POSINTEXT     = "posintext";
    public static final String POSOFPHRASE   = "posofphrase";
    public static final String WORDDISTANCE  = "worddistance";
    public static final String APPURL        = "appurl";
    public static final String APPDESCR      = "appdescr";
    public static final String APPAUTHOR     = "appauthor";
    public static final String APPTAGS       = "apptags";
    public static final String APPREF        = "appref";
    public static final String APPEMPH       = "appemph";
    public static final String CATINDEXOF    = "catindexof";
    public static final String CATHASIMAGE   = "cathasimage";
    public static final String CATHASAUDIO   = "cathasaudio";
    public static final String CATHASVIDEO   = "cathasvideo";
    public static final String CATHASAPP     = "cathasapp";

    // post-sort predicates
    public static final String QUERYINURL         = "queryinurl";
    public static final String QUERYINDESCR       = "queryindescr";
    public static final String URLCOMPINTOPLIST   = "urlcompintoplist";
    public static final String DESCRCOMPINTOPLIST = "descrcompintoplist";
    public static final String PREFER = "prefer";

    private int
        coeff_domlength, coeff_ybr, coeff_date, coeff_wordsintitle, coeff_wordsintext, coeff_phrasesintext,
        coeff_llocal, coeff_lother, coeff_urllength, coeff_urlcomps, coeff_hitcount, 
        coeff_posintext, coeff_posofphrase, coeff_worddistance,
        coeff_appurl, coeff_appdescr, coeff_appauthor, coeff_apptags, coeff_appref, coeff_appemph,
        coeff_catindexof, coeff_cathasimage, coeff_cathasaudio, coeff_cathasvideo, coeff_cathasapp,
        coeff_queryinurl, coeff_queryindescr, coeff_urlcompintoplist, coeff_descrcompintoplist, coeff_prefer;
    
    public plasmaSearchRankingProfile(String mediatype) {
        // set default-values
        if (mediatype == null) mediatype = "text";
        coeff_domlength          = 8;
        coeff_ybr                = 8;
        coeff_date               = 4;
        coeff_wordsintitle       = 4;
        coeff_wordsintext        = 1;
        coeff_phrasesintext      = 1;
        coeff_llocal             = 2;
        coeff_lother             = 3;
        coeff_urllength          = 14;
        coeff_urlcomps           = 14;
        coeff_hitcount           = 5;
        coeff_posintext          = 7;
        coeff_posofphrase        = 6;
        coeff_worddistance       = 15;
        coeff_appurl             = 14;
        coeff_appdescr           = 13;
        coeff_appauthor          = 13;
        coeff_apptags            = 8;
        coeff_appref             = 9;
        coeff_appemph            = 11;
        coeff_queryinurl         = 12;
        coeff_queryindescr       = 8;
        coeff_urlcompintoplist   = 3;
        coeff_descrcompintoplist = 2;
        coeff_prefer             = 15;
        coeff_catindexof         = (mediatype.equals("text")) ? 0 : 10;
        coeff_cathasimage        = (mediatype.equals("image")) ? 15 : 0;
        coeff_cathasaudio        = (mediatype.equals("audio")) ? 15 : 0;
        coeff_cathasvideo        = (mediatype.equals("video")) ? 15 : 0;
        coeff_cathasapp          = (mediatype.equals("app")) ? 15 : 0;
    }
    
    public plasmaSearchRankingProfile(String prefix, String profile) {
        this("text"); // set defaults
        if ((profile != null) && (profile.length() > 0)) {
            //parse external form
            HashMap coeff = new HashMap();
            String[] elts = ((profile.startsWith("{") && (profile.endsWith("}"))) ? profile.substring(1, profile.length() - 1) : profile).split(",");
            int p;
            int s = (prefix == null) ? 0 : prefix.length();
            String e;
            for (int i = 0; i < elts.length; i++) {
                e = elts[i].trim();
                if ((s == 0) || (e.startsWith(prefix))) {
                    coeff.put(e.substring(s, (p = e.indexOf("="))), new Integer(Integer.parseInt(e.substring(p + 1))));
                }
            }
            coeff_domlength          = parseMap(coeff, DOMLENGTH, coeff_domlength);
            coeff_ybr                = parseMap(coeff, YBR, coeff_ybr);
            coeff_date               = parseMap(coeff, DATE, coeff_date);
            coeff_wordsintitle       = parseMap(coeff, WORDSINTITLE, coeff_wordsintitle);
            coeff_wordsintext        = parseMap(coeff, WORDSINTEXT, coeff_wordsintext);
            coeff_phrasesintext      = parseMap(coeff, PHRASESINTEXT, coeff_phrasesintext);
            coeff_llocal             = parseMap(coeff, LLOCAL, coeff_llocal);
            coeff_lother             = parseMap(coeff, LOTHER, coeff_lother);
            coeff_urllength          = parseMap(coeff, URLLENGTH, coeff_urllength);
            coeff_urlcomps           = parseMap(coeff, URLCOMPS, coeff_urlcomps);
            coeff_hitcount           = parseMap(coeff, HITCOUNT, coeff_hitcount);
            coeff_posintext          = parseMap(coeff, POSINTEXT, coeff_posintext);
            coeff_posofphrase        = parseMap(coeff, POSOFPHRASE, coeff_posofphrase);
            coeff_worddistance       = parseMap(coeff, WORDDISTANCE, coeff_worddistance);
            coeff_appurl             = parseMap(coeff, APPURL, coeff_appurl);
            coeff_appdescr           = parseMap(coeff, APPDESCR, coeff_appdescr);
            coeff_appauthor          = parseMap(coeff, APPAUTHOR, coeff_appauthor);
            coeff_apptags            = parseMap(coeff, APPTAGS, coeff_apptags);
            coeff_appref             = parseMap(coeff, APPREF, coeff_appref);
            coeff_appemph            = parseMap(coeff, APPEMPH, coeff_appemph);
            coeff_catindexof         = parseMap(coeff, APPEMPH, coeff_catindexof);
            coeff_cathasimage        = parseMap(coeff, APPEMPH, coeff_cathasimage);
            coeff_cathasaudio        = parseMap(coeff, APPEMPH, coeff_cathasaudio);
            coeff_cathasvideo        = parseMap(coeff, APPEMPH, coeff_cathasvideo);
            coeff_cathasapp          = parseMap(coeff, APPEMPH, coeff_cathasapp);
            coeff_queryinurl         = parseMap(coeff, QUERYINURL, coeff_queryinurl);
            coeff_queryindescr       = parseMap(coeff, QUERYINDESCR, coeff_queryindescr);
            coeff_urlcompintoplist   = parseMap(coeff, URLCOMPINTOPLIST, coeff_urlcompintoplist);
            coeff_descrcompintoplist = parseMap(coeff, DESCRCOMPINTOPLIST, coeff_descrcompintoplist);
            coeff_prefer             = parseMap(coeff, PREFER, coeff_prefer);
        }
    }
    
    private static int parseMap(HashMap coeff, String attr, int dflt) {
        if (coeff.containsKey(attr)) try {
            return Integer.parseInt((String) coeff.get(attr));
        } catch (NumberFormatException e) {
            return dflt;
        } else {
            return dflt;
        }
    }

    public String toExternalString() {
        return toExternalMap("").toString();
    }
    
    public Map toExternalMap(String prefix) {
        Map ext = new HashMap();
        ext.put(prefix + DOMLENGTH, Integer.toString(coeff_domlength));
        ext.put(prefix + YBR, Integer.toString(coeff_ybr));
        ext.put(prefix + DATE, Integer.toString(coeff_date));
        ext.put(prefix + WORDSINTITLE, Integer.toString(coeff_wordsintitle));
        ext.put(prefix + WORDSINTEXT, Integer.toString(coeff_wordsintext));
        ext.put(prefix + PHRASESINTEXT, Integer.toString(coeff_phrasesintext));
        ext.put(prefix + LLOCAL, Integer.toString(coeff_llocal));
        ext.put(prefix + LOTHER, Integer.toString(coeff_lother));
        ext.put(prefix + URLLENGTH, Integer.toString(coeff_urllength));
        ext.put(prefix + URLCOMPS, Integer.toString(coeff_urlcomps));
        ext.put(prefix + HITCOUNT, Integer.toString(coeff_hitcount));
        ext.put(prefix + POSINTEXT, Integer.toString(coeff_posintext));
        ext.put(prefix + POSOFPHRASE, Integer.toString(coeff_posofphrase));
        ext.put(prefix + WORDDISTANCE, Integer.toString(coeff_worddistance));
        ext.put(prefix + APPURL, Integer.toString(coeff_appurl));
        ext.put(prefix + APPDESCR, Integer.toString(coeff_appdescr));
        ext.put(prefix + APPAUTHOR, Integer.toString(coeff_appauthor));
        ext.put(prefix + APPTAGS, Integer.toString(coeff_apptags));
        ext.put(prefix + APPREF, Integer.toString(coeff_appref));
        ext.put(prefix + APPEMPH, Integer.toString(coeff_appemph));
        ext.put(prefix + CATINDEXOF, Integer.toString(coeff_catindexof));
        ext.put(prefix + CATHASIMAGE, Integer.toString(coeff_cathasimage));
        ext.put(prefix + CATHASAUDIO, Integer.toString(coeff_cathasaudio));
        ext.put(prefix + CATHASVIDEO, Integer.toString(coeff_cathasvideo));
        ext.put(prefix + CATHASAPP, Integer.toString(coeff_cathasapp));
        ext.put(prefix + QUERYINURL, Integer.toString(coeff_queryinurl));
        ext.put(prefix + QUERYINDESCR, Integer.toString(coeff_queryindescr));
        ext.put(prefix + URLCOMPINTOPLIST, Integer.toString(coeff_urlcompintoplist));
        ext.put(prefix + DESCRCOMPINTOPLIST, Integer.toString(coeff_descrcompintoplist));
        ext.put(prefix + PREFER, Integer.toString(coeff_prefer));
        return ext;
    }
    
    public String toExternalURLGet(String prefix) {
        Iterator i = toExternalMap("").entrySet().iterator();
        Map.Entry entry;
        StringBuffer ext = new StringBuffer();
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            ext.append("&");
            ext.append(prefix);
            ext.append((String) entry.getKey());
            ext.append("=");
            ext.append(entry.getValue());
        }
        return new String(ext);
    }
    
    public long preRanking(indexRWIEntry normalizedEntry, String searchedWord) {
        // the normalizedEntry must be a normalized indexEntry
        long ranking = 0;
        ranking += (256 - plasmaURL.domLengthNormalized(normalizedEntry.urlHash())) << coeff_domlength;
        ranking += plasmaSearchPreOrder.ybr_p(normalizedEntry.urlHash()) << coeff_ybr;
        ranking += normalizedEntry.virtualAge() << coeff_date;
        ranking += normalizedEntry.wordsintitle() << coeff_wordsintitle;
        ranking += normalizedEntry.wordsintext() << coeff_wordsintext;
        ranking += normalizedEntry.phrasesintext() << coeff_phrasesintext;
        ranking += normalizedEntry.llocal() << coeff_llocal;
        ranking += normalizedEntry.lother() << coeff_lother;
        ranking += (normalizedEntry.urllength() == 0) ? 0 : (256 - normalizedEntry.urllength()) << coeff_urllength;
        ranking += (normalizedEntry.urlcomps() == 0) ? 0 : (256 - normalizedEntry.urlcomps()) << coeff_urlcomps;
        ranking += (normalizedEntry.hitcount() == 0) ? 0 : normalizedEntry.hitcount() << coeff_hitcount;
        ranking += (normalizedEntry.posintext() == 0) ? 0 : (256 - normalizedEntry.posintext()) << coeff_posintext;
        ranking += (normalizedEntry.posofphrase() == 0) ? 0 : (256 - normalizedEntry.hitcount()) << coeff_posofphrase;
        ranking += (normalizedEntry.worddistance() == 0) ? 0 : (256 - normalizedEntry.worddistance()) << coeff_worddistance;

        kelondroBitfield flags = normalizedEntry.flags();
        ranking += (flags.get(indexRWIEntryNew.flag_app_url)) ? 256 << coeff_appurl : 0;
        ranking += (flags.get(indexRWIEntryNew.flag_app_descr)) ? 256 << coeff_appdescr : 0;
        ranking += (flags.get(indexRWIEntryNew.flag_app_author)) ? 256 << coeff_appauthor : 0;
        ranking += (flags.get(indexRWIEntryNew.flag_app_tags)) ? 256 << coeff_apptags : 0;
        ranking += (flags.get(indexRWIEntryNew.flag_app_reference)) ? 256 << coeff_appref : 0;
        ranking += (flags.get(indexRWIEntryNew.flag_app_emphasized)) ? 256 << coeff_appemph : 0;
        ranking += (flags.get(plasmaCondenser.flag_cat_indexof)) ? 256 << coeff_catindexof : 0;
        ranking += (flags.get(plasmaCondenser.flag_cat_hasimage)) ? 256 << coeff_cathasimage : 0;
        ranking += (flags.get(plasmaCondenser.flag_cat_hasaudio)) ? 256 << coeff_cathasaudio : 0;
        ranking += (flags.get(plasmaCondenser.flag_cat_hasvideo)) ? 256 << coeff_cathasvideo : 0;
        ranking += (flags.get(plasmaCondenser.flag_cat_hasapp)) ? 256 << coeff_cathasapp : 0;
        
        ranking += (plasmaURL.probablyRootURL(normalizedEntry.urlHash())) ? 16 << coeff_urllength : 0;
        ranking += (plasmaURL.probablyWordURL(normalizedEntry.urlHash(), searchedWord) != null) ? 256 << coeff_queryinurl : 0;

        /*
        if (indexURL.probablyWordURL(normalizedEntry.urlHash(), searchedWord))
            System.out.println("DEBUG - hash " + normalizedEntry.urlHash() + " contains word " + searchedWord + ", weighted " + ((Integer) coeff.get(QUERYINURL)).intValue() + ", ranking = " + ranking);
        else
            System.out.println("DEBUG - hash " + normalizedEntry.urlHash() + " contains not word " + searchedWord + ", ranking = " + ranking);
        */
        return ranking;
    }
    
    public long postRanking(
                    long ranking,
                    plasmaSearchQuery query,
                    Set topwords,
                    String[] urlcomps,
                    String[] descrcomps,
                    indexURLEntry page) {

        // for media search: prefer pages with many links
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) ranking += page.limage() << coeff_cathasimage;
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) ranking += page.limage() << coeff_cathasaudio;
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) ranking += page.limage() << coeff_cathasvideo;
        if (query.contentdom == plasmaSearchQuery.CONTENTDOM_APP  ) ranking += page.limage() << coeff_cathasapp;
        
        // prefer hit with 'prefer' pattern
        indexURLEntry.Components comp = page.comp();
        if (comp.url().toNormalform().matches(query.prefer)) ranking += 256 << coeff_prefer;
        if (comp.descr().matches(query.prefer)) ranking += 256 << coeff_prefer;
        
        // apply 'common-sense' heuristic using references
        for (int j = 0; j < urlcomps.length; j++) {
            if (topwords.contains(urlcomps[j])) ranking += 256 << coeff_urlcompintoplist;
        }
        for (int j = 0; j < descrcomps.length; j++) {
            if (topwords.contains(descrcomps[j])) ranking += 256 << coeff_descrcompintoplist;
        }

        // apply query-in-result matching
        Set urlcomph = plasmaCondenser.words2hashSet(urlcomps);
        Set descrcomph = plasmaCondenser.words2hashSet(descrcomps);
        Iterator shi = query.queryHashes.iterator();
        String queryhash;
        while (shi.hasNext()) {
            queryhash = (String) shi.next();
            if (urlcomph.contains(queryhash)) ranking += 256 << coeff_queryinurl;
            if (descrcomph.contains(queryhash)) ranking += 256 << coeff_queryindescr;
        }

        return ranking;
    }
    
}
