// FlatWordPartitionScheme.java 
// ------------------------------
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

import java.util.Random;
import java.util.TreeMap;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.HandleMap;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.util.MemoryControl;
import de.anomic.yacy.yacySeed;

/**
 * A flat word partition scheme is a metric for words on the range of a distributed
 * hash table. The dht is reflected by a 0..Long.MAX_VALUE integer range, each word gets
 * a number on that range. To compute a number, the hash representation is used to compute
 * the hash position from the first 63 bits of the b64 hash string.
 */
public class FlatWordPartitionScheme implements PartitionScheme {

    public static final FlatWordPartitionScheme std = new FlatWordPartitionScheme();
    
    public FlatWordPartitionScheme() {
        // nothing to initialize
    }

    public int verticalPartitions() {
        return 1;
    }
    
    public long dhtPosition(byte[] wordHash, String urlHash) {
        // the urlHash has no relevance here
        // normalized to Long.MAX_VALUE
        return Base64Order.enhancedCoder.cardinal(wordHash);
    }

    public final long dhtDistance(final byte[] word, final String urlHash, final yacySeed peer) {
        return dhtDistance(word, urlHash, UTF8.getBytes(peer.hash));
    }
    
    private final long dhtDistance(final byte[] from, final String urlHash, final byte[] to) {
        // the dht distance is a positive value between 0 and 1
        // if the distance is small, the word more probably belongs to the peer
        assert to != null;
        assert from != null;
        final long toPos = dhtPosition(to, null);
        final long fromPos = dhtPosition(from, urlHash);
        return dhtDistance(fromPos, toPos);
    }

    public long dhtPosition(byte[] wordHash, int verticalPosition) {
        return dhtPosition(wordHash, null);
    }

    public long[] dhtPositions(byte[] wordHash) {
        long[] l = new long[1];
        l[1] = dhtPosition(wordHash, null);
        return l;
    }

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
        String s = UTF8.String(Base64Order.enhancedCoder.uncardinal(l));
        while (s.length() < 12) s += "A";
        return UTF8.getBytes(s);
    }
    

    public static void main(String[] args) {
        int count = (args.length == 0) ? 1000000 : Integer.parseInt(args[0]);
        System.out.println("Starting test with " + count + " objects");
        System.out.println("expected  memory: " + (count * 16) + " bytes");
        System.out.println("available memory: " + MemoryControl.available());
        Random r = new Random(0);
        long start = System.currentTimeMillis();

        System.gc(); // for resource measurement
        long a = MemoryControl.available();
        HandleMap idx = new HandleMap(12, Base64Order.enhancedCoder, 4, 150000, "test");
        for (int i = 0; i < count; i++) {
            try {
                idx.inc(FlatWordPartitionScheme.positionToHash(r.nextInt(count)));
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
                break;
            }
        }
        long timek = ((long) count) * 1000L / (System.currentTimeMillis() - start);
        System.out.println("Result HandleMap: " + timek + " inc per second");
        System.gc();
        long memk = a - MemoryControl.available();
        System.out.println("Used Memory: " + memk + " bytes");
        System.out.println("x " + idx.get(FlatWordPartitionScheme.positionToHash(0)));
        idx.close();
        
        r = new Random(0);
        start = System.currentTimeMillis();
        byte[] hash;
        Integer d;
        System.gc(); // for resource measurement
        a = MemoryControl.available();
        TreeMap<byte[], Integer> hm = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        for (int i = 0; i < count; i++) {
            hash = FlatWordPartitionScheme.positionToHash(r.nextInt(count));
            d = hm.get(hash);
            if (d == null) hm.put(hash, 1); else hm.put(hash, d + 1);
        }
        long timej =  ((long) count) * 1000L / (System.currentTimeMillis() - start);
        System.out.println("Result   TreeMap: " + timej + " inc per second");
        System.gc();
        long memj = a - MemoryControl.available();
        System.out.println("Used Memory: " + memj + " bytes");
        System.out.println("x " + hm.get(FlatWordPartitionScheme.positionToHash(0)));
        System.out.println("Geschwindigkeitsfaktor j/k: " + ((float) (10 * timej / timek) / 10.0) + " - je kleiner desto besser fuer kelondro");
        System.out.println("Speicherplatzfaktor    j/k: " + ((float) (10 * memj / memk) / 10.0) + " - je groesser desto besser fuer kelondro");
        System.exit(0);
    }

}
