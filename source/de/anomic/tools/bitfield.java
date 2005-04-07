// bitfield.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 11.08.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.tools;

public class bitfield {
    
    private byte[] bb;    

    public bitfield(int bytelength) {
        this.bb= new byte[bytelength];
        for (int i = 0 ; i < bytelength; i++) bb[i] = (char) 48;
    }
    
    public bitfield(byte[] field) {
        bb = field;
    }
    
    private static byte setAtom(byte a, int pos) {
        if ((pos > 5) || (pos < 0)) throw new RuntimeException("atom position out of bounds: " + pos);
        return (byte) ((64 | ((a + 16) | (1<<pos))) - 16);
    }
    
    private static  byte unsetAtom(byte a, int pos) {
        if ((pos > 5) || (pos < 0)) throw new RuntimeException("atom position out of bounds: " + pos);
        return (byte) (((a + 16) & (0xff ^ (1<<pos))) - 16);
    }
    
    public void set(int pos, boolean value) {
        int slot = pos / 6;
        if ((pos < 0) || (slot > bb.length)) throw new RuntimeException("position out of bounds: " + pos);
        bb[slot] = (value) ? setAtom(bb[slot], pos % 6) : unsetAtom(bb[slot], pos % 6);
    }
    
    public boolean get(int pos) {
        int slot = pos / 6;
        if ((pos < 0) || (slot > bb.length)) throw new RuntimeException("position out of bounds: " + pos);
        return (bb[slot] & (1<<(pos%6))) > 0;
    }

    public int length() {
        return bb.length * 6;
    }
    
    public byte[] getBytes() {
        return bb;
    }
    
    public String toString() {
        return new String(bb);
    }
    
    public static void main(String[] args) {
        bitfield test = new bitfield(4);
        int l = test.length();
        System.out.println("available: " + l);
        System.out.println("bevore:    " + test.toString());
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
