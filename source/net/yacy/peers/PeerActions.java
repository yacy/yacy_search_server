// yacyPeerActions.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2005
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

import java.util.Map;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.kelondro.util.MapTools;
import net.yacy.peers.operation.yacyVersion;


public class PeerActions {

    private final SeedDB seedDB;
    private Map<String, String> userAgents;
    public  long disconnects;
    private final NewsPool newsPool;

    public PeerActions(final SeedDB seedDB, final NewsPool newsPool) {
        this.seedDB = seedDB;
        this.newsPool = newsPool;
        this.userAgents = new ConcurrentARC<String, String>(10000, Runtime.getRuntime().availableProcessors() + 1);
        this.disconnects = 0;
    }

    public void close() {
        // the seedDB and newsPool should be cleared elsewhere
        if (this.userAgents != null) this.userAgents.clear();
        this.userAgents = null;
    }

    public boolean connectPeer(final Seed seed, final boolean direct) {
        // store a remote peer's seed
        // returns true if the peer is new and previously unknown
        if (seed == null) {
            Network.log.severe("connect: WRONG seed (NULL)");
            return false;
        }
        final String error = seed.isProper(false);
        if (error != null) {
            Network.log.severe("connect: WRONG seed (" + seed.getName() + "/" + seed.hash + "): " + error);
            return false;
        }
        if ((this.seedDB.mySeedIsDefined()) && (seed.hash.equals(this.seedDB.mySeed().hash))) {
            Network.log.info("connect: SELF reference " + seed.getPublicAddress());
            return false;
        }
        final String peerType = seed.get(Seed.PEERTYPE, Seed.PEERTYPE_VIRGIN);

        if ((peerType.equals(Seed.PEERTYPE_VIRGIN)) || (peerType.equals(Seed.PEERTYPE_JUNIOR))) {
            // reject unqualified seeds
            if (Network.log.isFine()) Network.log.fine("connect: rejecting NOT QUALIFIED " + peerType + " seed " + seed.getName());
            return false;
        }
        if (!(peerType.equals(Seed.PEERTYPE_SENIOR) || peerType.equals(Seed.PEERTYPE_PRINCIPAL))) {
            // reject unqualified seeds
            if (Network.log.isFine()) Network.log.fine("connect: rejecting NOT QUALIFIED " + peerType + " seed " + seed.getName());
            return false;
        }

        final Seed doubleSeed = this.seedDB.lookupByIP(seed.getInetAddress(), seed.getPort(), true, false, false);
        if ((doubleSeed != null) && (doubleSeed.getPort() == seed.getPort()) && (!(doubleSeed.hash.equals(seed.hash)))) {
            // a user frauds with his peer different peer hashes
            if (Network.log.isFine()) Network.log.fine("connect: rejecting FRAUD (double hashes " + doubleSeed.hash + "/" + seed.hash + " on same port " + seed.getPort() + ") peer " + seed.getName());
            return false;
        }

        if (seed.get(Seed.LASTSEEN, "").length() != 14) {
            // hack for peers that do not have a LastSeen date
            seed.setLastSeenUTC();
            if (Network.log.isFine()) Network.log.fine("connect: reset wrong date (" + seed.getName() + "/" + seed.hash + ")");
        }

        // connection time
        final long nowUTC0Time = System.currentTimeMillis(); // is better to have this value in a variable for debugging
        long ctimeUTC0 = seed.getLastSeenUTC();

        if (ctimeUTC0 > nowUTC0Time) {
            // the peer is future-dated, correct it
            seed.setLastSeenUTC();
            ctimeUTC0 = nowUTC0Time;
            assert (seed.getLastSeenUTC() - ctimeUTC0 < 100);
        }
        if (Math.abs(nowUTC0Time - ctimeUTC0) / 1000 / 60 > 60 * 6 ) {
            // the new connection is out-of-age, we reject the connection
            if (Network.log.isFine()) Network.log.fine("connect: rejecting out-dated peer '" + seed.getName() + "' from " + seed.getPublicAddress() + "; nowUTC0=" + nowUTC0Time + ", seedUTC0=" + ctimeUTC0 + ", TimeDiff=" + formatInterval(Math.abs(nowUTC0Time - ctimeUTC0)));
            return false;
        }

        // disconnection time
        long dtimeUTC0;
        final Seed disconnectedSeed = this.seedDB.getDisconnected(seed.hash);
        if (disconnectedSeed == null) {
            dtimeUTC0 = 0; // never disconnected: virtually disconnected maximum time ago
        } else {
            dtimeUTC0 = disconnectedSeed.getLong("dct", 0);
        }

        if (direct) {
            // remember the moment
            // Date applies the local UTC offset, which is wrong
            // we correct that by subtracting the local offset and adding
            // the remote offset.
            seed.setLastSeenUTC();
            seed.setFlagDirectConnect(true);
        } else {
            // set connection flag
            if (Math.abs(nowUTC0Time - ctimeUTC0) > 120000) seed.setFlagDirectConnect(false); // 2 minutes
        }

        // update latest version number
        if (seed.getVersion() > yacyVersion.latestRelease) yacyVersion.latestRelease = seed.getVersion();

        // prepare to update
        if (disconnectedSeed != null) {
            // if the indirect connect aims to announce a peer that we know
            // has been disconnected then we compare the dates:
            // if the new peer has a LastSeen date, and that date is before
            // the disconnection date, then we ignore the new peer
            if (!direct) {
                if (ctimeUTC0 < dtimeUTC0) {
                    // the disconnection was later, we reject the connection
                    if (Network.log.isFine()) Network.log.fine("connect: rejecting disconnected peer '" + seed.getName() + "' from " + seed.getPublicAddress());
                    return false;
                }
            }

            // this is a return of a lost peer
            if (Network.log.isFine()) Network.log.fine("connect: returned KNOWN " + peerType + " peer '" + seed.getName() + "' from " + seed.getPublicAddress());
            this.seedDB.addConnected(seed);
            return true;
        }
        final Seed connectedSeed = this.seedDB.getConnected(seed.hash);
        if (connectedSeed != null) {
            // the seed is known: this is an update
            try {
                // if the old LastSeen date is later then the other
                // info, then we reject the info
                if ((ctimeUTC0 < (connectedSeed.getLastSeenUTC())) && (!direct)) {
                    if (Network.log.isFine()) Network.log.fine("connect: rejecting old info about peer '" + seed.getName() + "'");
                    return false;
                }

                /*if (connectedSeed.getName() != seed.getName()) {
                    // TODO: update seed name lookup cache
                }*/
            } catch (final NumberFormatException e) {
                if (Network.log.isFine()) Network.log.fine("connect: rejecting wrong peer '" + seed.getName() + "' from " + seed.getPublicAddress() + ". Cause: " + e.getMessage());
                return false;
            }
            if (Network.log.isFine()) Network.log.fine("connect: updated KNOWN " + ((direct) ? "direct " : "") + peerType + " peer '" + seed.getName() + "' from " + seed.getPublicAddress());
            this.seedDB.addConnected(seed);
            return true;
        }

        // the seed is new
        if ((this.seedDB.mySeedIsDefined()) && (seed.getIP().equals(this.seedDB.mySeed().getIP()))) {
            // seed from the same IP as the calling client: can be
            // the case if there runs another one over a NAT
            if (Network.log.isFine()) Network.log.fine("connect: saved NEW seed (myself IP) " + seed.getPublicAddress());
        } else {
            // completely new seed
            if (Network.log.isFine()) Network.log.fine("connect: saved NEW " + peerType + " peer '" + seed.getName() + "' from " + seed.getPublicAddress());
        }
        this.seedDB.addConnected(seed);
        return true;
    }

