// serverCharBuffer.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: borg-0300 $
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

package de.anomic.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

public final class serverCharBuffer extends Writer {
    
    public static final char singlequote = '\'';
    public static final char doublequote = '"';
    public static final char equal       = '=';
    
    private char[] buffer;
    private int offset;
    private int length;

    
    public serverCharBuffer() {
        buffer = new char[10];
        length = 0;
        offset = 0;
    }
    
    public serverCharBuffer(final int initLength) {
        this.buffer = new char[initLength];
        this.length = 0;
        this.offset = 0;
    }        
    
    public serverCharBuffer(final char[] bb) {
        buffer = bb;
        length = bb.length;
        offset = 0;
    }

    public serverCharBuffer(final char[] bb, final int initLength) {
        this.buffer = new char[initLength];
        System.arraycopy(bb, 0, buffer, 0, bb.length);
        length = bb.length;
        offset = 0;
    }
    
    public serverCharBuffer(final char[] bb, final int of, final int le) {
        if (of * 2 > bb.length) {
            buffer = new char[le];
            System.arraycopy(bb, of, buffer, 0, le);
            length = le;
            offset = 0;
        } else {
            buffer = bb;
            length = le;
            offset = of;
        }
    }

    public serverCharBuffer(final serverCharBuffer bb) {
        buffer = bb.buffer;
        length = bb.length;
        offset = bb.offset;
    }

    public serverCharBuffer(final File f) throws IOException {
        // initially fill the buffer with the content of a file
        if (f.length() > Integer.MAX_VALUE) throw new IOException("file is too large for buffering");

        length = (int) f.length();
        buffer = new char[length*2];
        offset = 0;

        try {
            final FileReader fr = new FileReader(f);
            final char[] temp = new char[256];
            int c;
            while ((c = fr.read(temp)) > 0) {
                this.append(temp,0,c);
            }
        } catch (final FileNotFoundException e) {
            throw new IOException("File not found: " + f.toString() + "; " + e.getMessage());
        }
    }

    public void clear() {
        this.buffer = new char[0];
        length = 0;
        offset = 0;
    }
    
    public int length() {
        return length;
    }

    private void grow() {
        int newsize = buffer.length * 2 + 1;
        if (newsize < 256) newsize = 256;
        char[] tmp = new char[newsize];
        System.arraycopy(buffer, offset, tmp, 0, length);
        buffer = tmp;
        tmp = null;
        offset = 0;
    }

    public void write(final int b) {
        write((char)b);
    }
    
    public void write(final char b) {
        if (offset + length + 1 > buffer.length) grow();
        buffer[offset + length++] = b;
    }
    
    public void write(final char[] bb) {
        write(bb, 0, bb.length);
    }
    
    public void write(final char[] bb, final int of, final int le) {
        while (offset + length + le > buffer.length) grow();
        System.arraycopy(bb, of, buffer, offset + length, le);
        length += le;
    }

// do not use/implement the following method, a
// "overridden method is a bridge method"
// will occur
//    public serverCharBuffer append(char b) {
//        write(b);
//        return this;
//    }

    public serverCharBuffer append(final int i) {
        write((char) (i));
        return this;
    }

    public serverCharBuffer append(final char[] bb) {
        write(bb);
        return this;
    }

    public serverCharBuffer append(final char[] bb, final int of, final int le) {
        write(bb, of, le);
        return this;
    }

    public serverCharBuffer append(final String s) {
        return append(s,0,s.length());
    }    
    
    public serverCharBuffer append(final String s, final int off, final int len) {
        final char[] temp = new char[len];
        s.getChars(off, (off + len), temp, 0);        
        return append(temp);
    }
    
    public serverCharBuffer append(final serverCharBuffer bb) {
        return append(bb.buffer, bb.offset, bb.length);
    }

//    public serverCharBuffer append(Object o) {
//        if (o instanceof String) return append((String) o);
//        if (o instanceof char[]) return append((char[]) o);
//        return null;
//    }
    
    public char charAt(final int pos) {
        if (pos < 0) throw new IndexOutOfBoundsException();
        if (pos > length) throw new IndexOutOfBoundsException();
        return buffer[offset + pos];
    }

    public void deleteCharAt(final int pos) {
        if (pos < 0) return;
        if (pos >= length) return;
        if (pos == length - 1) {
            length--;
        } else {
            System.arraycopy(buffer, offset + pos + 1, buffer, offset + pos, length - pos - 1);
        }
    }
    
    public int indexOf(final char b) {
        return indexOf(b, 0);
    }

    public int indexOf(final char[] bs) {
        return indexOf(bs, 0);
    }

    public int indexOf(final char b, final int start) {
        if (start >= length) return -1;
        for (int i = start; i < length; i++) if (buffer[offset + i] == b) return i;
        return -1;
    }

    public int indexOf(final char[] bs, final int start) {
        if (start + bs.length > length) return -1;
        loop: for (int i = start; i <= length - bs.length; i++) {
            // first test only first char
            if (buffer[offset + i] != bs[0]) continue loop;
            
            // then test all remaining char
            for (int j = 1; j < bs.length; j++) {
                if (buffer[offset + i + j] != bs[j]) continue loop;
            }
            
            // found hit
            return i;
        }
        return -1;
    }

    public int lastIndexOf(final char b) {
        for (int i = length - 1; i >= 0; i--) if (buffer[offset + i] == b) return i;
        return -1;
    }

    public boolean startsWith(final char[] bs) {
        if (length < bs.length) return false;
        for (int i = 0; i < bs.length; i++) {
            if (buffer[offset + i] != bs[i]) return false;
        }
        return true;
    }
    
    public char[] getChars() {
        return getChars(0);
    }

