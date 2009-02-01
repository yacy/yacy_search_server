// plasmaDHTTransfer.java 
// ------------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.plasma;

import java.util.HashMap;

import de.anomic.kelondro.util.Log;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyPeerActions;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;

public class plasmaDHTTransfer extends Thread {

    public static final int TRANSFER_MODE_DISTRIBUTION = 0;
    public static final int TRANSFER_MODE_FLUSH = 1;
    
    // connection properties
    private boolean gzipBody4Transfer = false;
    private int timeout4Transfer;    

    // status fields
    private boolean stopped = false;
    private long transferTime = 0;
    private long payloadSize = 0;
    private int transferStatus = plasmaDHTChunk.chunkStatus_UNDEFINED;
    private String transferStatusMessage = "";

    // delivery destination
    private yacySeed seed = null;

    // word chunk
    plasmaDHTChunk dhtChunk;

    // other fields
    private final yacySeedDB seedDB;
    private final yacyPeerActions peerActions;
    Log log;

    public plasmaDHTTransfer(
            final Log log,
            final yacySeedDB seedDB,
            final yacyPeerActions peerActions,
            final yacySeed destSeed, 
            final plasmaDHTChunk dhtChunk, 
            final boolean gzipBody, 
            final int timeout
    ) {
        super(new ThreadGroup("TransferIndexThreadGroup"), "TransferIndexWorker_" + destSeed.getName());
        this.log = log;
        this.seedDB = seedDB;
        this.peerActions = peerActions;
        this.gzipBody4Transfer = gzipBody;
        this.timeout4Transfer = timeout;
        this.dhtChunk = dhtChunk;
        this.seed = destSeed;
    }
    
    public void run() {
        try {
            this.uploadIndex();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private boolean isAborted() {
        if (this.stopped || Thread.currentThread().isInterrupted()) {
            this.transferStatus = plasmaDHTChunk.chunkStatus_INTERRUPTED;
            this.transferStatusMessage = "aborted"; 
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
    
    public long getPayloadSize() {
        return this.payloadSize;
    }
    
    public int getStatus() {
        return this.transferStatus;
    }

    public String getStatusMessage() {
        return this.transferStatusMessage;
    }
    
    public yacySeed getSeed() {
        return this.seed;
    }
    
    public void uploadIndex() throws InterruptedException {

        /* loop until we 
         * - have successfully transfered the words list or 
         * - the retry counter limit was exceeded
         */
        this.transferStatus = plasmaDHTChunk.chunkStatus_RUNNING;
        final long start = System.currentTimeMillis();
        // testing if we were aborted
        if (this.isAborted()) return;

        // transferring selected words to remote peer
        this.transferStatusMessage = "Running: Transfering chunk to target " + this.seed.hash + "/" + this.seed.getName();
        final HashMap<String, Object> result = yacyClient.transferIndex(this.seedDB, this.seed, this.dhtChunk.containers(), this.dhtChunk.urlCacheMap(), this.gzipBody4Transfer, this.timeout4Transfer);
        final String error = (String) result.get("result");
        if (error == null) {
            // words successfully transfered
            this.transferTime = System.currentTimeMillis() - start;                
            this.payloadSize = ((Integer)result.get("payloadSize")).intValue();
            
            this.log.logInfo("Index transfer of " + this.dhtChunk.indexCount() + 
                             " entries " + this.dhtChunk.containerSize() +
                             " words [" + this.dhtChunk.firstContainer().getWordHash() + " .. " + this.dhtChunk.lastContainer().getWordHash() + "]" + 
                             " and " + this.dhtChunk.urlCacheMap().size() + " URLs" +
                             " to peer " + this.seed.getName() + ":" + this.seed.hash + 
                             " in " + (this.transferTime / 1000) + 
                             " seconds successful ("  + (1000 * this.dhtChunk.indexCount() / (this.transferTime + 1)) + 
                             " words/s, " + this.payloadSize + " Bytes)");
            
            // if the peer has set a pause time and we are in flush mode (index transfer)
            // then we pause for a while now
            this.transferStatusMessage = "Finished: Transfer of chunk to target " + this.seed.hash + "/" + this.seed.getName();
            
            // transfer of chunk finished
            this.transferStatus = plasmaDHTChunk.chunkStatus_COMPLETE;
            return;
        } 
        
        if (this.isAborted()) return;                      

        if (error.equals("busy")) {
            this.transferStatusMessage = "Peer " + this.seed.getName() + ":" + this.seed.hash + " is busy.";
            this.log.logInfo(this.transferStatusMessage);              
        } else {
            this.transferStatusMessage = "Transfer to peer " + this.seed.getName() + ":" + this.seed.hash + " failed:'" + error + "', Trying to reconnect ...";
            
            // force disconnection of peer
            peerActions.peerDeparture(this.seed, "DHT Transfer: " + this.transferStatusMessage);
            this.log.logWarning(this.transferStatusMessage);

        }
    }
}
