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
    public static final String POSINPHRASE   = "posinphrase";
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
    public static final String URLCOMPINTOPLIST   = "urlcompintoplist";
    public static final String DESCRCOMPINTOPLIST = "descrcompintoplist";
    public static final String PREFER = "prefer";

    public int
        coeff_domlength, coeff_ybr, coeff_date, coeff_wordsintitle, coeff_wordsintext, coeff_phrasesintext,
        coeff_llocal, coeff_lother, coeff_urllength, coeff_urlcomps, coeff_hitcount, 
        coeff_posintext, coeff_posofphrase, coeff_posinphrase, coeff_worddistance,
        coeff_appurl, coeff_appdescr, coeff_appauthor, coeff_apptags, coeff_appref, coeff_appemph,
        coeff_catindexof, coeff_cathasimage, coeff_cathasaudio, coeff_cathasvideo, coeff_cathasapp,
        coeff_urlcompintoplist, coeff_descrcompintoplist, coeff_prefer;
    
    public plasmaSearchRankingProfile(int mediatype) {
        // set default-values
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
        coeff_posinphrase        = 1;
        coeff_worddistance       = 15;
        coeff_appurl             = 14;
        coeff_appdescr           = 13;
        coeff_appauthor          = 13;
        coeff_apptags            = 8;
        coeff_appref             = 9;
        coeff_appemph            = 13;
        coeff_urlcompintoplist   = 3;
        coeff_descrcompintoplist = 2;
        coeff_prefer             = 15;
        coeff_catindexof         = (mediatype == plasmaSearchQuery.CONTENTDOM_TEXT) ? 1 : 10;
        coeff_cathasimage        = (mediatype == plasmaSearchQuery.CONTENTDOM_IMAGE) ? 15 : 1;
        coeff_cathasaudio        = (mediatype == plasmaSearchQuery.CONTENTDOM_AUDIO) ? 15 : 1;
        coeff_cathasvideo        = (mediatype == plasmaSearchQuery.CONTENTDOM_VIDEO) ? 15 : 1;
        coeff_cathasapp          = (mediatype == plasmaSearchQuery.CONTENTDOM_APP) ? 15 : 1;
    }
    
    public plasmaSearchRankingProfile(String prefix, String profile) {
        this(plasmaSearchQuery.CONTENTDOM_TEXT); // set defaults
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
                    p = e.indexOf("=");
                    if (p < 0) System.out.println("DEBUG: bug in plasmaSearchRankingProfile: e = " + e);
                    if ((p > 0) && (e.length() > p + 1)) coeff.put(e.substring(s, p), new Integer(Integer.parseInt(e.substring(p + 1))));
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
            coeff_posinphrase        = parseMap(coeff, POSINPHRASE, coeff_posinphrase);
            coeff_worddistance       = parseMap(coeff, WORDDISTANCE, coeff_worddistance);
            coeff_appurl             = parseMap(coeff, APPURL, coeff_appurl);
            coeff_appdescr           = parseMap(coeff, APPDESCR, coeff_appdescr);
            coeff_appauthor          = parseMap(coeff, APPAUTHOR, coeff_appauthor);
            coeff_apptags            = parseMap(coeff, APPTAGS, coeff_apptags);
            coeff_appref             = parseMap(coeff, APPREF, coeff_appref);
            coeff_appemph            = parseMap(coeff, APPEMPH, coeff_appemph);
            coeff_catindexof         = parseMap(coeff, CATINDEXOF, coeff_catindexof);
            coeff_cathasimage        = parseMap(coeff, CATHASIMAGE, coeff_cathasimage);
            coeff_cathasaudio        = parseMap(coeff, CATHASAUDIO, coeff_cathasaudio);
            coeff_cathasvideo        = parseMap(coeff, CATHASVIDEO, coeff_cathasvideo);
            coeff_cathasapp          = parseMap(coeff, CATHASAPP, coeff_cathasapp);
            coeff_urlcompintoplist   = parseMap(coeff, URLCOMPINTOPLIST, coeff_urlcompintoplist);
            coeff_descrcompintoplist = parseMap(coeff, DESCRCOMPINTOPLIST, coeff_descrcompintoplist);
            coeff_prefer             = parseMap(coeff, PREFER, coeff_prefer);
        }
    }
    
    private static int parseMap(HashMap coeff, String attr, int dflt) {
        if (coeff.containsKey(attr)) try {
            return ((Integer) coeff.get(attr)).intValue();
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
    	Map ext = preToExternalMap(prefix);
    	ext.putAll(postToExternalMap(prefix));
    	return ext;
    }
    
    public Map preToExternalMap(String prefix) {
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
        ext.put(prefix + POSINPHRASE, Integer.toString(coeff_posinphrase));
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
        return ext;
    }
    
    public Map postToExternalMap(String prefix) {
    	Map ext = new HashMap();
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
    
}
