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

import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeed;

public class plasmaDHTFlush extends Thread {
        private yacySeed seed = null;
        private boolean delete = false;
        private boolean finished = false;
        private boolean gzipBody4Transfer = false;
        private int timeout4Transfer = 60000;
        private int transferedEntryCount = 0;
        private int transferedContainerCount = 0;
        private String status = "Running";
        private String oldStartingPointHash = "------------", startPointHash = "------------";
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
            this.initialWordsDBSize = sb.wordIndex.size();   
            this.gzipBody4Transfer = gzipBody;
            this.timeout4Transfer = timeout;
            //this.maxOpenFiles4Transfer = (int) sb.getConfigLong("indexTransfer.maxOpenFiles",800);
        }
        
        public void run() {
            performTransferWholeIndex();
        }
        
        public void stopIt(boolean wait) throws InterruptedException {
            this.finished = true;
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
        
        public float getTransferedContainerPercent() {
            long currentWordsDBSize = sb.wordIndex.size(); 
            if (initialWordsDBSize == 0) return 100;
            else if (currentWordsDBSize >= initialWordsDBSize) return 0;
            //else return (float) ((initialWordsDBSize-currentWordsDBSize)/(initialWordsDBSize/100));
            else return (float)(this.transferedContainerCount*100/initialWordsDBSize);
        }
        
        public int getTransferedEntitySpeed() {
            long transferTime = System.currentTimeMillis() - startingTime;
            if (transferTime <= 0) transferTime = 1;
            return (int) ((1000 * transferedEntryCount) / transferTime);
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
                return new String[]{"[" + oldStartingPointHash + ".." + startPointHash + "]",
                                    "[" + workerThread.dhtChunk.firstContainer().hashCode() + ".." + workerThread.dhtChunk.lastContainer().hashCode() + "]"};
            }
            return new String[]{"[" + oldStartingPointHash + ".." + startPointHash + "]","[------------..------------]"};
        }
        
        public void performTransferWholeIndex() {
            plasmaDHTChunk newDHTChunk = null, oldDHTChunk = null;
            try {
                // pausing the regular index distribution
                // TODO: adding sync, to wait for a still running index distribution to finish
                //plasmaWordIndexDistribution.paused = true;
                
                // initial startingpoint of intex transfer is "------------"                 
                log.logFine("Selected hash " + startPointHash + " as start point for index distribution of whole index");        
                
                /* Loop until we have
                 * - finished transfer of whole index
                 * - detected a server shutdown or user interruption
                 * - detected a failure
                 */
                long selectionStart = System.currentTimeMillis(), selectionEnd = 0, selectionTime = 0, iteration = 0;
                
                while (!finished && !Thread.currentThread().isInterrupted()) {
                    iteration++;
                    selectionStart = System.currentTimeMillis();
                    oldDHTChunk = newDHTChunk;
                    
                    // selecting 500 words to transfer
                    this.status = "Running: Selecting chunk " + iteration;
                    newDHTChunk = new plasmaDHTChunk(log, wordIndex, sb.urlPool.loadedURL, this.chunkSize/3, this.chunkSize, this.startPointHash);
                    
                    /* If we havn't selected a word chunk this could be because of
                     * a) no words are left in the index
                     * b) max open file limit was exceeded 
                     */
                    if ((newDHTChunk == null) ||
                        (newDHTChunk.containerSize() == 0) ||
                        (newDHTChunk.getStatus() == plasmaDHTChunk.chunkStatus_FAILED)) {
                        if (sb.wordIndex.size() > 0) {
                            // if there are still words in the index we try it again now
                            startPointHash = "------------";
                        } else {                            
                            // otherwise we could end transfer now
                            log.logFine("No index available for index transfer, hash start-point " + startPointHash);
                            this.status = "Finished. " + iteration + " chunks transfered.";
                            finished = true; 
                        }
                    } else {
                        
                        // getting start point for next DHT-selection
                        oldStartingPointHash = startPointHash;
                        startPointHash = newDHTChunk.lastContainer().wordHash(); // DHT targets must have greater hashes
                        
                        selectionEnd = System.currentTimeMillis();
                        selectionTime = selectionEnd - selectionStart;
                        log.logInfo("Index selection of " + newDHTChunk.indexCount() + " words [" + newDHTChunk.firstContainer().wordHash() + " .. " + newDHTChunk.lastContainer().wordHash() + "]" +
                                " in " +
                                (selectionTime / 1000) + " seconds (" +
                                (1000 * newDHTChunk.indexCount() / (selectionTime+1)) + " words/s)");                     
                    }
                    
                    // query status of old worker thread
                    if (worker != null) {
                        this.status = "Finished: Selecting chunk " + iteration;
                        worker.join();
                        if (worker.dhtChunk.getStatus() != plasmaDHTChunk.chunkStatus_COMPLETE) {
                            // if the transfer failed we abort index transfer now
                            this.status = "Aborted because of Transfer error:\n" + worker.dhtChunk.getStatus();
                            
                            // abort index transfer
                            return;
                        } else {
                            /* 
                             * If index transfer was done successfully we close all remaining open
                             * files that belong to the old index chunk and handover a new chunk
                             * to the transfer thread.
                             * Addintionally we recalculate the chunk size to optimize performance
                             */
                            
                            this.chunkSize = worker.dhtChunk.indexCount();
                            long transferTime = worker.getTransferTime();
                            //TODO: only increase chunk Size if there is free memory left on the server
                            
                            // we need aprox. 73Byte per IndexEntity and an average URL length of 32 char
                            //if (ft.freeMemory() < 73*2*100)                                                        
                            if (transferTime > 60*1000) {
                                if (chunkSize>200) chunkSize-=100;
                            } else if (selectionTime < transferTime){
                                this.chunkSize +=100;
                                //chunkSize+=50;
                            } else if (selectionTime >= selectionTime){
                                if (chunkSize>200) chunkSize-=100;
                            }
                            
                            selectionStart = System.currentTimeMillis();
                            
                            // deleting transfered words from index
                            if (delete) {
                                this.status = "Running: Deleting chunk " + iteration;
                                transferedEntryCount += oldDHTChunk.indexCount();
                                transferedContainerCount += oldDHTChunk.containerSize();
                                int urlReferences = oldDHTChunk.deleteTransferIndexes();
                                log.logFine("Deleted from " + oldDHTChunk.containerSize() + " transferred RWIs locally " + urlReferences + " URL references");
                            } else {
                                transferedEntryCount += oldDHTChunk.indexCount();
                                transferedContainerCount += oldDHTChunk.containerSize();
                            }
                            oldDHTChunk = null;
                        }
                        this.worker = null;
                    }
                    
                    // handover chunk to transfer worker
                    if ((newDHTChunk != null) &&
                        (newDHTChunk.containerSize() > 0) ||
                        (newDHTChunk.getStatus() == plasmaDHTChunk.chunkStatus_FILLED)) {
                        worker = new plasmaDHTTransfer(log, seed, newDHTChunk, gzipBody4Transfer, timeout4Transfer, 5);
                        worker.start();
                    }
                }
                
                // if we reach this point we were aborted by the user or by server shutdown
                if (sb.wordIndex.size() > 0) this.status = "aborted";
            } catch (Exception e) {
                this.status = "Error: " + e.getMessage();
                log.logWarning("Index transfer to peer " + seed.getName() + ":" + seed.hash + " failed:'" + e.getMessage() + "'",e);
                
            } finally {
                if (worker != null) {
                    worker.stopIt();
                    try {worker.join();}catch(Exception e){}
                    // worker = null;
                }
                
                //plasmaWordIndexDistribution.paused = false;
            }
        }

    
}
