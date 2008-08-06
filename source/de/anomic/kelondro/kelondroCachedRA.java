// kelondroCachedRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 06.10.2004
// this file was previously named kelondroBufferedRA
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

public class kelondroCachedRA extends kelondroAbstractRA implements kelondroRA {

    protected kelondroRA ra; 
    protected kelondroMScoreCluster<Integer> cacheScore;
    protected HashMap<Integer, byte[]> cacheMemory;
    private final int cacheMaxElements;
    private final int cacheElementSize;
    private long seekpos;
    
    public kelondroCachedRA(final kelondroRA ra, final int cachesize, final int elementsize) {
	this.ra  = ra;
        this.name = ra.name();
        this.file = ra.file();
        this.cacheMemory = new HashMap<Integer, byte[]>();
        this.cacheScore = new kelondroMScoreCluster<Integer>();
        this.cacheElementSize = elementsize;
        this.cacheMaxElements = cachesize / cacheElementSize;
        this.seekpos = 0;
    }
    
    public long length() throws IOException {
        return ra.available();
    }
    
    public long available() throws IOException {
        synchronized (ra) {
            ra.seek(seekpos);
            return ra.available();
        }
    }
    
    private int cacheElementNumber(final long address) {
        return (int) address / cacheElementSize;
    }
    
    private int cacheElementOffset(final long address) {
        return (int) address % cacheElementSize;
    }
    
    private byte[] readCache(final int cacheNr) throws IOException {
        final Integer cacheNrI = Integer.valueOf(cacheNr);
        byte[] cache = cacheMemory.get(cacheNrI);
        if (cache == null) {
            if (cacheMemory.size() >= cacheMaxElements) {
                // delete elements in buffer if buffer too big
                final Iterator<Integer> it = cacheScore.scores(true);
                final Integer element = it.next();
                writeCache(cacheMemory.get(element), element.intValue());
                cacheMemory.remove(element);
                final int age = cacheScore.deleteScore(element);
                de.anomic.server.logging.serverLog.logFine("CACHE: " + name, "GC; age=" + ((((int) (0xFFFFFFFFL & System.currentTimeMillis())) - age) / 1000));
            }
            // add new element
            cache = new byte[cacheElementSize];
            //System.out.println("buffernr=" + bufferNr + ", elSize=" + bufferElementSize);
            ra.seek(cacheNr * (long) cacheElementSize);
            ra.read(cache, 0, cacheElementSize);
            cacheMemory.put(cacheNrI, cache);
        }
        cacheScore.setScore(cacheNrI, (int) (0xFFFFFFFFL & System.currentTimeMillis()));
        return cache;
    }
    
    private void writeCache(final byte[] cache, final int cacheNr) throws IOException {
        if (cache == null) return;
        final Integer cacheNrI = Integer.valueOf(cacheNr);
        ra.seek(cacheNr * (long) cacheElementSize);
        ra.write(cache, 0, cacheElementSize);
        cacheScore.setScore(cacheNrI, (int) (0xFFFFFFFFL & System.currentTimeMillis()));
    }
    
    // pseudo-native method read
    public int read() throws IOException {
        final int bn = cacheElementNumber(seekpos);
        final int offset = cacheElementOffset(seekpos);
        seekpos++;
        return 0xFF & readCache(bn)[offset];
    }

    // pseudo-native method write
    public void write(final int b) throws IOException {
        final int bn = cacheElementNumber(seekpos);
        final int offset = cacheElementOffset(seekpos);
        final byte[] cache = readCache(bn);
        seekpos++;
        cache[offset] = (byte) b;
        //writeBuffer(buffer, bn);
    }

    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int bn1 = cacheElementNumber(seekpos);
        final int bn2 = cacheElementNumber(seekpos + len - 1);
        final int offset = cacheElementOffset(seekpos);
        final byte[] buffer = readCache(bn1);
        if (bn1 == bn2) {
            // simple case
            //System.out.println("C1: bn1=" + bn1 + ", offset=" + offset + ", off=" + off + ", len=" + len);
            System.arraycopy(buffer, offset, b, off, len);
            seekpos += len;
            return len;
        }
        
        // do recursively
        final int thislen = cacheElementSize - offset;
        //System.out.println("C2: bn1=" + bn1 + ", bn2=" + bn2 +", offset=" + offset + ", off=" + off + ", len=" + len + ", thislen=" + thislen);
        System.arraycopy(buffer, offset, b, off, thislen);
        seekpos += thislen;
        return thislen + read(b, off + thislen, len - thislen);
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
        final int bn1 = cacheElementNumber(seekpos);
        final int bn2 = cacheElementNumber(seekpos + len - 1);
        final int offset = cacheElementOffset(seekpos);
        final byte[] cache = readCache(bn1);
        if (bn1 == bn2) {
            // simple case
            System.arraycopy(b, off, cache, offset, len);
            seekpos += len;
            //writeBuffer(buffer, bn1);
        } else {
            // do recursively
            final int thislen = cacheElementSize - offset;
            System.arraycopy(b, off, cache, offset, thislen);
            seekpos += thislen;
            //writeBuffer(buffer, bn1);
            write(b, off + thislen, len - thislen);
        }
    }

    public void seek(final long pos) throws IOException {
        seekpos = pos;
    }

    public void close() throws IOException {
        // write all unwritten buffers
        if (cacheMemory == null) return;
        final Iterator<Integer> it = cacheScore.scores(true);
        while (it.hasNext()) {
            final Integer element = it.next();
            writeCache(cacheMemory.get(element), element.intValue());
            cacheMemory.remove(element);
        }
        ra.close();
        cacheScore = null;
        cacheMemory = null;
    }

    protected void finalize() {
        try {
            close();
        } catch (final IOException e) {}
    }
}
