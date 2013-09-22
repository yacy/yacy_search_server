//httpChunkedOutputStream.java
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Martin Thelian
//last major change: 05.09.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.server.http;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.server.serverCore;

public final class ChunkedOutputStream extends FilterOutputStream {
    private boolean finished = false;

    public ChunkedOutputStream(final OutputStream out) {
        super(out);
    }

    @Override
    public synchronized void close() throws IOException {
        if (!this.finished) this.finish();
        this.out.close();
    }

    public void finish() throws IOException {
        if (!this.finished) {
            this.out.write((byte) 48);
            this.out.write(serverCore.CRLF);
            this.out.write(serverCore.CRLF);
            this.out.flush();
            this.finished = true;
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        if (this.finished) throw new IOException("ChunkedOutputStream already finalized.");
        if (b.length == 0) return;

        this.out.write(ASCII.getBytes(Integer.toHexString(b.length)));
        this.out.write(serverCore.CRLF);
        this.out.write(b);
        this.out.write(serverCore.CRLF);
        this.out.flush();
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (this.finished) throw new IOException("ChunkedOutputStream already finalized.");
        if (len == 0) return;

        this.out.write(ASCII.getBytes(Integer.toHexString(len)));
        this.out.write(serverCore.CRLF);
        this.out.write(b, off, len);
        this.out.write(serverCore.CRLF);
        this.out.flush();
    }

    public void write(final ByteBuffer b, final int off, final int len) throws IOException {
        if (this.finished) throw new IOException("ChunkedOutputStream already finalized.");
        if (len == 0) return;

        this.out.write(ASCII.getBytes(Integer.toHexString(len)));
        this.out.write(serverCore.CRLF);
        this.out.write(b.getBytes(off, len));
        this.out.write(serverCore.CRLF);
        this.out.flush();
    }

    public void write(final InputStream b) throws IOException {
        if (this.finished) throw new IOException("ChunkedOutputStream already finalized.");
        final int len = b.available();
        if (len == 0) return;

        this.out.write(ASCII.getBytes(Integer.toHexString(len)));
        this.out.write(serverCore.CRLF);
        FileUtils.copy(b, this.out, len);
        this.out.write(serverCore.CRLF);
        this.out.flush();
    }

    @Override
    public void write(final int b) throws IOException {
        if (this.finished) throw new IOException("ChunkedOutputStream already finalized.");

        this.out.write(UTF8.getBytes("1"));
        this.out.write(serverCore.CRLF);
        this.out.write(b);
        this.out.write(serverCore.CRLF);
        this.out.flush();
    }
}
