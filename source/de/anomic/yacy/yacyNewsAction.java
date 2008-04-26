// yacyNewsAction.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
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
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.io.IOException;

import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;

public class yacyNewsAction implements yacyPeerAction {

    yacyNewsPool pool;

    public yacyNewsAction(yacyNewsPool pool) {
        this.pool = pool;
    }

    public void processPeerArrival(yacySeed peer, boolean direct) {
        String recordString = peer.get("news", null);
        //System.out.println("### triggered news arrival from peer " + peer.getName() + ", news " + ((recordString == null) ? "empty" : "attached"));
        if ((recordString == null) || (recordString.length() == 0)) return;
        String decodedString = de.anomic.tools.crypt.simpleDecode(recordString, "");
        yacyNewsRecord record = yacyNewsRecord.newRecord(decodedString);
        if (record != null) {
            RSSFeed.channels(yacyCore.channelName).addMessage(new RSSMessage("Peer Arrival", peer.getName() + " has joined the network"));
            //System.out.println("### news arrival from peer " + peer.getName() + ", decoded=" + decodedString + ", record=" + recordString + ", news=" + record.toString());
            String cre1 = (String) serverCodings.string2map(decodedString, ",").get("cre");
            String cre2 = (String) serverCodings.string2map(record.toString(), ",").get("cre");
            if ((cre1 == null) || (cre2 == null) || (!(cre1.equals(cre2)))) {
                System.out.println("### ERROR - cre are not equal: cre1=" + cre1 + ", cre2=" + cre2);
                return;
            }
            try {
                synchronized (pool) {this.pool.enqueueIncomingNews(record);}
            } catch (IOException e) {
                serverLog.logSevere("YACY", "processPeerArrival", e);
            }
        }
    }

    public void processPeerDeparture(yacySeed peer) {
        RSSFeed.channels(yacyCore.channelName).addMessage(new RSSMessage("Peer Departure", peer.getName() + " has left the network"));
    }

    public void processPeerPing(yacySeed peer) {
        processPeerArrival(peer, true);
    }

}