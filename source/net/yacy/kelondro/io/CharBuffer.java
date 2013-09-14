// serverCharBuffer.java
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

package net.yacy.kelondro.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

import net.yacy.cora.document.encoding.UTF8;

public final class CharBuffer extends Writer {

    public static final char singlequote = '\'';
    public static final char doublequote = '"';
    public static final char equal       = '=';

    private char[] buffer;
    private int offset;
    private int length;
    private final int maximumLength;

    public CharBuffer(final int maximumLength) {
        this.buffer = new char[10];
        this.length = 0;
        this.offset = 0;
        this.maximumLength = maximumLength;
    }

    public CharBuffer(final int maximumLength, final int initLength) {
        this.buffer = new char[initLength];
        this.length = 0;
        this.offset = 0;
        this.maximumLength = maximumLength;
    }

    public CharBuffer(final int maximumLength, final char[] bb) {
        this.buffer = bb;
        this.length = bb.length;
        this.offset = 0;
        this.maximumLength = maximumLength;
    }

    public CharBuffer(final int maximumLength, final char[] bb, final int initLength) {
        this.buffer = new char[initLength];
        System.arraycopy(bb, 0, this.buffer, 0, bb.length);
        this.length = bb.length;
        this.offset = 0;
        this.maximumLength = maximumLength;
    }

    public CharBuffer(final File f) throws IOException {
        // initially fill the buffer with the content of a file
        if (f.length() > Integer.MAX_VALUE) throw new IOException("file is too large for buffering");
        this.maximumLength = Integer.MAX_VALUE;

        this.length = 0;
        this.buffer = new char[(int) f.length()*2];
        this.offset = 0;

        FileReader fr = null;
        try {
			fr = new FileReader(f);
            final char[] temp = new char[256];
            int c;
            while ((c = fr.read(temp)) > 0) {
                this.append(temp,0,c);
            }
        } catch (final FileNotFoundException e) {
            throw new IOException("File not found: " + f.toString() + "; " + e.getMessage());
        } finally {
        	if(fr != null)
        		fr.close();
        }
    }

    public void clear() {
        this.buffer = new char[0];
        this.length = 0;
        this.offset = 0;
    }

    public int length() {
        return this.length;
    }

    public boolean isEmpty() {
        return this.length == 0;
    }

    private void grow(int minSize) {
        int newsize = 12 * Math.max(this.buffer.length, minSize) / 10; // grow by 20%
        char[] tmp = new char[newsize];
        System.arraycopy(this.buffer, this.offset, tmp, 0, this.length);
        this.buffer = tmp;
        this.offset = 0;
    }

    @Override
    public void write(final int b) {
        write((char)b);
    }

    public void write(final char b) {
        if (this.buffer.length > this.maximumLength) return;
        if (this.offset + this.length + 1 > this.buffer.length) grow(this.offset + this.length + 1);
        this.buffer[this.offset + this.length++] = b;
    }

    @Override
    public void write(final char[] bb) {
        write(bb, 0, bb.length);
    }

    @Override
    public void write(final char[] bb, final int of, final int le) {
        if (this.buffer.length > this.maximumLength) return;
        if (this.offset + this.length + le > this.buffer.length) grow(this.offset + this.length + le);
        System.arraycopy(bb, of, this.buffer, this.offset + this.length, le);
        this.length += le;
    }

    private static final char SPACE = ' ';
    private static final char CR = (char) 13;
    private static final char LF = (char) 10;

    public CharBuffer appendSpace() {
        write(SPACE);
        return this;
    }

    public CharBuffer appendCR() {
        write(CR);
        return this;
    }

    public CharBuffer appendLF() {
        write(LF);
        return this;
    }

    public CharBuffer append(final int i) {
        write((char) i);
        return this;
    }

    public CharBuffer append(final char[] bb) {
        write(bb, 0, bb.length);
        return this;
    }

    public CharBuffer append(final char[] bb, final int of, final int le) {
        write(bb, of, le);
        return this;
    }

    @Override
    public CharBuffer append(final char c) {
        write(c);
        return this;
    }

    public CharBuffer append(final String s) {
        final char[] temp = new char[s.length()];
        s.getChars(0, temp.length, temp, 0);
        write(temp, 0, temp.length);
        return this;
    }

    public CharBuffer append(final String s, final int off, final int len) {
        final char[] temp = new char[len];
        s.getChars(off, (off + len), temp, 0);
        write(temp, 0, len);
        return this;
    }

    public CharBuffer append(final CharBuffer bb) {
        write(bb.buffer, bb.offset, bb.length);
        return this;
    }

