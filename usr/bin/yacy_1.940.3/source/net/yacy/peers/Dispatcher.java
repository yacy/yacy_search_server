// Dispatcher.java
// ------------------------------
// part of YaCy
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 28.01.2009
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.peers;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Memory;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.kelondro.workflow.WorkflowTask;
import net.yacy.peers.Transmission.Chunk;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;

public class Dispatcher implements WorkflowTask<Transmission.Chunk> {

    /**
     * the dispatcher class accumulates indexContainerCache objects before they are transfered
     * to other peers. A dispatcher holds several of such caches to enhance the transmission process.
     * Before a RWI is sent, the following process is applied:
     * - (1) a number of RWIs are selected and accumulated.
     *       When they are selected, they are removed from the index
     * - (2) the RWI collection is split into a number of partitions according to the vertical DHT.
     * - (3) the split RWIs are enqueued as Entry object in the write buffer of the dispatcher
     * - (4) more entries may be enqueued to the dispatcher and
     *       entries with the same primary target are accumulated to the same buffer entry.
     * - (5) the largest entries are selected from the dispatcher write buffer and enqueued to the 'next' array
     *       which means that they are ready for transmission
     * - (6) the dispatcher takes some of the entries in the next queue and initiates
     *       transmission to other peers concurrently. As much transmissions are initiated concurrently
     *       as the redundancy factor.
     * - (7) a transmission thread executes the entry transmission.
     * - (8) the transmission thread initiates another transmission in case that it fails
     * - (9) when the wanted number of redundant peers have received the entries,
     *       they are removed from the next queue
     * Concurrency in this process:
     * 1-3 follow directly and should be synchronous because of the database operation that are used
     * 4   is a repeated action of 1-3 and should be done in a busyThread
     * 5&6 is a repeated action as (4), but must be executed multiple times of (4) in a busyThread,
     *     which idle is shorter than the idle time of (4)
     * 7&8 this is done concurrently with other transmission threads for the same entry and other entries
     *     for example, if the redundancy factor is 3 and 2 entries are in the 'next' queue, then 6
     *     transmissions are running concurrently
     * 9   concurrency ends for the transmission, if the wanted number of redundant peers received the entry,
     *     or the target queue runs out of entries. If the target queue is empty, the transmission is
     *     called failed. In case of a fail, the RWI fragment is put back into the backend index structure
     */

    /**
     * A transmission buffer is a write buffer for the rwi objects (indices) that wait to be transmitted.
     * The String-key is the primary target as contained in the chunk entry.
     */
    private Map<String, Transmission.Chunk> transmissionBuffer;

    /** the segment backend is used to store the remaining indexContainers in case that the object is closed */
    private final Segment segment;

    /** the seed database */
    private final SeedDB seeds;

    /** the log */
    private final ConcurrentLog log;

    /** transmission process */
    private WorkflowProcessor<Transmission.Chunk> indexingTransmissionProcessor;

    /** transmission object */
    private final Transmission transmission;
    
    /** The Switchboard instance holding the server environment */
    private final Switchboard env;

    public Dispatcher(
            final Switchboard env,
            final boolean gzipBody,
            final int timeout
            ) {
        this.env = env;
        this.transmissionBuffer = new ConcurrentHashMap<String, Transmission.Chunk>();
        this.segment = env.index;
        this.seeds = env.peers;
        this.log = new ConcurrentLog("DHT-OUT");
		this.transmission = new Transmission(env, this.log, gzipBody, timeout);

        final int concurrentSender = Math.min(8, WorkflowProcessor.availableCPU);
        this.indexingTransmissionProcessor = new WorkflowProcessor<Transmission.Chunk>(
                "transferDocumentIndex",
                "This is the RWI transmission process",
                new String[]{"RWI/Cache/Collections"},
                this, concurrentSender * 3, null, concurrentSender);
    }

    public int bufferSize() {
    	return (this.transmissionBuffer == null) ? 0 : this.transmissionBuffer.size();
    }

