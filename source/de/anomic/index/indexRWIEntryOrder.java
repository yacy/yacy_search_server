// indexRWIEntryOrder.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.11.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package de.anomic.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSearchRankingProcess;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.server.serverProcessor;
import de.anomic.yacy.yacyURL;

public class indexRWIEntryOrder {
    private indexRWIVarEntry min, max;
    private plasmaSearchRankingProfile ranking;
    private kelondroMScoreCluster<String> doms; // collected for "authority" heuristic 
    private int maxdomcount;
    
    public indexRWIEntryOrder(plasmaSearchRankingProfile profile) {
        this.min = null;
        this.max = null;
        this.ranking = profile;
        this.doms = new kelondroMScoreCluster<String>();
        this.maxdomcount = 0;
    }
    
    public ArrayList<indexRWIVarEntry> normalizeWith(indexContainer container) {
        // normalize ranking: find minimum and maxiumum of separate ranking criteria
        assert (container != null);
        ArrayList<indexRWIVarEntry> result = null;
        
        //long s0 = System.currentTimeMillis();
        if ((serverProcessor.useCPU > 1) && (container.size() > 600)) {
            // run minmax with two threads
            int middle = container.size() / 2;
            minmaxfinder mmf0 = new minmaxfinder(container, 0, middle);
            mmf0.start(); // fork here
            minmaxfinder mmf1 = new minmaxfinder(container, middle, container.size());
            mmf1.run(); // execute other fork in this thread
            if (this.min == null) this.min = mmf1.entryMin.clone(); else this.min.min(mmf1.entryMin);
            if (this.max == null) this.max = mmf1.entryMax.clone(); else this.max.max(mmf1.entryMax);
            Map.Entry<String, Integer> entry;
            Iterator<Map.Entry<String, Integer>> di = mmf1.domcount().entrySet().iterator();
            while (di.hasNext()) {
            	entry = di.next();
            	this.doms.addScore(entry.getKey(), ((Integer) entry.getValue()).intValue());
            }
            try {mmf0.join();} catch (InterruptedException e) {} // wait for fork thread to finish
            if (this.min == null) this.min = mmf0.entryMin.clone(); else this.min.min(mmf0.entryMin);
            if (this.max == null) this.max = mmf0.entryMax.clone(); else this.max.max(mmf0.entryMax);
            di = mmf0.domcount().entrySet().iterator();
            while (di.hasNext()) {
            	entry = di.next();
            	this.doms.addScore(entry.getKey(), ((Integer) entry.getValue()).intValue());
            }
            result = mmf0.decodedEntries;
            result.addAll(mmf1.decodedContainer());
            //long s1= System.currentTimeMillis(), sc = Math.max(1, s1 - s0);
            //System.out.println("***DEBUG*** indexRWIEntry.Order (2-THREADED): " + sc + " milliseconds for " + container.size() + " entries, " + (container.size() / sc) + " entries/millisecond");
        } else if (container.size() > 0) {
            // run minmax in one thread
            minmaxfinder mmf = new minmaxfinder(container, 0, container.size());
            mmf.run(); // execute without multi-threading
            if (this.min == null) this.min = mmf.entryMin.clone(); else this.min.min(mmf.entryMin);
            if (this.max == null) this.max = mmf.entryMax.clone(); else this.max.max(mmf.entryMax);
            Map.Entry<String, Integer> entry;
            Iterator<Map.Entry<String, Integer>> di = mmf.domcount().entrySet().iterator();
            while (di.hasNext()) {
            	entry = di.next();
            	this.doms.addScore(entry.getKey(), ((Integer) entry.getValue()).intValue());
            }
            result = mmf.decodedContainer();
            //long s1= System.currentTimeMillis(), sc = Math.max(1, s1 - s0);
            //System.out.println("***DEBUG*** indexRWIEntry.Order (ONETHREAD): " + sc + " milliseconds for " + container.size() + " entries, " + (container.size() / sc) + " entries/millisecond");
        }
        if (this.doms.size() > 0) this.maxdomcount = this.doms.getMaxScore();
        return result;
    }
    
