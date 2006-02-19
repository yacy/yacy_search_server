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

public class plasmaSearchRankingProfile {

    // old parameters for ordering
    public static final String ORDER_QUALITY = "Quality";
    public static final String ORDER_DATE    = "Date";
    public static final String ORDER_YBR     = "YBR";
    
    // pre-sort attributes
    public static final String ENTROPY = "entropy";
    public static final String DATE = "date";
    public static final String YBR = "ybr";
    public static final String POSINTEXT = "posintext";
    public static final String WORDDISTANCE = "worddistance";
    public static final String HITCOUNT = "hitcount";
    public static final String DOMLENGTH = "domlength";
    
    // post-sort attributes
    public static final String URLLENGTH = "urllength";
    public static final String URLCOMPS = "urlcomps";
    public static final String DESCRLENGTH = "descrlength";
    public static final String DESCRCOMPS = "descrcomps";

    // post-sort predicates
    public static final String QUERYINURL = "queryinurl";
    public static final String QUERYINDESCR = "queryindescr";
    public static final String URLCOMPINTOPLIST = "urlcompintoplist";
    public static final String DESCRCOMPINTOPLIST = "descrcompintoplist";
    
    public String[] order;
    private HashMap coeff;
    
    public plasmaSearchRankingProfile() {
        // set some default-values
        this.order = null;
        this.coeff = new HashMap();
        coeff.put(ENTROPY, new Integer(0));
        coeff.put(DATE, new Integer(4));
        coeff.put(YBR, new Integer(8));
        coeff.put(POSINTEXT, new Integer(7));
        coeff.put(WORDDISTANCE, new Integer(6));
        coeff.put(HITCOUNT, new Integer(5));
        coeff.put(DOMLENGTH, new Integer(8));
        coeff.put(URLLENGTH, new Integer(15));
        coeff.put(URLCOMPS, new Integer(15));
        coeff.put(DESCRLENGTH, new Integer(4));
        coeff.put(DESCRCOMPS, new Integer(4));
        coeff.put(QUERYINURL, new Integer(13));
        coeff.put(QUERYINDESCR, new Integer(8));
        coeff.put(URLCOMPINTOPLIST, new Integer(3));
        coeff.put(DESCRCOMPINTOPLIST, new Integer(2));
    }
    
    public plasmaSearchRankingProfile(String prefix, String profile) {
        this(); // set defaults
        //parse external form
        String[] elts = profile.substring(1, profile.length() - 1).split(",");
        int p;
        int s = prefix.length();
        String e;
        for (int i = 0; i < elts.length; i++) {
            e = elts[i].trim();
            if ((s == 0) || (e.startsWith(prefix))) {
                coeff.put(e.substring(s, (p = e.indexOf("="))), new Integer(Integer.parseInt(e.substring(p + 1))));
            }
        }
    }
    
    public plasmaSearchRankingProfile(String[] order) {
        this(); // set defaults
        this.order = order;
        // overwrite defaults with order attributes
        for (int i = 0; i < 3; i++) {
            if (this.order[i].equals(plasmaSearchRankingProfile.ORDER_QUALITY))   coeff.put(ENTROPY, new Integer((4 * (3 - i))));
            else if (this.order[i].equals(plasmaSearchRankingProfile.ORDER_DATE)) coeff.put(DATE, new Integer((4 * (3 - i))));
            else if (this.order[i].equals(plasmaSearchRankingProfile.ORDER_YBR))  coeff.put(YBR, new Integer((4 * (3 - i))));
        }
    }
    
    public String orderString() {
        if (order == null) return "YBR-Date-Quality";
        return order[0] + "-" + order[1] + "-" + order[2];
    }

    public String toExternalString() {
        return coeff.toString();
    }
    
