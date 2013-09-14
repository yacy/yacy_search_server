// bitfield.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

package net.yacy.utils;

import net.yacy.cora.document.encoding.ASCII;


public class bitfield {
    
    private byte[] bb;    

    public bitfield() {
        this(0);
    }
    
    public bitfield(final int bytelength) {
        this.bb= new byte[bytelength];
        for (int i = 0 ; i < bytelength; i++) bb[i] = ' ';
    }
    
    public bitfield(final byte[] field) {
        bb = field;
    }
    
    private static byte setAtom(final byte a, final int pos) {
        if ((pos > 5) || (pos < 0)) throw new RuntimeException("atom position out of bounds: " + pos);
        byte b = (byte) (((a - 32) | (1<<pos)) + 32);
        return b;
    }
    
    private static byte unsetAtom(final byte a, final int pos) {
        if ((pos > 5) || (pos < 0)) throw new RuntimeException("atom position out of bounds: " + pos);
        byte b = (byte) (((a - 32) & (0xff ^ (1<<pos))) + 32);
        return b;
    }
    
    public void set(final int pos, final boolean value) {
        final int slot = pos / 5;
        if (pos < 0) throw new RuntimeException("position out of bounds: " + pos);
        if (slot > bb.length) {
            // extend capacity
            byte[] nb = new byte[slot + 1];
            System.arraycopy(bb, 0, nb, 0, bb.length);
            for (int i = bb.length; i < nb.length; i++) nb[i] = 0;
            bb = nb;
        }
        bb[slot] = (value) ? setAtom(bb[slot], pos % 5) : unsetAtom(bb[slot], pos % 5);
    }
    
    public boolean get(final int pos) {
        final int slot = pos / 5;
        if (pos < 0) throw new RuntimeException("position out of bounds: " + pos);
        if (slot > bb.length) return false;
        boolean b = ((bb[slot] - 32) & (1 << (pos % 5))) > 0;
        return b;
    }

    public int length() {
        return bb.length * 5;
    }
    
    public byte[] getBytes() {
        return bb;
    }
    
    @Override
    public String toString() {
        //throw new UnsupportedOperationException("testing");
        
        StringBuilder sb = new StringBuilder(length());
        for (int i = length() - 1; i >= 0; i--) sb.append((get(i)) ? '1' : '0');
        return ASCII.String(bb) + " " + sb.toString();
        
    }
    
    public static void main(final String[] args) {
        //final bitfield f = new bitfield(ASCII.getBytes(Seed.FLAGSZERO));
        final bitfield f = new bitfield(4);
        for (int i = 0; i < 20; i++) {
            f.set(i, false);
            if (f.get(i)) System.out.println("i = " + i);
            f.set(i, true);
            if (!f.get(i)) System.out.println("!i = " + i);
            System.out.println(i + ":" + f.toString());
        }
        
        final bitfield test = new bitfield(4);
        final int l = test.length();
        System.out.println("available: " + l);
        System.out.println("before:    " + test.toString());
        for (int i = 0; i < l/2; i++) {
            test.set(i, true);
            System.out.println(i + ":" + test.toString()); 
        }
        for (int i = l/2 - 1; i >= 0; i--) {
            test.set(i, false);
            System.out.println(i + ":" + test.toString()); 
        }
        System.out.println("after:     " + test.toString());       
    }
    
}
