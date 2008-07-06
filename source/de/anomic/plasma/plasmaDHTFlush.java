// plasmaDHTFlush.java 
// ------------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005, 2006
// 
// This Class was written by Martin Thelian
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;

public class plasmaDHTFlush extends Thread {
        private yacySeed seed = null;
        private boolean delete = false;
        private boolean finished = false;
        private boolean gzipBody4Transfer = false;
        private int timeout4Transfer = 60000;
        private int transferedEntryCount = 0;
        private long transferedBytes = 0;
        private int transferedContainerCount = 0;
        private String status = "Running";
        private String oldStartingPointHash = "AAAAAAAAAAAA", startPointHash = "AAAAAAAAAAAA";
        private int initialWordsDBSize = 0;
        private int chunkSize = 500;   
        private final long startingTime = System.currentTimeMillis();
        private final plasmaSwitchboard sb;
        private plasmaDHTTransfer worker = null;
        private serverLog log;
        private plasmaWordIndex wordIndex;
        
        public plasmaDHTFlush(serverLog log, plasmaWordIndex wordIndex, yacySeed seed, boolean delete, boolean gzipBody, int timeout) {
            super(new ThreadGroup("TransferIndexThreadGroup"),"TransferIndex_" + seed.getName());
            this.log = log;
            this.wordIndex = wordIndex;
            this.seed = seed;
            this.delete = delete;
            this.sb = plasmaSwitchboard.getSwitchboard();
            this.initialWordsDBSize = this.sb.webIndex.size();   
            this.gzipBody4Transfer = gzipBody;
            this.timeout4Transfer = timeout;
            //this.maxOpenFiles4Transfer = (int) sb.getConfigLong("indexTransfer.maxOpenFiles",800);
        }
        
        public void run() {
            this.performTransferWholeIndex();
        }
        
        public void stopIt(boolean wait) throws InterruptedException {
            this.finished = true;
            if (this.worker != null) this.worker.stopIt();
            if (wait) this.join();
        }
        
        public boolean isFinished() {
            return this.finished;
        }
        
        public boolean deleteIndex() {
            return this.delete;
        }
        
        public int[] getIndexCount() {
            plasmaDHTTransfer workerThread = this.worker;
            if (workerThread != null) {
                return new int[]{this.chunkSize, workerThread.dhtChunk.indexCount()};
            }
            return new int[]{this.chunkSize, 500};
        }
        
        public int getTransferedEntryCount() {
            return this.transferedEntryCount;
        }
        
        public int getTransferedContainerCount() {
            return this.transferedContainerCount;
        }
        
        public long getTransferedBytes() {
            return this.transferedBytes;
        }
        
        public float getTransferedContainerPercent() {
            long currentWordsDBSize = this.sb.webIndex.size(); 
            if (this.initialWordsDBSize == 0) return 100;
            else if (currentWordsDBSize >= this.initialWordsDBSize) return 0;
            //else return (float) ((initialWordsDBSize-currentWordsDBSize)/(initialWordsDBSize/100));
            else return (this.transferedContainerCount*100/this.initialWordsDBSize);
        }
        
        public int getTransferedEntrySpeed() {
            long transferTime = System.currentTimeMillis() - this.startingTime;
            if (transferTime <= 0) transferTime = 1;
            return (int) ((1000 * this.transferedEntryCount) / transferTime);
        }
        
        public yacySeed getSeed() {
            return this.seed;
        }
        
        public String[] getStatus() {
            plasmaDHTTransfer workerThread = this.worker;
            if (workerThread != null) {
                return new String[]{this.status,workerThread.getStatusMessage()};
            }
            return new String[]{this.status,"Not running"};
        }
        
        public String[] getRange() {
            plasmaDHTTransfer workerThread = this.worker;
            if (workerThread != null) {
                return new String[]{"[" + this.oldStartingPointHash + ".." + this.startPointHash + "]",
                                    "[" + workerThread.dhtChunk.firstContainer().getWordHash() + ".." + workerThread.dhtChunk.lastContainer().getWordHash() + "]"};
            }
            return new String[]{"[" + this.oldStartingPointHash + ".." + this.startPointHash + "]","[------------..------------]"};
        }
        
