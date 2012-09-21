/**
 *  HorizontalPartition
 *  Copyright 2009 by Michael Peter Christen
 *  First released 28.01.2009 at http://yacy.net
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

package net.yacy.cora.services.federated.yacy.dht;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.order.Base64Order;

/**
 * A flat word partition scheme is a metric for words on the range of a distributed
 * hash table. The dht is reflected by a 0..Long.MAX_VALUE integer range, each word gets
 * a number on that range. To compute a number, the hash representation is used to compute
 * the hash position from the first 63 bits of the b64 hash string.
 */
public class HorizontalPartition implements Partition {

    public static final HorizontalPartition std = new HorizontalPartition();

    public HorizontalPartition() {
        // nothing to initialize
    }

    @Override
    public int verticalPartitions() {
        return 1;
    }

    @Override
    public long dhtPosition(byte[] wordHash, String urlHash) {
        // the urlHash has no relevance here
        // normalized to Long.MAX_VALUE
        return Base64Order.enhancedCoder.cardinal(wordHash);
    }

    public final long dhtDistance(final byte[] from, final String urlHash, final byte[] to) {
        // the dht distance is a positive value between 0 and 1
        // if the distance is small, the word more probably belongs to the peer
        assert to != null;
        assert from != null;
        final long toPos = dhtPosition(to, null);
        final long fromPos = dhtPosition(from, urlHash);
        return dhtDistance(fromPos, toPos);
    }

    @Override
    public long dhtPosition(byte[] wordHash, int verticalPosition) {
        return dhtPosition(wordHash, null);
    }

    @Override
    public long[] dhtPositions(byte[] wordHash) {
        long[] l = new long[1];
        l[1] = dhtPosition(wordHash, null);
        return l;
    }

    @Override
    public int verticalPosition(byte[] urlHash) {
        return 0; // this is not a method stub, this is actually true for all FlatWordPartitionScheme
    }

    public final static long dhtDistance(final long fromPos, final long toPos) {
        return (toPos >= fromPos) ?
                toPos - fromPos :
                (Long.MAX_VALUE - fromPos) + toPos + 1;
    }

    public static byte[] positionToHash(final long l) {
        // transform the position of a peer position into a close peer hash
        String s = ASCII.String(Base64Order.enhancedCoder.uncardinal(l));
        while (s.length() < 12) s += "A";
        return ASCII.getBytes(s);
    }


}
