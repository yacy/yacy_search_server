// serverByteBuffer.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

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
    
    public serverByteBuffer(int initLength) {
        this.buffer = new byte[initLength];
        this.length = 0;
        this.offset = 0;
    }        
    
    public serverByteBuffer(byte[] bb) {
        buffer = bb;
        length = bb.length;
        offset = 0;
    }

    public serverByteBuffer(byte[] bb, int initLength) {
        this.buffer = new byte[initLength];
        System.arraycopy(bb, 0, buffer, 0, bb.length);
        length = bb.length;
        offset = 0;
    }
    
    public serverByteBuffer(byte[] bb, int of, int le) {
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

    public serverByteBuffer(serverByteBuffer bb) {
        buffer = bb.buffer;
        length = bb.length;
        offset = bb.offset;
    }

    public serverByteBuffer(File f) throws IOException {
    // initially fill the byte buffer with the content of a file
    if (f.length() > (long) Integer.MAX_VALUE) throw new IOException("file is too large for buffering");

    length = (int) f.length();
    buffer = new byte[length];
    offset = 0;

    try {
        FileInputStream fis = new FileInputStream(f);
//        byte[] buf = new byte[512];
//        int p = 0;
//        while ((l = fis.read(buf)) > 0) {
//        System.arraycopy(buf, 0, buffer, p, l);
//        p += l;
        /*int l =*/ fis.read(buffer);
//        }
        fis.close();
    } catch (FileNotFoundException e) {
        throw new IOException("File not found: " + f.toString() + "; " + e.getMessage());
    }
    }

    public void clear() {
        this.buffer = new byte[0];
        length = 0;
        offset = 0;
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

    public void write(int b) {
        write((byte) (b & 0xff));
    }
    
    public void write(byte b) {
        if (offset + length + 1 > buffer.length) grow();
        buffer[offset + length++] = b;
    }
    
    public void write(byte[] bb) {
        write(bb, 0, bb.length);
    }
    
    public void write(byte[] bb, int of, int le) {
        while (offset + length + le > buffer.length) grow();
        System.arraycopy(bb, of, buffer, offset + length, le);
        length += le;
    }
    
    public serverByteBuffer append(byte b) {
        write(b);
        return this;
    }

    public serverByteBuffer append(int i) {
        write((byte) (i & 0xFF));
        return this;
    }

    public serverByteBuffer append(byte[] bb) {
        write(bb);
        return this;
    }

    public serverByteBuffer append(byte[] bb, int of, int le) {
        write(bb, of, le);
        return this;
    }

    public serverByteBuffer append(String s) {
        return append(s.getBytes());
    }

    public serverByteBuffer append(serverByteBuffer bb) {
        return append(bb.buffer, bb.offset, bb.length);
    }

    public serverByteBuffer append(Object o) {
        if (o instanceof String) return append((String) o);
        if (o instanceof byte[]) return append((byte[]) o);
        return null;
    }
    
    public byte byteAt(int pos) {
        if (pos > length) return -1;
        return buffer[offset + pos];
    }

    public void deleteByteAt(int pos) {
        if (pos < 0) return;
        if (pos >= length) return;
        if (pos == length - 1) {
            length--;
        } else {
            System.arraycopy(buffer, offset + pos + 1, buffer, offset + pos, length - pos - 1);
        }
    }
    
    public int indexOf(byte b) {
        return indexOf(b, 0);
    }

    public int indexOf(byte[] bs) {
        return indexOf(bs, 0);
    }

    public int indexOf(byte b, int start) {
        if (start >= length) return -1;
        for (int i = start; i < length; i++) if (buffer[offset + i] == b) return i;
        return -1;
    }

    public int indexOf(byte[] bs, int start) {
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

    public int lastIndexOf(byte b) {
        for (int i = length - 1; i >= 0; i--) if (buffer[offset + i] == b) return i;
        return -1;
    }

    public boolean startsWith(byte[] bs) {
        if (length < bs.length) return false;
        for (int i = 0; i < bs.length; i++) {
            if (buffer[offset + i] != bs[i]) return false;
        }
        return true;
    }
    
    public byte[] getBytes() {
        return getBytes(0);
    }

    public byte[] getBytes(int start) {
        return getBytes(start, length);
    }

    public byte[] getBytes(int start, int end) {
        // start is inclusive, end is exclusive
        if (end > length) throw new IndexOutOfBoundsException("getBytes: end > length");
        if (start > length) throw new IndexOutOfBoundsException("getBytes: start > length");
        byte[] tmp = new byte[end - start];
        System.arraycopy(buffer, offset + start, tmp, 0, end - start);
        return tmp;
    }

    public serverByteBuffer trim(int start) {
        // the end value is outside (+1) of the wanted target array
        if (start > length) throw new IndexOutOfBoundsException("trim: start > length");
        offset = offset + start;
        length = length - start;
        return this;
    }

    public serverByteBuffer trim(int start, int end) {
        // the end value is outside (+1) of the wanted target array
        if (start > length) throw new IndexOutOfBoundsException("trim: start > length");
        if (end > length) throw new IndexOutOfBoundsException("trim: end > length");
        if (start > end) throw new IndexOutOfBoundsException("trim: start > end");
        offset = offset + start;
        length = end - start;
        return this;
    }

    public serverByteBuffer trim() {
        int l = 0;
        while ((l < length) && (buffer[offset + l] <= 32)) l++;
        int r = length;
        int u;
        while ((r > 0) && (buffer[offset + r - 1] <= 32)) {
            u = isUTF8char(r - 1);
            if (u > 0) {
                r += u - 1;
                break;
            }
            r--;
        }
        if (l > r) r = l;
        return trim(l, r);
    }
    
    public int isUTF8char(int start) {
        // a sequence of bytes is a utf-8 character, if one of the following 4 conditions is true:
        // - ASCII equivalence range; (first) byte begins with zero
        // - first byte begins with 110, the following byte begins with 10
        // - first byte begins with 1110, the following two bytes begin with 10
        // - First byte begins with 11110, the following three bytes begin with 10
        // if an utf-8 sequence is detected, the length of the sequence is returned. -1 othervise
        if ((start < length) &&
            ((buffer[offset + start] & 0x80) != 0)) return 1;
        if ((start < length - 1) &&
            ((buffer[offset + start    ] & 0xF0) == 0xC0) &&
            ((buffer[offset + start + 1] & 0xF0) == 0x80)) return 2;
        if ((start < length - 2) &&
            ((buffer[offset + start    ] & 0xF0) == 0xE0) &&
            ((buffer[offset + start + 1] & 0xF0) == 0x80) &&
            ((buffer[offset + start + 2] & 0xF0) == 0x80)) return 3;
        if ((start < length - 3) &&
            ((buffer[offset + start    ] & 0xF8) == 0xF0) &&
            ((buffer[offset + start + 1] & 0xF0) == 0x80) &&
            ((buffer[offset + start + 2] & 0xF0) == 0x80) &&
            ((buffer[offset + start + 3] & 0xF0) == 0x80)) return 4;
        return -1;
    }

    public boolean isWhitespace(boolean includeNonLetterBytes) {
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
    
    public int whitespaceStart(boolean includeNonLetterBytes) {
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
    
    public int whitespaceEnd(boolean includeNonLetterBytes) {
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
    
    public String toString(String charsetName) {
        try {
            return new String(this.getBytes(),charsetName);
        } catch (UnsupportedEncodingException e) {
            return new String(this.getBytes());
        }
    }

    public String toString(int left, int rightbound) {
        return new String(buffer, offset + left, rightbound - left);
    }

    public Properties propParser(String charset) {
        // extract a=b or a="b" - relations from the buffer
        int pos = offset;
        int start;
        String key;
        Properties p = new Properties();
        // eat up spaces at beginning
        while ((pos < length) && (buffer[pos] <= 32)) pos++;
        while (pos < length) {
            // pos is at start of next key
            start = pos;
            while ((pos < length) && (buffer[pos] != equal)) pos++;
            if (pos >= length) break; // this is the case if we found no equal
            try {
                key = new String(buffer, start, pos - start,charset).trim().toLowerCase();
            } catch (UnsupportedEncodingException e1) {
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
                } catch (UnsupportedEncodingException e) {
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
                } catch (UnsupportedEncodingException e) {
                    p.setProperty(key, new String(buffer, start, pos - start).trim());
                }
                pos++;
            } else {
                // search next whitespace
                start = pos;
                while ((pos < length) && (buffer[pos] > 32)) pos++;
                try {
                    p.setProperty(key, new String(buffer, start, pos - start,charset).trim());
                } catch (UnsupportedEncodingException e) {
                    p.setProperty(key, new String(buffer, start, pos - start).trim());
                }
            }
            // pos should point now to a whitespace: eat up spaces
            while ((pos < length) && (buffer[pos] <= 32)) pos++;
            // go on with next loop
        }
        return p;
    }
    
    public static boolean equals(byte[] buffer, byte[] pattern) {
        return equals(buffer, 0, pattern);
    }
    
    public static boolean equals(byte[] buffer, int offset, byte[] pattern) {
        // compares two byte arrays: true, if pattern appears completely at offset position
        if (buffer.length < offset + pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (buffer[offset + i] != pattern[i]) return false;
        return true;
    }

    public void reset() {
        this.length = 0;
        this.offset = 0;
    }        
    
    public void reset(int newSize) {  
        this.resize(newSize);
        this.reset();
    }         
     
    public void resize(int newSize) {
        if(newSize < 0) throw new IllegalArgumentException("Illegal array size: " + newSize);
        byte[] v = new byte[newSize];
        System.arraycopy(this.buffer,0,v,0,newSize > this.buffer.length ? this.buffer.length : newSize);
        this.buffer = v;          
    }
    
    public byte toByteArray()[] {
        byte[] newbuf = new byte[this.length];
        System.arraycopy(this.buffer, 0, newbuf, 0, this.length);
        return newbuf;
    }    
        
}