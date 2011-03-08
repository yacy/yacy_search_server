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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

public class SentenceReader implements Iterator<StringBuilder> {
    // read sentences from a given input stream
    // this enumerates StringBuilder objects
    
    private StringBuilder buffer;
    private BufferedReader raf;
    private int counter = 0;
    private boolean pre = false;

    public SentenceReader(final InputStream is) {
        assert is != null;
        try {
            raf = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        buffer = nextElement0();
        counter = 0;
        pre = false;
    }

    public void pre(final boolean x) {
        this.pre = x;
    }
    
    private StringBuilder nextElement0() {
        try {
            final StringBuilder s = readSentence(raf, pre);
            //System.out.println(" SENTENCE='" + s + "'"); // DEBUG 
            if (s == null) {
                raf.close();
                return null;
            }
            return s;
        } catch (final IOException e) {
            try {
                raf.close();
            } catch (final Exception ee) {
            }
            return null;
        }
    }
    
    private static StringBuilder readSentence(final Reader reader, final boolean pre) throws IOException {
        final StringBuilder s = new StringBuilder(80);
        int nextChar;
        char c, lc = ' '; // starting with ' ' as last character prevents that the result string starts with a ' '
        
        // find sentence end
        while (true) {
            nextChar = reader.read();
            //System.out.print((char) nextChar); // DEBUG    
            if (nextChar < 0) {
                if (s.length() == 0) return null;
                break;
            }
            c = (char) nextChar;
            if (pre && ((c == (char) 10) || (c == (char) 13))) break;
            if (c < ' ') c = ' ';
            if ((lc == ' ') && (c == ' ')) continue; // ignore double spaces
            s.append(c);
            if (punctuation(lc) && invisible(c)) break;
            lc = c;
        }
        
        if (s.length() == 0) return s;
        if (s.charAt(s.length() - 1) == ' ') {
            s.trimToSize();
            s.deleteCharAt(s.length() - 1);
        }
        return s;
    }

    public final static boolean invisible(final char c) {
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
    
    public boolean hasNext() {
        return buffer != null;
    }

    public StringBuilder next() {
        if (buffer == null) {
            return null;
        }
        counter = counter + buffer.length() + 1;
        final StringBuilder r = buffer;
        buffer = nextElement0();
        return r;
    }

    public int count() {
        return counter;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
