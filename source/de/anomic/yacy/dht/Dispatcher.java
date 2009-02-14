// Dispatcher.java 
// ------------------------------
// part of YaCy
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 28.01.2009
//
// $LastChangedDate: 2009-01-23 16:32:27 +0100 (Fr, 23 Jan 2009) $
// $LastChangedRevision: 5514 $
// $LastChangedBy: orbiter $
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

package de.anomic.yacy.dht;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRI;
import de.anomic.index.indexRWIRowEntry;
import de.anomic.index.indexRepositoryReference;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.util.Log;
import de.anomic.server.serverProcessor;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;

public class Dispatcher {

    /**
     * the dispatcher class accumulates indexContainerCache objects before they are transfered
     * to other peers. A dispatcher holds several of such caches to enhance the transmission process.
     * Before a RWI is sent, the following process is applied:
     * - (1) a number of RWIs are selected and accumulated.
     *       When they are selected, they are removed from the index
     * - (2) the RWI collection is splitted into a number of partitions according to the vertical DHT.
     * - (3) the splitted RWIs are enqueued as Entry object in the entry 'cloud' of the dispatcher
     * - (4) more entries may be enqueued to the dispatcher and entries with the same primary target
     *       are accumulated.
     * - (5) the largest entries are selected from the dispatcher cloud and enqueued to the 'next' array
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
    
    // a cloud is a cache for the objects that wait to be transmitted
    // the String-key is the primary target as contained in the Entry
    private HashMap<String, Transmission.Chunk> transmissionCloud;
    
    // the backend is used to store the remaining indexContainers in case that the object is closed
    private indexRI backend;
    
    // the seed database
    private yacySeedDB seeds;
    
    // the log
    private Log log;
    
    // transmission process
    private serverProcessor<Transmission.Chunk> indexingTransmissionProcessor;
   
    // transmission object
    private Transmission transmission;
    
    public Dispatcher(
            final indexRI backend,
            final indexRepositoryReference repository,
            final yacySeedDB seeds,
            final boolean gzipBody, 
            final int timeout
            ) {
        this.transmissionCloud = new HashMap<String, Transmission.Chunk>();
        this.backend = backend;
        this.seeds = seeds;
        this.log = new Log("INDEX TRANSFER DISPATCHER");
        this.transmission = new Transmission(
            log,
            repository, 
            seeds,
            backend,
            gzipBody,
            timeout);
        //this.selectedContainerCache = null;
        //this.splittedContainerCache = null;
        
        int concurrentSender = Math.min(25, Math.max(10, serverProcessor.useCPU * 2 + 1));
        indexingTransmissionProcessor = new serverProcessor<Transmission.Chunk>(
                "storeDocumentIndex",
                "This is the RWI transmission process",
                new String[]{"RWI/Cache/Collections"},
                this, "storeDocumentIndex", concurrentSender * 2, null, concurrentSender);
    }
    
    public int cloudSize() {
    	return this.transmissionCloud.size();
    }
    
    public int transmissionSize() {
    	return this.indexingTransmissionProcessor.queueSize();
    }
    
    /**
     * PROCESS(1)
     * select a number of index containers from the backend index.
     * Selected containers are removed from the backend.
     * @param hash
     * @param limitHash
     * @param maxContainerCount
     * @param maxtime
     * @return
     * @throws IOException
     */
    private ArrayList<indexContainer> selectContainers(
            final String hash,
            final String limitHash,
            final int maxContainerCount,
            final int maxReferenceCount,
            final int maxtime) throws IOException {
        
    	// prefer file
        ArrayList<indexContainer> containers = selectContainers(hash, limitHash, maxContainerCount, maxReferenceCount, maxtime, false);

        // if ram does not provide any result, take from file
        //if (containers.size() == 0) containers = selectContainers(hash, limitHash, maxContainerCount, maxtime, false);
        return containers;
    }
    
