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


package net.yacy.search.ranking;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.BinSearch;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.rwi.ReferenceIterator;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.peers.graphics.WebStructureGraph.HostReference;
import net.yacy.search.index.Segment;

public class BlockRank {

    /**
     * collect host index information from other peers. All peers in the seed database are asked
     * this may take some time; please wait up to one minute
     * @param seeds
     * @return a merged host index from all peers
     */
    public static ReferenceContainerCache<HostReference> collect(final SeedDB seeds, final WebStructureGraph myGraph, int maxcount) {

        final ReferenceContainerCache<HostReference> index = new ReferenceContainerCache<HostReference>(WebStructureGraph.hostReferenceFactory, Base64Order.enhancedCoder, 6);

        // start all jobs
        final Iterator<Seed> si = seeds.seedsConnected(true, false, null, 0.99f);
        final ArrayList<IndexRetrieval> jobs = new ArrayList<IndexRetrieval>();
        while (maxcount-- > 0 && si.hasNext()) {
            final IndexRetrieval loader = new IndexRetrieval(index, si.next());
            loader.start();
            jobs.add(loader);
        }

        // get the local index
        if (myGraph != null) try {
            final ReferenceContainerCache<HostReference> myIndex = myGraph.incomingReferences();
            ConcurrentLog.info("BlockRank", "loaded " + myIndex.size() + " host indexes from my peer");
            index.merge(myIndex);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }

        // wait for termination
        for (final IndexRetrieval job: jobs) try { job.join(); } catch (final InterruptedException e) { }
        ConcurrentLog.info("BlockRank", "create " + index.size() + " host indexes from all peers");

        return index;
    }

    public static class IndexRetrieval extends Thread {

        ReferenceContainerCache<HostReference> index;
        Seed seed;

        public IndexRetrieval(final ReferenceContainerCache<HostReference> index, final Seed seed) {
            this.index = index;
            this.seed = seed;
        }

        @Override
        public void run() {
            final ReferenceContainerCache<HostReference> partialIndex = Protocol.loadIDXHosts(this.seed);
            if (partialIndex == null || partialIndex.isEmpty()) return;
            ConcurrentLog.info("BlockRank", "loaded " + partialIndex.size() + " host indexes from peer " + this.seed.getName());
            try {
                this.index.merge(partialIndex);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    /**
     * save the index into a BLOB
     * @param index
     * @param file
     */
    public static void saveHostIndex(final ReferenceContainerCache<HostReference> index, final File file) {
        ConcurrentLog.info("BlockRank", "saving " + index.size() + " host indexes to file " + file.toString());
        index.dump(file, Segment.writeBufferSize, false);
        ConcurrentLog.info("BlockRank", "saved " + index.size() + " host indexes to file " + file.toString());
    }

    public static ReferenceContainerCache<HostReference> loadHostIndex(final File file) {

        ConcurrentLog.info("BlockRank", "reading host indexes from file " + file.toString());
        final ReferenceContainerCache<HostReference> index = new ReferenceContainerCache<HostReference>(WebStructureGraph.hostReferenceFactory, Base64Order.enhancedCoder, 6);

        // load from file
        try {
            final ReferenceIterator<HostReference> ri = new ReferenceIterator<HostReference>(file, WebStructureGraph.hostReferenceFactory);
            while (ri.hasNext()) {
                final ReferenceContainer<HostReference> references = ri.next();
                index.add(references);
            }
            ri.close();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }

        ConcurrentLog.info("BlockRank", "read " + index.size() + " host indexes from file " + file.toString());
        return index;
    }

    public static int ranking(final byte[] hash, final BinSearch[] rankingTable) {
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
