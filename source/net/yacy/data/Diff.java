// Diff.java 
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 03.02.2007
//
// This file is contributed by Franz Brausze
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

package net.yacy.data;

import java.util.ArrayList;

import net.yacy.document.parser.html.CharacterCoding;


/**
 * This class provides a diff-functionality.
 */
public class Diff {
    
    private   final ArrayList <Part> parts = new ArrayList<Part>();
    protected final Object[] original;
    protected final Object[] changed;
    
    /**
     * @param original the original <code>String</code>
     * @param changed the new <code>String</code>
     * @throws NullPointerException if one of the arguments is <code>null</code>
     */
    public Diff(final String original, final String changed) {
        this(original, changed, 1);
    }
    
    /**
     * @param original the original <code>String</code>
     * @param changed the new <code>String</code>
     * @param minConsecutive the minimum number of consecutive equal characters in
     * both Strings. Smaller seperations will only be performed on the end of either
     * String if needed
     * @throws NullPointerException if <code>original</code> or <code>changed</code> is
     * <code>null</code>
     */
    public Diff(final String original, final String changed, final int minConsecutive) {
        if (original == null || changed == null) throw new NullPointerException("input Strings must be null");
        this.original = new Comparable[original.length()];
        for (int i=0; i<original.length(); i++)
            this.original[i] = Character.valueOf(original.charAt(i));
        this.changed = new Comparable[changed.length()];
        for (int i=0; i<changed.length(); i++)
            this.changed[i] = Character.valueOf(changed.charAt(i));
        parse((minConsecutive > 0) ? minConsecutive : 1);
    }
    
    public Diff(final Object[] original, final Object[] changed, final int minConsecutive) {
        if (original == null || changed == null) throw new NullPointerException("input Objects must be null");
        this.original = original;
        this.changed = changed;
        parse((minConsecutive > 0) ? minConsecutive : 1);
    }
    
    private void parse(final int minLength) {
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
        final boolean[][] matrix = new boolean[this.changed.length][this.original.length];
        for (int y=0; y<this.changed.length; y++)
            for (int x=0; x<this.original.length; x++)
                matrix[y][x] = this.original[x].equals(this.changed[y]);
        
        int s = 0, t = 0;
        int[] tmp;
        while ((tmp = findDiagonal(s, t, matrix, minLength)) != null) {
            addReplacementParts(s, t, tmp[0], tmp[1]);
            this.parts.add(new Part(Part.UNCHANGED, tmp[0], s = tmp[0] + tmp[2]));
            t = tmp[1] + tmp[2];
        }
        addReplacementParts(s, t, this.original.length, this.changed.length);
    }
    
    private void addReplacementParts(final int startx, final int starty, final int endx, final int endy) {
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
    private static int[] findDiagonal(final int x, final int y, final boolean[][] matrix, final int minLength) {
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
    public Object[] getOriginal() { return this.original; }
    
    /**
     * @return the new <code>Object[]</code> passed to this class on instantiation
     */
    public Object[] getNew() { return this.changed; }
    
    /**
     * A diff is composed of different parts. Each of these parts stands for an
     * operation, like "do nothing", "add" or "delete".
     * 
     * @see Part
     * @return all parts this diff consists of in correct order
     */
    public Part[] getParts() { return this.parts.toArray(new Part[this.parts.size()]); }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(this.parts.size() * 20);
        for (final Part part :parts)
            sb.append(part.toString()).append("\n");
        return sb.toString();
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
        
        Part(final int action, final int posOld, final int posNew) {
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
            final StringBuilder sb = new StringBuilder(this.posNew - this.posOld);
            if (this.action == ADDED) {
                for (int i = this.posOld; i < this.posNew; i++)
                    sb.append(Diff.this.changed[i]);
            } else {
                for (int i = this.posOld; i < this.posNew; i++)
                    sb.append(Diff.this.original[i]);
            }
            return sb.toString();
        }
        
        /**
         * @return the string this diff-part cares about in typical diff-notation:
         * <dl>
         * <dt>unchanged</dt><dd>"<code>&nbsp;&nbsp;STRING</code>"</dd>
         * <dt>added</dt><dd>"<code>+ STRING</code>"</dd>
         * <dt>deleted</dt><dd>"<code>- STRING</code>"</dd>
         * </dl>
         */
        @Override
        public String toString() {
            return ((this.action == UNCHANGED) ? " " :
                    (this.action == ADDED) ? "+" : "-") + " " + getString();
        }
    }
    
    public static String toHTML(final Diff[] diffs) {
        final StringBuilder sb = new StringBuilder(diffs.length * 60);
        Diff.Part[] ps;
        for (Diff d : diffs) {
            sb.append("<p class=\"diff\">\n");
            ps = d.getParts();
            for (Diff.Part part :ps) {
                sb.append("<span\nclass=\"");
                switch (part.getAction()) {
                    case Diff.Part.UNCHANGED: sb.append("unchanged"); break;
                    case Diff.Part.ADDED: sb.append("added"); break;
                    case Diff.Part.DELETED: sb.append("deleted"); break;
                }
                sb.append("\">").append(CharacterCoding.unicode2html(part.getString(), true).replaceAll("\n", "<br />"));
                sb.append("</span>");
            }
            sb.append("</p>");
        }
        return sb.toString();
    }
}