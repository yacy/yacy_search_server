// ByteBuffer.java
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

package net.yacy.kelondro.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import net.yacy.cora.document.UTF8;

public final class ByteBuffer extends OutputStream {

    public static final byte singlequote = (byte) 39;
    public static final byte doublequote = (byte) 34;
    public static final byte equal       = (byte) '=';

    private byte[] buffer;
    private int offset;
    private int length;


    public ByteBuffer() {
        this.buffer = new byte[10];
        this.length = 0;
        this.offset = 0;
    }

    public ByteBuffer(final int initLength) {
        this.buffer = new byte[initLength];
        this.length = 0;
        this.offset = 0;
    }

    public ByteBuffer(final byte[] bb) {
        this.buffer = bb;
        this.length = bb.length;
        this.offset = 0;
    }

    public ByteBuffer(final String s) {
        this.buffer = UTF8.getBytes(s);
        this.length = this.buffer.length;
        this.offset = 0;
    }

    public ByteBuffer(final byte[] bb, final int initLength) {
        this.buffer = new byte[initLength];
        System.arraycopy(bb, 0, this.buffer, 0, bb.length);
        this.length = bb.length;
        this.offset = 0;
    }

    public ByteBuffer(final byte[] bb, final int of, final int le) {
        if (of * 2 > bb.length) {
            this.buffer = new byte[le];
            System.arraycopy(bb, of, this.buffer, 0, le);
            this.length = le;
            this.offset = 0;
        } else {
            this.buffer = bb;
            this.length = le;
            this.offset = of;
        }
    }

    public ByteBuffer(final ByteBuffer bb) {
        this.buffer = bb.buffer;
        this.length = bb.length;
        this.offset = bb.offset;
    }

