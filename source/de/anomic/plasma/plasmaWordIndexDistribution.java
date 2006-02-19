//plasmaWordIndexDistribution.java 
//-------------------------------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import java.util.Enumeration;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.server.logging.serverLog;

public final class plasmaWordIndexDistribution {

    // distributes parts of the index to other peers
    // stops as soon as an error occurrs

    private int indexCount;
    private int juniorPeerCount, seniorPeerCount;
    private long maxTime;

    private final plasmaURLPool urlPool;
    private final plasmaWordIndex wordIndex;
    final serverLog log;
    boolean paused = false;
    private boolean enabled;
    private boolean enabledWhileCrawling;
    private boolean closed;
    private boolean gzipBody4Distribution;
    private int timeout4Distribution;

    public plasmaWordIndexDistribution(
            plasmaURLPool urlPool, 
            plasmaWordIndex wordIndex, 
            serverLog log,
            boolean enable, 
            boolean enabledWhileCrawling, 
            boolean gzipBody, 
            int timeout
    ) {
        this.urlPool = urlPool;
        this.wordIndex = wordIndex;
        this.enabled = enable;
        this.enabledWhileCrawling = enabledWhileCrawling;
        this.log = log;
        this.closed = false;
        setCounts(100 /*indexCount*/,  1 /*juniorPeerCount*/, 3 /*seniorPeerCount*/, 8000);
        this.gzipBody4Distribution = gzipBody;
        this.timeout4Distribution = timeout;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public void enableWhileCrawling() {
        this.enabledWhileCrawling = true;
    }

    public void disableWhileCrawling() {
        this.enabledWhileCrawling = false;
    }

    public void close() {
        closed = true;

    }

    private boolean isClosed() {
        return (this.closed || Thread.currentThread().isInterrupted());
    }
    
    public boolean job() {

        if (this.closed) {
            log.logFine("no DHT distribution: closed");
            return false;
        }
        if (yacyCore.seedDB == null) {
            log.logFine("no DHT distribution: seedDB == null");
            return false;
        }
        if (yacyCore.seedDB.mySeed == null) {
            log.logFine("no DHT distribution: mySeed == null");
            return false;
        }
        if (yacyCore.seedDB.mySeed.isVirgin()) {
            log.logFine("no DHT distribution: status is virgin");
            return false;
        }
        if (!(enabled)) {
            log.logFine("no DHT distribution: not enabled");
            return false;
        }
        if (paused) {
            log.logFine("no DHT distribution: paused");
            return false;            
        }
        if (urlPool.loadedURL.size() < 10) {
            log.logFine("no DHT distribution: loadedURL.size() = " + urlPool.loadedURL.size());
            return false;
        }
        if (wordIndex.size() < 100) {
            log.logFine("no DHT distribution: not enough words - wordIndex.size() = " + wordIndex.size());
            return false;
        }
        if ((!enabledWhileCrawling) && (urlPool.noticeURL.stackSize() > 0)) {
            log.logFine("no DHT distribution: crawl in progress - noticeURL.stackSize() = " + urlPool.noticeURL.stackSize());
            return false;
        }

        // do the transfer
        int peerCount = (yacyCore.seedDB.mySeed.isJunior()) ? juniorPeerCount : seniorPeerCount;
        long starttime = System.currentTimeMillis();
        int transferred = performTransferIndex(indexCount, peerCount, true);

        if (transferred <= 0) {
            log.logFine("no word distribution: transfer failed");
            return false;
        }

        // adopt transfer count
        if ((System.currentTimeMillis() - starttime) > (maxTime * peerCount))
            indexCount--;
        else
            indexCount++;
        if (indexCount < 50) indexCount = 50;
        
        // show success
        return true;

    }

    public void setCounts(int indexCount, int juniorPeerCount, int seniorPeerCount, long maxTimePerTransfer) {
        this.maxTime = maxTimePerTransfer;
        this.indexCount = indexCount;
        if (indexCount < 30) indexCount = 30;
        this.juniorPeerCount = juniorPeerCount;
        this.seniorPeerCount = seniorPeerCount;
    }

    public int performTransferIndex(int indexCount, int peerCount, boolean delete) {
        if ((yacyCore.seedDB == null) || (yacyCore.seedDB.sizeConnected() == 0)) return -1;
        
        // collect index
        plasmaDHTChunk dhtChunk = new plasmaDHTChunk(this.log, this.wordIndex, this.urlPool.loadedURL, 30, indexCount);

        try {
            // find start point for DHT-selection
            String keyhash = dhtChunk.lastContainer().wordHash(); // DHT targets must have greater hashes

            // find a list of DHT-peers
            yacySeed[] seeds = new yacySeed[peerCount + 10];
            int hc0 = 0;
            double ownDistance = Math.min(yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, dhtChunk.firstContainer().wordHash()), yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, dhtChunk.lastContainer().wordHash()));
            double maxDistance = Math.min(ownDistance, 0.4);
            synchronized (yacyCore.dhtAgent) {
                double avdist;
                Enumeration e = yacyCore.dhtAgent.getAcceptRemoteIndexSeeds(keyhash);
                while ((e.hasMoreElements()) && (hc0 < seeds.length)) {
                    seeds[hc0] = (yacySeed) e.nextElement();
                    if (seeds[hc0] != null) {
                        avdist = Math.max(yacyDHTAction.dhtDistance(seeds[hc0].hash, dhtChunk.firstContainer().wordHash()), yacyDHTAction.dhtDistance(seeds[hc0].hash, dhtChunk.lastContainer().wordHash()));
                        if (avdist < maxDistance) {
                            log.logInfo("Selected " + ((hc0 < peerCount) ? "primary" : "reserve") + " DHT target peer " + seeds[hc0].getName() + ":" + seeds[hc0].hash + ", distance = " + avdist);
                            hc0++;
                        }
                    }
                }
                e = null; // finish enumeration
            }

            if (hc0 < peerCount) {
                log.logWarning("found not enough (" + hc0 + ") peers for distribution");
                return -1;
            }
            
            // send away the indexes to all these indexes
            String error;
            String peerNames = "";
            long start;
            int hc1 = 0;
            for (int i = 0; i < hc0; i++) {
                if (this.isClosed()) {
                    this.log.logSevere("Index distribution interrupted by close, nothing deleted locally.");
                    return -1; // interrupted
                }
                start = System.currentTimeMillis();
                error = yacyClient.transferIndex(
                        seeds[i],
                        dhtChunk.containers(),
                        dhtChunk.urlCacheMap(),
                        this.gzipBody4Distribution,
                        this.timeout4Distribution);
                if (error == null) {
                    this.log.logInfo("Index transfer of " + indexCount + " words [" + dhtChunk.firstContainer().wordHash() + " .. " + dhtChunk.lastContainer().wordHash() + "] to peer " + seeds[i].getName() + ":" + seeds[i].hash + " in " + ((System.currentTimeMillis() - start) / 1000)
                            + " seconds successfull (" + (1000 * indexCount / (System.currentTimeMillis() - start + 1)) + " words/s)");
                    peerNames += ", " + seeds[i].getName();
                    hc1++;
                } else {
                    this.log.logWarning("Index transfer to peer " + seeds[i].getName() + ":" + seeds[i].hash + " failed:'" + error + "', disconnecting peer");
                    yacyCore.peerActions.peerDeparture(seeds[i]);
                }
                if (hc1 >= peerCount) break;
            }
            if (peerNames.length() > 0) peerNames = peerNames.substring(2); // remove comma
            
            // clean up and finish with deletion of indexes
            if (hc1 >= peerCount) {
                // success
                if (delete) {
                    int deletedURLs = dhtChunk.deleteTransferIndexes();
                    this.log.logFine("Deleted from " + dhtChunk.containers().length + " transferred RWIs locally, removed " + deletedURLs + " URL references");
                    return indexCount;
                } 
                return indexCount;
            }
            this.log.logSevere("Index distribution failed. Too few peers (" + hc1 + ") received the index, not deleted locally.");
            return -1;
        } finally {
            if (dhtChunk.containers() != null) {
                // simply close the indexEntities
                closeTransferIndexes(dhtChunk.containers());                
            }
        }
    }

    void closeTransferIndexes(plasmaWordIndexEntryContainer[] indexContainers) {
        for (int i = 0; i < indexContainers.length; i++) {
            indexContainers[i] = null;
        }
    }

}
