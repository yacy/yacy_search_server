// serverByteBuffer.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 11.03.2004
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

public final class serverByteBuffer extends OutputStream {
    
    public static final byte singlequote = (byte) 39;
    public static final byte doublequote = (byte) 34;
    public static final byte equal       = (byte) '=';
    
    private byte[] buffer;
    private int offset;
    private int length;

    
    public serverByteBuffer() {
        buffer = new byte[10];
        length = 0;
        offset = 0;
    }
    
    public serverByteBuffer(final int initLength) {
        this.buffer = new byte[initLength];
        this.length = 0;
        this.offset = 0;
    }        
    
    public serverByteBuffer(final byte[] bb) {
        buffer = bb;
        length = bb.length;
        offset = 0;
    }

    public serverByteBuffer(final byte[] bb, final int initLength) {
        this.buffer = new byte[initLength];
        System.arraycopy(bb, 0, buffer, 0, bb.length);
        length = bb.length;
        offset = 0;
    }
    
    public serverByteBuffer(final byte[] bb, final int of, final int le) {
        if (of * 2 > bb.length) {
            buffer = new byte[le];
            System.arraycopy(bb, of, buffer, 0, le);
            length = le;
            offset = 0;
        } else {
            buffer = bb;
            length = le;
            offset = of;
        }
    }

    public serverByteBuffer(final serverByteBuffer bb) {
        buffer = bb.buffer;
        length = bb.length;
        offset = bb.offset;
    }

    public serverByteBuffer(final File f) throws IOException {
    // initially fill the byte buffer with the content of a file
    if (f.length() > Integer.MAX_VALUE) throw new IOException("file is too large for buffering");

    length = (int) f.length();
    buffer = new byte[length];
    offset = 0;

    try {
        final FileInputStream fis = new FileInputStream(f);
//        byte[] buf = new byte[512];
//        int p = 0;
//        while ((l = fis.read(buf)) > 0) {
//        System.arraycopy(buf, 0, buffer, p, l);
//        p += l;
        /*int l =*/ fis.read(buffer);
//        }
        fis.close();
    } catch (final FileNotFoundException e) {
        throw new IOException("File not found: " + f.toString() + "; " + e.getMessage());
    }
    }

    public void clear() {
    	// we keep the byte[] and just set the pointer to write positions to zero
        this.length = 0;
        this.offset = 0;
    }
    
    public int length() {
        return length;
    }

    private void grow() {
        int newsize = buffer.length * 2 + 1;
        if (newsize < 256) newsize = 256;
        byte[] tmp = new byte[newsize];
        System.arraycopy(buffer, offset, tmp, 0, length);
        buffer = tmp;
        tmp = null;
        offset = 0;
    }

    public void write(final int b) {
        write((byte) (b & 0xff));
    }
    
    public void write(final byte b) {
        if (offset + length + 1 > buffer.length) grow();
        buffer[offset + length++] = b;
    }
    
    public void write(final byte[] bb) {
        write(bb, 0, bb.length);
    }
    
    public void write(final byte[] bb, final int of, final int le) {
        while (offset + length + le > buffer.length) grow();
        System.arraycopy(bb, of, buffer, offset + length, le);
        length += le;
    }
    
    // overwrite does not increase the 'length' write position pointer!
    
    public void overwrite(final int pos, final int b) {
        overwrite(pos, (byte) (b & 0xff));
    }
    
    public void overwrite(final int pos, final byte b) {
        if (offset + pos + 1 > buffer.length) grow();
        buffer[offset + pos] = b;
        if (pos >= length) length = pos + 1;
    }
    
    public void overwrite(final int pos, final byte[] bb) {
        overwrite(pos, bb, 0, bb.length);
    }
    
    public void overwrite(final int pos, final byte[] bb, final int of, final int le) {
        while (offset + pos + le > buffer.length) grow();
        System.arraycopy(bb, of, buffer, offset + pos, le);
        if (pos + le > length) length = pos + le;
    }
    
    public serverByteBuffer append(final byte b) {
        write(b);
        return this;
    }

