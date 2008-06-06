// kelondroCachedRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
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

package de.anomic.kelondro;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

public class kelondroCachedRA extends kelondroAbstractRA implements kelondroRA {

    protected kelondroRA ra; 
    protected kelondroMScoreCluster<Integer> cacheScore;
    protected HashMap<Integer, byte[]> cacheMemory;
    private int cacheMaxElements;
    private int cacheElementSize;
    private long seekpos;
    
    public kelondroCachedRA(kelondroRA ra, int cachesize, int elementsize) {
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
    
    private int cacheElementNumber(long address) {
        return (int) address / cacheElementSize;
    }
    
    private int cacheElementOffset(long address) {
        return (int) address % cacheElementSize;
    }
    
    private byte[] readCache(int cacheNr) throws IOException {
        Integer cacheNrI = new Integer(cacheNr);
        byte[] cache = cacheMemory.get(cacheNrI);
        if (cache == null) {
            if (cacheMemory.size() >= cacheMaxElements) {
                // delete elements in buffer if buffer too big
                Iterator<Integer> it = cacheScore.scores(true);
                Integer element = it.next();
                writeCache(cacheMemory.get(element), element.intValue());
                cacheMemory.remove(element);
                int age = cacheScore.deleteScore(element);
                de.anomic.server.logging.serverLog.logFine("CACHE: " + name, "GC; age=" + ((((int) (0xFFFFFFFFL & System.currentTimeMillis())) - age) / 1000));
            }
            // add new element
            cache = new byte[cacheElementSize];
            //System.out.println("buffernr=" + bufferNr + ", elSize=" + bufferElementSize);
            ra.seek(cacheNr * cacheElementSize);
            ra.read(cache, 0, cacheElementSize);
            cacheMemory.put(cacheNrI, cache);
        }
        cacheScore.setScore(cacheNrI, (int) (0xFFFFFFFFL & System.currentTimeMillis()));
        return cache;
    }
    
    private void writeCache(byte[] cache, int cacheNr) throws IOException {
        if (cache == null) return;
        Integer cacheNrI = new Integer(cacheNr);
        ra.seek(cacheNr * cacheElementSize);
        ra.write(cache, 0, cacheElementSize);
        cacheScore.setScore(cacheNrI, (int) (0xFFFFFFFFL & System.currentTimeMillis()));
    }
    
    // pseudo-native method read
    public int read() throws IOException {
        int bn = cacheElementNumber(seekpos);
        int offset = cacheElementOffset(seekpos);
        seekpos++;
        return 0xFF & readCache(bn)[offset];
    }

    // pseudo-native method write
    public void write(int b) throws IOException {
        int bn = cacheElementNumber(seekpos);
        int offset = cacheElementOffset(seekpos);
        byte[] cache = readCache(bn);
        seekpos++;
        cache[offset] = (byte) b;
        //writeBuffer(buffer, bn);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int bn1 = cacheElementNumber(seekpos);
        int bn2 = cacheElementNumber(seekpos + len - 1);
        int offset = cacheElementOffset(seekpos);
        byte[] buffer = readCache(bn1);
        if (bn1 == bn2) {
            // simple case
            //System.out.println("C1: bn1=" + bn1 + ", offset=" + offset + ", off=" + off + ", len=" + len);
            System.arraycopy(buffer, offset, b, off, len);
            seekpos += len;
            return len;
        }
        
        // do recursively
        int thislen = cacheElementSize - offset;
        //System.out.println("C2: bn1=" + bn1 + ", bn2=" + bn2 +", offset=" + offset + ", off=" + off + ", len=" + len + ", thislen=" + thislen);
        System.arraycopy(buffer, offset, b, off, thislen);
        seekpos += thislen;
        return thislen + read(b, off + thislen, len - thislen);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        int bn1 = cacheElementNumber(seekpos);
        int bn2 = cacheElementNumber(seekpos + len - 1);
        int offset = cacheElementOffset(seekpos);
        byte[] cache = readCache(bn1);
        if (bn1 == bn2) {
            // simple case
            System.arraycopy(b, off, cache, offset, len);
            seekpos += len;
            //writeBuffer(buffer, bn1);
        } else {
            // do recursively
            int thislen = cacheElementSize - offset;
            System.arraycopy(b, off, cache, offset, thislen);
            seekpos += thislen;
            //writeBuffer(buffer, bn1);
            write(b, off + thislen, len - thislen);
        }
    }

    public void seek(long pos) throws IOException {
        seekpos = pos;
    }

    public void close() throws IOException {
        // write all unwritten buffers
        if (cacheMemory == null) return;
        Iterator<Integer> it = cacheScore.scores(true);
        while (it.hasNext()) {
            Integer element = it.next();
            writeCache(cacheMemory.get(element), element.intValue());
            cacheMemory.remove(element);
        }
        ra.close();
        cacheScore = null;
        cacheMemory = null;
    }

    public void finalize() {
        try {
            close();
        } catch (IOException e) {}
    }
}