    public boolean peerArrival(final Seed peer, final boolean direct) {
        if (peer == null) return false;
        final boolean res = connectPeer(peer, direct);
        if (res) {
            // perform all actions if peer is effective new
            processPeerArrival(peer);
            EventChannel.channels(EventChannel.PEERNEWS).addMessage(new RSSMessage(peer.getName() + " joined the network", "", ""));
        }
        return res;
    }

    public void peerDeparture(final Seed peer, final String cause) {
        if (peer == null) return;
        // we do this if we did not get contact with the other peer
        if (Network.log.isFine()) Network.log.fine("connect: no contact to a " + peer.get(Seed.PEERTYPE, Seed.PEERTYPE_VIRGIN) + " peer '" + peer.getName() + "' at " + peer.getPublicAddress() + ". Cause: " + cause);
        synchronized (this.seedDB) {
            if (!this.seedDB.hasDisconnected(ASCII.getBytes(peer.hash))) { this.disconnects++; }
            peer.put(Seed.DCT, Long.toString(System.currentTimeMillis()));
            this.seedDB.addDisconnected(peer); // update info
        }
        EventChannel.channels(EventChannel.PEERNEWS).addMessage(new RSSMessage(peer.getName() + " left the network", "", ""));
    }

    public void peerPing(final Seed peer) {
        if (peer == null) return;
        // this is called only if the peer has junior status
        this.seedDB.addPotential(peer);
        // perform all actions
        processPeerArrival(peer);
        EventChannel.channels(EventChannel.PEERNEWS).addMessage(new RSSMessage(peer.getName() + " sent me a ping", "", ""));
    }

