// kelondroBufferedRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 06.10.2004
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

public class kelondroBufferedRA extends kelondroAbstractRA implements kelondroRA {

    protected kelondroRA ra; 
    protected kelondroMScoreCluster bufferScore;
    protected HashMap bufferMemory;
    private int bufferMaxElements;
    private int bufferElementSize;
    private long seekpos;
    
    public kelondroBufferedRA(kelondroRA ra, int buffersize, int elementsize) throws FileNotFoundException {
	this.ra  = ra;
        this.name = ra.name();
        this.bufferMemory = new HashMap();
        this.bufferScore = new kelondroMScoreCluster();
        this.bufferElementSize = elementsize;
        this.bufferMaxElements = (int) (buffersize / bufferElementSize);
        this.seekpos = 0;
    }

    private int bufferElementNumber(long address) {
        return (int) address / bufferElementSize;
    }
    
    private int bufferElementOffset(long address) {
        return (int) address % bufferElementSize;
    }
    
    private byte[] readBuffer(int bufferNr) throws IOException {
        Integer bufferNrI = new Integer(bufferNr);
        byte[] buffer = (byte[]) bufferMemory.get(bufferNrI);
        if (buffer == null) {
            if (bufferMemory.size() >= bufferMaxElements) {
                // delete elements in buffer if buffer too big
                Iterator it = bufferScore.scores(true);
                Integer element = (Integer) it.next();
                writeBuffer((byte[]) bufferMemory.get(element), element.intValue());
                bufferMemory.remove(element);
                int age = bufferScore.deleteScore(element);
                de.anomic.server.logging.serverLog.logFine("CACHE: " + name, "GC; age=" + ((((int) (0xFFFFFFFFL & System.currentTimeMillis())) - age) / 1000));
            }
            // add new element
            buffer = new byte[bufferElementSize];
            //System.out.println("buffernr=" + bufferNr + ", elSize=" + bufferElementSize);
            ra.seek(bufferNr * bufferElementSize);
            ra.read(buffer, 0, bufferElementSize);
            bufferMemory.put(bufferNrI, buffer);
        }
        bufferScore.setScore(bufferNrI, (int) (0xFFFFFFFFL & System.currentTimeMillis()));
        return buffer;
    }
    
    private void writeBuffer(byte[] buffer, int bufferNr) throws IOException {
        if (buffer == null) return;
        Integer bufferNrI = new Integer(bufferNr);
        ra.seek(bufferNr * bufferElementSize);
        ra.write(buffer, 0, bufferElementSize);
        bufferScore.setScore(bufferNrI, (int) (0xFFFFFFFFL & System.currentTimeMillis()));
    }
    
    // pseudo-native method read
    public int read() throws IOException {
        int bn = bufferElementNumber(seekpos);
        int offset = bufferElementOffset(seekpos);
        seekpos++;
        return 0xFF & readBuffer(bn)[offset];
    }

    // pseudo-native method write
    public void write(int b) throws IOException {
        int bn = bufferElementNumber(seekpos);
        int offset = bufferElementOffset(seekpos);
        byte[] buffer = readBuffer(bn);
        seekpos++;
        buffer[offset] = (byte) b;
        //writeBuffer(buffer, bn);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int bn1 = bufferElementNumber(seekpos);
        int bn2 = bufferElementNumber(seekpos + len - 1);
        int offset = bufferElementOffset(seekpos);
        byte[] buffer = readBuffer(bn1);
        if (bn1 == bn2) {
            // simple case
            //System.out.println("C1: bn1=" + bn1 + ", offset=" + offset + ", off=" + off + ", len=" + len);
            System.arraycopy(buffer, offset, b, off, len);
            seekpos += len;
            return len;
        } else {
            // do recursively
            int thislen = bufferElementSize - offset;
            //System.out.println("C2: bn1=" + bn1 + ", bn2=" + bn2 +", offset=" + offset + ", off=" + off + ", len=" + len + ", thislen=" + thislen);
            System.arraycopy(buffer, offset, b, off, thislen);
            seekpos += thislen;
            return thislen + read(b, thislen, len - thislen);
        }
    }

    public void write(byte[] b, int off, int len) throws IOException {
        int bn1 = bufferElementNumber(seekpos);
        int bn2 = bufferElementNumber(seekpos + len - 1);
        int offset = bufferElementOffset(seekpos);
        byte[] buffer = readBuffer(bn1);
        if (bn1 == bn2) {
            // simple case
            System.arraycopy(b, off, buffer, offset, len);
            seekpos += len;
            //writeBuffer(buffer, bn1);
        } else {
            // do recursively
            int thislen = bufferElementSize - offset;
            System.arraycopy(b, off, buffer, offset, thislen);
            seekpos += thislen;
            //writeBuffer(buffer, bn1);
            write(b, thislen, len - thislen);
        }
    }

    public void seek(long pos) throws IOException {
        seekpos = pos;
    }

    public void close() throws IOException {
        // write all unwritten buffers
        Iterator it = bufferScore.scores(true);
        while (it.hasNext()) {
            Integer element = (Integer) it.next();
            writeBuffer((byte[]) bufferMemory.get(element), element.intValue());
            bufferMemory.remove(element);
        }
        ra.close();
        bufferScore = null;
        bufferMemory = null;
    }

}
