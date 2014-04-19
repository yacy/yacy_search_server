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

package net.yacy.search.ranking;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Condenser;
import net.yacy.document.LargeNumberCache;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.Bitfield;


public class ReferenceOrder {

    private static int cores = Runtime.getRuntime().availableProcessors();

    private       int maxdomcount;
    private       WordReferenceVars min, max;
    private final ConcurrentScoreMap<String> doms; // collected for "authority" heuristic
    private final RankingProfile ranking;
    private final String language;

    public ReferenceOrder(final RankingProfile profile, final String language) {
        this.min = null;
        this.max = null;
        this.ranking = profile;
        this.doms = new ConcurrentScoreMap<String>();
        this.maxdomcount = 0;
        this.language = language;
    }

    public BlockingQueue<WordReferenceVars> normalizeWith(final ReferenceContainer<WordReference> container, long maxtime, final boolean local) {
        final LinkedBlockingQueue<WordReferenceVars> out = new LinkedBlockingQueue<WordReferenceVars>();
        int threads = cores;
        if (container.size() < 100) threads = 2;
        final Thread distributor = new NormalizeDistributor(container, out, threads, maxtime, local);
        distributor.start();

        // return the resulting queue while the processing queues are still working
        return out;
    }

    private final class NormalizeDistributor extends Thread {

        ReferenceContainer<WordReference> container;
        LinkedBlockingQueue<WordReferenceVars> out;
        private final int threads;
        private final long maxtime;
        private final boolean local;
        
        public NormalizeDistributor(final ReferenceContainer<WordReference> container, final LinkedBlockingQueue<WordReferenceVars> out, final int threads, final long maxtime, final boolean local) {
            this.container = container;
            this.out = out;
            this.threads = threads;
            this.maxtime = maxtime;
            this.local = local;
        }

        @Override
        public void run() {
            // transform the reference container into a stream of parsed entries
            final BlockingQueue<WordReferenceVars> vars = WordReferenceVars.transform(this.container, this.maxtime, this.local);

            // start the transformation threads
            final Semaphore termination = new Semaphore(this.threads);
            final NormalizeWorker[] worker = new NormalizeWorker[this.threads];
            for (int i = 0; i < this.threads; i++) {
                worker[i] = new NormalizeWorker(this.out, termination, this.maxtime);
                worker[i].start();
            }

            // fill the queue
            WordReferenceVars iEntry;
            int p = 0;
            long timeout = this.maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + this.maxtime;
            try {
                while ((iEntry = vars.take()) != WordReferenceVars.poison) {
                    worker[p % this.threads].add(iEntry);
                    p++;
                    if (System.currentTimeMillis() > timeout) {
                        ConcurrentLog.warn("NormalizeDistributor", "adding of decoded rows to workers ended with timeout = " + this.maxtime);
                    }
                }
            } catch (final InterruptedException e) {
            }

            // insert poison to stop the queues
            for (int i = 0; i < this.threads; i++) worker[i].add(WordReferenceVars.poison);

            // wait for termination but not too long to make it possible that this
            // is called from outside with a join to get some normalization results
            // before going on
            for (int i = 0; i < this.threads; i++) try {worker[i].join(100);} catch (final InterruptedException e) {}
        }
    }

    /**
     * normalize ranking: find minimum and maximum of separate ranking criteria
     */
    private class NormalizeWorker extends Thread {

        private final BlockingQueue<WordReferenceVars> out;
        private final Semaphore termination;
        private final BlockingQueue<WordReferenceVars> decodedEntries;
        private final long maxtime;

        public NormalizeWorker(final BlockingQueue<WordReferenceVars> out, final Semaphore termination, long maxtime) {
            this.out = out;
            this.termination = termination;
            this.decodedEntries = new LinkedBlockingQueue<WordReferenceVars>();
            this.maxtime = maxtime;
        }

        public void add(final WordReferenceVars entry) {
            try {
                this.decodedEntries.put(entry);
            } catch (final InterruptedException e) {
            }
        }

