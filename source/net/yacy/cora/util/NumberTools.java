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


package net.yacy.cora.util;

import java.util.Random;

public class NumberTools {

    /**
     * this method replaces Long.parseLong/2 where a substring of decimal numbers shall be parsed
     * Strings are also auto-trimmed, that means parsing stops at spaces without throwing a NumberFormatException
     * @param s
     * @param startPos
     * @return the number
     * @throws NumberFormatException
     */
    public static final long parseLongDecSubstring(String s) throws NumberFormatException {
        if (s == null) throw new NumberFormatException(s);
        return parseLongDecSubstring(s, 0, s.length());
    }

    public static final long parseLongDecSubstring(String s, int startPos) throws NumberFormatException {
        if (s == null) throw new NumberFormatException(s);
        return parseLongDecSubstring(s, startPos, s.length());
    }

    public static final long parseLongDecSubstring(String s, int startPos, final int endPos) throws NumberFormatException {
        if (s == null || endPos > s.length() || endPos <= startPos) throw new NumberFormatException(s);

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
            i++;
            if (endPos == i) throw new NumberFormatException(s);
        }
        multmin = limit / 10;
        while (i < endPos) {
            c = s.charAt(i++);
            if (c == ' ') break;
            digit = c - '0';
            if (digit < 0 || digit > 9 || result < multmin) throw new NumberFormatException(s);
            result *= 10;
            if (result < limit + digit) throw new NumberFormatException(s);
            result -= digit;
        }
        return negative ? result : -result;
    }

    public static final int parseIntDecSubstring(String s) throws NumberFormatException {
        if (s == null) throw new NumberFormatException(s);
        return parseIntDecSubstring(s, 0, s.length());
    }

    public static final int parseIntDecSubstring(String s, int startPos) throws NumberFormatException {
        if (s == null) throw new NumberFormatException(s);
        return parseIntDecSubstring(s, startPos, s.length());
    }

    public static final int parseIntDecSubstring(String s, int startPos, final int endPos) throws NumberFormatException {
        if (s == null || endPos > s.length() || endPos <= startPos) throw new NumberFormatException(s);

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
            i++;
            if (endPos == i) throw new NumberFormatException(s);
        }
        multmin = limit / 10;
        while (i < endPos) {
            c = s.charAt(i++);
            if (c == ' ') break;
            digit = c - '0';
            if (digit < 0  || digit > 9 || result < multmin) throw new NumberFormatException(s);
            result *= 10;
            //result = (result << 3) + (result << 1);
            if (result < limit + digit) throw new NumberFormatException(s);
            result -= digit;
        }
        return negative ? result : -result;
    }

    public static void main(String[] args) {
        System.out.println("the number is " + parseLongDecSubstring("number=78 ", 7));
        System.out.println("the number is " + parseIntDecSubstring("number=78x ", 7, 9));
        Random r = new Random(1);
        String[] s = new String[1000000];
        for (int i = 0; i < s.length; i++) s[i] = "abc " + Integer.toString(r.nextInt()) + " ";
        long d = 0;
        long t0 = System.currentTimeMillis();
        for (String element : s) {
            d += Integer.parseInt(element.substring(4).trim());
        }
        System.out.println("java: " + d + " - " + (System.currentTimeMillis() - t0) + " millis");
        d = 0;
        t0 = System.currentTimeMillis();
        for (String element : s) {
            d += parseIntDecSubstring(element, 4);
        }
        System.out.println("cora: " + d + " - " + (System.currentTimeMillis() - t0) + " millis");

        r = new Random(1);
        for (int i = 0; i < s.length; i++) s[i] = Integer.toString(r.nextInt());

        d = 0;
        t0 = System.currentTimeMillis();
        for (String element : s) {
            d += Integer.parseInt(element);
        }
        System.out.println("java: " + d + " - " + (System.currentTimeMillis() - t0) + " millis");
        d = 0;
        t0 = System.currentTimeMillis();
        for (String element : s) {
            d += parseIntDecSubstring(element);
        }
        System.out.println("cora: " + d + " - " + (System.currentTimeMillis() - t0) + " millis");
    }
}
