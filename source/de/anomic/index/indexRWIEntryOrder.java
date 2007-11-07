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

import de.anomic.kelondro.kelondroAbstractOrder;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSearchRankingProcess;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.yacy.yacyURL;

public class indexRWIEntryOrder extends kelondroAbstractOrder implements kelondroOrder {
    private indexRWIVarEntry min, max;
    private plasmaSearchRankingProfile ranking;
    
    private static final int processors = Runtime.getRuntime().availableProcessors(); // for multiprocessor support, used during normalization
    
    public indexRWIEntryOrder(plasmaSearchRankingProfile profile) {
        this.min = null;
        this.max = null;
        this.ranking = profile;
    }
    
    public void extend(indexContainer container) {
        assert (container != null);
    
        //long s0 = System.currentTimeMillis();
        if ((processors > 1) && (container.size() > 10000)) {
            // run minmax with two threads
            int middle = container.size() / 2;
            minmaxfinder mmf0 = new minmaxfinder(container, 0, middle);
            mmf0.start(); // fork here
            minmaxfinder mmf1 = new minmaxfinder(container, middle, container.size());
            mmf1.run(); // execute other fork in this thread
            if (this.min == null) this.min = mmf1.entryMin; else indexRWIVarEntry.min(this.min, mmf1.entryMin);
            if (this.max == null) this.max = mmf1.entryMax; else indexRWIVarEntry.max(this.max, mmf1.entryMax);
            try {mmf0.join();} catch (InterruptedException e) {} // wait for fork thread to finish
            if (this.min == null) this.min = mmf0.entryMin; else indexRWIVarEntry.min(this.min, mmf0.entryMin);
            if (this.max == null) this.max = mmf0.entryMax; else indexRWIVarEntry.max(this.max, mmf0.entryMax);
            //long s1= System.currentTimeMillis(), sc = Math.max(1, s1 - s0);
            //System.out.println("***DEBUG*** indexRWIEntry.Order (2-THREADED): " + sc + " milliseconds for " + container.size() + " entries, " + (container.size() / sc) + " entries/millisecond");
        } else {
            // run minmax in one thread
            minmaxfinder mmf = new minmaxfinder(container, 0, container.size());
            mmf.run(); // execute without multi-threading
            if (this.min == null) this.min = mmf.entryMin; else indexRWIVarEntry.min(this.min, mmf.entryMin);
            if (this.max == null) this.max = mmf.entryMax; else indexRWIVarEntry.max(this.max, mmf.entryMax);
            //long s1= System.currentTimeMillis(), sc = Math.max(1, s1 - s0);
            //System.out.println("***DEBUG*** indexRWIEntry.Order (ONETHREAD): " + sc + " milliseconds for " + container.size() + " entries, " + (container.size() / sc) + " entries/millisecond");
        }
    }
    
    public Object clone() {
        return null;
    }
    
    public long cardinal(byte[] key) {
        return cardinal(new indexRWIRowEntry(key));
    }