    public char charAt(final int pos) {
        if (pos < 0) throw new IndexOutOfBoundsException();
        if (pos > this.length) throw new IndexOutOfBoundsException();
        return this.buffer[this.offset + pos];
    }

    public void deleteCharAt(final int pos) {
        if (pos < 0) return;
        if (pos >= this.length) return;
        if (pos == this.length - 1) {
            this.length--;
        } else {
            System.arraycopy(this.buffer, this.offset + pos + 1, this.buffer, this.offset + pos, this.length - pos - 1);
        }
    }

    public int indexOf(final char b) {
        return indexOf(b, 0);
    }

    public int indexOf(final char[] bs) {
        return indexOf(bs, 0);
    }

    public int indexOf(final char b, final int start) {
        if (start >= this.length) return -1;
        for (int i = start; i < this.length; i++) if (this.buffer[this.offset + i] == b) return i;
        return -1;
    }

    public int indexOf(final char[] bs, final int start) {
        if (start + bs.length > this.length) return -1;
        loop: for (int i = start; i <= this.length - bs.length; i++) {
            // first test only first char
            if (this.buffer[this.offset + i] != bs[0]) continue loop;

            // then test all remaining char
            for (int j = 1; j < bs.length; j++) {
                if (this.buffer[this.offset + i + j] != bs[j]) continue loop;
            }

            // found hit
            return i;
        }
        return -1;
    }

    public static int indexOf(final char[] b, final char c) {
        return indexOf(b, 0, c);
    }

    public static int indexOf(final char[] b, final int offset, final char c) {
        for (int i = offset; i < b.length; i++) if (b[i] == c) return i;
        return -1;
    }

    public static int indexOf(final char[] b, final char[] s) {
        return indexOf(b, 0, s);
    }

    public static int indexOf(final char[] b, final int start, final char[] bs) {
        if (start + bs.length > b.length) return -1;
        loop: for (int i = start; i <= b.length - bs.length; i++) {
            // first test only first char
            if (b[i] != bs[0]) continue loop;

            // then test all remaining char
            for (int j = 1; j < bs.length; j++) {
                if (b[i + j] != bs[j]) continue loop;
            }

            // found hit
            return i;
        }
        return -1;
    }

    public int lastIndexOf(final char b) {
        for (int i = this.length - 1; i >= 0; i--) if (this.buffer[this.offset + i] == b) return i;
        return -1;
    }

    public boolean startsWith(final char[] bs) {
        if (this.length < bs.length) return false;
        for (int i = 0; i < bs.length; i++) {
            if (this.buffer[this.offset + i] != bs[i]) return false;
        }
        return true;
    }

    public char[] getChars() {
        return getChars(0);
    }

    public char[] getChars(final int start) {
        return getChars(start, this.length);
    }

    public char[] getChars(final int start, final int end) {
        // start is inclusive, end is exclusive
        if (end > this.length) throw new IndexOutOfBoundsException("getBytes: end > length");
        if (start > this.length) throw new IndexOutOfBoundsException("getBytes: start > length");
        final char[] tmp = new char[end - start];
        System.arraycopy(this.buffer, this.offset + start, tmp, 0, end - start);
        return tmp;
    }

    public byte[] getBytes() {
        return UTF8.getBytes(this.toString());
    }

    public CharBuffer trim(final int start) {
        // the end value is outside (+1) of the wanted target array
        if (start > this.length) throw new IndexOutOfBoundsException("trim: start > length");
        this.offset = this.offset + start;
        this.length = this.length - start;
        return this;
    }

    public CharBuffer trim(final int start, final int end) {
        // the end value is outside (+1) of the wanted target array
        if (start > this.length) throw new IndexOutOfBoundsException("trim: start > length");
        if (end > this.length) throw new IndexOutOfBoundsException("trim: end > length");
        if (start > end) throw new IndexOutOfBoundsException("trim: start > end");
        this.offset = this.offset + start;
        this.length = end - start;
        return this;
    }

    public CharBuffer trim() {
        int l = 0;
        while ((l < this.length) && (this.buffer[this.offset + l] <= ' ')) l++;
        int r = this.length;
        while ((r > 0) && (this.buffer[this.offset + r - 1] <= ' ')) r--;
        if (l > r) r = l;
        return trim(l, r);
    }

