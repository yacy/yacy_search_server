/**
 *  ByteBuffer
 *  Copyright 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 2004 on http://yacy.net
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.yacy.cora.document.encoding.UTF8;

public final class ByteBuffer extends OutputStream {

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

    public void clear() {
    	// we keep the byte[] and just set the pointer to write positions to zero
        this.length = 0;
        this.offset = 0;
    }

    public int length() {
        return this.length;
    }
    
    public boolean isEmpty() {
        return this.length == 0;
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

    private void write(final byte b) {
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

    public byte byteAt(final int pos) {
        if (pos > this.length) return -1;
        return this.buffer[this.offset + pos];
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

    private byte[] getBytes(final int start) {
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

    public void copyTo(byte[] otherArray, int offset) {
        System.arraycopy(this.buffer, 0, otherArray, offset, this.length);
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

    public static boolean equals(final byte[] buffer, final byte[] pattern) {
        // compares two byte arrays: true, if pattern appears completely at offset position
        if (buffer.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if (buffer[i] != pattern[i]) return false;
        return true;
    }

    public static boolean equals(final byte[] b0, final int off0, final byte[] b1, final int off1, final int length) {
        if (b0.length - off0 < length || b1.length - off1 < length) return false;
        for (int i = 0; i < length; i++) if (b0[off0 + i] != b1[off1 + i]) return false;
        return true;
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
            }
            // found a separator
            final byte[] bb = new byte[i - c];
            System.arraycopy(b, c, bb, 0, bb.length);
            a.add(bb);
            c = i + 1;
        }
        return a;
    }

}