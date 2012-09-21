// BinSearch.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 22.11.2005
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

package net.yacy.kelondro.index;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.NaturalOrder;


public final class BinSearch {
    
    private final byte[] chunks;
    private final int    chunksize;
    private final int    count;
    private static final ByteOrder objectOrder = new NaturalOrder(true); // the natural order is much faster than the b64Order
    
    public BinSearch(final byte[] chunks, final int chunksize) {
        this.chunks = chunks;
        this.chunksize = chunksize;
        this.count = chunks.length / chunksize;
    }
    
    public BinSearch(final List<byte[]> chunkList, final int chunksize) {
        byte[][] chunksa = new byte[chunkList.size()][];
        chunksa = chunkList.toArray(chunksa);
        Arrays.sort(chunksa, objectOrder);
        this.chunks = new byte[chunkList.size() * chunksize];
        for (int i = 0; i < chunksa.length; i++) System.arraycopy(chunksa[i], 0, this.chunks, i * chunksize, chunksize);
        this.chunksize = chunksize;
        this.count = chunks.length / chunksize;
        assert this.count == chunkList.size();
    }
    
    public final boolean contains(final byte[] t) {
        return contains(t, 0, this.count);
    }

    private final boolean contains(final byte[] t, int beginPos, int endPos) {
        // the endPos is exclusive, beginPos is inclusive
        // this method is synchronized to make the use of the buffer possible
        assert t.length == this.chunksize;
        while (true) {
            if (beginPos >= endPos) return false;
            final int pivot = (beginPos + endPos) / 2;
            if ((pivot < 0) || (pivot >= this.count)) return false;
            assert this.chunksize == t.length;
            final int c = objectOrder.compare(this.chunks, pivot * this.chunksize, t, 0, this.chunksize);
            if (c == 0) return true;
            if (c < 0) /* buffer < t */ {beginPos = pivot + 1; continue;}
            if (c > 0) /* buffer > t */ {endPos = pivot; continue;}
            return false;
        }
    }
    
    public final int size() {
        return count;
    }
    
    public final byte[] get(final int element) {
        final byte[] a = new byte[chunksize];
        System.arraycopy(this.chunks, element * this.chunksize, a, 0, chunksize);
        return a;
    }
    
    public final byte[] get(final int element, byte[] a) {
        assert a.length == chunksize;
        System.arraycopy(this.chunks, element * this.chunksize, a, 0, chunksize);
        return a;
    }
    
    public final void write(File f) throws IOException {
        FileOutputStream os = new FileOutputStream(f);
        os.write(this.chunks);
        os.flush();
        os.close();
    }
    
    public static void main(final String[] args) {
        final String s = "4CEvsI8FRczRBo_ApRCkwfEbFLn1pIFXg39QGMgj5RHM6HpIMJq67QX3M5iQYr_LyI_5aGDaa_bYbRgJ9XnQjpmq6QkOoGWAoEaihRqhV3kItLFHjRtqauUR";
        final BinSearch bs = new BinSearch(s.getBytes(), 6);
        for (int i = 0; i + 6 <= s.length(); i = i + 6) {
            System.out.println(s.substring(i, i + 6) + ":" + ((bs.contains(s.substring(i, i + 6).getBytes())) ? "drin" : "draussen"));
        }
        for (int i = 0; i + 7 <= s.length(); i = i + 6) {
            System.out.println(s.substring(i + 1, i + 7) + ":" + ((bs.contains(s.substring(i + 1, i + 7).getBytes())) ? "drin" : "draussen"));
        }
    }
}