    public Map toExternalMap(String prefix) {
        Iterator i = this.coeff.entrySet().iterator();
        Map.Entry entry;
        Map ext = new HashMap();
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            ext.put(prefix + (String) entry.getKey(), entry.getValue());
        }
        return ext;
    }
    
    public String toExternalURLGet(String prefix) {
        Iterator i = this.coeff.entrySet().iterator();
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
    
    public HashMap getPreRanking(plasmaWordIndexEntry normalizedEntry){
        HashMap map=new HashMap();
        map.put(ENTROPY, new Integer(normalizedEntry.getQuality()));
        map.put(DATE, new Integer(normalizedEntry.getVirtualAge()));
        map.put(YBR, new Integer(plasmaSearchPreOrder.ybr_p(normalizedEntry.getUrlHash())));
        map.put(POSINTEXT, new Integer((normalizedEntry.posintext() == 0) ? 0 : (255 - normalizedEntry.posintext())));
        map.put(WORDDISTANCE, new Integer((normalizedEntry.worddistance() == 0) ? 0 : (255 - normalizedEntry.worddistance())));
        map.put(HITCOUNT, new Integer((normalizedEntry.hitcount() == 0) ? 0 : normalizedEntry.hitcount()));
        map.put(DOMLENGTH, new Integer((255 - normalizedEntry.domlengthNormalized())));
        return map;
    }
    public long preRanking(plasmaWordIndexEntry normalizedEntry) {
        long ranking = 0;
        HashMap map=getPreRanking(normalizedEntry);
        ranking += ((Integer)map.get(ENTROPY)).intValue() << ((Integer) coeff.get(ENTROPY)).intValue();
        ranking += ((Integer)map.get(DATE)).intValue() << ((Integer) coeff.get(DATE)).intValue();
        ranking += ((Integer)map.get(YBR)).intValue() << ((Integer) coeff.get(YBR)).intValue();
        ranking += ((Integer)map.get(POSINTEXT)).intValue() << ((Integer) coeff.get(POSINTEXT)).intValue();
        ranking += ((Integer)map.get(WORDDISTANCE)).intValue() << ((Integer) coeff.get(WORDDISTANCE)).intValue();
        ranking += ((Integer)map.get(HITCOUNT)).intValue() << ((Integer) coeff.get(HITCOUNT)).intValue();
        ranking += ((Integer)map.get(DOMLENGTH)).intValue() << ((Integer) coeff.get(DOMLENGTH)).intValue();
        return ranking;
    }
    
    public HashMap getPostRanking(plasmaWordIndexEntry normalizedEntry,
            plasmaSearchQuery query,
            Set topwords,
            String[] urlcomps,
            String[] descrcomps,
            plasmaCrawlLURL.Entry page){
        HashMap map=new HashMap();
        HashMap tmp, tmp2;

        //apply 'common-sense' heuristic using references
        tmp=new HashMap();
        for (int j = 0; j < urlcomps.length; j++) {
            if (topwords.contains(urlcomps[j]))
                tmp.put(urlcomps[j], new Integer(256));
            else
                tmp.put(urlcomps[j], new Integer(0));
        }
        map.put(URLCOMPINTOPLIST, tmp);
        tmp=new HashMap();
        for (int j = 0; j < descrcomps.length; j++) {
            if (topwords.contains(descrcomps[j]))
                tmp.put(descrcomps[j], new Integer(256));
            else
                tmp.put(descrcomps[j], new Integer(0));
        }
        map.put(DESCRCOMPINTOPLIST, tmp);
        
        // apply query-in-result matching
        Set urlcomph = plasmaSearchQuery.words2hashes(urlcomps);
        Set descrcomph = plasmaSearchQuery.words2hashes(descrcomps);
        Iterator shi = query.queryHashes.iterator();
        String queryhash;
        tmp=new HashMap();
        tmp2=new HashMap();
        while (shi.hasNext()) {
            queryhash = (String) shi.next();
            if (urlcomph.contains(queryhash))
                tmp.put(queryhash, new Integer(256));
            else
                tmp.put(queryhash, new Integer(0));
            if (descrcomph.contains(queryhash))
                tmp2.put(queryhash, new Integer(256));
            else
                tmp2.put(queryhash, new Integer(0));
        }
        map.put(QUERYINURL, tmp);
        map.put(QUERYINDESCR, tmp2);

        // prefer short urls
        map.put(URLLENGTH, new Integer((256 - page.url().toString().length())));
        map.put(URLCOMPS, new Integer((32 - urlcomps.length)));

        // prefer long descriptions
        map.put(DESCRLENGTH, new Integer((255 * page.descr().length() / 80)));
        map.put(DESCRCOMPS, new Integer((255 * (12 - Math.abs(12 - Math.min(12, descrcomps.length))) / 12)));
        return map;
    }
    public long postRanking(
                    plasmaWordIndexEntry normalizedEntry,
                    plasmaSearchQuery query,
                    Set topwords,
                    String[] urlcomps,
                    String[] descrcomps,
                    plasmaCrawlLURL.Entry page) {

        // apply pre-calculated order attributes
        long ranking = this.preRanking(normalizedEntry);
        HashMap map=getPostRanking(normalizedEntry, query, topwords, urlcomps, descrcomps, page);
        Iterator it;
        HashMap tmp;

        // apply 'common-sense' heuristic using references
        tmp=(HashMap) map.get(URLCOMPINTOPLIST);
        it=tmp.keySet().iterator();
        while(it.hasNext()){
            ranking+= ((Integer)tmp.get((String)it.next())).intValue() << ((Integer)coeff.get(URLCOMPINTOPLIST)).intValue();
        }
        tmp=(HashMap) map.get(DESCRCOMPINTOPLIST);
        it=tmp.keySet().iterator();
        while(it.hasNext()){
            ranking+= ((Integer)tmp.get((String)it.next())).intValue() << ((Integer)coeff.get(DESCRCOMPINTOPLIST)).intValue();
        }

        // apply query-in-result matching
        tmp=(HashMap) map.get(QUERYINURL);
        it=tmp.keySet().iterator();
        while(it.hasNext()){
            ranking+= ((Integer)tmp.get((String)it.next())).intValue() << ((Integer)coeff.get(QUERYINURL)).intValue();
        }
        tmp=(HashMap) map.get(QUERYINDESCR);
        it=tmp.keySet().iterator();
        while(it.hasNext()){
            ranking+= ((Integer)tmp.get((String)it.next())).intValue() << ((Integer)coeff.get(QUERYINDESCR)).intValue();
        }

        // prefer short urls
        ranking += ((Integer)map.get(URLLENGTH)).intValue() << ((Integer) coeff.get(URLLENGTH)).intValue();
        ranking += ((Integer)map.get(URLCOMPS)).intValue() << ((Integer) coeff.get(URLCOMPS)).intValue();

        // prefer long descriptions
        ranking += ((Integer)map.get(DESCRLENGTH)).intValue() << ((Integer) coeff.get(DESCRLENGTH)).intValue();
        ranking += ((Integer)map.get(DESCRCOMPS)).intValue() << ((Integer) coeff.get(DESCRCOMPS)).intValue();

        return ranking;
    }
    
}