    private ArrayList<indexContainer> selectContainers(
            final String hash,
            final String limitHash,
            final int maxContainerCount,
            final int maxReferenceCount,
            final int maxtime,
            final boolean ram) throws IOException {
        
        final ArrayList<indexContainer> containers = new ArrayList<indexContainer>(maxContainerCount);
        
        final Iterator<indexContainer> indexContainerIterator = this.backend.wordContainerIterator(hash, true, ram);
        indexContainer container;
        int refcount = 0;

        // first select the container
        final long timeout = (maxtime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        while (
                (containers.size() < maxContainerCount) &&
                (refcount < maxReferenceCount) &&
                (indexContainerIterator.hasNext()) &&
                (System.currentTimeMillis() < timeout) &&
                ((container = indexContainerIterator.next()) != null) &&
                ((containers.size() == 0) ||
                 (Base64Order.enhancedComparator.compare(container.getWordHash(), limitHash) < 0))
                
        ) {
            if (container.size() == 0) continue;
            refcount += container.size();
            containers.add(container);
        }
        // then remove the container from the backend
        for (indexContainer c: containers) this.backend.deleteContainer(c.getWordHash());
        
        // finished. The caller must take care of the containers and must put them back if not needed
        return containers;
    }

    /**
     * PROCESS(2)
     * split a list of containers into partitions according to the vertical distribution scheme
     * @param containers
     * @param scheme
     * @return
     */
    @SuppressWarnings("unchecked")
    private ArrayList<indexContainer>[] splitContainers(ArrayList<indexContainer> containers) {
        
        // init the result vector
        int partitionCount = this.seeds.scheme.verticalPartitions();
        ArrayList<indexContainer>[] partitions = (ArrayList<indexContainer>[]) new ArrayList[partitionCount];
        for (int i = 0; i < partitions.length; i++) partitions[i] = new ArrayList<indexContainer>();
        
        // check all entries and split them to the partitions
        indexContainer[] partitionBuffer = new indexContainer[partitionCount];
        indexRWIRowEntry re;
        for (indexContainer container: containers) {
            // init the new partitions
            for (int j = 0; j < partitionBuffer.length; j++) {
                partitionBuffer[j] = new indexContainer(container.getWordHash(), container.row(), container.size() / partitionCount);
            }

            // split the container
            Iterator<indexRWIRowEntry> i = container.entries();
            while (i.hasNext()) {
                re = i.next();
                if (re == null) continue;
                partitionBuffer[this.seeds.scheme.verticalPosition(re.urlHash())].add(re);
            }
            
            // add the containers to the result vector
            for (int j = 0; j < partitionBuffer.length; j++) {
                partitions[j].add(partitionBuffer[j]);
            }
        }
        return partitions;
    }
    
    /**
     * PROCESS(3) and PROCESS(4)
     * put containers into cloud. This needs information about the network,
     * because the possible targets are assigned here as well. The indexRepositoryReference
     * is the database of references which is needed here because this is the place where
     * finally is checked if the reference exists. If the entry does not exist for specific
     * entries in the indexContainer, then it is discarded. If it exists, then the entry is
     * stored in a cache of the Entry for later transmission to the targets, which means that
     * then no additional IO is necessary.
     */
    private void enqueueContainersToCloud(final ArrayList<indexContainer>[] containers) {
        indexContainer lastContainer;
        String primaryTarget;
        Transmission.Chunk entry;
        for (int vertical = 0; vertical < containers.length; vertical++) {
            // the 'new' primary target is the word hash of the last container
            lastContainer = containers[vertical].get(containers[vertical].size() - 1);
            primaryTarget = FlatWordPartitionScheme.positionToHash(this.seeds.scheme.dhtPosition(lastContainer.getWordHash(), vertical));

            // get or make a entry object
            entry = this.transmissionCloud.get(primaryTarget); // if this is not null, the entry is extended here
            ArrayList<yacySeed> targets = PeerSelection.getAcceptRemoteIndexSeedsList(
                    seeds,
                    primaryTarget,
                    seeds.redundancy() * 3,
                    true);
            this.log.logInfo("enqueueContainers: selected " + targets.size() + " targets for primary target key " + primaryTarget + "/" + vertical + " with " + containers[vertical].size() + " index containers.");
            if (entry == null) entry = transmission.newChunk(primaryTarget, targets, lastContainer.row());
            
            // fill the entry with the containers
            for (indexContainer c: containers[vertical]) {
                entry.add(c);
            }
            
            // put the entry into the cloud
            if (entry.containersSize() > 0) this.transmissionCloud.put(primaryTarget, entry);
        }
    }

    public synchronized boolean selectContainersEnqueueToCloud(
            final String hash,
            final String limitHash,
            final int maxContainerCount,
            final int maxReferenceCount,
            final int maxtime) throws IOException {

    	ArrayList<indexContainer> selectedContainerCache = selectContainers(hash, limitHash, maxContainerCount, maxReferenceCount, maxtime);
        this.log.logInfo("selectContainersToCache: selectedContainerCache was filled with " + selectedContainerCache.size() + " entries");
        
        if (selectedContainerCache == null || selectedContainerCache.size() == 0) {
        	this.log.logInfo("splitContainersFromCache: selectedContainerCache is empty, cannot do anything here.");
        	return false;
        }

        ArrayList<indexContainer>[] splittedContainerCache = splitContainers(selectedContainerCache);
        selectedContainerCache = null;
        if (splittedContainerCache == null) {
        	this.log.logInfo("enqueueContainersFromCache: splittedContainerCache is empty, cannot do anything here.");
        	return false;
        }
        this.log.logInfo("splitContainersFromCache: splittedContainerCache filled with " + splittedContainerCache.length + " partitions, deleting selectedContainerCache");
        if (splittedContainerCache.length != this.seeds.scheme.verticalPartitions()) {
        	this.log.logWarning("enqueueContainersFromCache: splittedContainerCache has wrong length.");
        	return false;
        }
        enqueueContainersToCloud(splittedContainerCache);
        splittedContainerCache = null;
    	this.log.logInfo("enqueueContainersFromCache: splittedContainerCache enqueued to cloud array which has now " + this.transmissionCloud.size() + " entries.");
        return true;
    }
    
    /**
     * PROCESS(5)
     * take the largest container from the cloud and put it into the 'next' array,
     * where it waits to be processed.
     * This method returns true if a container was dequeued, false if not
     */
    public boolean dequeueContainer() {
        if (this.indexingTransmissionProcessor.queueSize() > indexingTransmissionProcessor.concurrency()) return false;
        String maxtarget = "";
        int maxsize = -1;
        for (Map.Entry<String, Transmission.Chunk> chunk: this.transmissionCloud.entrySet()) {
            if (chunk.getValue().containersSize() > maxsize) {
                maxsize = chunk.getValue().containersSize();
                maxtarget = chunk.getKey();
            }
        }
        if (maxsize < 0) return false;
        Transmission.Chunk chunk = this.transmissionCloud.remove(maxtarget);
        try {
            this.indexingTransmissionProcessor.enQueue(chunk);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }
    
    public Transmission.Chunk storeDocumentIndex(Transmission.Chunk chunk) {

        // do the transmission
        boolean success = chunk.transmit();
        
        if (success && chunk.isFinished()) {
                // finished with this queue!
                this.log.logInfo("STORE: Chunk " + chunk.primaryTarget() + " has FINISHED all transmissions!");
                return chunk;
        }
        
        this.log.logInfo("STORE: Chunk " + chunk.primaryTarget() + " has failed to transmit index; marked peer as busy");
        
        if (chunk.canFinish()) {
            try {
                if (this.indexingTransmissionProcessor != null) this.indexingTransmissionProcessor.enQueue(chunk);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
            return chunk;
        } else {
            this.log.logInfo("STORE: Chunk " + chunk.primaryTarget() + " has not enough targets left. This transmission has failed, putting back index to backend");
            chunk.restore();
            return null;
        }
    }

    public void close() {
        // removes all entries from the dispatcher and puts them back to a RAMRI
        if (indexingTransmissionProcessor != null) this.indexingTransmissionProcessor.announceShutdown();
        if (this.transmissionCloud != null) {
        	for (Map.Entry<String, Transmission.Chunk> e : this.transmissionCloud.entrySet()) {
        		for (indexContainer i : e.getValue()) try {this.backend.addEntries(i);} catch (IOException e1) {}
        	}
        	this.transmissionCloud.clear();
        }
        this.transmissionCloud = null;
        if (indexingTransmissionProcessor != null) {
        	this.indexingTransmissionProcessor.awaitShutdown(10000);
        	this.indexingTransmissionProcessor.clear();
        }
        this.indexingTransmissionProcessor = null;
    }
    
}
