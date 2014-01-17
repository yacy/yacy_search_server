/**
 *  VerticalPartition
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

package net.yacy.cora.federate.yacy;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;

/**
 * calculate the DHT position for horizontal and vertical performance scaling:
 * horizontal: scale with number of words
 * vertical: scale with number of references for every word
 * The vertical scaling is selected using the corresponding reference hash, the url hash
 * This has the effect that every vertical position accumulates references for the same url
 * and the urls are not spread over all positions of the DHT.
 */
public class Distribution {
    
    private final int verticalPartitionExponent;
    private final int shiftLength;
    private final int partitionCount;
    private final long partitionSize;
    private final long partitionMask;
    
    /**
     * 
     * @param verticalPartitionExponent, the number of partitions should be computed with partitions = 2**n, n = scaling factor
     */
    public Distribution(int verticalPartitionExponent) {        
        // the partition exponent is the number of bits that we use for the partition
        this.verticalPartitionExponent = verticalPartitionExponent;
        
        // number of partitions that is possible for the given number of partition exponent bits
        this.partitionCount = 1 << this.verticalPartitionExponent;
        
        // we use Long.SIZE - 1 as bitlength since we use only the 63 bits of 0..Long.MAX_VALUE
        this.shiftLength = Long.SIZE - 1 - this.verticalPartitionExponent;
        
        // the partition size is the cardinal number of possible hash positions for each segment of the DHT
        this.partitionSize = 1L << this.shiftLength;
        
        // the partition mask is a bitmask for each partition
        this.partitionMask = this.partitionSize - 1L;
    }

    public int verticalPartitions() {
        return this.partitionCount;
    }
    
    /**
     * the horizontal DHT position uses simply the ordering on hashes, the base 64 order to assign a cardinal
     * in the range of 0..Long.MAX_VALUE to the word.
     * @param wordHash
     * @return
     */
    public final static long horizontalDHTPosition(byte[] wordHash) {
        assert wordHash != null;
        assert wordHash[2] != '@';
        return Base64Order.enhancedCoder.cardinal(wordHash);
    }

    /**
     * the horizontal DHT distance is the cardinal number between the cardinal position of the hashes of two objects in the DHT
     * Since the DHT is closed at the end, a cardinal at the high-end of 0..Long.MAX_VALUE can be very close to a low cardinal number.
     * @param from the start DHT position as word hash
     * @param to the end DHT position as word hash
     * @return the distance of two positions. The maximal distance is Long.MAX_VALUE / 2
     */
    public final static long horizontalDHTDistance(final byte[] from, final byte[] to) {
        // the dht distance is a positive value between 0 and 1
        // if the distance is small, the word more probably belongs to the peer
        final long toPos = horizontalDHTPosition(to);
        final long fromPos = horizontalDHTPosition(from);
        return horizontalDHTDistance(fromPos, toPos);
    }

    /**
     * the horizontalDHTDistance computes the closed-at-the-end ordering of two cardinal DHT positions
     * @param fromPos the start DHT position as cardinal of the word hash
     * @param toPos the end DHT position as cardinal of the word hash
     * @return the distance of two positions. The maximal distance is Long.MAX_VALUE / 2
     */
    public final static long horizontalDHTDistance(final long fromPos, final long toPos) {
        return (toPos >= fromPos) ? toPos - fromPos : (Long.MAX_VALUE - fromPos) + toPos + 1;
    }

    /**
     * the reverse function to horizontalDHTPosition
     * This is a bit fuzzy since the horizontalDHTPosition cannot represent all 72 bits of the word hash (Yes, its a HASH!)
     * @param l the cardinal position in the DHT
     * @return the abstract/computed word of the cardinal.
     */
    public final static byte[] positionToHash(final long l) {
        // transform the position of a peer position into a close peer hash
        byte[] h = Base64Order.enhancedCoder.uncardinal(l);
        assert h.length == 12;
        return h;
    }

    /**
     * the partition size is (Long.MAX + 1) / 2 ** e == 2 ** (63 - e)
     * compute the position using a specific fragment of the word hash and the url hash:
     * - from the word hash take the 63 - <partitionExponent> lower bits
     * - from the url hash take the <partitionExponent> higher bits
     * in case that the partitionExpoent is 1, only one bit is taken from the urlHash,
     * which means that the partition is in two parts.
     * With partitionExponent = 2 it is divided in four parts and so on.
     * @param wordHash
     * @param urlHash
     * @return
     */
    public final long verticalDHTPosition(final byte[] wordHash, final String urlHash) {
        // this creates 1^^e different positions for the same word hash (according to url hash)
        return (Distribution.horizontalDHTPosition(wordHash) & partitionMask) | (Distribution.horizontalDHTPosition(ASCII.getBytes(urlHash)) & ~partitionMask);
    }
    
    /**
     * compute a vertical DHT position for a given word
     * This is used when a word is searched and the peers holding the word must be computed
     * @param wordHash, the hash of the word
     * @param verticalPosition (0 <= verticalPosition <  verticalPartitions())
     * @return a number that can represents a position and can be computed to a word hash again
     */
    public final long verticalDHTPosition(final byte[] wordHash, final int verticalPosition) {
        assert verticalPosition >= 0 && verticalPosition < verticalPartitions();
        long verticalMask = ((long) verticalPosition) << this.shiftLength; // don't remove the cast! it will become an integer result which is wrong.
        return (Distribution.horizontalDHTPosition(wordHash) & partitionMask) | verticalMask;
    }
    
    /**
     * compute the vertical position of a url hash. Thats the same value as second parameter in verticalDHTPosition/2
     * @param urlHash
     * @return a number from 0..verticalPartitions()
     */
    public final int verticalDHTPosition(final byte[] urlHash) {
        int vdp = (int) (Distribution.horizontalDHTPosition(urlHash) >> this.shiftLength); // take only the top-<partitionExponent> bits
        assert vdp >= 0;
        assert vdp < this.partitionCount;
        return vdp;
    }
    
    public static void main(String[] args) {
        // java -classpath classes de.anomic.yacy.yacySeed hHJBztzcFn76
        // java -classpath classes de.anomic.yacy.yacySeed hHJBztzcFG76 M8hgtrHG6g12 3
        // test the DHT position calculation
        byte[] wordHash = UTF8.getBytes("hHJBztzcFn76");
        long   dhtl;
        int partitionExponent = 4;
        Distribution partition = new Distribution(partitionExponent);
        dhtl = Distribution.horizontalDHTPosition(wordHash);
        System.out.println("DHT Long                = " + dhtl);
        System.out.println("DHT as b64 from Long    = " + ASCII.String(Distribution.positionToHash(dhtl)));
        
        System.out.println("all " + partition.verticalPartitions()  + " DHT positions from long   : ");
        for (int i = 0; i < partition.verticalPartitions(); i++) {
            long l = partition.verticalDHTPosition(wordHash, i);
            System.out.println(ASCII.String(Distribution.positionToHash(l)));
        }
        System.out.println();
        

        long c1 = Base64Order.enhancedCoder.cardinal("AAAAAAAAAAAA".getBytes());
        System.out.println(ASCII.String(Base64Order.enhancedCoder.uncardinal(c1)));
        long c2 = Base64Order.enhancedCoder.cardinal("____________".getBytes());
        System.out.println(ASCII.String(Base64Order.enhancedCoder.uncardinal(c2)));
    }

}
