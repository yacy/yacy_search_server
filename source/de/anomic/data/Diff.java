// Diff.java 
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 03.02.2007
//
// This file is contributed by Franz Brauße
//
// $LastChangedDate: $
// $LastChangedRevision: $
// $LastChangedBy: $
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

package de.anomic.data;

import java.util.ArrayList;

/**
 * This class provides a diff-functionality.
 */
public class Diff {
    
    private final ArrayList /* of Part */ parts = new ArrayList();
    private final String o;
    private final String n;
    
    /**
     * @param o the original <code>String</code>
     * @param n the new <code>String</code>
     * @throws NullPointerException if one of the arguments is <code>null</code>
     */
    public Diff(String o, String n) {
        if (o == null || n == null) throw new NullPointerException("neither o nor n must be null");
        this.o = o;
        this.n = n;
        parse(1);
    }
    
    /**
     * @param o the original <code>String</code>
     * @param n the new <code>String</code>
     * @param minConsecutive the minimum number of consecutive equal characters in
     * both Strings. Smaller seperations will only be performed on the end of either
     * String if needed
     * @throws NullPointerException if <code>o</code> or <code>n</code> is
     * <code>null</code>
     */
    public Diff(String o, String n, int minConsecutive) {
        if (o == null || n == null) throw new NullPointerException("neither o nor n must be null");
        this.o = o;
        this.n = n;
        parse((minConsecutive > 0) ? minConsecutive : 1);
    }
    
    private void parse(int minLength) {
        /* Matrix: find as long diagonals as possible,
         *         delete the old horizontally and add the new vertically
         * 
         *                    ~ OLD ~
         *     |T|H|E| |F|I|R|S|T| |S|E|N|T|E|N|C|E|
         *    T|#| | | | | | | |#| | | | |#| | | | |
         *    H| |#| | | | | | | | | | | | | | | | |
         *    E| | |#| | | | | | | | |#| | |#| | |#|
         *     | | | |#| | | | | |#| | | | | | | | |
         *    N| | | | | | | | | | | | |#| | |#| | |
         *    E| | |#| | | | | | | | |#| | |#| | |#|
         * ~  X| | | | | | | | | | | | | | | | | | |
         * N  T|#| | | | | | | |#| | | | |#| | | | |
         * E   | | | |#| | | | | |#| | | | | | | | |
         * W  S| | | | | | | |#| | |#| | | | | | | |
         * ~  E| | |#| | | | | | | | |#| | |#| | |#|
         *    N| | | | | | | | | | | | |#| | |#| | |
         *    T|#| | | | | | | |#| | | | |#| | | | |
         *    E| | |#| | | | | | | | |#| | |#| | |#|
         *    N| | | | | | | | | | | | |#| | |#| | |
         *    C| | | | | | | | | | | | | | | | |#| |
         *    E| | |#| | | | | | | | | |#| | |#| |#|
         */
        boolean[][] matrix = new boolean[this.n.length()][this.o.length()];
        for (int y=0; y<this.n.length(); y++)
            for (int x=0; x<this.o.length(); x++)
                matrix[y][x] = this.o.charAt(x) == this.n.charAt(y);
        
        int s = 0, t = 0;
        int[] tmp = findDiagonal(s, t, matrix, minLength);
        while (tmp != null) {
            addReplacementParts(s, t, tmp[0], tmp[1]);
            this.parts.add(new Part(Part.UNCHANGED, tmp[0], s = tmp[0] + tmp[2]));
            t = tmp[1] + tmp[2];
            tmp = findDiagonal(s, t, matrix, minLength);
        }
        addReplacementParts(s, t, this.o.length(), this.n.length());
    }
    
    private void addReplacementParts(int startx, int starty, int endx, int endy) {
        if (startx < endx) this.parts.add(new Part(Part.DELETED, startx, endx));
        if (starty < endy) this.parts.add(new Part(Part.ADDED, starty, endy));
    }
    
    private static int[] findDiagonal(int x, int y, boolean[][] matrix, int minLength) {
        int rx, ry, yy, xx, i;
        // Zeilenweise nach Diagonalen mit mindest-Laenge minLength suchen
        for (yy=y; yy<matrix.length; yy++)
            for (xx=x; xx<matrix[yy].length; xx++)
                if (matrix[yy][xx]) {
                    rx = xx;
                    ry = yy;
                    for (i=1; (yy + i)<matrix.length && (xx + i)<matrix[yy].length; i++)
                        if (!matrix[yy + i][xx + i]) break;
                    if (i <= minLength && yy + i < matrix.length && xx + i < matrix[yy].length) {
                        // vorzeitig abgebrochen => zuwenige chars in Diagonale => weitersuchen
                        break;
                    } else {
                        return new int[] { rx, ry, i };
                    }
                }
        return null;
    }
    
    /**
     * @return the original <code>String</code> passed to this class on instantiation
     */
    public String getOriginal() { return this.o; }
    
    /**
     * @return the new <code>String</code> passed to this class on instantiation
     */
    public String getNew() { return this.n; }
    
    /**
     * A diff is composed of different parts. Each of these parts stands for an
     * operation, like "do nothing", "add" or "delete".
     * 
     * @see Part
     * @return all parts this diff consists of in correct order
     */
    public Part[] getParts() { return (Part[])this.parts.toArray(new Part[this.parts.size()]); }
    
    /**
     * This class represents a part of the diff, meaning one operation
     * (or one line of a "normal" diff)
     */
    public class Part {
        
        /** The string this diff-part cares about has not been changed */
        public static final int UNCHANGED = 0;
        /** The string this diff-part cares about has been added in the new version */
        public static final int ADDED = 1;
        /** The string this diff-part cares about has been removed in the new version */
        public static final int DELETED = 2;
        
        private final int action;
        private final int posOld;
        private final int posNew;
        
        private Part(int action, int posOld, int posNew) {
            this.action = action;
            this.posOld = posOld;
            this.posNew = posNew;
        }
        
        /**
         * @return whether the string shan't be changed, shall be added or deleted
         */
        public int getAction() { return this.action; }
        public int getPosOld() { return this.posOld; }
        public int getPosNew() { return this.posNew; }
        
        /**
         * @return the plain string this diff-part cares about
         */
        public String getString() {
            return ((this.action == ADDED) ? Diff.this.n : Diff.this.o).substring(this.posOld, this.posNew);
        }
        
        /**
         * @return the string this diff-part cares about in typical diff-notation:
         * <dl>
         * <dt>unchanged</dt><dd>"<code>&nbsp;&nbsp;STRING</code>"</dd>
         * <dt>added</dt><dd>"<code>+ STRING</code>"</dd>
         * <dt>deleted</dt><dd>"<code>- STRING</code>"</dd>
         * </dl>
         */
        public String toString() {
            return ((this.action == UNCHANGED) ? " " :
                    (this.action == ADDED) ? "+" : "-") + " " + getString();
        }
    }
}