        @Override
        public void run() {
            try {
                WordReferenceVars iEntry;
                final Map<String, Integer> doms0 = new HashMap<String, Integer>();
                String dom;
                Integer count;
                final Integer int1 = 1;
                long timeout = this.maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + this.maxtime;
                while ((iEntry = this.decodedEntries.take()) != WordReferenceVars.poison) {
                    // find min/max
                    if (ReferenceOrder.this.min == null) ReferenceOrder.this.min = iEntry.clone(); else ReferenceOrder.this.min.min(iEntry);
                    if (ReferenceOrder.this.max == null) ReferenceOrder.this.max = iEntry.clone(); else ReferenceOrder.this.max.max(iEntry);
                    this.out.put(iEntry); // must be after the min/max check to prevent that min/max is null in cardinal()
                    // update domcount
                    dom = iEntry.hosthash();
                    count = doms0.get(dom);
                    if (count == null) {
                        doms0.put(dom, int1);
                    } else {
                        doms0.put(dom, LargeNumberCache.valueOf(count.intValue() + 1));
                    }

                    if (System.currentTimeMillis() > timeout) {
                        ConcurrentLog.warn("NormalizeWorker", "normlization of decoded rows ended with timeout = " + this.maxtime);
                        break;
                    }
                }

                // update domain score
                Map.Entry<String, Integer> entry;
                final Iterator<Map.Entry<String, Integer>> di = doms0.entrySet().iterator();
                while (di.hasNext()) {
                    entry = di.next();
                    ReferenceOrder.this.doms.inc(entry.getKey(), (entry.getValue()).intValue());
                }
                if (!ReferenceOrder.this.doms.isEmpty()) ReferenceOrder.this.maxdomcount = ReferenceOrder.this.doms.getMaxScore();
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
            } finally {
                // insert poison to signal the termination to next queue
                try {
                    this.termination.acquire();
                    if (this.termination.availablePermits() == 0) this.out.put(WordReferenceVars.poison);
                } catch (final InterruptedException e) {}
            }
        }
    }

    public int authority(final String hostHash) {
        assert hostHash.length() == 6;
        return (this.doms.get(hostHash) << 8) / (1 + this.maxdomcount);
    }

