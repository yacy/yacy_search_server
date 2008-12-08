// kelondroCachedRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2004-2008
// last major change: 04.12.2008
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.server.serverMemory;

public class kelondroCachedRA extends kelondroAbstractRA implements kelondroRA {

    // a shared cache for all users of this class
    private static final int elementsize = 8192;
    private static final int remainingfree = 30 * 1024 * 1024;
    private static HashMap<String, byte[]> cacheMemory = new HashMap<String, byte[]>();
    
    // class variables
    protected kelondroRA ra; 
    private long seekpos;
    private String id;
    
    public kelondroCachedRA(final kelondroRA ra) {
        this.ra  = ra;
        this.name = ra.name();
        this.file = ra.file();
        this.id = file.toString();
        this.seekpos = 0;
    }

    public synchronized long length() throws IOException {
        return ra.length();
    }
    
    public synchronized int available() throws IOException {
        return (int) (ra.length() - seekpos);
    }
    
    private int cacheElementNumber(final long address) {
        return (int) address / elementsize;
    }
    
    private int cacheElementOffset(final long address) {
        return (int) address % elementsize;
    }
    
    private byte[] readCache(final int cacheNr) throws IOException {
        String key = this.id + cacheNr;
        byte[] cache = cacheMemory.get(key);
        if (cache == null) {
            if (serverMemory.available() < remainingfree) {
                // delete elements in buffer if buffer too big
                synchronized(cacheMemory) {
                    Iterator<Map.Entry<String, byte[]>> i = cacheMemory.entrySet().iterator();
                    for (int j = 0; j < 10; j++) {
                        if (!i.hasNext()) break;
                        i.next();
                        i.remove();
                    }
                }
            }
            // check if we have enough space in the file to read a complete cache element
            long seek = cacheNr * (long) elementsize;
            if (ra.length() - seek < elementsize) return null;
            // add new element
            cache = new byte[elementsize];
            ra.seek(seek);
            ra.readFully(cache, 0, elementsize);
            cacheMemory.put(key, cache);
        }
        return cache;
    }
    
    private boolean existCache(final int cacheNr) throws IOException {
        return cacheMemory.containsKey(Integer.valueOf(cacheNr));
    }
    
    public synchronized void readFully(byte[] b, int off, int len) throws IOException {
        final int bn1 = cacheElementNumber(seekpos);
        final int bn2 = cacheElementNumber(seekpos + len - 1);
        final int offset = cacheElementOffset(seekpos);
        final byte[] cache = readCache(bn1);
        if (bn1 == bn2) {
            // simple case
            if (cache == null) {
                ra.seek(seekpos);
                ra.readFully(b, off, len);
                seekpos += len;
                return;
            }
            //System.out.println("cache hit");
            System.arraycopy(cache, offset, b, off, len);
            seekpos += len;
            return;
        }
        assert cache != null;
        
        // do recursively
        final int thislen = elementsize - offset;
        System.arraycopy(cache, offset, b, off, thislen);
        seekpos += thislen;
        readFully(b, off + thislen, len - thislen);
    }

    public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
        final int bn1 = cacheElementNumber(seekpos);
        final int bn2 = cacheElementNumber(seekpos + len - 1);
        final int offset = cacheElementOffset(seekpos);
        if (bn1 == bn2) {
            if (existCache(bn1)) {
                // write to cache and file; here: write only to cache
                final byte[] cache = readCache(bn1);
                assert cache != null;
                System.arraycopy(b, off, cache, offset, len);
            } else {
                // in case that the cache could be filled completely
                // create a new entry here and store it also to the cache
                if (offset == 0 && len >= elementsize) {
                    final byte[] cache = new byte[elementsize];
                    System.arraycopy(b, off, cache, 0, elementsize);
                    cacheMemory.put(this.id + bn1, cache);
                }
            }
            // write to file
            ra.seek(seekpos);
            ra.write(b, off, len);
            seekpos += len;
            return;
        }
        
        // do recursively
        final int thislen = elementsize - offset;
        if (existCache(bn1)) {
            // write to cache and file; here: write only to cache
            final byte[] cache = readCache(bn1);
            assert cache != null;
            System.arraycopy(b, off, cache, offset, thislen);
        } else {
            // in case that the cache could be filled completely
            // create a new entry here and store it also to the cache
            if (offset == 0 && len >= elementsize) {
                final byte[] cache = new byte[elementsize];
                System.arraycopy(b, off, cache, 0, elementsize);
                cacheMemory.put(this.id + bn1, cache);
            }
        }
        // write to file
        ra.seek(seekpos);
        ra.write(b, off, thislen);
        seekpos += thislen;
        write(b, off + thislen, len - thislen);
    }

    public synchronized void seek(final long pos) throws IOException {
        seekpos = pos;
    }

    public synchronized void close() throws IOException {
        ra.close();
    }

    protected void finalize() {
        try {
            close();
        } catch (final IOException e) {}
    }

}
