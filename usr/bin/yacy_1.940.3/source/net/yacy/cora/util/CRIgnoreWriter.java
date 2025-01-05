/**
 *  CRIgnoreWriter
 *  Copyright 29.5.2015 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

import java.io.StringWriter;

public class CRIgnoreWriter extends StringWriter {

    public CRIgnoreWriter() {
        super();
    }
    
    public CRIgnoreWriter(final int initialSize) {
        super(initialSize);
    }

    @Override
    public void write(int c) {
        if (c >= 32) super.write(c);
    }

    @Override
    public void write(char cbuf[], int off, int len) {
        if ((off < 0) || (off > cbuf.length) || (len < 0) ||
            ((off + len) > cbuf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        int p = off;
        char c;
        for (int i = 0; i < len; i++) {
            c = cbuf[p];
            if (c >= 32) super.write(c);
            p++;
        }
    }

    @Override
    public void write(String str) {
        int len = str.length();
        char c;
        for (int i = 0; i < len; i++) {
            c = str.charAt(i);
            if (c >= 32) super.write(c);
        }
    }

    @Override
    public void write(String str, int off, int len)  {
        int p = off;
        char c;
        for (int i = 0; i < len; i++) {
            c = str.charAt(p);
            if (c >= 32) super.write(c);
            p++;
        }
    }

    @Override
    public CRIgnoreWriter append(CharSequence csq) {
        this.write(csq == null ? "null" : csq.toString());
        return this;
    }

    @Override
    public CRIgnoreWriter append(CharSequence csq, int start, int end) {
        CharSequence cs = (csq == null ? "null" : csq);
        this.write(cs.subSequence(start, end).toString());
        return this;
    }

    @Override
    public CRIgnoreWriter append(char c) {
        if (c >= 32) write(c);
        return this;
    }

}
