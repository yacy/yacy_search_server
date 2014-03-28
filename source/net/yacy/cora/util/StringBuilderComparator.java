/**
 *  StringBuilderComparator.java
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.11.2011 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * case-insensitive compare of two StringBuilder objects
 * this shall replace the corresponding method in class String when StringBuilder objects are not transformed into string
 */
public class StringBuilderComparator implements Comparator<StringBuilder> {

    public static final StringBuilderComparator CASE_SENSITIVE_ORDER = new StringBuilderComparator(false);
    public static final StringBuilderComparator CASE_INSENSITIVE_ORDER = new StringBuilderComparator(true);

    private final boolean caseInsensitive;

    public StringBuilderComparator(final boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    @Override
    public int compare(final StringBuilder sb0, final StringBuilder sb1) {
        final int l0 = sb0.length();
        final int l1 = sb1.length();
        final int ml = Math.min(l0, l1);
        char c0, c1;
        for (int i = 0; i < ml; i++) {
            c0 = sb0.charAt(i);
            c1 = sb1.charAt(i);
            if (c0 == c1) continue;
            if (this.caseInsensitive) {
                c0 = Character.toUpperCase(c0);
                c1 = Character.toUpperCase(c1);
                if (c0 == c1) continue;
                c0 = Character.toLowerCase(c0);
                c1 = Character.toLowerCase(c1);
                if (c0 == c1) continue;
            }
            return c0 - c1;
        }
        return l0 - l1;
    }

    public boolean equals(final StringBuilder sb0, final StringBuilder sb1) {
        final int l0 = sb0.length();
        final int l1 = sb1.length();
        if (l0 != l1) return false;
        return equals(sb0, sb1, 0, l1);
    }

    public boolean startsWith(final StringBuilder sb0, final StringBuilder sb1) {
        final int l0 = sb0.length();
        final int l1 = sb1.length();
        if (l0 < l1) return false;
        return equals(sb0, sb1, 0, l1);
    }

    public boolean endsWith(final StringBuilder sb0, final StringBuilder sb1) {
        final int l0 = sb0.length();
        final int l1 = sb1.length();
        if (l0 < l1) return false;
        return equals(sb0, sb1, l0 - l1, l1);
    }

    private boolean equals(final StringBuilder sb0, final StringBuilder sb1, int start, final int l) {
        char c0, c1;
        for (int i = start; i < l; i++) {
            c0 = sb0.charAt(i);
            c1 = sb1.charAt(i);
            if (c0 == c1) continue;
            if (this.caseInsensitive) {
                c0 = Character.toUpperCase(c0);
                c1 = Character.toUpperCase(c1);
                if (c0 == c1) continue;
                c0 = Character.toLowerCase(c0);
                c1 = Character.toLowerCase(c1);
                if (c0 == c1) continue;
            }
            return false;
        }
        return true;
    }

    // methods that can be useful for StringBuilder as replacement of String

    public int indexOf(final StringBuilder sb, final char ch) {
        final int max = sb.length();
        for (int i = 0; i < max ; i++) {
            if (sb.charAt(i) == ch) return i;
        }
        return -1;
    }

    public int indexOf(final StringBuilder sb, final int off, final char ch) {
        final int max = sb.length();
        for (int i = off; i < max ; i++) {
            if (sb.charAt(i) == ch) return i;
        }
        return -1;
    }

    public StringBuilder[] split(final StringBuilder sb, final char c) {
        int next = 0;
        int off = 0;
        final ArrayList<String> list = new ArrayList<String>();
        while ((next = indexOf(sb, off, c)) != -1) {
            list.add(sb.substring(off, next));
            off = next + 1;
        }
        if (off == 0) return new StringBuilder[] { sb };

        list.add(sb.substring(off, sb.length()));

        int resultSize = list.size();
        while (resultSize > 0 && list.get(resultSize - 1).isEmpty()) resultSize--;
        final StringBuilder[] result = new StringBuilder[resultSize];
        for (int i = 0; i < resultSize; i++) result[i] = new StringBuilder(list.get(i));
        return result;
    }

    public static StringBuilder[] split(final StringBuilder sb, final Pattern pattern) {
        final String[] p = pattern.split(sb);
        final StringBuilder[] h = new StringBuilder[p.length];
        for (int i = 0; i < p.length; i++) h[i] = new StringBuilder(p[i]);
        return h;
    }

    public static void main(final String[] args) {
        final StringBuilder s = new StringBuilder("ene mene mu");
        final StringBuilder[] t = StringBuilderComparator.CASE_INSENSITIVE_ORDER.split(s, ' ');
        for (final StringBuilder u: t) System.out.println(u.toString());
    }

}
