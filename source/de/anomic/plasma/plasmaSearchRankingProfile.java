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
    
    public plasmaSearchRankingProfile(String[] order) {
        this.order = order;
        this.coeff = new HashMap();
        for (int i = 0; i < 3; i++) {
            if (this.order[i].equals(plasmaSearchRankingProfile.ORDER_QUALITY))   coeff.put(ENTROPY, new Integer((4 * (3 - i))));
            else if (this.order[i].equals(plasmaSearchRankingProfile.ORDER_DATE)) coeff.put(DATE, new Integer((4 * (3 - i))));
            else if (this.order[i].equals(plasmaSearchRankingProfile.ORDER_YBR))  coeff.put(YBR, new Integer((4 * (3 - i))));
        }
        coeff.put(POSINTEXT, new Integer(11));
        coeff.put(WORDDISTANCE, new Integer(10));
        coeff.put(HITCOUNT, new Integer(9));
        coeff.put(DOMLENGTH, new Integer(8));
        coeff.put(URLLENGTH, new Integer(10));
        coeff.put(URLCOMPS, new Integer(10));
        coeff.put(DESCRLENGTH, new Integer(10));
        coeff.put(DESCRCOMPS, new Integer(10));
        coeff.put(QUERYINURL, new Integer(13));
        coeff.put(QUERYINDESCR, new Integer(14));
        coeff.put(URLCOMPINTOPLIST, new Integer(12));
        coeff.put(DESCRCOMPINTOPLIST, new Integer(11));
    }
    
    public String orderString() {
        return order[0] + "-" + order[1] + "-" + order[2];
    }

    public long preRanking(plasmaWordIndexEntry normalizedEntry) {
        long ranking = 0;
        
        ranking += normalizedEntry.getQuality() << ((Integer) coeff.get(ENTROPY)).intValue();
        ranking += normalizedEntry.getVirtualAge() << ((Integer) coeff.get(DATE)).intValue();
        ranking += plasmaSearchPreOrder.ybr_p(normalizedEntry.getUrlHash()) << ((Integer) coeff.get(YBR)).intValue();
        ranking += (normalizedEntry.posintext()    == 0) ? 0 : (255 - normalizedEntry.posintext()) << ((Integer) coeff.get(POSINTEXT)).intValue();
        ranking += (normalizedEntry.worddistance() == 0) ? 0 : (255 - normalizedEntry.worddistance()) << ((Integer) coeff.get(WORDDISTANCE)).intValue();
        ranking += (normalizedEntry.hitcount()     == 0) ? 0 : normalizedEntry.hitcount() << ((Integer) coeff.get(HITCOUNT)).intValue();
        ranking += (255 - normalizedEntry.domlengthNormalized()) << ((Integer) coeff.get(DOMLENGTH)).intValue();
        return ranking;
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

        // apply 'common-sense' heuristic using references
        for (int j = 0; j < urlcomps.length; j++) {
            if (topwords.contains(urlcomps[j])) ranking += 1 << ((Integer) coeff.get(URLCOMPINTOPLIST)).intValue();
        }
        for (int j = 0; j < descrcomps.length; j++) {
            if (topwords.contains(descrcomps[j])) ranking += 1 << ((Integer) coeff.get(DESCRCOMPINTOPLIST)).intValue();
        }

        // apply query-in-result matching
        Set urlcomph = plasmaSearchQuery.words2hashes(urlcomps);
        Set descrcomph = plasmaSearchQuery.words2hashes(descrcomps);
        Iterator shi = query.queryHashes.iterator();
        String queryhash;
        while (shi.hasNext()) {
            queryhash = (String) shi.next();
            if (urlcomph.contains(queryhash)) ranking += 1 << ((Integer) coeff.get(QUERYINURL)).intValue();
            if (descrcomph.contains(queryhash)) ranking += 1 << ((Integer) coeff.get(QUERYINDESCR)).intValue();
        }

        // prefer short urls
        ranking += (255 - page.url().toString().length()) << ((Integer) coeff.get(URLLENGTH)).intValue();
        ranking += (24 - urlcomps.length) << ((Integer) coeff.get(URLCOMPS)).intValue();

        // prefer long descriptions
        ranking += (40 - Math.abs(40 - Math.min(40, page.descr().length()))) << ((Integer) coeff.get(DESCRLENGTH)).intValue();
        ranking += (8 - Math.abs(8 - Math.min(8, descrcomps.length))) << ((Integer) coeff.get(DESCRCOMPS)).intValue();

        return ranking;
    }
    
}
