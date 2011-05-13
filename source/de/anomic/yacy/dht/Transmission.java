// Transmission.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.02.2009 on http://yacy.net
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

package de.anomic.yacy.dht;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.workflow.WorkflowJob;

import de.anomic.search.Segment;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import java.util.List;
import java.util.SortedMap;

public class Transmission {

    protected Log log;
    protected Segment segment;
    protected yacySeedDB seeds;
    protected boolean gzipBody4Transfer;
    protected int timeout4Transfer;
    
    public Transmission(
            Log log,
            Segment segment, 
            yacySeedDB seeds,
            boolean gzipBody4Transfer,
            int timeout4Transfer) {
        this.log = log;
        this.segment = segment;
        this.seeds = seeds;
        this.gzipBody4Transfer = gzipBody4Transfer;
        this.timeout4Transfer = timeout4Transfer;
    }

    public Chunk newChunk(
                byte[] primaryTarget,
                final List<yacySeed> targets,
                final Row payloadrow) {
        return new Chunk(primaryTarget, targets, payloadrow);
    }

    public class Chunk extends WorkflowJob implements Iterable<ReferenceContainer<WordReference>> {
        /**
         * a dispatcher entry contains
         * - the primary target, which is a word hash, as marker for the entry
         * - a set of indexContainers in a cache,
         * - the associated URLs in a set to have a cache for the transmission
         *   to multiple peers and to ensure that all entries in the indexContainers
         *   have a reference in the urls
         * - a set of yacy seeds which will shrink as the containers are transmitted to them
         * - a counter that gives the number of sucessful and unsuccessful transmissions so far
         */
        private final byte[]                          primaryTarget;
        private final ReferenceContainerCache<WordReference> containers;
        private final SortedMap<byte[], URIMetadataRow> references;
        private final HandleSet                       badReferences;
        private final List<yacySeed>             targets;
        private int                             hit, miss;
        
        /**
         * generate a new dispatcher target. such a target is defined with a primary target and 
         * a set of target peers that shall receive the entries of the containers
         * the payloadrow defines the structure of container entries
         * @param primaryTarget
         * @param targets
         * @param payloadrow
         */
        public Chunk(
                byte[] primaryTarget,
                final List<yacySeed> targets,
                final Row payloadrow) {
            super();
            this.primaryTarget = primaryTarget;
            this.containers = new ReferenceContainerCache<WordReference>(Segment.wordReferenceFactory, payloadrow, Segment.wordOrder);
            this.references = new TreeMap<byte[], URIMetadataRow>(Base64Order.enhancedCoder);
            this.badReferences = new HandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            this.targets    = targets;
            this.hit = 0;
            this.miss = 0;
        }
    
        /**
         * add a container to the Entry cache.
         * all entries in the container are checked and only such are stored which have a reference entry
         * @param container
         * @throws RowSpaceExceededException 
         */
        public void add(ReferenceContainer<WordReference> container) throws RowSpaceExceededException {
            // iterate through the entries in the container and check if the reference is in the repository
            Iterator<WordReference>  i = container.entries();
            List<byte[]> notFoundx = new ArrayList<byte[]>();
            while (i.hasNext()) {
                WordReference e = i.next();
                if (references.containsKey(e.metadataHash())) continue;
                if (badReferences.has(e.metadataHash())) {
                    notFoundx.add(e.metadataHash());
                    continue;
                }
                URIMetadataRow r = segment.urlMetadata().load(e.metadataHash());
                if (r == null) {
                    notFoundx.add(e.metadataHash());
                    badReferences.put(e.metadataHash());
                } else {
                    references.put(e.metadataHash(), r);
                }
            }
            // now delete all references that were not found
            for (final byte[] b : notFoundx) container.removeReference(b);
            // finally add the remaining container to the cache
            containers.add(container);
        }
        
        /**
         * get all containers from the entry. This method may be used to flush remaining entries
         * if they had been finished transmission without success (not enough peers arrived)
         */
        public Iterator<ReferenceContainer<WordReference>> iterator() {
            return this.containers.iterator();
        }
        
