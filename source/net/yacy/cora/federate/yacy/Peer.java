/**
 *  Peer
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 21.09.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.federate.yacy;

import java.io.Serializable;
import java.util.HashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.Base64Order;

/**
 * new implemenentation of "Seed" objects: Peers.
 * A Peer contains the attributes that a peer wants to show in public.
 * This Class is simply a representation of the XML Schema that is used in the Network.xml interface.
 */
public class Peer extends HashMap<Peer.Schema, String> implements Comparable<Peer>, Serializable  {

    private static final long serialVersionUID = -3279480981385980050L;

    public enum Schema implements Serializable {
        hash,fullname,version,ppm,qph,uptime,links,
        words,rurls,lastseen,sendWords,receivedWords,
        sendURLs,receivedURLs,type,direct,acceptcrawl,dhtreceive,
        nodestate,location,seedurl,age,seeds,connects,address,useragent;
    }
    
    private long time;
    
    public Peer() {
        super();
        this.time = System.currentTimeMillis();
    }
    
    /**
     * Get the number of minutes that the peer which returns the peer list knows when the peer was last seen.
     * This value is only a relative to the moment when another peer was asked for fresh information.
     * To compute an absolute value for last-seen, use the lastseenTime() method.
     * @return time in minutes
     */
    public int lastseen() {
        String x = this.get(Schema.lastseen);
        if (x == null) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(x);
        } catch (final NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
    
    /**
     * get the absolute time when this peer was seen in the network
     * @return time in milliseconds
     */
    public long lastseenTime() {
        return time - lastseen() * 60000;
    }

    /**
     * get the version number of the peer
     * @return
     */
    public float version() {
        String x = this.get(Schema.version); if (x == null) return 0.0f;
        int p = x.indexOf('/'); if (p < 0) return 0.0f;
        x = x.substring(0, p);
        try {
            return Float.parseFloat(x);
        } catch (final NumberFormatException e) {
            return 0.0f;
        }
    }
    
    /**
     * check if the peer supports the solr interface
     * @return true if the peer supports the solr interface
     */
    public boolean supportsSolr() {
        return version() >= 1.041f;
    }

    /**
     * compare the peer to another peer.
     * The comparisment is done using the base 64 order on the peer hash.
     */
    @Override
    public int compareTo(Peer o) {
        String h0 = this.get(Schema.hash);
        String h1 = o.get(Schema.hash);
        return Base64Order.enhancedCoder.compare(ASCII.getBytes(h0), ASCII.getBytes(h1));
    }

    /**
     * get the hash code of the peer.
     * The hash code is a number that has the same order as the peer order.
     */
    @Override
    public int hashCode() {
        String h = this.get(Schema.hash);
        return (int) (Base64Order.enhancedCoder.cardinal(h) >> 32);
    }
    
    /**
     * check if two peers are equal:
     * two peers are equal if they have the same hash.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Peer)) return false;
        String h0 = this.get(Schema.hash);
        String h1 = ((Peer) o).get(Schema.hash);
        return h0.equals(h1);
    }
    
    public static void main(String[] args) {
        String h = "____________";
        long l = Base64Order.enhancedCoder.cardinal(h);
        System.out.println("l = " + l + ", h = " + ((int) (l >> 32)));
        System.out.println("l-maxlong = " + (l - Long.MAX_VALUE) + ", (h-maxint) = " + (((int) (l >> 32)) - Integer.MAX_VALUE));
    }
}
