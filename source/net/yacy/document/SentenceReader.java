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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Read sentences from a given text.
 * This enumerates StringBuilder objects. 
 */
public class SentenceReader implements Iterator<StringBuilder>, Iterable<StringBuilder> {

    /** Holds the next element */
    private StringBuilder buffer;

    /** List of already parsed sentences, eventually in addition to those extracted from the main text. */
    private List<StringBuilder> parsedSentences;

    /** Current position in the parsedSentences list. */
    private int sentencesPos;

    /** The main text to parse for sentences */
    private String text;

    /** The current character position in the main text */
    private int pos;

    /** When true sentences can not include line break characters */
    private boolean pre = false;

    public SentenceReader(final String text) {
        this(new ArrayList<>(), text, false);
    }

    public SentenceReader(final String text, final boolean pre) {
        this(new ArrayList<>(), text, pre);
    }

    public SentenceReader(final List<StringBuilder> parsedSentences, final String text, final boolean pre) {
        assert text != null;
        this.text = text;
        this.pos = 0;
        this.pre = pre;
        if(parsedSentences == null) {
            this.parsedSentences = new ArrayList<>();
        } else {
            this.parsedSentences = parsedSentences;
        }
        this.sentencesPos = 0;
        this.buffer = nextElement0();
    }

    public void pre(final boolean x) {
        this.pre = x;
    }

    private StringBuilder nextElement0() {
        if(this.sentencesPos < this.parsedSentences.size()) {
            final StringBuilder element = this.parsedSentences.get(this.sentencesPos);
            this.sentencesPos++;
            return element;
        }

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
        /* Add to parsed sentences list for eventual reuse after a reset */
        this.parsedSentences.add(s);
        this.sentencesPos++;
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
                || punctuation(c) || digitsep(c));
    }

    public final static boolean punctuation(final char c) {
        switch (c) {
            case '.':
            case '!':
            case '?':
                return true;
            default:
                return false;
        }
    }

    public final static boolean digitsep(final char c) {
        switch (c) {
            case '.':
            case ',':
                return true;
            default:
                return false;
        }
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

    /**
     * Reset the iterator position to zero
     */
    public void reset() {
           /* Reset only the sentences position to reuse already parsed sentences */
           this.sentencesPos = 0;
           this.buffer = nextElement0();
    }

    public synchronized void close() {
        this.text = null;
        this.parsedSentences = null;
    }

    public static void main(String[] args) {
        String s = "a b 1.5 ccc -4,7 d. so -o et, qu. 4.7Ohm 2.54inch.";
        SentenceReader sr = new SentenceReader(s);
        for (StringBuilder a: sr) System.out.println(a);
        sr = new SentenceReader(s);

        WordTokenizer words = new WordTokenizer(sr, null);
        try {
            while (words.hasMoreElements()) {
                System.out.println(words.nextElement().toString());
            }
        } finally {
            words.close();
            words = null;
        }
    }
}