    public long cardinal(indexRWIEntry t) {
        //return Long.MAX_VALUE - preRanking(ranking, iEntry, this.entryMin, this.entryMax, this.searchWords);
        // the normalizedEntry must be a normalized indexEntry
        kelondroBitfield flags = t.flags();
        long r = ((255 - yacyURL.domLengthNormalized(t.urlHash())) << ranking.coeff_domlength)
           + ((255 - (plasmaSearchRankingProcess.ybr(t.urlHash()) << 4                                                )) << ranking.coeff_ybr)
           + ((255 - (((t.virtualAge()   - min.virtualAge()   ) << 8) / (1 + max.virtualAge()   - min.virtualAge())   )) << ranking.coeff_date)
           + (       (((t.wordsintitle() - min.wordsintitle() ) << 8) / (1 + max.wordsintitle() - min.wordsintitle())  ) << ranking.coeff_wordsintitle)
           + (       (((t.wordsintext()  - min.wordsintext()  ) << 8) / (1 + max.wordsintext()  - min.wordsintext())   ) << ranking.coeff_wordsintext)
           + (       (((t.phrasesintext()- min.phrasesintext()) << 8) / (1 + max.phrasesintext()- min.phrasesintext()) ) << ranking.coeff_phrasesintext)
           + (       (((t.llocal()       - min.llocal()       ) << 8) / (1 + max.llocal()       - min.llocal())        ) << ranking.coeff_llocal)
           + (       (((t.lother()       - min.lother()       ) << 8) / (1 + max.lother()       - min.lother())        ) << ranking.coeff_lother)
           + (       (((t.hitcount()     - min.hitcount()     ) << 8) / (1 + max.hitcount()     - min.hitcount())      ) << ranking.coeff_hitcount)
           + ((255 - (((t.urllength()    - min.urllength()    ) << 8) / (1 + max.urllength()    - min.urllength())    )) << ranking.coeff_urllength)
           + ((255 - (((t.urlcomps()     - min.urlcomps()     ) << 8) / (1 + max.urlcomps()     - min.urlcomps())     )) << ranking.coeff_urlcomps)
           + ((255 - (((t.posintext()    - min.posintext()    ) << 8) / (1 + max.posintext()    - min.posintext())    )) << ranking.coeff_posintext)
           + ((255 - (((t.posofphrase()  - min.posofphrase()  ) << 8) / (1 + max.posofphrase()  - min.posofphrase())  )) << ranking.coeff_posofphrase)
           + ((255 - (((t.posinphrase()  - min.posinphrase()  ) << 8) / (1 + max.posinphrase()  - min.posinphrase())  )) << ranking.coeff_posinphrase)
           + ((255 - (((t.worddistance() - min.worddistance() ) << 8) / (1 + max.worddistance() - min.worddistance()) )) << ranking.coeff_worddistance)
           + (((flags.get(indexRWIEntry.flag_app_url))        ? 255 << ranking.coeff_appurl      : 0))
           + (((flags.get(indexRWIEntry.flag_app_descr))      ? 255 << ranking.coeff_appdescr    : 0))
           + (((flags.get(indexRWIEntry.flag_app_author))     ? 255 << ranking.coeff_appauthor   : 0))
           + (((flags.get(indexRWIEntry.flag_app_tags))       ? 255 << ranking.coeff_apptags     : 0))
           + (((flags.get(indexRWIEntry.flag_app_reference))  ? 255 << ranking.coeff_appref      : 0))
           + (((flags.get(indexRWIEntry.flag_app_emphasized)) ? 255 << ranking.coeff_appemph     : 0))
           + (((flags.get(plasmaCondenser.flag_cat_indexof))  ? 255 << ranking.coeff_catindexof  : 0))
           + (((flags.get(plasmaCondenser.flag_cat_hasimage)) ? 255 << ranking.coeff_cathasimage : 0))
           + (((flags.get(plasmaCondenser.flag_cat_hasaudio)) ? 255 << ranking.coeff_cathasaudio : 0))
           + (((flags.get(plasmaCondenser.flag_cat_hasvideo)) ? 255 << ranking.coeff_cathasvideo : 0))
           + (((flags.get(plasmaCondenser.flag_cat_hasapp))   ? 255 << ranking.coeff_cathasapp   : 0))
           + (((yacyURL.probablyRootURL(t.urlHash()))         ?  15 << ranking.coeff_urllength   : 0));
        //if (searchWords != null) r += (yacyURL.probablyWordURL(t.urlHash(), searchWords) != null) ? 256 << ranking.coeff_appurl : 0;

        return Long.MAX_VALUE - r; // returns a reversed number: the lower the number the better the ranking. This is used for simple sorting with a TreeMap
    }

    public int compare(Object a, Object b) {
        if ((a instanceof indexRWIEntry) && (b instanceof indexRWIEntry)) {
            return compare((indexRWIEntry) a, (indexRWIEntry) b);
        } else {
            return super.compare(a, b);
        }
    }
    
    public int compare(indexRWIEntry a, indexRWIEntry b) {
        return 0;
    }
    
    public int compare(byte[] a, byte[] b) {
        return compare(new indexRWIRowEntry(a), new indexRWIRowEntry(b));
    }

    public int compare(byte[] a, int aoffset, int alength, byte[] b, int boffset, int blength) {
        return compare(new indexRWIRowEntry(a, aoffset, false), new indexRWIRowEntry(b, boffset, false));
    }

    public String signature() {
        return "rx";
    }

    public boolean wellformed(byte[] a) {
        return true;
    }

    public boolean wellformed(byte[] a, int astart, int alength) {
        return true;
    }

    public static class minmaxfinder extends Thread {

        private indexRWIVarEntry entryMin, entryMax;
        private indexContainer container;
        private int start, end;
        
        public minmaxfinder(indexContainer container, int start /*including*/, int end /*excluding*/) {
            this.container = container;
            this.start = start;
            this.end = end;
        }
        
        public void run() {
            // find min/max to obtain limits for normalization
            this.entryMin = null;
            this.entryMax = null;
            indexRWIRowEntry iEntry;
            int p = this.start;
            while (p < this.end) {
                iEntry = new indexRWIRowEntry(container.get(p++));
                if (this.entryMin == null) this.entryMin = new indexRWIVarEntry(iEntry); else indexRWIVarEntry.min(this.entryMin, iEntry);
                if (this.entryMax == null) this.entryMax = new indexRWIVarEntry(iEntry); else indexRWIVarEntry.max(this.entryMax, iEntry);
            }
        }
    }
    
}
