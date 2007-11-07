// kelondroBitfield.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 22.22.2006 on http://www.anomic.de
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

public class kelondroBitfield implements Cloneable {

    // the bitfield implements a binary array. Such arrays may be exported in a base64-String
    
    private byte[] bb;    

    public kelondroBitfield() {
        this(0);
    }
    
    public kelondroBitfield(byte[] b) {
        if (b == null) this.bb = new byte[0]; else this.bb = b;
    }
    
    public kelondroBitfield(int bytelength) {
        this.bb= new byte[bytelength];
        for (int i = 0 ; i < bytelength; i++) bb[i] = 0;
    }

    public kelondroBitfield(int bytelength, String exported) {
        // imports a b64-encoded bitfield
        byte[] b = kelondroBase64Order.enhancedCoder.decode(exported);
        if (b.length == bytelength) {
            bb = b;
        } else {
            bb = new byte[bytelength];
            assert (b.length <= bytelength) : "exported = " + exported + " has bytelength = " + b.length + " > " + bytelength;
            System.arraycopy(b, 0, bb, 0, Math.min(b.length, bytelength));
        }
    }
    
    public Object clone() {
        kelondroBitfield theClone = new kelondroBitfield(new byte[this.bb.length]);
        System.arraycopy(this.bb, 0, theClone.bb, 0, this.bb.length);
        return theClone;
    }
    
    public void set(int pos, boolean value) {
        assert (pos >= 0);
        int slot = pos >> 3; // /8
        if (slot >= bb.length) {
            // extend capacity
            byte[] nb = new byte[slot + 1];
            System.arraycopy(bb, 0, nb, 0, bb.length);
            for (int i = bb.length; i < nb.length; i++) nb[i] = 0;
            bb = nb;
            nb = null;
        }
        if (value) {
            bb[slot] = (byte) (bb[slot] | (1 << (pos % 8)));
        } else {
            bb[slot] = (byte) (bb[slot] & (0xff ^ (1 << (pos % 8))));
        }
    }
    
    public boolean get(int pos) {
        assert (pos >= 0);
        int slot = pos >> 3; // /8
        if (slot > bb.length) return false;
        return (bb[slot] & (1 << (pos % 8))) > 0;
    }

    public int length() {
        return bb.length << 3;
    }
    
    public String exportB64() {
        return kelondroBase64Order.enhancedCoder.encode(bb);
    }
    
    public byte[] bytes() {
        return bb;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer(length());
        for (int i = length() - 1; i >= 0; i--) sb.append((this.get(i)) ? '1' : '0');
        return new String(sb);
    }
    
    public boolean equals(kelondroBitfield x) {
        if (x.bb.length != bb.length) return false;
        for (int i = 0; i < bb.length; i++) if (bb[i] != x.bb[i]) return false;
        return true;
    }
    
    public void and(kelondroBitfield x) {
        int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) set(i, this.get(i) && x.get(i));
    }
    
    public void or(kelondroBitfield x) {
        int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) set(i, this.get(i) || x.get(i));
        if (x.length() > c) {
            for (int i = c; i < x.length(); i++) set(i, x.get(i));
        }
    }
    
    public void xor(kelondroBitfield x) {
        int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) set(i, this.get(i) != x.get(i));
        if (x.length() > c) {
            for (int i = c; i < x.length(); i++) set(i, x.get(i));
        }
    }
    
    public boolean anyOf(kelondroBitfield x) {
        int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) if ((x.get(i)) && (this.get(i))) return true;
        return false;
    }
    
    public boolean allOf(kelondroBitfield x) {
        int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) if ((x.get(i)) && (!(this.get(i)))) return false;
        if (x.length() > c) {
            for (int i = c; i < x.length(); i++) if (x.get(i)) return false;
        }
        return true;
    }
    
    public static void main(String[] args) {
        kelondroBitfield test = new kelondroBitfield(4);
        int l = test.length();
        System.out.println("available: " + l);
        System.out.println("bevore:    " + test.toString());
        for (int i = 0; i < l/2; i++) {
            System.out.println(new String(test.exportB64()));
            test.set(i, true);
            System.out.println(i + ":" + test.toString()); 
        }
        for (int i = l/2; i < l; i++) {
            System.out.println(new String(test.exportB64()));
            test = new kelondroBitfield(4, test.exportB64());
            test.set(i, true);
            System.out.println(i + ":" + test.toString()); 
        }
        System.out.println(new String(test.exportB64()));
        for (int i = l - 1; i >= 0; i--) {
            test.set(i, false);
            System.out.println(i + ":" + test.toString()); 
        }
        System.out.println("after:     " + test.toString());       
    }
}
