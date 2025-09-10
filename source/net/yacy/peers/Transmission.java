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

package net.yacy.peers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.workflow.WorkflowJob;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;

public class Transmission {

    // The number of RWIs we can be sure a remote peer will accept
    // anything beyond that might get discarded without notice
    public static final int maxRWIsCount = 1000; // since SVN 7993 hardcoded in htroot/yacy/transferRWI.java:161

    /** The Switchboard instance holding the server environment */
    private final Switchboard env;
    
    protected ConcurrentLog log;
    protected Segment segment;
    protected SeedDB seeds;
    protected boolean gzipBody4Transfer;
    protected int timeout4Transfer;

    public Transmission(
    		final Switchboard env,
            final ConcurrentLog log,
            final boolean gzipBody4Transfer,
            final int timeout4Transfer) {
    	this.env = env;
        this.log = log;
        this.segment = env.index;
        this.seeds = env.peers;
        this.gzipBody4Transfer = gzipBody4Transfer;
        this.timeout4Transfer = timeout4Transfer;
    }

    public Chunk newChunk(final Seed dhtTarget) {
        return new Chunk(dhtTarget);
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
         */
        private final Seed                           dhtTarget;
        private final ReferenceContainerCache<WordReference> containers;
        private final HandleSet                      references;
        private final HandleSet                      badReferences;

        /**
         * generate a new dispatcher target. such a target is defined with a primary target and
         * a set of target peers that shall receive the entries of the containers
         * the payloadrow defines the structure of container entries
         * @param dhtTarget
         */
        public Chunk(final Seed dhtTarget) {
            super();
            this.dhtTarget = dhtTarget;
            this.containers = new ReferenceContainerCache<WordReference>(Segment.wordReferenceFactory, Segment.wordOrder, Word.commonHashLength);
            this.references = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            this.badReferences = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
        }

        /*
         * return a new container with at most max elements and put the rest back to the index
         * as this chunk might be transferred back to myself a random selection needs to be taken
         * @param container
         * @param max
         * @throws RowSpaceExceededException
         * @return
         */
        private ReferenceContainer<WordReference> trimContainer(final ReferenceContainer<WordReference> container, final int max) throws SpaceExceededException {
            final int minRWIs = 800; // Add this to enforce the minimum RWIs
            final int effectiveMax = Math.max(max, minRWIs); // Ensure at least 800 RWIs 
      
            final ReferenceContainer<WordReference> c = new ReferenceContainer<WordReference>(Segment.wordReferenceFactory, container.getTermHash(), effectiveMax);
            final int part = container.size() / effectiveMax + 1;
            final Random r = new Random();
            WordReference w;
            final List<byte[]> selected = new ArrayList<byte[]>();
            final Iterator<WordReference>  i = container.entries();
            while ((i.hasNext()) && (c.size() < effectiveMax)) {
                w = i.next();
                if (r.nextInt(part) == 0) {
                    c.add(w);
                    selected.add(w.urlhash());
                }
            }
            // remove the selected entries from container
            for (final byte[] b : selected) container.removeReference(b);
            // put container back
            try {
                Transmission.this.segment.storeRWI(container);
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
            }
            return c;
        }

        /**
         * add a container to the Entry cache.
         * all entries in the container are checked and only such are stored which have a reference entry
         * @param container
         * @throws SpaceExceededException
         */
        public void add(final ReferenceContainer<WordReference> container) throws SpaceExceededException {
            int remaining = maxRWIsCount;
            for (final ReferenceContainer<WordReference> ic : this) remaining -= ic.size();
            if (remaining <= 0) {
                // No space left in this chunk
                try {
                    Transmission.this.segment.storeRWI(container);
                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                }
                return;
            }
            final ReferenceContainer<WordReference> c = (remaining >= container.size()) ? container : trimContainer(container, remaining);
            // iterate through the entries in the container and check if the reference is in the repository
            final List<byte[]> notFoundx = new ArrayList<byte[]>();
            Set<String> testids = new HashSet<String>();
            Iterator<WordReference>  i = c.entries();
            while (i.hasNext()) {
                final WordReference e = i.next();
                if (this.references.has(e.urlhash())) continue;
                if (this.badReferences.has(e.urlhash())) {
                    notFoundx.add(e.urlhash());
                    continue;
                }
                testids.add(ASCII.String(e.urlhash()));
            }
            i = c.entries();
            while (i.hasNext()) {
                final WordReference e = i.next();
                if (Transmission.this.segment.fulltext().exists(ASCII.String(e.urlhash()))) {
                    this.references.put(e.urlhash());
                } else {
                    notFoundx.add(e.urlhash());
                    this.badReferences.put(e.urlhash());
                }
            }
            // now delete all references that were not found
            for (final byte[] b : notFoundx) c.removeReference(b);
            // finally add the remaining container to the cache
            this.containers.add(c);
        }

        /**
         * get all containers from the entry. This method may be used to flush remaining entries
         * if they had been finished transmission without success (not enough peers arrived)
         */
        @Override
        public Iterator<ReferenceContainer<WordReference>> iterator() {
            return this.containers.iterator();
        }

        public int containersSize() {
            return this.containers.size();
        }

        public Seed dhtTarget() {
            return this.dhtTarget;
        }

        public boolean transmit() {
            // transferring selected words to remote peer
            if (this.dhtTarget == Transmission.this.seeds.mySeed() || this.dhtTarget.hash.equals(Transmission.this.seeds.mySeed().hash)) {
            	// target is my own peer. This is easy. Just restore the indexContainer
            	restore();
            	Transmission.this.log.info("Transfer of chunk to myself-target");
            	return true;
            }
            Transmission.this.log.info("starting new index transmission request to " + this.dhtTarget.getName());
            final long start = System.currentTimeMillis();
			final String error = Protocol.transferIndex(Transmission.this.env, this.dhtTarget, this.containers,
					this.references, Transmission.this.segment, Transmission.this.gzipBody4Transfer,
					Transmission.this.timeout4Transfer);
            if (error == null) {
                // words successfully transfered
                final long transferTime = System.currentTimeMillis() - start;
                final Iterator<ReferenceContainer<WordReference>> i = this.containers.iterator();
                final ReferenceContainer<WordReference> firstContainer = (i == null) ? null : i.next();
                Transmission.this.log.info("Index transfer of " + this.containers.size() +
                                 " references for terms [ " + ((firstContainer == null) ? null : ASCII.String(firstContainer.getTermHash()))  + " ..]" +
                                 " and " + this.references.size() + " URLs" +
                                 " to peer " + this.dhtTarget.getName() + ":" + this.dhtTarget.hash +
                                 " in " + (transferTime / 1000) +
                                 " seconds successful ("  + (1000 * this.containers.size() / (transferTime + 1)) +
                                 " words/s)");
                Transmission.this.seeds.mySeed().incSI(this.containers.size());
                Transmission.this.seeds.mySeed().incSU(this.references.size());
                // if the peer has set a pause time and we are in flush mode (index transfer)
                // then we pause for a while now
                Transmission.this.log.info("Transfer finished of chunk to target " + this.dhtTarget.hash + "/" + this.dhtTarget.getName());
                return true;
            }
            Transmission.this.log.info(
                    "Index transfer to peer " + this.dhtTarget.getName() + ":" + this.dhtTarget.hash +
                    " failed: " + error);
            // write information that peer does not receive index transmissions
            Transmission.this.log.info("Transfer failed of chunk to target " + this.dhtTarget.hash + "/" + this.dhtTarget.getName() + ": " + error);
            // get possibly newer target Info
            final Seed newTarget = Transmission.this.seeds.get(this.dhtTarget.hash);
            if (newTarget != null) {
                if (this.dhtTarget.clash(newTarget.getIPs())) {
                    newTarget.setFlagAcceptRemoteIndex(false);
                    Transmission.this.seeds.updateConnected(newTarget);
                } else {
                    // we tried an old Address. Don't change anything
                }
            } else {
                // target not in DB anymore. ???
            }
            return false;
        }

        public void restore() {
            for (final ReferenceContainer<WordReference> ic : this) try {
                Transmission.this.segment.storeRWI(ic);
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
            }
        }

    }

}
