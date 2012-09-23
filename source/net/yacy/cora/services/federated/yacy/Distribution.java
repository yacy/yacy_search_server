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

package net.yacy.cora.services.federated.yacy;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.order.Base64Order;

/**
 * calculate the DHT position for horizontal and vertical performance scaling:
 * horizontal: scale with number of words
 * vertical: scale with number of references for every word
 * The vertical scaling is selected using the corresponding reference hash, the url hash
 * This has the effect that every vertical position accumulates references for the same url
 * and the urls are not spread over all positions of the DHT. To use this effect, the
 * horizontal DHT position must be normed to a 'rest' value of a partition size.
 * @param wordHash, the hash of the RWI
 * @param urlHash, the hash of a reference
 * @return a double in the range 0 .. 1.0 (including 0, excluding 1.0), the DHT position
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
        assert verticalPartitionExponent > 0;
        this.verticalPartitionExponent = verticalPartitionExponent;
        this.partitionCount = 1 << verticalPartitionExponent;
        this.shiftLength = Long.SIZE - 1 - this.verticalPartitionExponent;
        this.partitionSize = 1L << this.shiftLength;
        this.partitionMask = (1L << shiftLength) - 1L;
    }

    public int verticalPartitions() {
        return 1 << verticalPartitionExponent;
    }
    
    public final static long horizontalDHTPosition(byte[] wordHash) {
        assert wordHash != null;
        return Base64Order.enhancedCoder.cardinal(wordHash);
    }

    public final static long horizontalDHTDistance(final byte[] from, final byte[] to) {
        // the dht distance is a positive value between 0 and 1
        // if the distance is small, the word more probably belongs to the peer
        assert to != null;
        assert from != null;
        final long toPos = horizontalDHTPosition(to);
        final long fromPos = horizontalDHTPosition(from);
        return horizontalDHTDistance(fromPos, toPos);
    }

    public final static long horizontalDHTDistance(final long fromPos, final long toPos) {
        return (toPos >= fromPos) ? toPos - fromPos : (Long.MAX_VALUE - fromPos) + toPos + 1;
    }

    public final static byte[] positionToHash(final long l) {
        // transform the position of a peer position into a close peer hash
        byte[] h = Base64Order.enhancedCoder.uncardinal(l);
        assert h.length == 12;
        return h;
    }

    public final long verticalDHTPosition(final byte[] wordHash, final String urlHash) {
        // this creates 1^^e different positions for the same word hash (according to url hash)
        assert wordHash != null;
        assert urlHash != null;
        if (urlHash == null || verticalPartitionExponent < 1) return Distribution.horizontalDHTPosition(wordHash);
        // the partition size is (Long.MAX + 1) / 2 ** e == 2 ** (63 - e)
        // compute the position using a specific fragment of the word hash and the url hash:
        // - from the word hash take the 63 - <partitionExponent> lower bits
        // - from the url hash take the <partitionExponent> higher bits
        // in case that the partitionExpoent is 1, only one bit is taken from the urlHash,
        // which means that the partition is in two parts.
        // With partitionExponent = 2 it is divided in four parts and so on.
        return (Distribution.horizontalDHTPosition(wordHash) & partitionMask) | (Distribution.horizontalDHTPosition(ASCII.getBytes(urlHash)) & ~partitionMask);
    }
    
    public final long verticalDHTPosition(final byte[] wordHash, final int verticalPosition) {
        assert wordHash != null;
        assert wordHash[2] != '@';
        long verticalMask = ((long) verticalPosition) << this.shiftLength; // don't remove the cast! it will become an integer result which is wrong.
        return (Distribution.horizontalDHTPosition(wordHash) & partitionMask) | verticalMask;
    }
    
    public final int verticalDHTPosition(final byte[] urlHash) {
        assert urlHash != null;
        return (int) (Distribution.horizontalDHTPosition(urlHash) >> this.shiftLength); // take only the top-<partitionExponent> bits
    }
    
    /**
     * compute all vertical DHT positions for a given word
     * This is used when a word is searched and the peers holding the word must be computed
     * @param wordHash, the hash of the word
     * @param partitions, the number of partitions of the DHT
     * @return a vector of long values, the possible DHT positions
     */
    public final long[] verticalDHTPositions(final byte[] wordHash) {
        assert wordHash != null;
        long[] l = new long[this.partitionCount];
        l[0] = Distribution.horizontalDHTPosition(wordHash) & (partitionSize - 1L); // this is the lowest possible position
        for (int i = 1; i < this.partitionCount; i++) {
            l[i] = l[i - 1] + partitionSize; // no overflow, because we started with the lowest
        }
        return l;
    }

    /*
    public static void main(String[] args) {
        long c1 = Base64Order.enhancedCoder.cardinal("AAAAAAAAAAAA".getBytes());
        System.out.println(ASCII.String(Base64Order.enhancedCoder.uncardinal(c1)));
        long c2 = Base64Order.enhancedCoder.cardinal("____________".getBytes());
        System.out.println(ASCII.String(Base64Order.enhancedCoder.uncardinal(c2)));
        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < 10000; i++) {
            long l = r.nextLong();
            byte[] h = positionToHash(l);
            if (l != Base64Order.enhancedCoder.cardinal(h)) System.out.println(l);
        }
    }
    */
    
    public static void main(String[] args) {
        // java -classpath classes de.anomic.yacy.yacySeed hHJBztzcFn76
        // java -classpath classes de.anomic.yacy.yacySeed hHJBztzcFG76 M8hgtrHG6g12 3
        // test the DHT position calculation
        String wordHash = "hHJBztzcFn76";
        //double dhtd;
        long   dhtl;
        int partitionExponent = 0;
        Distribution partition = new Distribution(0);
        if (args.length == 3) {
            // the horizontal and vertical position calculation
            String urlHash = args[1];
            partitionExponent = Integer.parseInt(args[2]);
            dhtl = partition.verticalDHTPosition(UTF8.getBytes(wordHash), urlHash);
        } else {
            // only a horizontal position calculation
            dhtl = Distribution.horizontalDHTPosition(UTF8.getBytes(wordHash));
        }
        //System.out.println("DHT Double              = " + dhtd);
        System.out.println("DHT Long                = " + dhtl);
        System.out.println("DHT as Double from Long = " + ((double) dhtl) / ((double) Long.MAX_VALUE));
        //System.out.println("DHT as Long from Double = " + (long) (Long.MAX_VALUE * dhtd));
        //System.out.println("DHT as b64 from Double  = " + positionToHash(dhtd));
        System.out.println("DHT as b64 from Long    = " + ASCII.String(Distribution.positionToHash(dhtl)));
        
        System.out.print("all " + (1 << partitionExponent) + " DHT positions from doubles: ");
        /*
        
        double[] d = dhtPositionsDouble(wordHash, partitionExponent);
        for (int i = 0; i < d.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(positionToHash(d[i]));
        }
        System.out.println();
        */
        System.out.print("all " + (1 << partitionExponent) + " DHT positions from long   : ");
        long[] l = partition.verticalDHTPositions(UTF8.getBytes(wordHash));
        for (int i = 0; i < l.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(ASCII.String(Distribution.positionToHash(l[i])));
        }
        System.out.println();
    }

}
