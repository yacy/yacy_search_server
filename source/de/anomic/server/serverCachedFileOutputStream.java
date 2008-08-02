// FileFallbackByteArrayOutputStream.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// 
// This file ist contributed by Franz Brausze
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class serverCachedFileOutputStream extends ByteArrayOutputStream {
    
    protected File fallbackFile;
    protected long fallbackSize;
    protected boolean buffered;
    
    protected long size = 0;
    protected boolean isFallback = false;
    protected OutputStream fallback = null;
    
    public serverCachedFileOutputStream(final long fallbackSize) throws IOException {
        this(fallbackSize, null, true, 32);
    }
    
    public serverCachedFileOutputStream(final long fallbackSize, final File fallback, final boolean buffered)
            throws IOException {
        this(fallbackSize, fallback, buffered, 32);
    }
    
    public serverCachedFileOutputStream(final long fallbackSize, final File fallback, final boolean buffered,
            final long size) throws IOException {
        this.fallbackSize = fallbackSize;
        this.fallbackFile = (fallback == null) ? File.createTempFile(
                serverCachedFileOutputStream.class.getName(),
                Long.toString(System.currentTimeMillis())) : fallback;
        this.buffered = buffered;
        checkFallback(size);
    }
    
    public serverCachedFileOutputStream(final long fallbackSize, final File fallback, final boolean buffered,
            final byte[] data) throws IOException {
        this(fallbackSize, fallback, buffered, 0);
        super.buf = data;
        super.count = data.length;
        checkFallback(this.size = data.length);
    }
    
    protected boolean checkFallback(final long size) {
        if (size > this.fallbackSize) try {
            fallback();
            return true;
        } catch (final IOException e) {
            throw new RuntimeException("error falling back to file", e);
        }
        return false;
    }
    
    public void fallback() throws IOException {
        if (this.isFallback) return;
        this.isFallback = true;
        if (!this.fallbackFile.exists()) {
            this.fallbackFile.createNewFile();
        } else if (this.fallbackFile.isDirectory()) {
            throw new IOException("cannot write on a directory");
        }
        final OutputStream os = new FileOutputStream(this.fallbackFile);
        this.fallback = (this.buffered) ? new BufferedOutputStream(os) : os;
        serverFileUtils.copy(new ByteArrayInputStream(super.buf), this.fallback);
        super.buf = new byte[0];
        super.count = 0;
        super.reset();
    }
    
    public boolean isFallback() {
        return this.isFallback;
    }
    
    public synchronized void write(final int b) {
        if (checkFallback(++this.size)) try {
            this.fallback.write(b);
        } catch (final IOException e) {
            throw new RuntimeException("error writing to fallback", e);
        } else {
            super.write(b);
        }
    }
    
    public synchronized void write(final byte[] b, final int off, final int len) {
        if (checkFallback(this.size += len)) try {
            this.fallback.write(b, off, len);
        } catch (final IOException e) {
            throw new RuntimeException("error writing to fallback", e);
        } else {
            super.write(b, off, len);
        }
    }
    
    public void close() throws IOException {
        if (this.fallback != null)
            this.fallback.close();
        super.close();
    }
    
    public InputStream getContent() throws IOException {
        close();
        if (this.isFallback) {
            final InputStream is = new FileInputStream(this.fallbackFile);
            return (this.buffered) ? new BufferedInputStream(is) : is;
        }
        return new ByteArrayInputStream(this.buf);
    }
    
    public byte[] getContentBAOS() {
        if (this.isFallback)
            throw new RuntimeException("underlying ByteArrayOutputStream not available, already fell back to file");
        return super.buf;
    }
    
    public File getContentFile() {
        if (!this.isFallback)
            throw new RuntimeException("haven't fallen back yet, fallback file has no content");
        return this.fallbackFile;
    }
    
    public long getLength() {
        return this.size;
    }
}
