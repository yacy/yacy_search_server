// VerticalWordPartitionScheme.java 
// --------------------------------
// part of YaCy
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 28.01.2009
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

package de.anomic.yacy.dht;

import net.yacy.cora.document.UTF8;
import de.anomic.yacy.yacySeed;

public class VerticalWordPartitionScheme implements PartitionScheme {
    
    int partitionExponent;
    
    public VerticalWordPartitionScheme(int partitionExponent) {
        this.partitionExponent = partitionExponent;
    }

    public int verticalPartitions() {
        return 1 << partitionExponent;
    }
    
    /**
     * calculate the DHT position for horizontal and vertical performance scaling:
     * horizontal: scale with number of words
     * vertical: scale with number of references for every word
     * The vertical scaling is selected using the corresponding reference hash, the url hash
     * This has the effect that every vertical position accumulates references for the same url
     * and the urls are not spread over all positions of the DHT. To use this effect, the
     * horizontal DHT position must be normed to a 'rest' value of a partition size
     * This method is compatible to the classic DHT computation as always one of the vertical
     * DHT position corresponds to the classic horizontal position. 
     * @param wordHash, the hash of the RWI
     * @param partitions, the number of partitions should be computed with partitions = 2**n, n = scaling factor
     * @param urlHash, the hash of a reference
     * @return a double in the range 0 .. 1.0 (including 0, excluding 1.0), the DHT position
     */
    public final long dhtPosition(final byte[] wordHash, final String urlHash) {
        // this creates 1^^e different positions for the same word hash (according to url hash)
        assert wordHash != null;
        assert urlHash != null;
        if (urlHash == null || partitionExponent < 1) return FlatWordPartitionScheme.std.dhtPosition(wordHash, null);
        // the partition size is (Long.MAX + 1) / 2 ** e == 2 ** (63 - e)
        assert partitionExponent > 0;
        long partitionMask = (1L << (Long.SIZE - 1 - partitionExponent)) - 1L;
        // compute the position using a specific fragment of the word hash and the url hash:
        // - from the word hash take the 63 - <partitionExponent> lower bits
        // - from the url hash take the <partitionExponent> higher bits
        // in case that the partitionExpoent is 1, only one bit is taken from the urlHash,
        // which means that the partition is in two parts.
        // With partitionExponent = 2 it is divided in four parts and so on.
        return (FlatWordPartitionScheme.std.dhtPosition(wordHash, null) & partitionMask) | (FlatWordPartitionScheme.std.dhtPosition(UTF8.getBytes(urlHash), null) & ~partitionMask);
    }
    
    public final long dhtPosition(final byte[] wordHash, final int verticalPosition) {
        assert wordHash != null;
        assert wordHash[2] != '@';
        if (partitionExponent == 0) return FlatWordPartitionScheme.std.dhtPosition(wordHash, null);
        long partitionMask = (1L << (Long.SIZE - 1 - partitionExponent)) - 1L;
        long verticalMask = ((long) verticalPosition) << (Long.SIZE - 1 - partitionExponent); // don't remove the cast! it will become an integer result which is wrong.
        return (FlatWordPartitionScheme.std.dhtPosition(wordHash, null) & partitionMask) | verticalMask;
    }
    
    public final int verticalPosition(final byte[] urlHash) {
        assert urlHash != null;
        if (urlHash == null || partitionExponent < 1) return 0;
        assert partitionExponent > 0;
        return (int) (FlatWordPartitionScheme.std.dhtPosition(urlHash, null) >> (Long.SIZE - 1 - partitionExponent)); // take only the top-<partitionExponent> bits
    }
    
    /**
     * compute all vertical DHT positions for a given word
     * This is used when a word is searched and the peers holding the word must be computed
     * @param wordHash, the hash of the word
     * @param partitions, the number of partitions of the DHT
     * @return a vector of long values, the possible DHT positions
     */
    public final long[] dhtPositions(final byte[] wordHash) {
        assert wordHash != null;
        int partitions = 1 << partitionExponent;
        long[] l = new long[partitions];
        long partitionSize = 1L << (Long.SIZE - 1 - partitionExponent);
        l[0] = FlatWordPartitionScheme.std.dhtPosition(wordHash, null) & (partitionSize - 1L); // this is the lowest possible position
        for (int i = 1; i < partitions; i++) {
            l[i] = l[i - 1] + partitionSize; // no overflow, because we started with the lowest
        }
        return l;
    }
 
    public final long dhtDistance(final byte[] word, final String urlHash, final yacySeed peer) {
        return dhtDistance(word, urlHash, UTF8.getBytes(peer.hash));
    }
    
    private long dhtDistance(final byte[] from, final String urlHash, final byte[] to) {
        // the dht distance is a positive value between 0 and 1
        // if the distance is small, the word more probably belongs to the peer
        assert to != null;
        assert from != null;
        final long toPos = FlatWordPartitionScheme.std.dhtPosition(to, null);
        final long fromPos = dhtPosition(from, urlHash);
        return FlatWordPartitionScheme.dhtDistance(fromPos, toPos);
    }

    public static void main(String[] args) {
        // java -classpath classes de.anomic.yacy.yacySeed hHJBztzcFn76
        // java -classpath classes de.anomic.yacy.yacySeed hHJBztzcFG76 M8hgtrHG6g12 3
        // test the DHT position calculation
        String wordHash = args[0];
        //double dhtd;
        long   dhtl;
        int partitionExponent = 0;
        VerticalWordPartitionScheme partition = new VerticalWordPartitionScheme(0);
        if (args.length == 3) {
            // the horizontal and vertical position calculation
            String urlHash = args[1];
            partitionExponent = Integer.parseInt(args[2]);
            dhtl = partition.dhtPosition(UTF8.getBytes(wordHash), urlHash);
        } else {
            // only a horizontal position calculation
            dhtl = FlatWordPartitionScheme.std.dhtPosition(UTF8.getBytes(wordHash), null);
        }
        //System.out.println("DHT Double              = " + dhtd);
        System.out.println("DHT Long                = " + dhtl);
        System.out.println("DHT as Double from Long = " + ((double) dhtl) / ((double) Long.MAX_VALUE));
        //System.out.println("DHT as Long from Double = " + (long) (Long.MAX_VALUE * dhtd));
        //System.out.println("DHT as b64 from Double  = " + positionToHash(dhtd));
        System.out.println("DHT as b64 from Long    = " + FlatWordPartitionScheme.positionToHash(dhtl));
        
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
        long[] l = partition.dhtPositions(UTF8.getBytes(wordHash));
        for (int i = 0; i < l.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(FlatWordPartitionScheme.positionToHash(l[i]));
        }
        System.out.println();
    }

}