    public char[] getChars(final int start) {
        return getChars(start, length);
    }

    public char[] getChars(final int start, final int end) {
        // start is inclusive, end is exclusive
        if (end > length) throw new IndexOutOfBoundsException("getBytes: end > length");
        if (start > length) throw new IndexOutOfBoundsException("getBytes: start > length");
        final char[] tmp = new char[end - start];
        System.arraycopy(buffer, offset + start, tmp, 0, end - start);
        return tmp;
    }

    public serverCharBuffer trim(final int start) {
        // the end value is outside (+1) of the wanted target array
        if (start > length) throw new IndexOutOfBoundsException("trim: start > length");
        offset = offset + start;
        length = length - start;
        return this;
    }

    public serverCharBuffer trim(final int start, final int end) {
        // the end value is outside (+1) of the wanted target array
        if (start > length) throw new IndexOutOfBoundsException("trim: start > length");
        if (end > length) throw new IndexOutOfBoundsException("trim: end > length");
        if (start > end) throw new IndexOutOfBoundsException("trim: start > end");
        offset = offset + start;
        length = end - start;
        return this;
    }

    public serverCharBuffer trim() {
        int l = 0;
        while ((l < length) && (buffer[offset + l] <= ' ')) l++;
        int r = length;
        while ((r > 0) && (buffer[offset + r - 1] <= ' ')) r--;
        if (l > r) r = l;
        return trim(l, r);
    }

    public boolean isWhitespace(final boolean includeNonLetterBytes) {
        // returns true, if trim() would result in an empty serverByteBuffer
        if (includeNonLetterBytes) {
            char b;
            for (int i = 0; i < length; i++) {
                b = buffer[offset + i];
                if (((b >= '0') && (b <= '9')) || ((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z'))) return false;
            }
        } else {
            for (int i = 0; i < length; i++) if (buffer[offset + i] > 32) return false;
        }
        return true;
    }
    
    public int whitespaceStart(final boolean includeNonLetterBytes) {
        // returns number of whitespace char at the beginning of text
        if (includeNonLetterBytes) {
            char b;
            for (int i = 0; i < length; i++) {
                b = buffer[offset + i];
                if (((b >= '0') && (b <= '9')) || ((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z'))) return i;
            }
        } else {
            for (int i = 0; i < length; i++) if (buffer[offset + i] > 32) return i;
        }
        return length;
    }
    
    public int whitespaceEnd(final boolean includeNonLetterBytes) {
        // returns position of whitespace at the end of text
        if (includeNonLetterBytes) {
            char b;
            for (int i = length - 1; i >= 0; i--) {
                b = buffer[offset + i];
                if (((b >= '0') && (b <= '9')) || ((b >= 'A') && (b <= 'Z')) || ((b >= 'a') && (b <= 'z'))) return i + 1;
            }
        } else {
            for (int i = length - 1; i >= 0; i--) if (buffer[offset + i] > 32) return i + 1;
        }
        return 0;
    }
    
    
    public String toString() {
        return new String(buffer, offset, length);
    }

    public String toString(final int left, final int rightbound) {
        return new String(buffer, offset + left, rightbound - left);
    }

    public Properties propParser() {
        // extract a=b or a="b" - relations from the buffer
        int pos = offset;
        int start;
        String key;
        final Properties p = new Properties();
        // eat up spaces at beginning
        while ((pos < length) && (buffer[pos] <= 32)) pos++;
        while (pos < length) {
            // pos is at start of next key
            start = pos;
            while ((pos < length) && (buffer[pos] != equal)) pos++;
            if (pos >= length) break; // this is the case if we found no equal
            key = new String(buffer, start, pos - start).trim().toLowerCase();
            // we have a key
            pos++;
            // find start of value
            while ((pos < length) && (buffer[pos] <= 32)) pos++;
            // doublequotes are obligatory. However, we want to be fuzzy if they
            // are ommittet
            if (pos >= length) {
                // error case: input ended too early
                break;
            } else if (buffer[pos] == doublequote) {
                // search next doublequote
                pos++;
                start = pos;
                while ((pos < length) && (buffer[pos] != doublequote)) pos++;
                if (pos >= length) break; // this is the case if we found no parent doublequote
                p.setProperty(key, new String(buffer, start, pos - start).trim());
                pos++;
            } else if (buffer[pos] == singlequote) {
                // search next singlequote
                pos++;
                start = pos;
                while ((pos < length) && (buffer[pos] != singlequote)) pos++;
                if (pos >= length) break; // this is the case if we found no parent singlequote
                p.setProperty(key, new String(buffer, start, pos - start).trim());
                pos++;
            } else {
                // search next whitespace
                start = pos;
                while ((pos < length) && (buffer[pos] > 32)) pos++;
                p.setProperty(key, new String(buffer, start, pos - start).trim());
            }
            // pos should point now to a whitespace: eat up spaces
            while ((pos < length) && (buffer[pos] <= 32)) pos++;
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
    
    public void reset(final int newSize) {  
        this.resize(newSize);
        this.reset();
    }         
     
    public void resize(final int newSize) {
        if(newSize < 0) throw new IllegalArgumentException("Illegal array size: " + newSize);
        final char[] v = new char[newSize];
        System.arraycopy(this.buffer,0,v,0,newSize > this.buffer.length ? this.buffer.length : newSize);
        this.buffer = v;          
    }
    
    public char toCharArray()[] {
        final char[] newbuf = new char[this.length];
        System.arraycopy(this.buffer, 0, newbuf, 0, this.length);
        return newbuf;
    }

    public void close() throws IOException {
        // TODO Auto-generated method stub        
    }

    public void flush() throws IOException {
        // TODO Auto-generated method stub        
    }    
        
}