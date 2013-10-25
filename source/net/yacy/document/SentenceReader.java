/**
 *  SentenceReader
 *  Copyright 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 09.02.2011 on http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.document;

import java.util.Iterator;

public class SentenceReader implements Iterator<StringBuilder>, Iterable<StringBuilder> {
    // read sentences from a given input stream
    // this enumerates StringBuilder objects

    private StringBuilder buffer;
    private String text;
    private int pos;
    private boolean pre = false;

    public SentenceReader(final String text) {
    	assert text != null;
        this.text = text;
        this.pos = 0;
        this.pre = false;
        this.buffer = nextElement0();
    }

    public SentenceReader(final String text, final boolean pre) {
    	this(text);
        this.pre = pre;
    }

    public void pre(final boolean x) {
        this.pre = x;
    }

    private StringBuilder nextElement0() {
        final StringBuilder s = new StringBuilder(80);
        int nextChar;
        char c, lc = ' '; // starting with ' ' as last character prevents that the result string starts with a ' '

        // find sentence end
        while (this.pos < this.text.length() && (nextChar = this.text.charAt(this.pos++)) > 0) {
            c = (char) nextChar;
            if (this.pre && (nextChar == 10 || nextChar == 13)) break;
            if (c < ' ') c = ' ';
            if (lc == ' ' && c == ' ') continue; // ignore double spaces
            s.append(c);
            if (punctuation(lc) && invisible(c)) break;
            lc = c;
        }

        if (s.length() == 0) return null;
        if (s.charAt(s.length() - 1) == ' ') {
            s.trimToSize();
            s.deleteCharAt(s.length() - 1);
        }
        return s;
    }

    public final static boolean invisible(final char c) {
    	// first check average simple case
    	if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return false;
    	// then check more complex case which applies to all character sets
    	final int type = Character.getType(c);
        return !(type == Character.LOWERCASE_LETTER
                || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.UPPERCASE_LETTER
                || type == Character.MODIFIER_LETTER
                || type == Character.OTHER_LETTER
                || type == Character.TITLECASE_LETTER
                || punctuation(c));
    }

    public final static boolean punctuation(final char c) {
        return c == '.' || c == '!' || c == '?';
    }

    @Override
    public boolean hasNext() {
        return this.buffer != null;
    }

    @Override
    public StringBuilder next() {
        if (this.buffer == null) {
            return null;
        }
        final StringBuilder r = this.buffer;
        this.buffer = nextElement0();
        return r;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<StringBuilder> iterator() {
        return this;
    }

    public synchronized void close() {
    	this.text = null;
    }

    public static void main(String[] args) {
        String s = "a b ccc d";
        SentenceReader sr = new SentenceReader(s);
        for (StringBuilder a: sr) {
            System.out.println(a);
        }
    }
}
