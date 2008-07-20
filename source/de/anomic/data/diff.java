// Diff.java 
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 03.02.2007
//
// This file is contributed by Franz Brausze
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

package de.anomic.data;

import java.util.ArrayList;

/**
 * This class provides a diff-functionality.
 */
public class diff {
    
    private final ArrayList <Part> parts = new ArrayList<Part>();
    final Object[] o;
    final Object[] n;
    
    /**
     * @param o the original <code>String</code>
     * @param n the new <code>String</code>
     * @throws NullPointerException if one of the arguments is <code>null</code>
     */
    public diff(String o, String n) {
        this(o, n, 1);
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
    public diff(String o, String n, int minConsecutive) {
        if (o == null || n == null) throw new NullPointerException("neither o nor n must be null");
        this.o = new Comparable[o.length()];
        for (int i=0; i<o.length(); i++)
            this.o[i] = new Character(o.charAt(i));
        this.n = new Comparable[n.length()];
        for (int i=0; i<n.length(); i++)
            this.n[i] = new Character(n.charAt(i));
        parse((minConsecutive > 0) ? minConsecutive : 1);
    }
    
    public diff(Object[] o, Object[] n, int minConsecutive) {
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
         *    E| | |#| | | | | | | | |#| | |#| | |#|
         */
        boolean[][] matrix = new boolean[this.n.length][this.o.length];
        for (int y=0; y<this.n.length; y++)
            for (int x=0; x<this.o.length; x++)
                matrix[y][x] = this.o[x].equals(this.n[y]);
        
        int s = 0, t = 0;
        int[] tmp;
        while ((tmp = findDiagonal(s, t, matrix, minLength)) != null) {
            addReplacementParts(s, t, tmp[0], tmp[1]);
            this.parts.add(new Part(Part.UNCHANGED, tmp[0], s = tmp[0] + tmp[2]));
            t = tmp[1] + tmp[2];
        }
        addReplacementParts(s, t, this.o.length, this.n.length);
    }
    
    private void addReplacementParts(int startx, int starty, int endx, int endy) {
        if (startx < endx) this.parts.add(new Part(Part.DELETED, startx, endx));
        if (starty < endy) this.parts.add(new Part(Part.ADDED, starty, endy));
    }
    
    /** Search for a diagonal with minimal length <code>minLength</code> line by line in a submatrix 
     * <code>{ x, y, matrix[0].length, matrix.length}</code> of the <code>matrix</code>:<br>
     *           <code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; {_1,__,__} -&gt X axis</code><br>
     *           <code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;,{__,_1,__} </code><br>
     *           <code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;,{__,__,_1} </code><br> 
     * <ul>
     * TODO: some optimisation ideas
     * <li>search for a better algorithm on the inet!!! :) </li> 
     * <li>pass only the part of the matrix where the search takes place - not the whole matrix everytime</li> 
     * <li>break the inner loop if the rest of the matrix is smaller than minLength (and no diagonal has been found yet) </li> 
     * <li>return diagonal topologicaly closest to the {0,0} </li> 
     * </ul>
     * @param x the starting position of the search on the optical horizontal axis  
     * @param y the starting position of the search on the optical vertical axis<br>
     * @param matrix the matrix to search through
     * @param minLength the minimal desired length of a diagonal to find
     * @return a vector in the form <code>{ diagStartX, diagStartY, diagLength }</code> where <code> diagLength >= minLength</code>
     */
    private static int[] findDiagonal(int x, int y, boolean[][] matrix, int minLength) {
        int rx, ry, yy, xx, i;
        for (yy=y; yy<matrix.length; yy++)
            for (xx=x; xx<matrix[yy].length; xx++)
                if (matrix[yy][xx]) {       // reverse order! [y][x]
                    // save position
                    rx = xx;
                    ry = yy;
                    // follow diagonal as long as far as possible
                    for (i=1; (yy + i)<matrix.length && (xx + i)<matrix[yy].length; i++)
                        if (!matrix[yy + i][xx + i]) 
                            break;
                    if (i >= minLength)
                        return new int[] { rx, ry, i };     // swap back the x and y axes for better readability 
                }
        return null;
    }
    
    /**
     * @return the original <code>Object[]</code> passed to this class on instantiation
     */
    public Object[] getOriginal() { return this.o; }
    
    /**
     * @return the new <code>Object[]</code> passed to this class on instantiation
     */
    public Object[] getNew() { return this.n; }
    
    /**
     * A diff is composed of different parts. Each of these parts stands for an
     * operation, like "do nothing", "add" or "delete".
     * 
     * @see Part
     * @return all parts this diff consists of in correct order
     */
    public Part[] getParts() { return this.parts.toArray(new Part[this.parts.size()]); }
    
    public String toString() {
        StringBuffer sb = new StringBuffer(this.parts.size() * 20);
        for (int j=0; j<this.parts.size(); j++)
            sb.append(this.parts.get(j).toString()).append("\n");
        return new String(sb);
    }
    
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
        
        Part(int action, int posOld, int posNew) {
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
            final StringBuffer sb = new StringBuffer(this.posNew - this.posOld);
            if (this.action == ADDED) {
                for (int i=this.posOld; i<this.posNew; i++)
                    sb.append(diff.this.n[i]);
            } else {
                for (int i=this.posOld; i<this.posNew; i++)
                    sb.append(diff.this.o[i]);
            }
            return new String(sb);
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
    
    public static String toHTML(diff[] diffs) {
        StringBuffer sb = new StringBuffer(diffs.length * 60);
        diff.Part[] ps;
        for (int i=0; i<diffs.length; i++) {
            sb.append("<p class=\"diff\">\n");
            ps = diffs[i].getParts();
            for (int j=0; j<ps.length; j++) {
                sb.append("<span\nclass=\"");
                switch (ps[j].getAction()) {
                case diff.Part.UNCHANGED: sb.append("unchanged"); break;
                case diff.Part.ADDED: sb.append("added"); break;
                case diff.Part.DELETED: sb.append("deleted"); break;
                }
                sb.append("\">").append(htmlTools.encodeUnicode2html(ps[j].getString(), true).replaceAll("\n", "<br />"));
                sb.append("</span>");
            }
            sb.append("</p>");
        }
        return new String(sb);
    }
}