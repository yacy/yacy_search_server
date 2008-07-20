// kelondroBinSearch.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 22.11.2005
//
// $LastChangedDate: 2005-09-22 22:01:26 +0200 (Thu, 22 Sep 2005) $
// $LastChangedRevision: 774 $
// $LastChangedBy: orbiter $
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

package de.anomic.kelondro;


public class kelondroBinSearch {
    
    private byte[] chunks;
    private int    chunksize;
    private int    count;
    private kelondroByteOrder objectOrder = new kelondroNaturalOrder(true);
    
    public kelondroBinSearch(byte[] chunks, int chunksize) {
        this.chunks = chunks;
        this.chunksize = chunksize;
        this.count = chunks.length / chunksize;
    }
    
    public boolean contains(byte[] t) {
        return contains(t, 0, this.count);
    }

    private synchronized boolean contains(byte[] t, int beginPos, int endPos) {
        // the endPos is exclusive, beginPos is inclusive
        // this method is synchronized to make the use of the buffer possible
        assert t.length == this.chunksize;
        if (beginPos >= endPos) return false;
        int pivot = (beginPos + endPos) / 2;
        if ((pivot < 0) || (pivot >= this.count)) return false;
        int c = objectOrder.compare(this.chunks, pivot * this.chunksize, this.chunksize, t, 0, t.length);
        if (c == 0) return true;
        if (c < 0) /* buffer < t */ return contains(t, pivot + 1, endPos);
        if (c > 0) /* buffer > t */ return contains(t, beginPos, pivot);
        return false;
    }
    
    public int size() {
        return count;
    }
    
    public byte[] get(int element) {
        byte[] a = new byte[chunksize];
        System.arraycopy(this.chunks, element * this.chunksize, a, 0, chunksize);
        return a;
    }
    
    public static void main(String[] args) {
        String s = "4CEvsI8FRczRBo_ApRCkwfEbFLn1pIFXg39QGMgj5RHM6HpIMJq67QX3M5iQYr_LyI_5aGDaa_bYbRgJ9XnQjpmq6QkOoGWAoEaihRqhV3kItLFHjRtqauUR";
        kelondroBinSearch bs = new kelondroBinSearch(s.getBytes(), 6);
        for (int i = 0; i + 6 <= s.length(); i = i + 6) {
            System.out.println(s.substring(i, i + 6) + ":" + ((bs.contains(s.substring(i, i + 6).getBytes())) ? "drin" : "draussen"));
        }
        for (int i = 0; i + 7 <= s.length(); i = i + 6) {
            System.out.println(s.substring(i + 1, i + 7) + ":" + ((bs.contains(s.substring(i + 1, i + 7).getBytes())) ? "drin" : "draussen"));
        }
    }
}
