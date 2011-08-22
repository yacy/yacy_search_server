/**
 *  BlockRankCollector
 *  Copyright 2011 by Michael Christen
 *  First released 18.05.2011 at http://yacy.net
 *  
 *  $LastChangedDate: 2011-04-26 19:39:16 +0200 (Di, 26 Apr 2011) $
 *  $LastChangedRevision: 7676 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package de.anomic.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.ranking.OrderedScoreMap;
import net.yacy.cora.ranking.ScoreMap;
import net.yacy.kelondro.index.BinSearch;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.rwi.ReferenceIterator;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.search.MetadataRepository.HostStat;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.graphics.WebStructureGraph;
import de.anomic.yacy.graphics.WebStructureGraph.HostReference;

public class BlockRank {

    public static BinSearch[] ybrTables = null; // block-rank tables
    
    
    /**
     * collect host index information from other peers. All peers in the seed database are asked
     * this may take some time; please wait up to one minute
     * @param seeds
     * @return a merged host index from all peers
     */
    public static ReferenceContainerCache<HostReference> collect(final yacySeedDB seeds, final WebStructureGraph myGraph) {
        
        ReferenceContainerCache<HostReference> index = new ReferenceContainerCache<HostReference>(WebStructureGraph.hostReferenceFactory, Base64Order.enhancedCoder, 6);
        
        // start all jobs
        Iterator<yacySeed> si = seeds.seedsConnected(true, false, null, 0.99f);
        ArrayList<IndexRetrieval> jobs = new ArrayList<IndexRetrieval>();
        while (si.hasNext()) {
            IndexRetrieval loader = new IndexRetrieval(index, si.next());
            loader.start();
            jobs.add(loader);
        }
        
        // get the local index
        if (myGraph != null) try {
            ReferenceContainerCache<HostReference> myIndex = myGraph.incomingReferences();
            Log.logInfo("BlockRank", "loaded " + myIndex.size() + " host indexes from my peer");
            index.merge(myIndex);
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        
        // wait for termination
        for (IndexRetrieval job: jobs) try { job.join(); } catch (InterruptedException e) { }
        Log.logInfo("BlockRank", "create " + index.size() + " host indexes from all peers");
        
        return index;
    }
    
    public static class IndexRetrieval extends Thread {
        
        ReferenceContainerCache<HostReference> index;
        yacySeed seed;
        
        public IndexRetrieval(ReferenceContainerCache<HostReference> index, yacySeed seed) {
            this.index = index;
            this.seed = seed;
        }
        
        public void run() {
            ReferenceContainerCache<HostReference> partialIndex = yacyClient.loadIDXHosts(this.seed);
            if (partialIndex == null || partialIndex.size() == 0) return;
            Log.logInfo("BlockRank", "loaded " + partialIndex.size() + " host indexes from peer " + this.seed.getName());
            try {
                this.index.merge(partialIndex);
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
        }
    }

    /**
     * save the index into a BLOB
     * @param index
     * @param file
     */
    public static void saveHostIndex(ReferenceContainerCache<HostReference> index, File file) {
        Log.logInfo("BlockRank", "saving " + index.size() + " host indexes to file " + file.toString());
        index.dump(file, Segment.writeBufferSize, false);
        Log.logInfo("BlockRank", "saved " + index.size() + " host indexes to file " + file.toString());
    }
    
    public static ReferenceContainerCache<HostReference> loadHostIndex(File file) {

        Log.logInfo("BlockRank", "reading host indexes from file " + file.toString());
        ReferenceContainerCache<HostReference> index = new ReferenceContainerCache<HostReference>(WebStructureGraph.hostReferenceFactory, Base64Order.enhancedCoder, 6);

        // load from file
        try {
            ReferenceIterator<HostReference> ri = new ReferenceIterator<HostReference>(file, WebStructureGraph.hostReferenceFactory);
            while (ri.hasNext()) {
                ReferenceContainer<HostReference> references = ri.next();
                index.add(references);
            }
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }

        Log.logInfo("BlockRank", "read " + index.size() + " host indexes from file " + file.toString());
        return index;
    }
    
    public static BinSearch[] evaluate(final ReferenceContainerCache<HostReference> index, Map<String, HostStat> hostHashResolver, BinSearch[] referenceTable, int recusions) {
        
        // first find out the maximum count of the hostHashResolver
        int maxHostCount = 1;
        for (HostStat stat: hostHashResolver.values()) {
            if (stat.count > maxHostCount) maxHostCount = stat.count;
        }
        
        // then just count the number of references. all other information from the index is not used because they cannot be trusted
        ScoreMap<byte[]> hostScore = new OrderedScoreMap<byte[]>(index.termKeyOrdering());
        HostStat hostStat;
        int hostCount;
        for (ReferenceContainer<HostReference> container: index) {
            if (container.size() == 0) continue;
            if (referenceTable == null) {
                hostStat = hostHashResolver.get(ASCII.String(container.getTermHash()));
                hostCount = hostStat == null ? 6 /* high = a penalty for 'i do not know this', this may not be fair*/ : Math.max(1, hostStat.count);
                hostScore.set(container.getTermHash(), container.size() * maxHostCount / hostCount);
            } else {
                int score = 0;
                Iterator<HostReference> hri = container.entries();
                HostReference hr;
                while (hri.hasNext()) {
                    hr = hri.next();
                    hostStat =  hostHashResolver.get(ASCII.String(hr.urlhash()));
                    hostCount = hostStat == null ? 6 /* high = a penalty for 'i do not know this', this may not be fair*/ : Math.max(1, hostStat.count);
                    score += (17 - ranking(hr.urlhash(), referenceTable)) * maxHostCount / hostCount;
                }
                hostScore.set(container.getTermHash(), score);
            }
        }
        
        // now divide the scores into two halves until the score map is empty
        List<BinSearch> table = new ArrayList<BinSearch>();
        while (hostScore.size() > 10) {
            List<byte[]> smallest = hostScore.lowerHalf();
            if (smallest.size() == 0) break; // should never happen but this ensures termination of the loop
            Log.logInfo("BlockRank", "index evaluation: computed partition of size " + smallest.size());
            table.add(new BinSearch(smallest, 6));
            for (byte[] host: smallest) hostScore.delete(host);
        }
        if (hostScore.size() > 0) {
            ArrayList<byte[]> list = new ArrayList<byte[]>();
            for (byte[] entry: hostScore) list.add(entry);
            Log.logInfo("BlockRank", "index evaluation: computed last partition of size " + list.size());
            table.add(new BinSearch(list, 6));
        }
        
        // the last table entry has now a list of host hashes that has the most references
        int binTables = Math.min(16, table.size());
        BinSearch[] newTables = new BinSearch[binTables];
        for (int i = 0; i < binTables; i++) newTables[i] = table.get(table.size() - i - 1);

        // re-use the new table for a recursion
        if (recusions == 0) return newTables;
        return evaluate(index, hostHashResolver, newTables, --recusions); // one recursion step
    }
    
    public static void analyse(BinSearch[] tables, final WebStructureGraph myGraph, final Map<String, HostStat> hostHash2hostName) {
        byte[] hosth = new byte[6];
        String hosths, hostn;
        HostStat hs;
        for (int ybr = 0; ybr < tables.length; ybr++) {
            row: for (int i = 0; i < tables[ybr].size(); i++) {
                hosth = tables[ybr].get(i, hosth);
                hosths = ASCII.String(hosth);
                hostn = myGraph.hostHash2hostName(hosths);
                if (hostn == null) {
                    hs = hostHash2hostName.get(hostn);
                    if (hs != null) hostn = hs.hostname;
                }
                if (hostn == null) {
                    //Log.logInfo("BlockRank", "Ranking for Host: ybr = " + ybr + ", hosthash = " + hosths);
                    continue row;
                }
                Log.logInfo("BlockRank", "Ranking for Host: ybr = " + ybr + ", hosthash = " + hosths + ", hostname = " + hostn);
            }
        }
    }
    
    
    /**
     * load YaCy Block Rank tables
     * These tables have a very simple structure: every file is a sequence of Domain hashes, ordered by b64.
     * Each Domain hash has a length of 6 bytes and there is no separation character between the hashes
     * @param rankingPath
     * @param count
     */
    public static void loadBlockRankTable(final File rankingPath, final int count) {
        if (!rankingPath.exists()) return;
        ybrTables = new BinSearch[count];
        String ybrName;
        File f;
        try {
            for (int i = 0; i < count; i++) {
                ybrName = "YBR-4-" + Digest.encodeHex(i, 2) + ".idx";
                f = new File(rankingPath, ybrName);
                if (f.exists()) {
                    ybrTables[i] = new BinSearch(FileUtils.read(f), 6);
                } else {
                    ybrTables[i] = null;
                }
            }
        } catch (final IOException e) {
        }
    }
    
    public static void storeBlockRankTable(final File rankingPath) {
        String ybrName;
        File f;
        try {
            // first delete all old files
            for (int i = 0; i < 16; i++) {
                ybrName = "YBR-4-" + Digest.encodeHex(i, 2) + ".idx";
                f = new File(rankingPath, ybrName);
                if (!f.exists()) continue;
                if (!f.canWrite()) return;
                f.delete();
            }
            // write new files
            for (int i = 0; i < Math.min(12, ybrTables.length); i++) {
                ybrName = "YBR-4-" + Digest.encodeHex(i, 2) + ".idx";
                f = new File(rankingPath, ybrName);
                ybrTables[i].write(f);
            }
        } catch (final IOException e) {
        }
    }
    
    /**
     * returns the YBR ranking value in a range of 0..15, where 0 means best ranking and 15 means worst ranking
     * @param hash
     * @return
     */
    public static int ranking(final byte[] hash) {
        return ranking(hash, ybrTables);
    }
    
    public static int ranking(final byte[] hash, BinSearch[] rankingTable) {
        if (rankingTable == null) return 16;
        byte[] hosthash;
        if (hash.length == 6) {
            hosthash = hash;
        } else {
            hosthash = new byte[6];
            System.arraycopy(hash, 6, hosthash, 0, 6);
        }
        final int m = Math.min(16, rankingTable.length);
        for (int i = 0; i < m; i++) {
            if (rankingTable[i] != null && rankingTable[i].contains(hosthash)) {
                return i;
            }
        }
        return 16;
    }
}