    public int transmissionSize() {
    	return (this.indexingTransmissionProcessor == null) ? 0 : this.indexingTransmissionProcessor.getQueueSize();
    }

    /**
     * PROCESS(1)
     * Select a number of index containers from the RWI index.
     * Selected containers are removed from the RWIs (not from Solr, only the DHT references).
     * @param hash
     * @param limitHash
     * @param maxContainerCount
     * @param maxtime
     * @return
     * @throws IOException
     */
    private ArrayList<ReferenceContainer<WordReference>> selectContainers(
            final byte[] hash,
            final byte[] limitHash,
            final int maxContainerCount,
            final int maxReferenceCount,
            final int maxtime) throws IOException {

    	// prefer file
        final ArrayList<ReferenceContainer<WordReference>> containers = selectContainers(hash, limitHash, maxContainerCount, maxReferenceCount, maxtime, false);

        // if ram does not provide any result, take from file
        //if (containers.isEmpty()) containers = selectContainers(hash, limitHash, maxContainerCount, maxtime, false);
        return containers;
    }

    private ArrayList<ReferenceContainer<WordReference>> selectContainers(
            final byte[] hash,
            final byte[] limitHash,
            final int maxContainerCount,
            final int maxReferenceCount,
            final int maxtime,
            final boolean ram) throws IOException {

        final ArrayList<ReferenceContainer<WordReference>> containers = new ArrayList<ReferenceContainer<WordReference>>(maxContainerCount);

        final Iterator<ReferenceContainer<WordReference>> indexContainerIterator = this.segment.termIndex() == null ? new ArrayList<ReferenceContainer<WordReference>>().iterator() : this.segment.termIndex().referenceContainerIterator(hash, true, true, ram); // very important that rotation is true here
        ReferenceContainer<WordReference> container;
        int refcount = 0;

        // first select the container
        final long timeout = maxtime == Integer.MAX_VALUE ? Long.MAX_VALUE : (maxtime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        while (
                (containers.size() < maxContainerCount) &&
                (refcount < maxReferenceCount) &&
                (indexContainerIterator.hasNext()) &&
                (System.currentTimeMillis() < timeout) &&
                ((container = indexContainerIterator.next()) != null) &&
                ((containers.isEmpty()) ||
                 (Base64Order.enhancedCoder.compare(container.getTermHash(), limitHash) < 0))

        ) {
            if (container.isEmpty()) continue;
            if (Word.isPrivate(container.getTermHash())) continue; // exclude private containers
            refcount += container.size();
            containers.add(container);
        }
        // then remove the container from the backend
        final ArrayList<ReferenceContainer<WordReference>> rc;
        if (ram) {
            // selection was only from ram, so we have to carefully remove only the selected entries
            final HandleSet urlHashes = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0);
            Iterator<WordReference> it;
            for (final ReferenceContainer<WordReference> c: containers) {
                urlHashes.clear();
                it = c.entries();
                while (it.hasNext()) try { urlHashes.put(it.next().urlhash()); } catch (final SpaceExceededException e) { ConcurrentLog.logException(e); }
                if (this.log.isFine()) this.log.fine("selected " + urlHashes.size() + " urls for word '" + ASCII.String(c.getTermHash()) + "'");
                if (this.segment.termIndex() != null && !urlHashes.isEmpty()) this.segment.termIndex().remove(c.getTermHash(), urlHashes);
            }
            rc = containers;
        } else {
            // selection was from whole index, so we can just delete the whole container
            // but to avoid race conditions return the results from the deletes
            rc = new ArrayList<ReferenceContainer<WordReference>>(containers.size());
            for (final ReferenceContainer<WordReference> c: containers) {
                container = this.segment.termIndex() == null ? null : this.segment.termIndex().remove(c.getTermHash()); // be aware this might be null!
                if (container != null && !container.isEmpty()) {
                    if (this.log.isFine()) this.log.fine("selected " + container.size() + " urls for word '" + ASCII.String(c.getTermHash()) + "'");
                    rc.add(container);
                }
            }
        }

        // finished. The caller must take care of the containers and must put them back if not needed
        return rc;
    }

