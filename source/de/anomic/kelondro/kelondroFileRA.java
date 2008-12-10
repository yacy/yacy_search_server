// kelondroFileRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2004-2008
// last major change: 09.12.2008
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

package de.anomic.kelondro;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

public final class kelondroFileRA extends kelondroAbstractRA implements kelondroRA {

    private RandomAccessFile RAFile;
    private byte[] cache;
    private long cachestart;
    private int cachelen;

    public kelondroFileRA(final File file) throws IOException, FileNotFoundException {
        this.name = file.getName();
        this.file = file;
        RAFile = new RandomAccessFile(file, "rw");
        cache = new byte[8192];
        cachestart = 0;
        cachelen = 0;
    }	
    
    public synchronized long length() throws IOException {
        return this.RAFile.length();
    }
    
    public synchronized void setLength(long length) throws IOException {
        cachelen = 0;
        RAFile.setLength(length);
    }
    
    public synchronized int available() throws IOException {
        return (int) (this.length() - RAFile.getFilePointer());
    }

    public synchronized final void readFully(final byte[] b, final int off, int len) throws IOException {
        long seek = RAFile.getFilePointer();
        if (cache != null && cachestart <= seek && cachelen - seek + cachestart >= len) {
            // read from cache
            //System.out.println("*** DEBUG FileRA " + this.file.getName() + ": CACHE HIT at " + seek);
            System.arraycopy(cache, (int) (seek - cachestart), b, off, len);
            RAFile.seek(seek + len);
            return;
        }
        if (cache == null || cache.length < len) {
            // cannot fill cache here
            RAFile.readFully(b, off, len);
            return;
        }
        // we fill the cache here
        int available = (int) (this.RAFile.length() - seek);
        if (available < len) throw new IOException("EOF, available = " + available + ", requested = " + len);
        if (cachestart + cachelen == seek && cache.length - cachelen >= len) {
            RAFile.readFully(cache, cachelen, len);
            //System.out.println("*** DEBUG FileRA " + this.file.getName() + ": append fill " + len + " bytes");
            System.arraycopy(cache, cachelen, b, off, len);
            cachelen += len;
        } else {
            // fill the cache as much as possible
            int m = Math.min(available, cache.length);
            RAFile.readFully(cache, 0, m);
            cachestart = seek;
            cachelen = m;
            if (m != len) RAFile.seek(seek + len);
            //System.out.println("*** DEBUG FileRA " + this.file.getName() + ": replace fill " + len + " bytes");
            System.arraycopy(cache, 0, b, off, len);
        }
        
    }

    public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
        //assert len > 0;
        // write to file
        //if (this.cache.length > 2048) this.cache = new byte[2048]; // the large cache is only useful during an initialization phase
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
        RAFile.write(b, off, len);
    }

    public synchronized void seek(final long pos) throws IOException {
        RAFile.seek(pos);
    }

    public synchronized void close() throws IOException {
        if (RAFile != null) RAFile.close();
        this.cache = null;
        this.RAFile = null;
    }

    protected void finalize() throws Throwable {
        if (RAFile != null) {
            this.close();
        }
        super.finalize();
    }
    
    // some static tools
    public static void writeMap(final File f, final Map<String, String> map, final String comment) throws IOException {
        final File fp = f.getParentFile();
        if (fp != null) fp.mkdirs();
        kelondroRA kra = null;
        try {
            kra = new kelondroFileRA(f);
            kra.writeMap(map, comment);
            kra.close();
        } finally {
            if (kra != null) try {kra.close();}catch(final Exception e){}
        }
    }

    public static Map<String, String> readMap(final File f) throws IOException {
        kelondroRA kra = null;
        try {
            kra = new kelondroFileRA(f);
            final Map<String, String> map = kra.readMap();
            kra.close();
            return map;
        } finally {
            if (kra != null) try {kra.close();}catch(final Exception e){}
        }
    }

}