    public int authority(String urlHash) {
    	return (doms.getScore(urlHash.substring(6)) << 8) / (1 + this.maxdomcount);
    }

    public long cardinal(indexRWIVarEntry t) {
        //return Long.MAX_VALUE - preRanking(ranking, iEntry, this.entryMin, this.entryMax, this.searchWords);
        // the normalizedEntry must be a normalized indexEntry
        kelondroBitfield flags = t.flags();
        long tf = ((max.termFrequency() == min.termFrequency()) ? 0 : (((int)(((t.termFrequency()-min.termFrequency())*256.0)/(max.termFrequency() - min.termFrequency())))) << ranking.coeff_termfrequency);
        //System.out.println("tf(" + t.urlHash + ") = " + Math.floor(1000 * t.termFrequency()) + ", min = " + Math.floor(1000 * min.termFrequency()) + ", max = " + Math.floor(1000 * max.termFrequency()) + ", tf-normed = " + tf);
        long r =
             ((256 - yacyURL.domLengthNormalized(t.urlHash())) << ranking.coeff_domlength)
           + ((256 - (plasmaSearchRankingProcess.ybr(t.urlHash()) << 4)) << ranking.coeff_ybr)
           + ((max.urlcomps()      == min.urlcomps()   )   ? 0 : (256 - (((t.urlcomps()     - min.urlcomps()     ) << 8) / (max.urlcomps()     - min.urlcomps())     )) << ranking.coeff_urlcomps)
           + ((max.urllength()     == min.urllength()  )   ? 0 : (256 - (((t.urllength()    - min.urllength()    ) << 8) / (max.urllength()    - min.urllength())    )) << ranking.coeff_urllength)
           + ((max.posintext()     == min.posintext()  )   ? 0 : (256 - (((t.posintext()    - min.posintext()    ) << 8) / (max.posintext()    - min.posintext())    )) << ranking.coeff_posintext)
           + ((max.posofphrase()   == min.posofphrase())   ? 0 : (256 - (((t.posofphrase()  - min.posofphrase()  ) << 8) / (max.posofphrase()  - min.posofphrase())  )) << ranking.coeff_posofphrase)
           + ((max.posinphrase()   == min.posinphrase())   ? 0 : (256 - (((t.posinphrase()  - min.posinphrase()  ) << 8) / (max.posinphrase()  - min.posinphrase())  )) << ranking.coeff_posinphrase)
           + ((max.worddistance()  == min.worddistance())  ? 0 : (256 - (((t.worddistance() - min.worddistance() ) << 8) / (max.worddistance() - min.worddistance()) )) << ranking.coeff_worddistance)
           + ((max.virtualAge()    == min.virtualAge())    ? 0 :        (((t.virtualAge()   - min.virtualAge()    ) << 8) / (max.virtualAge()   - min.virtualAge())    ) << ranking.coeff_date)
           + ((max.wordsintitle()  == min.wordsintitle())  ? 0 : (((t.wordsintitle() - min.wordsintitle()  ) << 8) / (max.wordsintitle() - min.wordsintitle())  ) << ranking.coeff_wordsintitle)
           + ((max.wordsintext()   == min.wordsintext())   ? 0 : (((t.wordsintext()  - min.wordsintext()   ) << 8) / (max.wordsintext()  - min.wordsintext())   ) << ranking.coeff_wordsintext)
           + ((max.phrasesintext() == min.phrasesintext()) ? 0 : (((t.phrasesintext()- min.phrasesintext() ) << 8) / (max.phrasesintext()- min.phrasesintext()) ) << ranking.coeff_phrasesintext)
           + ((max.llocal()        == min.llocal())        ? 0 : (((t.llocal()       - min.llocal()        ) << 8) / (max.llocal()       - min.llocal())        ) << ranking.coeff_llocal)
           + ((max.lother()        == min.lother())        ? 0 : (((t.lother()       - min.lother()        ) << 8) / (max.lother()       - min.lother())        ) << ranking.coeff_lother)
           + ((max.hitcount()      == min.hitcount())      ? 0 : (((t.hitcount()     - min.hitcount()      ) << 8) / (max.hitcount()     - min.hitcount())      ) << ranking.coeff_hitcount)
           + tf
           + (authority(t.urlHash()) << ranking.coeff_authority)
           + (((flags.get(indexRWIEntry.flag_app_dc_identifier))  ? 255 << ranking.coeff_appurl             : 0))
           + (((flags.get(indexRWIEntry.flag_app_dc_title))       ? 255 << ranking.coeff_app_dc_title       : 0))
           + (((flags.get(indexRWIEntry.flag_app_dc_creator))     ? 255 << ranking.coeff_app_dc_creator     : 0))
           + (((flags.get(indexRWIEntry.flag_app_dc_subject))     ? 255 << ranking.coeff_app_dc_subject     : 0))
           + (((flags.get(indexRWIEntry.flag_app_dc_description)) ? 255 << ranking.coeff_app_dc_description : 0))
           + (((flags.get(indexRWIEntry.flag_app_emphasized))     ? 255 << ranking.coeff_appemph            : 0))
           + (((flags.get(plasmaCondenser.flag_cat_indexof))      ? 255 << ranking.coeff_catindexof         : 0))
           + (((flags.get(plasmaCondenser.flag_cat_hasimage))     ? 255 << ranking.coeff_cathasimage        : 0))
           + (((flags.get(plasmaCondenser.flag_cat_hasaudio))     ? 255 << ranking.coeff_cathasaudio        : 0))
           + (((flags.get(plasmaCondenser.flag_cat_hasvideo))     ? 255 << ranking.coeff_cathasvideo        : 0))
           + (((flags.get(plasmaCondenser.flag_cat_hasapp))       ? 255 << ranking.coeff_cathasapp          : 0))
           + (((yacyURL.probablyRootURL(t.urlHash()))             ?  15 << ranking.coeff_urllength          : 0));
        //if (searchWords != null) r += (yacyURL.probablyWordURL(t.urlHash(), searchWords) != null) ? 256 << ranking.coeff_appurl : 0;

        return Long.MAX_VALUE - r; // returns a reversed number: the lower the number the better the ranking. This is used for simple sorting with a TreeMap
    }