        public void performTransferWholeIndex() {
            plasmaDHTChunk newDHTChunk = null, oldDHTChunk = null;
            try {
                // initial startingpoint of intex transfer is "AAAAAAAAAAAA"                 
                if (this.log.isFine()) this.log.logFine("Selected hash " + this.startPointHash + " as start point for index distribution of whole index");        
                
                /* Loop until we have
                 * - finished transfer of whole index
                 * - detected a server shutdown or user interruption
                 * - detected a failure
                 */
                long iteration = 0;
                
                while (!this.finished && !Thread.currentThread().isInterrupted()) {
                    iteration++;
                    oldDHTChunk = newDHTChunk;
                    
                    // selecting 500 words to transfer
                    this.status = "Running: Selecting chunk " + iteration;
                    newDHTChunk = new plasmaDHTChunk(this.log, this.wordIndex, this.chunkSize/3*2, this.chunkSize, -1, this.startPointHash);
                    
                    /* If we havn't selected a word chunk this could be because of
                     * a) no words are left in the index
                     * b) max open file limit was exceeded 
                     */
                    if (nothingSelected(newDHTChunk)) {
                        if (this.sb.webIndex.size() > 0 && this.delete) {
                            // if there are still words in the index we try it again now
                        	if((iteration % 10L) == 0) { // seems to be blocked, try another startpoint
                        		this.startPointHash = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(System.currentTimeMillis()))).substring(2, 2 + yacySeedDB.commonHashLength);
                        	} else {
                        		this.startPointHash = "AAAAAAAAAAAA";
                        	}   
                        } else {                            
                            // otherwise we could end transfer now
                            if (this.log.isFine()) this.log.logFine("No index available for index transfer, hash start-point " + this.startPointHash);
                            this.status = "Finished. " + iteration + " chunks transfered.";
                            this.finished = true; 
                        }
                    } else {
                        
                        // getting start point for next DHT-selection
                        this.oldStartingPointHash = this.startPointHash;
                        this.startPointHash = newDHTChunk.lastContainer().getWordHash(); // DHT targets must have greater hashes
                        
                        this.log.logInfo("Index selection of " + newDHTChunk.indexCount() + " words [" + newDHTChunk.firstContainer().getWordHash() + " .. " + newDHTChunk.lastContainer().getWordHash() + "]" +
                                " in " +
                                (newDHTChunk.getSelectionTime() / 1000) + " seconds (" +
                                (1000 * newDHTChunk.indexCount() / (newDHTChunk.getSelectionTime()+1)) + " words/s)");                     
                    }
                    
                    // query status of old worker thread
                    if (this.worker != null) {
                        this.status = "Finished: Selecting chunk " + iteration;
                        this.worker.join();
                        if (this.worker.getStatus() != plasmaDHTChunk.chunkStatus_COMPLETE) {
                            // if the transfer failed we abort index transfer now
                            this.status = "Aborted because of Transfer error:\n" + this.worker.dhtChunk.getStatus();
                            
                            // abort index transfer
                            return;
                        }
                        
                        // calculationg the new transfer size
                        this.calculateNewChunkSize();

                        // counting transfered containers / entries
                        this.transferedEntryCount += oldDHTChunk.indexCount();
                        this.transferedContainerCount += oldDHTChunk.containerSize();
                        this.transferedBytes += this.worker.getPayloadSize();
                        
                        this.worker = null;
                        
                        // deleting transfered words from index
                        if (this.delete) {
                            this.status = "Running: Deleting chunk " + iteration;
                            String urlReferences = oldDHTChunk.deleteTransferIndexes();
                            if (this.log.isFine()) this.log.logFine("Deleted from " + oldDHTChunk.containerSize() + " transferred RWIs locally " + urlReferences + " URL references");
                        } 
                        oldDHTChunk = null;
                    }
                    
                    // handover chunk to transfer worker
                    if ((newDHTChunk.containerSize() > 0) || (newDHTChunk.getStatus() == plasmaDHTChunk.chunkStatus_FILLED)) {
                        this.worker = new plasmaDHTTransfer(this.log, this.wordIndex.seedDB, this.wordIndex.peerActions, this.seed, newDHTChunk, this.gzipBody4Transfer, this.timeout4Transfer, 5);
                        this.worker.setTransferMode(plasmaDHTTransfer.TRANSFER_MODE_FLUSH);
                        this.worker.start();
                    }
                }
                
                // if we reach this point we were aborted by the user or by server shutdown
                if (this.sb.webIndex.size() > 0) this.status = "aborted";
            } catch (Exception e) {
                this.status = "Error: " + e.getMessage();
                this.log.logWarning("Index transfer to peer " + this.seed.getName() + ":" + this.seed.hash + " failed:'" + e.getMessage() + "'",e);
                
            } finally {
                if (this.worker != null) {
                    this.worker.stopIt();
                    try {this.worker.join();}catch(Exception e){}
                }
            }
        }

    private void calculateNewChunkSize() {
        // getting the transfered chunk size
        this.chunkSize = this.worker.dhtChunk.indexCount();
        
        // getting the chunk selection time
        long selectionTime = this.worker.dhtChunk.getSelectionTime();
        
        // getting the chunk transfer time
        long transferTime = this.worker.getTransferTime();

        // calculationg the new chunk size
        if (transferTime > 60*1000 && this.chunkSize>200) {
            this.chunkSize-=100;
        } else if (selectionTime < transferTime){
            this.chunkSize +=100;
        } else if (selectionTime >= selectionTime && this.chunkSize>200){
            this.chunkSize-=100;
        }    
    }

    private static boolean nothingSelected(plasmaDHTChunk newDHTChunk) {
        return (newDHTChunk == null) ||
               (newDHTChunk.containerSize() == 0) ||
               (newDHTChunk.getStatus() == plasmaDHTChunk.chunkStatus_FAILED);        
    }
}