    /**
     * return the ranking of a given word entry
     * @param t
     * @return a ranking: the higher the number, the better is the ranking
     */
    public long cardinal(final WordReference t) {
        //return Long.MAX_VALUE - preRanking(ranking, iEntry, this.entryMin, this.entryMax, this.searchWords);
        // the normalizedEntry must be a normalized indexEntry
        final Bitfield flags = t.flags();
        assert this.min != null;
        assert this.max != null;
        assert t != null;
        assert this.ranking != null;
        final long tf = ((this.max.termFrequency() == this.min.termFrequency()) ? 0 : (((int)(((t.termFrequency()-this.min.termFrequency())*256.0)/(this.max.termFrequency() - this.min.termFrequency())))) << this.ranking.coeff_termfrequency);
        //System.out.println("tf(" + t.urlHash + ") = " + Math.floor(1000 * t.termFrequency()) + ", min = " + Math.floor(1000 * min.termFrequency()) + ", max = " + Math.floor(1000 * max.termFrequency()) + ", tf-normed = " + tf);
        final int maxmaxpos = this.max.maxposition();
        final int minminpos = this.min.minposition();
        final long r =
             ((256 - DigestURL.domLengthNormalized(t.urlhash())) << this.ranking.coeff_domlength)
           + ((this.max.urlcomps()      == this.min.urlcomps()   )   ? 0 : (256 - (((t.urlcomps()     - this.min.urlcomps()     ) << 8) / (this.max.urlcomps()     - this.min.urlcomps())     )) << this.ranking.coeff_urlcomps)
           + ((this.max.urllength()     == this.min.urllength()  )   ? 0 : (256 - (((t.urllength()    - this.min.urllength()    ) << 8) / (this.max.urllength()    - this.min.urllength())    )) << this.ranking.coeff_urllength)
           + ((maxmaxpos == minminpos)                               ? 0 : (256 - (((t.minposition() - minminpos) << 8) / (maxmaxpos - minminpos))) << this.ranking.coeff_posintext)
           + ((this.max.posofphrase()   == this.min.posofphrase())   ? 0 : (256 - (((t.posofphrase()  - this.min.posofphrase()  ) << 8) / (this.max.posofphrase()  - this.min.posofphrase())  )) << this.ranking.coeff_posofphrase)
           + ((this.max.posinphrase()   == this.min.posinphrase())   ? 0 : (256 - (((t.posinphrase()  - this.min.posinphrase()  ) << 8) / (this.max.posinphrase()  - this.min.posinphrase())  )) << this.ranking.coeff_posinphrase)
           + ((this.max.distance()      == this.min.distance()   )   ? 0 : (256 - (((t.distance()     - this.min.distance()     ) << 8) / (this.max.distance()     - this.min.distance())     )) << this.ranking.coeff_worddistance)
           + ((this.max.virtualAge()    == this.min.virtualAge())    ? 0 :        (((t.virtualAge()   - this.min.virtualAge()   ) << 8) / (this.max.virtualAge()   - this.min.virtualAge())    ) << this.ranking.coeff_date)
           + ((this.max.wordsintitle()  == this.min.wordsintitle())  ? 0 : (((t.wordsintitle() - this.min.wordsintitle()  ) << 8) / (this.max.wordsintitle() - this.min.wordsintitle())  ) << this.ranking.coeff_wordsintitle)
           + ((this.max.wordsintext()   == this.min.wordsintext())   ? 0 : (((t.wordsintext()  - this.min.wordsintext()   ) << 8) / (this.max.wordsintext()  - this.min.wordsintext())   ) << this.ranking.coeff_wordsintext)
           + ((this.max.phrasesintext() == this.min.phrasesintext()) ? 0 : (((t.phrasesintext()- this.min.phrasesintext() ) << 8) / (this.max.phrasesintext()- this.min.phrasesintext()) ) << this.ranking.coeff_phrasesintext)
           + ((this.max.llocal()        == this.min.llocal())        ? 0 : (((t.llocal()       - this.min.llocal()        ) << 8) / (this.max.llocal()       - this.min.llocal())        ) << this.ranking.coeff_llocal)
           + ((this.max.lother()        == this.min.lother())        ? 0 : (((t.lother()       - this.min.lother()        ) << 8) / (this.max.lother()       - this.min.lother())        ) << this.ranking.coeff_lother)
           + ((this.max.hitcount()      == this.min.hitcount())      ? 0 : (((t.hitcount()     - this.min.hitcount()      ) << 8) / (this.max.hitcount()     - this.min.hitcount())      ) << this.ranking.coeff_hitcount)
           + tf
           + ((this.ranking.coeff_authority > 12) ? (authority(t.hosthash()) << this.ranking.coeff_authority) : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_identifier))  ? 255 << this.ranking.coeff_appurl             : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_title))       ? 255 << this.ranking.coeff_app_dc_title       : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_creator))     ? 255 << this.ranking.coeff_app_dc_creator     : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_subject))     ? 255 << this.ranking.coeff_app_dc_subject     : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_description)) ? 255 << this.ranking.coeff_app_dc_description : 0)
           + ((flags.get(WordReferenceRow.flag_app_emphasized))     ? 255 << this.ranking.coeff_appemph            : 0)
           + ((flags.get(Condenser.flag_cat_indexof))      ? 255 << this.ranking.coeff_catindexof         : 0)
           + ((flags.get(Condenser.flag_cat_hasimage))     ? 255 << this.ranking.coeff_cathasimage        : 0)
           + ((flags.get(Condenser.flag_cat_hasaudio))     ? 255 << this.ranking.coeff_cathasaudio        : 0)
           + ((flags.get(Condenser.flag_cat_hasvideo))     ? 255 << this.ranking.coeff_cathasvideo        : 0)
           + ((flags.get(Condenser.flag_cat_hasapp))       ? 255 << this.ranking.coeff_cathasapp          : 0)
           + ((ByteBuffer.equals(t.getLanguage(), ASCII.getBytes(this.language))) ? 255 << this.ranking.coeff_language    : 0);

        //if (searchWords != null) r += (yacyURL.probablyWordURL(t.urlHash(), searchWords) != null) ? 256 << ranking.coeff_appurl : 0;

        return r; // the higher the number the better the ranking.
    }
    
    public long cardinal(final URIMetadataNode t) {
        //return Long.MAX_VALUE - preRanking(ranking, iEntry, this.entryMin, this.entryMax, this.searchWords);
        // the normalizedEntry must be a normalized indexEntry
        final Bitfield flags = t.flags();
        assert t != null;
        assert this.ranking != null;
        final long r =
             ((256 - DigestURL.domLengthNormalized(t.hash())) << this.ranking.coeff_domlength)
           + ((256 - (t.urllength() << 8)) << this.ranking.coeff_urllength)
           + (t.virtualAge()  << this.ranking.coeff_date)
           + (t.wordsintitle()<< this.ranking.coeff_wordsintitle)
           + (t.wordCount()   << this.ranking.coeff_wordsintext)
           + (t.llocal()      << this.ranking.coeff_llocal)
           + (t.lother()      << this.ranking.coeff_lother)
           + ((this.ranking.coeff_authority > 12) ? (authority(t.hosthash()) << this.ranking.coeff_authority) : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_identifier))  ? 255 << this.ranking.coeff_appurl             : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_title))       ? 255 << this.ranking.coeff_app_dc_title       : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_creator))     ? 255 << this.ranking.coeff_app_dc_creator     : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_subject))     ? 255 << this.ranking.coeff_app_dc_subject     : 0)
           + ((flags.get(WordReferenceRow.flag_app_dc_description)) ? 255 << this.ranking.coeff_app_dc_description : 0)
           + ((flags.get(WordReferenceRow.flag_app_emphasized))     ? 255 << this.ranking.coeff_appemph            : 0)
           + ((flags.get(Condenser.flag_cat_indexof))      ? 255 << this.ranking.coeff_catindexof         : 0)
           + ((flags.get(Condenser.flag_cat_hasimage))     ? 255 << this.ranking.coeff_cathasimage        : 0)
           + ((flags.get(Condenser.flag_cat_hasaudio))     ? 255 << this.ranking.coeff_cathasaudio        : 0)
           + ((flags.get(Condenser.flag_cat_hasvideo))     ? 255 << this.ranking.coeff_cathasvideo        : 0)
           + ((flags.get(Condenser.flag_cat_hasapp))       ? 255 << this.ranking.coeff_cathasapp          : 0)
           + ((this.language.equals(t.language())) ? 255 << this.ranking.coeff_language    : 0);
        return r; // the higher the number the better the ranking.
    }

}
