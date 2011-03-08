// Bitfield.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.22.2006 on http://www.anomic.de
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.order;


public class Bitfield implements Cloneable {

    // the bitfield implements a binary array. Such arrays may be exported in a base64-String
    
    private byte[] bb;    

    public Bitfield() {
        this(0);
    }
    
    public Bitfield(final byte[] b) {
        if (b == null) this.bb = new byte[0]; else this.bb = b;
    }
    
    public Bitfield(final int bytelength) {
        this.bb= new byte[bytelength];
        for (int i = 0 ; i < bytelength; i++) bb[i] = 0;
    }

    public Bitfield(final int bytelength, final String exported) {
        // imports a b64-encoded bitfield
        final byte[] b = Base64Order.enhancedCoder.decode(exported);
        if (b.length == bytelength) {
            bb = b;
        } else {
            bb = new byte[bytelength];
            assert (b.length <= bytelength) : "exported = " + exported + " has bytelength = " + b.length + " > " + bytelength;
            System.arraycopy(b, 0, bb, 0, Math.min(b.length, bytelength));
        }
    }
    
    @Override
    public Bitfield clone() {
        final Bitfield theClone = new Bitfield(new byte[this.bb.length]);
        System.arraycopy(this.bb, 0, theClone.bb, 0, this.bb.length);
        return theClone;
    }
    
    public void set(final int pos, final boolean value) {
        assert (pos >= 0);
        final int slot = pos >> 3; // /8
        if (slot >= bb.length) {
            // extend capacity
            byte[] nb = new byte[slot + 1];
            System.arraycopy(bb, 0, nb, 0, bb.length);
            for (int i = bb.length; i < nb.length; i++) nb[i] = 0;
            bb = nb;
        }
        if (value) {
            bb[slot] = (byte) (bb[slot] | (1 << (pos % 8)));
        } else {
            bb[slot] = (byte) (bb[slot] & (0xff ^ (1 << (pos % 8))));
        }
    }
    
    public boolean get(final int pos) {
        assert (pos >= 0);
        final int slot = pos >> 3; // /8
        if (slot >= bb.length) return false;
        return (bb[slot] & (1 << (pos % 8))) > 0;
    }

    public int length() {
        return bb.length << 3;
    }
    
    public String exportB64() {
        return Base64Order.enhancedCoder.encode(bb);
    }
    
    public byte[] bytes() {
        return bb;
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(length());
        for (int i = length() - 1; i >= 0; i--) sb.append((this.get(i)) ? '1' : '0');
        return sb.toString();
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Bitfield)) return false;
        Bitfield other = (Bitfield) obj;
        if (other.bb.length != this.bb.length) return false;
        for (int i = 0; i < this.bb.length; i++) if (this.bb[i] != other.bb[i]) return false;
        return true;
    }
    
    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }
    
    public void and(final Bitfield x) {
        final int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) set(i, this.get(i) && x.get(i));
    }
    
    public void or(final Bitfield x) {
        final int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) set(i, this.get(i) || x.get(i));
        if (x.length() > c) {
            for (int i = c; i < x.length(); i++) set(i, x.get(i));
        }
    }
    
    public void xor(final Bitfield x) {
        final int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) set(i, this.get(i) != x.get(i));
        if (x.length() > c) {
            for (int i = c; i < x.length(); i++) set(i, x.get(i));
        }
    }
    
    public boolean anyOf(final Bitfield x) {
        final int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) if ((x.get(i)) && (this.get(i))) return true;
        return false;
    }
    
    public boolean allOf(final Bitfield x) {
        final int c = Math.min(x.length(), this.length());
        for (int i = 0; i < c; i++) if ((x.get(i)) && (!(this.get(i)))) return false;
        if (x.length() > c) {
            for (int i = c; i < x.length(); i++) if (x.get(i)) return false;
        }
        return true;
    }
    
    public static void main(final String[] args) {
        Bitfield test = new Bitfield(4);
        final int l = test.length();
        System.out.println("available: " + l);
        System.out.println("bevore:    " + test.toString());
        for (int i = 0; i < l/2; i++) {
            System.out.println(test.exportB64());
            test.set(i, true);
            System.out.println(i + ":" + test.toString()); 
        }
        for (int i = l/2; i < l; i++) {
            System.out.println(test.exportB64());
            test = new Bitfield(4, test.exportB64());
            test.set(i, true);
            System.out.println(i + ":" + test.toString()); 
        }
        System.out.println(test.exportB64());
        for (int i = l - 1; i >= 0; i--) {
            test.set(i, false);
            System.out.println(i + ":" + test.toString()); 
        }
        System.out.println("after:     " + test.toString());       
    }
}