        public int containersSize() {
            return this.containers.size();
        }
        
        public byte[] primaryTarget() {
            return this.primaryTarget;
        }
        
        /**
         * return the number of successful transmissions
         * @return
         */
        public int hit() {
            return this.hit;
        }
        
        /**
         * return the number of unsuccessful transmissions
         * @return
         */
        public int miss() {
            return this.miss;
        }
        
        /**
         * return the number of targets that are left in the target cache
         * if this is empty, there may be no more use of this object and it should be flushed
         * with the iterator method
         * @return
         */
        public int targets() {
            return this.targets.size();
        }
        
        public boolean transmit() {
            if (this.targets.isEmpty()) return false;
            yacySeed target = this.targets.remove(0);
            // transferring selected words to remote peer
            if (target == seeds.mySeed() || target.hash.equals(seeds.mySeed().hash)) {
            	// target is my own peer. This is easy. Just restore the indexContainer
            	restore();
            	this.hit++;
            	log.logInfo("Transfer of chunk to myself-target");
            	return true;
            }
            log.logInfo("starting new index transmission request to " + UTF8.String(this.primaryTarget));
            long start = System.currentTimeMillis();
            final String error = yacyClient.transferIndex(target, this.containers, this.references, gzipBody4Transfer, timeout4Transfer);
            if (error == null) {
                // words successfully transfered
                long transferTime = System.currentTimeMillis() - start;
                Iterator<ReferenceContainer<WordReference>> i = this.containers.iterator();
                ReferenceContainer<WordReference> firstContainer = (i == null) ? null : i.next();
                log.logInfo("Index transfer of " + this.containers.size() + 
                                 " words [" + ((firstContainer == null) ? null : UTF8.String(firstContainer.getTermHash())) + " .. " + UTF8.String(this.primaryTarget) + "]" + 
                                 " and " + this.references.size() + " URLs" +
                                 " to peer " + target.getName() + ":" + target.hash + 
                                 " in " + (transferTime / 1000) + 
                                 " seconds successful ("  + (1000 * this.containers.size() / (transferTime + 1)) + 
                                 " words/s)");
                seeds.mySeed().incSI(this.containers.size());
                seeds.mySeed().incSU(this.references.size());
                // if the peer has set a pause time and we are in flush mode (index transfer)
                // then we pause for a while now
                log.logInfo("Transfer finished of chunk to target " + target.hash + "/" + target.getName());
                this.hit++;
                return true;
            }
            this.miss++;
            // write information that peer does not receive index transmissions
            log.logInfo("Transfer failed of chunk to target " + target.hash + "/" + target.getName() + ": " + error);
            // get possibly newer target Info
            yacySeed newTarget = seeds.get(target.hash);
            if (newTarget != null) {
                String oldAddress = target.getPublicAddress();
                if ((oldAddress != null) && (oldAddress.equals(newTarget.getPublicAddress()))) {
                    newTarget.setFlagAcceptRemoteIndex(false);
                    seeds.update(newTarget.hash, newTarget);
                } else {
                    // we tried an old Address. Don't change anything
                }
            } else {
                // target not in DB anymore. ???
            }
            return false;
        }
        
        public boolean isFinished() {
        	//System.out.println("canFinish: hit = " + this.hit + ", redundancy = " + seeds.redundancy() + ", targets.size() = " + targets.size());
            return this.hit >= seeds.redundancy();
        }
        
        public boolean canFinish() {
        	//System.out.println("canFinish: hit = " + this.hit + ", redundancy = " + seeds.redundancy() + ", targets.size() = " + targets.size());
            return this.targets.size() >= seeds.redundancy() - this.hit;
        }

        public void restore() {
            for (ReferenceContainer<WordReference> ic : this) try {
                segment.termIndex().add(ic);
            } catch (Exception e) {
                Log.logException(e);
            }
        }

    }
    
}