    public static class minmaxfinder extends Thread {

        indexRWIVarEntry entryMin;
        indexRWIVarEntry entryMax;
        private indexContainer container;
        private int start, end;
        private HashMap<String, Integer> doms;
        private Integer int1;
        ArrayList<indexRWIVarEntry> decodedEntries;
        
        public minmaxfinder(indexContainer container, int start /*including*/, int end /*excluding*/) {
            this.container = container;
            this.start = start;
            this.end = end;
            this.doms = new HashMap<String, Integer>();
            this.int1 = new Integer(1);
            this.decodedEntries = new ArrayList<indexRWIVarEntry>();
        }
        
        public void run() {
            // find min/max to obtain limits for normalization
            this.entryMin = null;
            this.entryMax = null;
            indexRWIVarEntry iEntry;
            int p = this.start;
            String dom;
            Integer count;
            while (p < this.end) {
                iEntry = new indexRWIVarEntry(new indexRWIRowEntry(container.get(p++)));
                this.decodedEntries.add(iEntry);
                // find min/max
                if (this.entryMin == null) this.entryMin = iEntry.clone(); else this.entryMin.min(iEntry);
                if (this.entryMax == null) this.entryMax = iEntry.clone(); else this.entryMax.max(iEntry);
                // update domcount
                dom = iEntry.urlHash().substring(6);
                count = (Integer) doms.get(dom);
                if (count == null) {
                	doms.put(dom, int1);
                } else {
                	doms.put(dom, new Integer(count.intValue() + 1));
                }
            }
        }
        
        public ArrayList<indexRWIVarEntry> decodedContainer() {
            return this.decodedEntries;
        }
        
        public HashMap<String, Integer> domcount() {
        	return this.doms;
        }
    }
    
}