    /**
     * PROCESS(2)
     * split a list of containers into partitions according to the vertical distribution scheme
     * @param containers
     * @return a #verticalPartitions list of reference containers, one for each vertical position
     * @throws SpaceExceededException
     */
    private ReferenceContainer<WordReference>[] splitContainer(final ReferenceContainer<WordReference> container) throws SpaceExceededException {

        // init the result vector
        final int partitionCount = this.seeds.scheme.verticalPartitions();

        // check all entries and split them to the partitions
        @SuppressWarnings("unchecked")
        final ReferenceContainer<WordReference>[] partitionBuffer = (ReferenceContainer<WordReference>[]) Array.newInstance(ReferenceContainer.class, partitionCount);
        
        // init the new partitions
        for (int j = 0; j < partitionBuffer.length; j++) {
            partitionBuffer[j] = new ReferenceContainer<WordReference>(Segment.wordReferenceFactory, container.getTermHash(), container.size() / partitionCount);
        }

        // split the container
        final Iterator<WordReference> i = container.entries();
        while (i.hasNext()) {
            WordReference wordReference = i.next();
            if (wordReference == null) continue;
            partitionBuffer[this.seeds.scheme.verticalDHTPosition(wordReference.urlhash())].add(wordReference);
        }

        return partitionBuffer;
    }