    private void processPeerArrival(final Seed peer) {
        final String recordString = peer.get(Seed.NEWS, null);
        //System.out.println("### triggered news arrival from peer " + peer.getName() + ", news " + ((recordString == null) ? "empty" : "attached"));
        if ((recordString == null) || (recordString.isEmpty())) return;
        final String decodedString = net.yacy.utils.crypt.simpleDecode(recordString);
        final NewsDB.Record record = this.newsPool.parseExternal(decodedString);
        if (record != null) {
            //System.out.println("### news arrival from peer " + peer.getName() + ", decoded=" + decodedString + ", record=" + recordString + ", news=" + record.toString());
            final String cre1 = MapTools.string2map(decodedString, ",").get("cre");
            final String cre2 = MapTools.string2map(record.toString(), ",").get("cre");
            if ((cre1 == null) || (cre2 == null) || (!(cre1.equals(cre2)))) {
                System.out.println("### ERROR - cre are not equal: cre1=" + cre1 + ", cre2=" + cre2);
                return;
            }
            try {
                synchronized (this.newsPool) {this.newsPool.enqueueIncomingNews(record);}
            } catch (final Exception e) {
                Network.log.severe("processPeerArrival", e);
            }
        }
    }

    public int sizeConnected() {
        return this.seedDB.sizeConnected();
    }

    public void setUserAgent(final String IP, final String userAgent) {
        if (this.userAgents == null) return; // case can happen during shutdown
        this.userAgents.put(IP, userAgent);
    }

    public String getUserAgent(final String IP) {
        final String userAgent = this.userAgents.get(IP);
        return (userAgent == null) ? "" : userAgent;
    }

    /**
     * Format a time inteval in milliseconds into a String of the form
     * X 'day'['s'] HH':'mm
     */
    public static String formatInterval(final long millis) {
        try {
            final long mins = millis / 60000;

            final StringBuilder uptime = new StringBuilder(40);

            final int uptimeDays  = (int) (Math.floor(mins/1440.0));
            final int uptimeHours = (int) (Math.floor(mins/60.0)%24);
            final int uptimeMins  = (int) mins%60;

            uptime.append(uptimeDays)
                  .append(((uptimeDays == 1)?" day ":" days "))
                  .append((uptimeHours < 10)?"0":"")
                  .append(uptimeHours)
                  .append(':')
                  .append((uptimeMins < 10)?"0":"")
                  .append(uptimeMins);

            return uptime.toString();
        } catch (final Exception e) {
            return "unknown";
        }
    }
}