    public boolean isWhitespace(final boolean includeNonLetterBytes) {
        // returns true, if trim() would result in an empty serverByteBuffer
        if (includeNonLetterBytes) {
            char b;
            for (int i = 0; i < this.length; i++) {
                b = this.buffer[this.offset + i];
                if (((b >= '0') && (b <= '9')) || ((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z'))) return false;
            }
        } else {
            for (int i = 0; i < this.length; i++) if (this.buffer[this.offset + i] > 32) return false;
        }
        return true;
    }

    public int whitespaceStart(final boolean includeNonLetterBytes) {
        // returns number of whitespace char at the beginning of text
        if (includeNonLetterBytes) {
            char b;
            for (int i = 0; i < this.length; i++) {
                b = this.buffer[this.offset + i];
                if (((b >= '0') && (b <= '9')) || ((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z'))) return i;
            }
        } else {
            for (int i = 0; i < this.length; i++) if (this.buffer[this.offset + i] > 32) return i;
        }
        return this.length;
    }

    public int whitespaceEnd(final boolean includeNonLetterBytes) {
        // returns position of whitespace at the end of text
        if (includeNonLetterBytes) {
            char b;
            for (int i = this.length - 1; i >= 0; i--) {
                b = this.buffer[this.offset + i];
                if (((b >= '0') && (b <= '9')) || ((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z'))) return i + 1;
            }
        } else {
            for (int i = this.length - 1; i >= 0; i--) if (this.buffer[this.offset + i] > 32) return i + 1;
        }
        return 0;
    }


    @Override
    public String toString() {
        return new String(this.buffer, this.offset, this.length);
    }

    public String toString(final int left, final int rightbound) {
        return new String(this.buffer, this.offset + left, rightbound - left);
    }

    public Properties propParser() {
        // extract a=b or a="b" - relations from the buffer
        int pos = this.offset;
        int start;
        String key;
        final Properties p = new Properties();
        // eat up spaces at beginning
        while ((pos < this.length) && (this.buffer[pos] <= 32)) pos++;
        while (pos < this.length) {
            // pos is at start of next key
            start = pos;
            while ((pos < this.length) && (this.buffer[pos] != equal)) pos++;
            if (pos >= this.length) break; // this is the case if we found no equal
            key = new String(this.buffer, start, pos - start).trim().toLowerCase();
            // we have a key
            pos++;
            // find start of value
            while ((pos < this.length) && (this.buffer[pos] <= 32)) pos++;
            // doublequotes are obligatory. However, we want to be fuzzy if they
            // are ommittet
            if (pos >= this.length) {
                // error case: input ended too early
                break;
            } else if (this.buffer[pos] == doublequote) {
                // search next doublequote
                pos++;
                start = pos;
                while ((pos < this.length) && (this.buffer[pos] != doublequote)) pos++;
                if (pos >= this.length) break; // this is the case if we found no parent doublequote
                p.setProperty(key, new String(this.buffer, start, pos - start).trim());
                pos++;
            } else if (this.buffer[pos] == singlequote) {
                // search next singlequote
                pos++;
                start = pos;
                while ((pos < this.length) && (this.buffer[pos] != singlequote)) pos++;
                if (pos >= this.length) break; // this is the case if we found no parent singlequote
                p.setProperty(key, new String(this.buffer, start, pos - start).trim());
                pos++;
            } else {
                // search next whitespace
                start = pos;
                while ((pos < this.length) && (this.buffer[pos] > 32)) pos++;
                p.setProperty(key, new String(this.buffer, start, pos - start).trim());
            }
            // pos should point now to a whitespace: eat up spaces
            while ((pos < this.length) && (this.buffer[pos] <= 32)) pos++;
            // go on with next loop
        }
        return p;
    }

    public static boolean equals(final char[] buffer, final char[] pattern) {
        return equals(buffer, 0, pattern);
    }

    public static boolean equals(final char[] buffer, final int offset, final char[] pattern) {
        // compares two char arrays: true, if pattern appears completely at offset position
        if (buffer.length < offset + pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (buffer[offset + i] != pattern[i]) return false;
        return true;
    }

    public void reset() {
        this.length = 0;
        this.offset = 0;
    }

    /**
     * call trimToSize() whenever a CharBuffer is not extended any more and is kept to store the content permanently
     */
    public void trimToSize() {
        final char[] v = new char[this.length];
        System.arraycopy(this.buffer, this.offset, v, 0, this.length);
        this.buffer = v;
    }

    public char toCharArray()[] {
        final char[] newbuf = new char[this.length];
        System.arraycopy(this.buffer, 0, newbuf, 0, this.length);
        return newbuf;
    }

    @Override
    public synchronized void close() {
        this.length = 0;
        this.offset = 0;
    	this.buffer = null; // assist with garbage collection
    }

    @Override
    public void flush() {
        trimToSize();
    }

}