    public serverByteBuffer append(final int i) {
        write((byte) (i & 0xFF));
        return this;
    }

    public serverByteBuffer append(final byte[] bb) {
        write(bb);
        return this;
    }

    public serverByteBuffer append(final byte[] bb, final int of, final int le) {
        write(bb, of, le);
        return this;
    }

    public serverByteBuffer append(final String s) {
        return append(s.getBytes());
    }
    
    public serverByteBuffer append(final String s, final String charset) throws UnsupportedEncodingException {
        return append(s.getBytes(charset));
    }    

    public serverByteBuffer append(final serverByteBuffer bb) {
        return append(bb.buffer, bb.offset, bb.length);
    }

    public serverByteBuffer append(final Object o) {
        if (o instanceof String) return append((String) o);
        if (o instanceof byte[]) return append((byte[]) o);
        return null;
    }
    
    public byte byteAt(final int pos) {
        if (pos > length) return -1;
        return buffer[offset + pos];
    }

    public void deleteByteAt(final int pos) {
        if (pos < 0) return;
        if (pos >= length) return;
        if (pos == length - 1) {
            length--;
        } else {
            System.arraycopy(buffer, offset + pos + 1, buffer, offset + pos, length - pos - 1);
        }
    }
    
    public int indexOf(final byte b) {
        return indexOf(b, 0);
    }

    public int indexOf(final byte[] bs) {
        return indexOf(bs, 0);
    }

    public int indexOf(final byte b, final int start) {
        if (start >= length) return -1;
        for (int i = start; i < length; i++) if (buffer[offset + i] == b) return i;
        return -1;
    }

    public int indexOf(final byte[] bs, final int start) {
        if (start + bs.length > length) return -1;
        loop: for (int i = start; i <= length - bs.length; i++) {
            // first test only first byte
            if (buffer[offset + i] != bs[0]) continue loop;
            
            // then test all remaining bytes
            for (int j = 1; j < bs.length; j++) {
                if (buffer[offset + i + j] != bs[j]) continue loop;
            }
            
            // found hit
            return i;
        }
        return -1;
    }

    public int lastIndexOf(final byte b) {
        for (int i = length - 1; i >= 0; i--) if (buffer[offset + i] == b) return i;
        return -1;
    }

    public boolean startsWith(final byte[] bs) {
        return startsWith(bs, 0);
    }
    
    public boolean startsWith(final byte[] bs, final int start) {
        if (length - start < bs.length) return false;
        for (int i = 0; i < bs.length; i++) {
            if (buffer[offset + i + start] != bs[i]) return false;
        }
        return true;
    }
    
    public byte[] getBytes() {
        return getBytes(0);
    }

    public byte[] getBytes(final int start) {
        return getBytes(start, this.length);
    }
    
    public byte[] getBytes(final int start, final int len) {
        // start is inclusive, end is exclusive
        if (len > length) throw new IndexOutOfBoundsException("getBytes: len > length");
        if (start > length) throw new IndexOutOfBoundsException("getBytes: start > length");
        if ((start == 0) && (len == length) && (len == buffer.length)) return buffer;
        final byte[] tmp = new byte[len];
        System.arraycopy(buffer, offset + start, tmp, 0, len);
        return tmp;
    }
    
    public serverByteBuffer trim(final int start) {
        trim(start, this.length - start);
        return this;
    }

    public serverByteBuffer trim(final int start, final int len) {
        if (start + len > this.length) throw new IndexOutOfBoundsException("trim: start + len > length; this.offset = " + this.offset + ", this.length = " + this.length + ", start = " + start + ", len = " + len);
        this.offset = this.offset + start;
        this.length = len;
        return this;
    }

    public serverByteBuffer trim() {
        int l = 0;
        while ((l < length) && (buffer[offset + l] <= 32)) {
            l++;
        }
        int r = length - 1;
        while ((r > l) && (buffer[offset + r] <= 32)) r--;
        return trim(l, r - l + 1);
    }
    
