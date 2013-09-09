// CachedFileWriter.java 
// ---------------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2004-2008s
//
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
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
import java.io.IOException;
import java.io.RandomAccessFile;

import net.yacy.cora.util.ConcurrentLog;

public final class CachedFileWriter extends AbstractWriter implements Writer {

    private RandomAccessFile RAFile;
    private byte[] cache;
    private long cachestart;
    private int cachelen;

    public CachedFileWriter(final File file) throws IOException, FileNotFoundException {
        this.name = file.getName();
        this.file = file;
        this.RAFile = new RandomAccessFile(this.file, "rw");
        this.cache = new byte[32768];
        this.cachestart = 0;
        this.cachelen = 0;
    }	
    
    @Override
    public final synchronized long length() throws IOException {
        checkReopen();
        return this.RAFile.length();
    }
    
    @Override
    public final synchronized void setLength(long length) throws IOException {
        checkReopen();
        this.cachelen = 0;
        this.RAFile.setLength(length);
    }
    
    @Override
    public final synchronized long available() throws IOException {
        checkReopen();
        return this.length() - this.RAFile.getFilePointer();
    }

    @Override
    public final synchronized void readFully(final byte[] b, final int off, int len) throws IOException {
        checkReopen();
        long seek = this.RAFile.getFilePointer();
        if (this.cache != null && this.cachestart <= seek && this.cachelen - seek + this.cachestart >= len) {
            // read from cache
            //System.out.println("*** DEBUG FileRA " + this.file.getName() + ": CACHE HIT at " + seek);
            System.arraycopy(this.cache, (int) (seek - this.cachestart), b, off, len);
            this.RAFile.seek(seek + len);
            return;
        }
        if (this.cache == null || this.cache.length < len) {
            // cannot fill cache here
            this.RAFile.readFully(b, off, len);
            return;
        }
        // we fill the cache here
        long available = this.RAFile.length() - seek;
        if (available == -seek) return; // we don't know how this happens but we just silently ignore it by now TODO:fixme
        //System.out.println("*** available = " + available);
        if (available < len) throw new IOException("EOF in " + this.file.getName() + ", available = " + available + ", requested = " + len + ", this.RAFile.length() = " + this.RAFile.length() + ", seek = " + seek);
        if (this.cachestart + this.cachelen == seek && this.cache.length - this.cachelen >= len) {
            this.RAFile.readFully(this.cache, this.cachelen, len);
            //System.out.println("*** DEBUG FileRA " + this.file.getName() + ": append fill " + len + " bytes");
            System.arraycopy(this.cache, this.cachelen, b, off, len);
            this.cachelen += len;
        } else {
            // fill the cache as much as possible
            int m = (int) Math.min(available, this.cache.length);
            this.RAFile.readFully(this.cache, 0, m);
            this.cachestart = seek;
            this.cachelen = m;
            if (m != len) this.RAFile.seek(seek + len);
            //System.out.println("*** DEBUG FileRA " + this.file.getName() + ": replace fill " + len + " bytes");
            System.arraycopy(this.cache, 0, b, off, len);
        }
        
    }

    @Override
    public final synchronized void write(final byte[] b, final int off, final int len) throws IOException {
        checkReopen();
        //assert len > 0;
        // write to file
        if (this.cache.length > 512) {
        	// the large cache is only useful during an initialization phase
        	byte[] newcache = new byte[512];
        	System.arraycopy(this.cache, 0, newcache, 0, newcache.length);
        	this.cache = newcache;
        	if (this.cachelen > this.cache.length) this.cachelen = this.cache.length;
        }
        long seekpos = this.RAFile.getFilePointer();
        if (this.cachelen + len <= this.cache.length && this.cachestart + this.cachelen == seekpos) {
            // append to cache
            System.arraycopy(b, off, this.cache, this.cachelen, len);
            //System.out.println("*** DEBUG FileRA " + this.file.getName() + ": write append " + len + " bytes");
            this.cachelen += len;
        } else if (len <= this.cache.length) {
            // copy to cache
            System.arraycopy(b, off, this.cache, 0, len);
            //System.out.println("*** DEBUG FileRA " + this.file.getName() + ": write copy " + len + " bytes");
            this.cachelen = len;
            this.cachestart = seekpos;
        } else {
            // delete cache
            this.cachelen = 0;
        }
        this.RAFile.write(b, off, len);
    }

    @Override
    public final synchronized void seek(final long pos) throws IOException {
        checkReopen();
        this.RAFile.seek(pos);
    }

    @Override
    public final synchronized void close() {
        if (this.RAFile != null) try {
            try{this.RAFile.getChannel().close();} catch (final IOException e) {}
            //System.out.println("***DEBUG*** closed file " + this.file + ", FD is " + ((RAFile.getFD().valid()) ? "VALID" : "VOID") + ", channel is " + ((RAFile.getChannel().isOpen()) ? "OPEN" : "CLOSE"));
            this.RAFile.close();
            //System.out.println("***DEBUG*** closed file " + this.file + ", FD is " + ((RAFile.getFD().valid()) ? "VALID" : "VOID") + ", channel is " + ((RAFile.getChannel().isOpen()) ? "OPEN" : "CLOSE"));
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        this.cache = null;
        this.RAFile = null;
    }
    
    private final void checkReopen() {
        if (this.RAFile != null) return;
        // re-open the file
        try {
            this.RAFile = new RandomAccessFile(this.file, "rw");
        } catch (final FileNotFoundException e) {
            ConcurrentLog.logException(e);
        }
        this.cache = new byte[8192];
        this.cachestart = 0;
        this.cachelen = 0;
    }

    @Override
    protected final void finalize() throws Throwable {
        this.close();
        super.finalize();
    }

}
