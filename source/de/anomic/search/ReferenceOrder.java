// ReferenceOrder.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.11.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package de.anomic.search;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.storage.ReversibleScoreMap;
import net.yacy.cora.storage.ClusteredScoreMap;
import net.yacy.document.Condenser;
import net.yacy.document.LargeNumberCache;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.ByteBuffer;


public class ReferenceOrder {

    private static int cores = Runtime.getRuntime().availableProcessors();
    
    private       int maxdomcount;
    private       WordReferenceVars min, max;
    private final ReversibleScoreMap<String> doms; // collected for "authority" heuristic 
    private final RankingProfile ranking;
    private final byte[] language;
    
    public ReferenceOrder(final RankingProfile profile, byte[] language) {
        this.min = null;
        this.max = null;
        this.ranking = profile;
        this.doms = new ClusteredScoreMap<String>();
        this.maxdomcount = 0;
        this.language = language;
    }
    
    public BlockingQueue<WordReferenceVars> normalizeWith(final ReferenceContainer<WordReference> container) {
        LinkedBlockingQueue<WordReferenceVars> out = new LinkedBlockingQueue<WordReferenceVars>();
        int threads = cores + 1;
        if (container.size() < 20) threads = 2;
        Thread distributor = new NormalizeDistributor(container, out, threads);
        distributor.start();
        
        // return the resulting queue while the processing queues are still working
        return out;
    }
    
    private final class NormalizeDistributor extends Thread {

        ReferenceContainer<WordReference> container;
        LinkedBlockingQueue<WordReferenceVars> out;
        private int threads;
        
        public NormalizeDistributor(ReferenceContainer<WordReference> container, LinkedBlockingQueue<WordReferenceVars> out, int threads) {
            this.container = container;
            this.out = out;
            this.threads = threads;
        }
        
        @Override
        public void run() {
            // transform the reference container into a stream of parsed entries
            BlockingQueue<WordReferenceVars> vars = WordReferenceVars.transform(container);
            
            // start the transformation threads
            Semaphore termination = new Semaphore(this.threads);
            NormalizeWorker[] worker = new NormalizeWorker[this.threads];
            for (int i = 0; i < this.threads; i++) {
                worker[i] = new NormalizeWorker(out, termination);
                worker[i].start();
            }
            
            // fill the queue
            WordReferenceVars iEntry;
            int p = 0;
            try {
                while ((iEntry = vars.take()) != WordReferenceVars.poison) {
                    worker[p % this.threads].add(iEntry);
                    p++;
                }
            } catch (InterruptedException e) {
            }
            
            // insert poison to stop the queues
            for (int i = 0; i < this.threads; i++) worker[i].add(WordReferenceVars.poison);
        }
    }

    /**
     * normalize ranking: find minimum and maximum of separate ranking criteria
     */
    private class NormalizeWorker extends Thread {
        
        private final BlockingQueue<WordReferenceVars> out;
        private final Semaphore termination;
        private final BlockingQueue<WordReferenceVars> decodedEntries;
        
        public NormalizeWorker(final BlockingQueue<WordReferenceVars> out, Semaphore termination) {
            this.out = out;
            this.termination = termination;
            this.decodedEntries = new LinkedBlockingQueue<WordReferenceVars>();
        }
        
        public void add(WordReferenceVars entry) {
            try {
                decodedEntries.put(entry);
            } catch (InterruptedException e) {
            }
        }
        
        public void run() {
            try {
                WordReferenceVars iEntry;
                Map<String, Integer> doms0 = new HashMap<String, Integer>();
                String dom;
                Integer count;
                final Integer int1 = 1;
                while ((iEntry = decodedEntries.take()) != WordReferenceVars.poison) {
                    // find min/max
                    if (min == null) min = iEntry.clone(); else min.min(iEntry);
                    if (max == null) max = iEntry.clone(); else max.max(iEntry);
                    out.put(iEntry); // must be after the min/max check to prevent that min/max is null in cardinal()
                    // update domcount
                    dom = UTF8.String(iEntry.metadataHash(), 6, 6);
                    count = doms0.get(dom);
                    if (count == null) {
                        doms0.put(dom, int1);
                    } else {
                        doms0.put(dom, LargeNumberCache.valueOf(count.intValue() + 1));
                    }
                }

                // update domain score
                Map.Entry<String, Integer> entry;
                final Iterator<Map.Entry<String, Integer>> di = doms0.entrySet().iterator();
                while (di.hasNext()) {
                    entry = di.next();
                    doms.inc(entry.getKey(), (entry.getValue()).intValue());
                }
                if (!doms.isEmpty()) maxdomcount = doms.getMaxScore();
            } catch (InterruptedException e) {
                Log.logException(e);
            } catch (Exception e) {
                Log.logException(e);
            } finally {
                // insert poison to signal the termination to next queue
                try {
                    this.termination.acquire();
                    if (this.termination.availablePermits() == 0) this.out.put(WordReferenceVars.poison);
                } catch (InterruptedException e) {}
            }
        }
    }
    
    public int authority(final byte[] urlHash) {
        return (doms.get(UTF8.String(urlHash, 6, 6)) << 8) / (1 + this.maxdomcount);
    }