    public int isUTF8char(final int start) {
        // a sequence of bytes is a utf-8 character, if one of the following 4 conditions is true:
        // - ASCII equivalence range; (first) byte begins with zero
        // - first byte begins with 110, the following byte begins with 10
        // - first byte begins with 1110, the following two bytes begin with 10
        // - First byte begins with 11110, the following three bytes begin with 10
        // if an utf-8 sequence is detected, the length of the sequence is returned. -1 othervise
        if ((start < length) &&
            ((buffer[offset + start] & 0x80) != 0)) return 1;
        if ((start < length - 1) &&
            ((buffer[offset + start    ] & 0xE0) == 0xC0) &&
            ((buffer[offset + start + 1] & 0xC0) == 0x80)) return 2;
        if ((start < length - 2) &&
            ((buffer[offset + start    ] & 0xF0) == 0xE0) &&
            ((buffer[offset + start + 1] & 0xC0) == 0x80) &&
            ((buffer[offset + start + 2] & 0xC0) == 0x80)) return 3;
        if ((start < length - 3) &&
            ((buffer[offset + start    ] & 0xF8) == 0xF0) &&
            ((buffer[offset + start + 1] & 0xC0) == 0x80) &&
            ((buffer[offset + start + 2] & 0xC0) == 0x80) &&
            ((buffer[offset + start + 3] & 0xC0) == 0x80)) return 4;
        return -1;
    }

    public boolean isWhitespace(final boolean includeNonLetterBytes) {
        // returns true, if trim() would result in an empty serverByteBuffer
        if (includeNonLetterBytes) {
            byte b;
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
        // returns number of whitespace bytes at the beginning of text
        if (includeNonLetterBytes) {
            byte b;
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
            byte b;
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
    
    public String toString(final String charsetName) {
        try {
            return new String(this.getBytes(),charsetName);
        } catch (final UnsupportedEncodingException e) {
            return new String(this.getBytes());
        }
    }

    public String toString(final int left, final int rightbound) {
        return new String(buffer, offset + left, rightbound - left);
    }

    public Properties propParser(final String charset) {
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
            try {
                key = new String(buffer, start, pos - start,charset).trim().toLowerCase();
            } catch (final UnsupportedEncodingException e1) {
                key = new String(buffer, start, pos - start).trim().toLowerCase();
            }
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
                try {
                    p.setProperty(key, new String(buffer, start, pos - start,charset).trim());
                } catch (final UnsupportedEncodingException e) {
                    p.setProperty(key, new String(buffer, start, pos - start).trim());
                } 
                pos++;
            } else if (buffer[pos] == singlequote) {
                // search next singlequote
                pos++;
                start = pos;
                while ((pos < length) && (buffer[pos] != singlequote)) pos++;
                if (pos >= length) break; // this is the case if we found no parent singlequote
                try {
                    p.setProperty(key, new String(buffer, start, pos - start,charset).trim());
                } catch (final UnsupportedEncodingException e) {
                    p.setProperty(key, new String(buffer, start, pos - start).trim());
                }
                pos++;
            } else {
                // search next whitespace
                start = pos;
                while ((pos < length) && (buffer[pos] > 32)) pos++;
                try {
                    p.setProperty(key, new String(buffer, start, pos - start,charset).trim());
                } catch (final UnsupportedEncodingException e) {
                    p.setProperty(key, new String(buffer, start, pos - start).trim());
                }
            }
            // pos should point now to a whitespace: eat up spaces
            while ((pos < length) && (buffer[pos] <= 32)) pos++;
            // go on with next loop
        }
        return p;
    }
    
    public static boolean equals(final byte[] buffer, final byte[] pattern) {
        return equals(buffer, 0, pattern);
    }
    
    public static boolean equals(final byte[] buffer, final int offset, final byte[] pattern) {
        // compares two byte arrays: true, if pattern appears completely at offset position
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
        final byte[] v = new byte[newSize];
        System.arraycopy(this.buffer,0,v,0,newSize > this.buffer.length ? this.buffer.length : newSize);
        this.buffer = v;          
    }
    
    public void writeTo(final OutputStream dest) throws IOException {
    	dest.write(this.buffer, this.offset, this.length);
        dest.flush();
    }
        
}