    /**
     * PROCESS(3) and PROCESS(4)
     * put containers into the write buffer. This needs information about the network,
     * because the possible targets are assigned here as well. The indexRepositoryReference
     * is the database of references which is needed here because this is the place where
     * finally is checked if the reference exists. If the entry does not exist for specific
     * entries in the indexContainer, then it is discarded. If it exists, then the entry is
     * stored in a cache of the Entry for later transmission to the targets, which means that
     * then no additional IO is necessary.
     * @param containers a reference containers array, one container for each vertical position
     */
    private void enqueueContainersToBuffer(final byte[] wordhash, final ReferenceContainer<WordReference>[] containers) {
        assert (containers.length == this.seeds.scheme.verticalPartitions());
        if (this.transmissionBuffer == null) return;
        List<Seed>[] targets = DHTSelection.selectDHTDistributionTargets(this.seeds, wordhash, 3, this.seeds.redundancy());
        assert (targets.length == this.seeds.scheme.verticalPartitions());
        assert (targets.length == containers.length);
        for (int vertical = 0; vertical < containers.length; vertical++) {
            ReferenceContainer<WordReference> verticalContainer = containers[vertical];
            if (verticalContainer.isEmpty()) continue;
            
            // extend the transmissionBuffer with entries for each redundant position
            for (Seed target: targets[vertical]) {
                Transmission.Chunk entry = this.transmissionBuffer.get(target.hash); // if this is not null, the entry is extended here
                if (entry == null) entry =  transmission.newChunk(target); else {
                    log.fine("extending chunk for peer " + entry.dhtTarget().hash + " containing " + entry.containersSize() + " references with " + verticalContainer.size() + " more entries");
                }
                try {
                    entry.add(verticalContainer);
                } catch (SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
                this.transmissionBuffer.put(target.hash, entry);
            }
        }
    }
    
    public boolean selectContainersEnqueueToBuffer(
            final byte[] hash,
            final byte[] limitHash,
            final int maxContainerCount,
            final int maxReferenceCount,
            final int maxtime) {
        if (this.transmissionBuffer == null) return false;

    	List<ReferenceContainer<WordReference>> selectedContainerCache;
        try {
            selectedContainerCache = selectContainers(hash, limitHash, maxContainerCount, maxReferenceCount, maxtime);
        } catch (final IOException e) {
            this.log.severe("selectContainersEnqueueToBuffer: selectedContainer failed", e);
            return false;
        }
        this.log.info("selectContainersEnqueueToBuffer: selectedContainerCache was filled with " + selectedContainerCache.size() + " entries");

        if (selectedContainerCache == null || selectedContainerCache.isEmpty()) {
        	this.log.info("selectContainersEnqueueToBuffer: selectedContainerCache is empty, cannot do anything here.");
        	return false;
        }

        // check all entries and split them to the partitions
        try {
            for (final ReferenceContainer<WordReference> container: selectedContainerCache) {
                // init the new partitions
                final ReferenceContainer<WordReference>[] partitionBuffer = splitContainer(container);
                enqueueContainersToBuffer(container.getTermHash(), partitionBuffer);
            }
        } catch (final SpaceExceededException e) {
            this.log.severe("splitContainer: splitContainers failed because of too low RAM", e);
            return false;
        }
    	this.log.info("selectContainersEnqueueToBuffer: splitContainerCache enqueued to the write buffer array which has now " + this.transmissionBuffer.size() + " entries.");
        return true;
    }

    /**
     * PROCESS(5)
     * take the largest container from the write buffer and put it into the 'next' array,
     * where it waits to be processed.
     * This method returns true if a container was dequeued, false if not
     */
    public boolean dequeueContainer() {
    	if (this.transmissionBuffer == null) return false;
        if (this.indexingTransmissionProcessor.getQueueSize() > this.indexingTransmissionProcessor.getMaxConcurrency()) return false;
        String maxtarget = null;
        int maxsize = -1;
        for (final Map.Entry<String, Transmission.Chunk> chunk: this.transmissionBuffer.entrySet()) {
            if (chunk.getValue().containersSize() > maxsize) {
                maxsize = chunk.getValue().containersSize();
                maxtarget = chunk.getKey();
            }
        }
        if (maxsize < 0) return false;
        final Transmission.Chunk chunk = this.transmissionBuffer.remove(maxtarget);
        this.indexingTransmissionProcessor.enQueue(chunk);
        return true;
    }
    
    @Override
    public Chunk process(final Transmission.Chunk chunk) throws Exception {
    	return transferDocumentIndex(chunk);
    }

    /**
     * Transfer job implementation
     */
    private Transmission.Chunk transferDocumentIndex(final Transmission.Chunk chunk) {

        // try to keep the system healthy; sleep as long as System load is too high
        while (Protocol.metadataRetrievalRunning.get() > 0) try {Thread.sleep(1000);} catch (InterruptedException e) {break;}
        
        // we must test this here again
        while (Memory.getSystemLoadAverage() > this.env.getConfigFloat(SwitchboardConstants.INDEX_DIST_LOADPREREQ, 2.0f)) try {Thread.sleep(10000);} catch (InterruptedException e) {break;}
        
        // do the transmission
        final boolean success = chunk.transmit();
        if (success) return chunk;

        this.log.info("STORE: Chunk " + chunk.dhtTarget().getName() + " does not respond or accept the dht index, putting back index to backend");
        chunk.restore();
        return null;
    }

    public void close() {
        // removes all entries from the dispatcher and puts them back to a RAMRI
        if (this.indexingTransmissionProcessor != null) this.indexingTransmissionProcessor.shutdown();
        if (this.transmissionBuffer != null) {
        	outerLoop: for (final Map.Entry<String, Transmission.Chunk> e : this.transmissionBuffer.entrySet()) {
        		for (final ReferenceContainer<WordReference> i : e.getValue()) try {
        		    this.segment.storeRWI(i);
        		} catch (final Exception e1) {
        		    ConcurrentLog.logException(e1);
        		    break outerLoop;
        		}
        	}
        	this.transmissionBuffer.clear();
        }
        this.transmissionBuffer = null;
        if (this.indexingTransmissionProcessor != null) {
        	this.indexingTransmissionProcessor.clear();
        }
        this.indexingTransmissionProcessor = null;
    }

}