    public ByteBuffer(final File f) throws IOException {
    // initially fill the byte buffer with the content of a file
    if (f.length() > Integer.MAX_VALUE) throw new IOException("file is too large for buffering");

    this.length = (int) f.length();
    this.buffer = new byte[this.length];
    this.offset = 0;

    try {
        final FileInputStream fis = new FileInputStream(f);
//        byte[] buf = new byte[512];
//        int p = 0;
//        while ((l = fis.read(buf)) > 0) {
//        System.arraycopy(buf, 0, buffer, p, l);
//        p += l;
        /*int l =*/ fis.read(this.buffer);
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
        return this.length;
    }

    private void grow() {
        int newsize = this.buffer.length * 2 + 1;
        if (newsize < 256) newsize = 256;
        final byte[] tmp = new byte[newsize];
        System.arraycopy(this.buffer, this.offset, tmp, 0, this.length);
        this.buffer = tmp;
        this.offset = 0;
    }

    @Override
    public void write(final int b) {
        write((byte) (b & 0xff));
    }

    public void write(final char b) {
        write((byte) b);
    }

    public void write(final byte b) {
        if (this.offset + this.length + 1 > this.buffer.length) grow();
        this.buffer[this.offset + this.length++] = b;
    }

    @Override
    public void write(final byte[] bb) {
        write(bb, 0, bb.length);
    }

    @Override
    public void write(final byte[] bb, final int of, final int le) {
        while (this.offset + this.length + le > this.buffer.length) grow();
        System.arraycopy(bb, of, this.buffer, this.offset + this.length, le);
        this.length += le;
    }

    // overwrite does not increase the 'length' write position pointer!

    public void overwrite(final int pos, final int b) {
        overwrite(pos, (byte) (b & 0xff));
    }

    public void overwrite(final int pos, final byte b) {
        if (this.offset + pos + 1 > this.buffer.length) grow();
        this.buffer[this.offset + pos] = b;
        if (pos >= this.length) this.length = pos + 1;
    }

    public void overwrite(final int pos, final byte[] bb) {
        overwrite(pos, bb, 0, bb.length);
    }

    public void overwrite(final int pos, final byte[] bb, final int of, final int le) {
        while (this.offset + pos + le > this.buffer.length) grow();
        System.arraycopy(bb, of, this.buffer, this.offset + pos, le);
        if (pos + le > this.length) this.length = pos + le;
    }

    public ByteBuffer append(final byte b) {
        write(b);
        return this;
    }

    public ByteBuffer append(final char b) {
        write(b);
        return this;
    }

    public ByteBuffer append(final int i) {
        write((byte) (i & 0xFF));
        return this;
    }

    public ByteBuffer append(final byte[] bb) {
        write(bb, 0, bb.length);
        return this;
    }

    public ByteBuffer append(final byte[] bb, final int of, final int le) {
        write(bb, of, le);
        return this;
    }

    public ByteBuffer append(final String s) {
        return append(UTF8.getBytes(s));
    }

    public ByteBuffer append(final String s, final String charset) throws UnsupportedEncodingException {
        return append(s.getBytes(charset));
    }

    public ByteBuffer append(final ByteBuffer bb) {
        return append(bb.buffer, bb.offset, bb.length);
    }

    public ByteBuffer append(final Object o) {
        if (o instanceof String) return append((String) o);
        if (o instanceof byte[]) return append((byte[]) o);
        return null;
    }

    public byte byteAt(final int pos) {
        if (pos > this.length) return -1;
        return this.buffer[this.offset + pos];
    }

    public void deleteByteAt(final int pos) {
        if (pos < 0) return;
        if (pos >= this.length) return;
        if (pos == this.length - 1) {
            this.length--;
        } else {
            System.arraycopy(this.buffer, this.offset + pos + 1, this.buffer, this.offset + pos, this.length - pos - 1);
        }
    }

    public int indexOf(final byte b) {
        return indexOf(b, 0);
    }

    public int indexOf(final byte[] bs) {
        return indexOf(bs, 0);
    }

    public int indexOf(final byte b, final int start) {
        if (start >= this.length) return -1;
        for (int i = start; i < this.length; i++) if (this.buffer[this.offset + i] == b) return i;
        return -1;
    }

    public int indexOf(final byte[] bs, final int start) {
        if (start + bs.length > this.length) return -1;
        loop: for (int i = start; i <= this.length - bs.length; i++) {
            // first test only first byte
            if (this.buffer[this.offset + i] != bs[0]) continue loop;

            // then test all remaining bytes
            for (int j = 1; j < bs.length; j++) {
                if (this.buffer[this.offset + i + j] != bs[j]) continue loop;
            }

            // found hit
            return i;
        }
        return -1;
    }

    public int lastIndexOf(final byte b) {
        for (int i = this.length - 1; i >= 0; i--) if (this.buffer[this.offset + i] == b) return i;
        return -1;
    }

    public boolean startsWith(final byte[] bs) {
        return startsWith(bs, 0);
    }

    public boolean startsWith(final byte[] bs, final int start) {
        if (this.length - start < bs.length) return false;
        for (int i = 0; i < bs.length; i++) {
            if (this.buffer[this.offset + i + start] != bs[i]) return false;
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
        if (len > this.length) throw new IndexOutOfBoundsException("getBytes: len > length");
        if (start > this.length) throw new IndexOutOfBoundsException("getBytes: start > length");
        if ((start == 0) && (len == this.length) && (len == this.buffer.length)) return this.buffer;
        final byte[] tmp = new byte[len];
        System.arraycopy(this.buffer, this.offset + start, tmp, 0, len);
        return tmp;
    }

    public ByteBuffer trim(final int start) {
        this.offset += start;
        this.length -= start;
        return this;
    }

    public ByteBuffer trim(final int start, final int len) {
        if (start + len > this.length) throw new IndexOutOfBoundsException("trim: start + len > length; this.offset = " + this.offset + ", this.length = " + this.length + ", start = " + start + ", len = " + len);
        this.offset = this.offset + start;
        this.length = len;
        return this;
    }

    public ByteBuffer trim() {
        int l = 0;
        while ((l < this.length) && (this.buffer[this.offset + l] <= 32)) {
            l++;
        }
        int r = this.length - 1;
        while ((r > l) && (this.buffer[this.offset + r] <= 32)) r--;
        return trim(l, r - l + 1);
    }

    public int isUTF8char(final int start) {
        // a sequence of bytes is a utf-8 character, if one of the following 4 conditions is true:
        // - ASCII equivalence range; (first) byte begins with zero
        // - first byte begins with 110, the following byte begins with 10
        // - first byte begins with 1110, the following two bytes begin with 10
        // - First byte begins with 11110, the following three bytes begin with 10
        // if an utf-8 sequence is detected, the length of the sequence is returned. -1 otherwise
        if ((start < this.length) &&
            ((this.buffer[this.offset + start] & 0x80) != 0)) return 1;
        if ((start < this.length - 1) &&
            ((this.buffer[this.offset + start    ] & 0xE0) == 0xC0) &&
            ((this.buffer[this.offset + start + 1] & 0xC0) == 0x80)) return 2;
        if ((start < this.length - 2) &&
            ((this.buffer[this.offset + start    ] & 0xF0) == 0xE0) &&
            ((this.buffer[this.offset + start + 1] & 0xC0) == 0x80) &&
            ((this.buffer[this.offset + start + 2] & 0xC0) == 0x80)) return 3;
        if ((start < this.length - 3) &&
            ((this.buffer[this.offset + start    ] & 0xF8) == 0xF0) &&
            ((this.buffer[this.offset + start + 1] & 0xC0) == 0x80) &&
            ((this.buffer[this.offset + start + 2] & 0xC0) == 0x80) &&
            ((this.buffer[this.offset + start + 3] & 0xC0) == 0x80)) return 4;
        return -1;
    }

    public boolean isWhitespace(final boolean includeNonLetterBytes) {
        // returns true, if trim() would result in an empty serverByteBuffer
        if (includeNonLetterBytes) {
            byte b;
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
        // returns number of whitespace bytes at the beginning of text
        if (includeNonLetterBytes) {
            byte b;
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
            byte b;
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
        return UTF8.String(this.buffer, this.offset, this.length);
    }

    public String toString(final int left, final int length) {
        return UTF8.String(this.buffer, this.offset + left, length);
    }

    public StringBuilder toStringBuilder(final int left, final int length, final int sblength) {
        assert sblength >= length;
        final StringBuilder sb = new StringBuilder(sblength);
        int i = 0;
        sb.setLength(length);
        for (int j = left; j < left + length; j++) sb.setCharAt(i++, (char) this.buffer[this.offset + j]);
        return sb;
    }

    public Properties propParser(final String charset) {
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
            try {
                key = new String(this.buffer, start, pos - start, charset).trim().toLowerCase();
            } catch (final UnsupportedEncodingException e1) {
                key = UTF8.String(this.buffer, start, pos - start).trim().toLowerCase();
            }
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
                try {
                    p.setProperty(key, new String(this.buffer, start, pos - start,charset).trim());
                } catch (final UnsupportedEncodingException e) {
                    p.setProperty(key, UTF8.String(this.buffer, start, pos - start).trim());
                }
                pos++;
            } else if (this.buffer[pos] == singlequote) {
                // search next singlequote
                pos++;
                start = pos;
                while ((pos < this.length) && (this.buffer[pos] != singlequote)) pos++;
                if (pos >= this.length) break; // this is the case if we found no parent singlequote
                try {
                    p.setProperty(key, new String(this.buffer, start, pos - start,charset).trim());
                } catch (final UnsupportedEncodingException e) {
                    p.setProperty(key, UTF8.String(this.buffer, start, pos - start).trim());
                }
                pos++;
            } else {
                // search next whitespace
                start = pos;
                while ((pos < this.length) && (this.buffer[pos] > 32)) pos++;
                try {
                    p.setProperty(key, new String(this.buffer, start, pos - start,charset).trim());
                } catch (final UnsupportedEncodingException e) {
                    p.setProperty(key, UTF8.String(this.buffer, start, pos - start).trim());
                }
            }
            // pos should point now to a whitespace: eat up spaces
            while ((pos < this.length) && (this.buffer[pos] <= 32)) pos++;
            // go on with next loop
        }
        return p;
    }

    public static boolean equals(final byte[] buffer, final byte[] pattern) {
        // compares two byte arrays: true, if pattern appears completely at offset position
        if (buffer.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (buffer[i] != pattern[i]) return false;
        return true;
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

    public void writeTo(final OutputStream dest) throws IOException {
    	dest.write(this.buffer, this.offset, this.length);
        dest.flush();
    }

    public static boolean contains(final Collection<byte[]> collection, final byte[] key) {
        for (final byte[] v: collection) {
            if (equals(v, key)) return true;
        }
        return false;
    }

    public static int remove(final Collection<byte[]> collection, final byte[] key) {
        Iterator<byte[]> i = collection.iterator();
        byte[] v;
        int c = 0;
        while (i.hasNext()) {
            v = i.next();
            if (equals(v, key)) {
                i.remove();
                c++;
            }
        }
        return c;
    }

    public static List<byte[]> split(final byte[] b, final byte s) {
        final ArrayList<byte[]> a = new ArrayList<byte[]>();
        int c = 0;
        loop: while (c < b.length) {
            int i = c;
            search: while (i < b.length) {
                if (b[i] == s) break search;
                i++;
            }
            if (i >= b.length) {
                // nothing found; this is the end of the search
                final byte[] bb = new byte[b.length - c];
                System.arraycopy(b, c, bb, 0, bb.length);
                a.add(bb);
                break loop;
            } else {
                // found a separator
                final byte[] bb = new byte[i - c];
                System.arraycopy(b, c, bb, 0, bb.length);
                a.add(bb);
                c = i + 1;
            }
        }
        return a;
    }

}