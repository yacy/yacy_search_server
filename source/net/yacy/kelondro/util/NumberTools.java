/**
 *  NumberTools
 *  Copyright 2012 by Sebastian Gaebel
 *  First released 21.05.2012 at http://yacy.net
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


package net.yacy.kelondro.util;

public class NumberTools {

    /**
     * this method replaces Long.parseLong/2 where a substring of decimal numbers shall be parsed
     * Strings are also auto-trimmed, that means parsing stops at spaces without throwing a NumberFormatException
     * @param s
     * @param startPos
     * @return the number
     * @throws NumberFormatException
     */
    public static final long parseLongDecSubstring(String s, int startPos) throws NumberFormatException {
        if (s == null) {
            throw new NumberFormatException("null");
        }
        final int len = s.length();
        if (len <= startPos) {
            throw new NumberFormatException(s);
        }

        long result = 0;
        boolean negative = false;
        int i = startPos;
        long limit = -Long.MAX_VALUE;
        final long multmin;
        int digit;
        char c;

        char firstChar = s.charAt(i);
        if (firstChar < '0') {
            if (firstChar == '-') {
                negative = true;
                limit = Long.MIN_VALUE;
            } else if (firstChar != '+') throw new NumberFormatException(s);

            if (len == 1) throw new NumberFormatException(s);
            i++;
        }
        multmin = limit / 10;
        while (i < len) {
            c = s.charAt(i++);
            if (c == ' ') break;
            digit = c - 48;
            if (digit < 0 || digit > 9 || result < multmin) throw new NumberFormatException(s);
            result *= 10;
            if (result < limit + digit) throw new NumberFormatException(s);
            result -= digit;
        }
        return negative ? result : -result;
    }

    public static final int parseIntDecSubstring(String s, int startPos) throws NumberFormatException {
        if (s == null) {
            throw new NumberFormatException("null");
        }

        final int len = s.length();
        if (len <= startPos) {
            throw new NumberFormatException(s);
        }

        int result = 0;
        boolean negative = false;
        int i = startPos;
        int limit = -Integer.MAX_VALUE;
        final int multmin;
        int digit;
        char c;

        char firstChar = s.charAt(i);
        if (firstChar < '0') {
            if (firstChar == '-') {
                negative = true;
                limit = Integer.MIN_VALUE;
            } else if (firstChar != '+') throw new NumberFormatException(s);

            if (len == 1) throw new NumberFormatException(s);
            i++;
        }
        multmin = limit / 10;
        while (i < len) {
            c = s.charAt(i++);
            if (c == ' ') break;
            digit = c - 48;
            if (digit < 0  || digit > 9 || result < multmin) throw new NumberFormatException(s);
            result *= 10;
            if (result < limit + digit) throw new NumberFormatException(s);
            result -= digit;
        }
        return negative ? result : -result;
    }

    public static void main(String[] args) {
        System.out.println("the number is " + parseLongDecSubstring("number=78 ", 7));
    }
}
