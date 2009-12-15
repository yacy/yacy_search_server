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

import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.ScoreCluster;


public class ReferenceOrder {
    
    protected int maxdomcount;
    protected WordReferenceVars min, max;
    protected final ScoreCluster<String> doms; // collected for "authority" heuristic 
    private   final RankingProfile ranking;
    private   String language;
    
    public ReferenceOrder(final RankingProfile profile, String language) {
        this.min = null;
        this.max = null;
        this.ranking = profile;
        this.doms = new ScoreCluster<String>();
        this.maxdomcount = 0;
        this.language = language;
    }
    
    public class Normalizer extends Thread {
        
        private ReferenceContainer<WordReference> container;
        private BlockingQueue<WordReferenceVars> decodedEntries;
        
        public Normalizer(final ReferenceContainer<WordReference> container) {
            // normalize ranking: find minimum and maximum of separate ranking criteria
            assert (container != null);
            this.container = container;
            this.decodedEntries = new LinkedBlockingQueue<WordReferenceVars>();
        }
        
        public void run() {
            BlockingQueue<WordReferenceVars> vars = WordReferenceVars.transform(container);
            
            HashMap<String, Integer> doms0 = new HashMap<String, Integer>();
            Integer int1 = 1;
            
            WordReferenceVars iEntry;
            String dom;
            Integer count;
            try {
                // calculate min and max for normalization
                while ((iEntry = vars.take()) != WordReferenceVars.poison) {
                    decodedEntries.put(iEntry);
                    // find min/max
                    if (min == null) min = iEntry.clone(); else min.min(iEntry);
                    if (max == null) max = iEntry.clone(); else max.max(iEntry);
                    // update domcount
                    dom = iEntry.metadataHash().substring(6);
                    count = doms0.get(dom);
                    if (count == null) {
                        doms0.put(dom, int1);
                    } else {
                        doms0.put(dom, Integer.valueOf(count.intValue() + 1));
                    }
                }

                // update domain score
                Map.Entry<String, Integer> entry;
                final Iterator<Map.Entry<String, Integer>> di = doms0.entrySet().iterator();
                while (di.hasNext()) {
                    entry = di.next();
                    doms.addScore(entry.getKey(), (entry.getValue()).intValue());
                }
                if (!doms.isEmpty()) maxdomcount = doms.getMaxScore();
            } catch (InterruptedException e) {
                Log.logException(e);
            } catch (Exception e) {
                Log.logException(e);
            } finally {
                try {
                    decodedEntries.put(WordReferenceVars.poison);
                } catch (InterruptedException e) {}
            }
        }
        
        public BlockingQueue<WordReferenceVars> decoded() {
            return this.decodedEntries;
        }
    }
    
    public BlockingQueue<WordReferenceVars> normalizeWith(final ReferenceContainer<WordReference> container) {
        Normalizer n = new Normalizer(container);
        n.start();
        return n.decoded();
    }
    
    public int authority(final String urlHash) {
        return (doms.getScore(urlHash.substring(6)) << 8) / (1 + this.maxdomcount);
    }

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
           + ((patchUK(t.language).equals(this.language))        ? 255 << ranking.coeff_language           : 0)
           + ((DigestURI.probablyRootURL(t.metadataHash()))             ?  15 << ranking.coeff_urllength          : 0);
        //if (searchWords != null) r += (yacyURL.probablyWordURL(t.urlHash(), searchWords) != null) ? 256 << ranking.coeff_appurl : 0;

        return Long.MAX_VALUE - r; // returns a reversed number: the lower the number the better the ranking. This is used for simple sorting with a TreeMap
    }

    private static final String patchUK(String l) {
        // this is to patch a bad language name setting that was used in 0.60 and before
        if (l == null || l.equals("uk")) return "en"; else return l;
    }
}