    /**
     * return the ranking of a given word entry
     * @param t
     * @return a ranking: the higher the number, the better is the ranking
     */
    public long cardinal(final WordReferenceVars t) {
        //return Long.MAX_VALUE - preRanking(ranking, iEntry, this.entryMin, this.entryMax, this.searchWords);
        // the normalizedEntry must be a normalized indexEntry
        final Bitfield flags = t.flags();
        assert min != null;
        assert max != null;
        assert t != null;
        assert ranking != null;
        final long tf = ((max.termFrequency() == min.termFrequency()) ? 0 : (((int)(((t.termFrequency()-min.termFrequency())*256.0)/(max.termFrequency() - min.termFrequency())))) << ranking.coeff_termfrequency);
        //System.out.println("tf(" + t.urlHash + ") = " + Math.floor(1000 * t.termFrequency()) + ", min = " + Math.floor(1000 * min.termFrequency()) + ", max = " + Math.floor(1000 * max.termFrequency()) + ", tf-normed = " + tf);
        int maxmaxpos = max.maxposition();
        int minminpos = min.minposition();
        final long r =
             ((256 - DigestURI.domLengthNormalized(t.metadataHash())) << ranking.coeff_domlength)
           + ((ranking.coeff_ybr > 12) ? ((256 - (RankingProcess.ybr(t.metadataHash()) << 4)) << ranking.coeff_ybr) : 0)
           + ((max.urlcomps()      == min.urlcomps()   )   ? 0 : (256 - (((t.urlcomps()     - min.urlcomps()     ) << 8) / (max.urlcomps()     - min.urlcomps())     )) << ranking.coeff_urlcomps)
           + ((max.urllength()     == min.urllength()  )   ? 0 : (256 - (((t.urllength()    - min.urllength()    ) << 8) / (max.urllength()    - min.urllength())    )) << ranking.coeff_urllength)
           + ((maxmaxpos           == minminpos        )   ? 0 : (256 - (((t.minposition()  - minminpos          ) << 8) / (maxmaxpos          - minminpos)          )) << ranking.coeff_posintext)
           + ((max.posofphrase()   == min.posofphrase())   ? 0 : (256 - (((t.posofphrase()  - min.posofphrase()  ) << 8) / (max.posofphrase()  - min.posofphrase())  )) << ranking.coeff_posofphrase)
           + ((max.posinphrase()   == min.posinphrase())   ? 0 : (256 - (((t.posinphrase()  - min.posinphrase()  ) << 8) / (max.posinphrase()  - min.posinphrase())  )) << ranking.coeff_posinphrase)
           + ((max.distance()      == min.distance()   )   ? 0 : (256 - (((t.distance()     - min.distance()     ) << 8) / (max.distance()     - min.distance())     )) << ranking.coeff_worddistance)
           + ((max.virtualAge()    == min.virtualAge())    ? 0 :        (((t.virtualAge()   - min.virtualAge()   ) << 8) / (max.virtualAge()   - min.virtualAge())    ) << ranking.coeff_date)
           + ((max.wordsintitle()  == min.wordsintitle())  ? 0 : (((t.wordsintitle() - min.wordsintitle()  ) << 8) / (max.wordsintitle() - min.wordsintitle())  ) << ranking.coeff_wordsintitle)
           + ((max.wordsintext()   == min.wordsintext())   ? 0 : (((t.wordsintext()  - min.wordsintext()   ) << 8) / (max.wordsintext()  - min.wordsintext())   ) << ranking.coeff_wordsintext)
           + ((max.phrasesintext() == min.phrasesintext()) ? 0 : (((t.phrasesintext()- min.phrasesintext() ) << 8) / (max.phrasesintext()- min.phrasesintext()) ) << ranking.coeff_phrasesintext)
           + ((max.llocal()        == min.llocal())        ? 0 : (((t.llocal()       - min.llocal()        ) << 8) / (max.llocal()       - min.llocal())        ) << ranking.coeff_llocal)
           + ((max.lother()        == min.lother())        ? 0 : (((t.lother()       - min.lother()        ) << 8) / (max.lother()       - min.lother())        ) << ranking.coeff_lother)
           + ((max.hitcount()      == min.hitcount())      ? 0 : (((t.hitcount()     - min.hitcount()      ) << 8) / (max.hitcount()     - min.hitcount())      ) << ranking.coeff_hitcount)
           + tf
           + ((ranking.coeff_authority > 12) ? (authority(t.metadataHash()) << ranking.coeff_authority) : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_identifier))  ? 255 << ranking.coeff_appurl             : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_title))       ? 255 << ranking.coeff_app_dc_title       : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_creator))     ? 255 << ranking.coeff_app_dc_creator     : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_subject))     ? 255 << ranking.coeff_app_dc_subject     : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_description)) ? 255 << ranking.coeff_app_dc_description : 0)
           + ((flags.get(WordReferenceRow.flag_app_emphasized))     ? 255 << ranking.coeff_appemph            : 0)
           + ((flags.get(Condenser.flag_cat_indexof))      ? 255 << ranking.coeff_catindexof         : 0)
           + ((flags.get(Condenser.flag_cat_hasimage))     ? 255 << ranking.coeff_cathasimage        : 0)
           + ((flags.get(Condenser.flag_cat_hasaudio))     ? 255 << ranking.coeff_cathasaudio        : 0)
           + ((flags.get(Condenser.flag_cat_hasvideo))     ? 255 << ranking.coeff_cathasvideo        : 0)
           + ((flags.get(Condenser.flag_cat_hasapp))       ? 255 << ranking.coeff_cathasapp          : 0)
           + ((ByteBuffer.equals(t.language, this.language)) ? 255 << ranking.coeff_language           : 0)
           + ((DigestURI.probablyRootURL(t.metadataHash())) ?  15 << ranking.coeff_urllength          : 0);

        //if (searchWords != null) r += (yacyURL.probablyWordURL(t.urlHash(), searchWords) != null) ? 256 << ranking.coeff_appurl : 0;

        return r; // the higher the number the better the ranking.
    }

}
