// plasmaDHTTransfer.java 
// ------------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005, 2006
// 
// This class was provided by Martin Thelian
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
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class plasmaDHTTransfer extends Thread {

    // connection properties
    private boolean gzipBody4Transfer = false;
    private int timeout4Transfer = 60000;

    // status fields
    private boolean stopped = false;
    private long transferTime = 0;
    private int transferStatus = plasmaDHTChunk.chunkStatus_UNDEFINED;
    private String transferStatusMessage = "";

    // delivery destination
    private yacySeed [] seeds = null;
    private static int seedcount = 0;
    private yacySeed seed = null;

    // word chunk
    plasmaDHTChunk dhtChunk;

    // other fields
    private int maxRetry;
    serverLog log;

    public plasmaDHTTransfer(serverLog log, yacySeed seed, plasmaDHTChunk dhtChunk, boolean gzipBody, int timeout, int retries) {
        super(new ThreadGroup("TransferIndexThreadGroup"), "TransferIndexWorker_" + seed.getName());
        this.log = log;
        this.gzipBody4Transfer = gzipBody;
        this.timeout4Transfer = timeout;
        this.dhtChunk = dhtChunk;
        this.maxRetry = retries;
        seeds = new yacySeed[1];
        seeds[0] = seed;
    }
    
    public plasmaDHTTransfer(serverLog log, yacySeed [] seeds, plasmaDHTChunk dhtChunk, boolean gzipBody, int timeout, int retries) {
        super(new ThreadGroup("TransferIndexThreadGroup"), "TransferIndexWorker_" + seedcount);
        this.log = log;
        this.gzipBody4Transfer = gzipBody;
        this.timeout4Transfer = timeout;
        this.dhtChunk = dhtChunk;
        this.maxRetry = retries;
        this.seeds = seeds;
    }

    public void run() {
        while (getStatus() != plasmaDHTChunk.chunkStatus_COMPLETE && seedcount < seeds.length)try {
            seed = seeds[seedcount++];
            uploadIndex();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {

        }
    }
    
    private boolean isAborted() {
        if (stopped || Thread.currentThread().isInterrupted()) {
            return true;
        }
        return false;
    }

    public void stopIt() {
        this.stopped = true;
    }

    public long getTransferTime() {
        return this.transferTime;
    }
    
    public int getStatus() {
        return transferStatus;
    }

    public String getStatusMessage() {
        return transferStatusMessage;
    }
    
    public void uploadIndex() throws InterruptedException {

        /* loop until we 
         * - have successfully transfered the words list or 
         * - the retry counter limit was exceeded
         */
        transferStatus = plasmaDHTChunk.chunkStatus_RUNNING;
        long retryCount = 0, start = System.currentTimeMillis();
        while (true) {
            // testing if we were aborted
            if (isAborted()) return;

            // transfering seleted words to remote peer
            transferStatusMessage = "Running: Transfering chunk to target " + seed.hash + "/" + seed.getName();
            String error = yacyClient.transferIndex(seed, dhtChunk.containers(), dhtChunk.urlCacheMap(), gzipBody4Transfer, timeout4Transfer);
            if (error == null) {
                // words successfully transfered
                transferTime = System.currentTimeMillis() - start;
                this.log.logInfo("Index transfer of " + dhtChunk.indexCount() + " words [" + dhtChunk.firstContainer().wordHash() + " .. " + dhtChunk.lastContainer().wordHash() + "]" + " to peer " + seed.getName() + ":" + seed.hash + " in " + (transferTime / 1000) + " seconds successful ("
                                + (1000 * dhtChunk.indexCount() / (transferTime + 1)) + " words/s)");
                retryCount = 0;
                transferStatusMessage = "Finished: Transfer of chunk to target " + seed.hash + "/" + seed.getName();
                transferStatus = plasmaDHTChunk.chunkStatus_COMPLETE;
                break;
            } else {
                // words transfer failed

                // inc retry counter
                retryCount++;

                // testing if we were aborted ...
                if (isAborted()) return;

                // we have lost the connection to the remote peer. Adding peer to disconnected list
                this.log.logWarning("Index transfer to peer " + seed.getName() + ":" + seed.hash + " failed:'" + error + "', disconnecting peer");
                yacyCore.peerActions.peerDeparture(seed);

                // if the retry counter limit was not exceeded we'll retry it in a few seconds
                transferStatusMessage = "Disconnected peer: " + ((retryCount > 5) ? error + ". Transfer aborted" : "Retry " + retryCount);
                if (retryCount > maxRetry) {
                    transferStatus = plasmaDHTChunk.chunkStatus_FAILED;
                    return;
                }
                Thread.sleep(retryCount * 5000);

                /* loop until 
                 * - we have successfully done a peer ping or 
                 * - the retry counter limit was exceeded
                 */
                while (true) {
                    // testing if we were aborted ...
                    if (isAborted())
                        return;

                    // doing a peer ping to the remote seed
                    int added = yacyClient.publishMySeed(seed.getAddress(), seed.hash);
                    if (added < 0) {
                        // inc. retry counter
                        retryCount++;
                        transferStatusMessage = "Disconnected peer: Peer ping failed. " + ((retryCount > 5) ? "Transfer aborted." : "Retry " + retryCount);
                        if (retryCount > maxRetry) return;
                        Thread.sleep(retryCount * 5000);
                        continue;
                    } else {
                        yacyCore.seedDB.getConnected(seed.hash);
                        transferStatusMessage = "running";
                        break;
                    }
                }
            }
        }
    }

    public static void setSeedcount(int seedcount) {
        plasmaDHTTransfer.seedcount = seedcount;
    }

    public static int getSeedcount() {
        return seedcount;
    }
}
