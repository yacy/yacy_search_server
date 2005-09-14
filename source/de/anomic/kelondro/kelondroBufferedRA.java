// kelondroBufferedRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 13.09.2005
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

public class kelondroBufferedRA extends kelondroAbstractRA implements kelondroRA {

    private static final int bufferSizeExp = 10;
    private static final int bufferSize = 1 << bufferSizeExp;
    private static final int bufferOffsetFilter = bufferSize - 1;
    
    protected kelondroRA ra; 
    protected byte[] buffer;
    protected int bufferPage;
    protected boolean bufferWritten;
    private long seekpos;
    
    public kelondroBufferedRA(kelondroRA ra) throws FileNotFoundException {
	this.ra  = ra;
        this.name = ra.name();
        this.buffer = new byte[bufferSize];
        this.seekpos = 0;
        this.bufferPage = -1;
        this.bufferWritten = true;
    }

    private void readBuffer(int newPageNr) throws IOException {
        if (newPageNr == bufferPage) return;
        bufferPage = newPageNr;
        ra.seek(bufferPage << bufferSizeExp);
        ra.readFully(buffer, 0, bufferSize);
        bufferWritten = true;
    }

    private void writeBuffer() throws IOException {
        if ((bufferWritten) || (bufferPage < 0)) return;
        ra.seek(bufferPage << bufferSizeExp);
        ra.write(buffer, 0, bufferSize);
        bufferWritten = true;
    }
    
    private void updateToBuffer(int newPageNr) throws IOException {
        if (newPageNr != bufferPage) {
            writeBuffer();
            readBuffer(newPageNr);
        }
    }
    
    // pseudo-native method read
    public int read() throws IOException {
        int bn = (int) seekpos >> bufferSizeExp; // buffer page number
        int offset = (int) seekpos & bufferOffsetFilter; // buffer page offset
        seekpos++;
        updateToBuffer(bn);
        return 0xFF & buffer[offset];
    }

    // pseudo-native method write
    public void write(int b) throws IOException {
        int bn = (int) seekpos >> bufferSizeExp; // buffer page number
        int offset = (int) seekpos & bufferOffsetFilter; // buffer page offset
        seekpos++;
        updateToBuffer(bn);
        buffer[offset] = (byte) b;
        bufferWritten = false;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int bn1 = (int) seekpos >> bufferSizeExp; // buffer page number, first position
        int bn2 = (int) (seekpos + len - 1) >> bufferSizeExp; // buffer page number, last position
        int offset = (int) seekpos & bufferOffsetFilter; // buffer page offset
        updateToBuffer(bn1);
        if (bn1 == bn2) {
            // simple case
            System.arraycopy(buffer, offset, b, off, len);
            seekpos += len;
            return len;
        } else {
            // do recursively
            int thislen = bufferSize - offset;
            System.arraycopy(buffer, offset, b, off, thislen);
            seekpos += thislen;
            return thislen + read(b, off + thislen, len - thislen);
        }
    }

    public void write(byte[] b, int off, int len) throws IOException {
        int bn1 = (int) seekpos >> bufferSizeExp; // buffer page number, first position
        int bn2 = (int) (seekpos + len - 1) >> bufferSizeExp; // buffer page number, last position
        int offset = (int) seekpos & bufferOffsetFilter; // buffer page offset
        updateToBuffer(bn1);
        if (bn1 == bn2) {
            // simple case
            System.arraycopy(b, off, buffer, offset, len);
            bufferWritten = false;
            seekpos += len;
        } else {
            // do recursively
            int thislen = bufferSize - offset;
            System.arraycopy(b, off, buffer, offset, thislen);
            bufferWritten = false;
            seekpos += thislen;
            write(b, off + thislen, len - thislen);
        }
    }

    public void seek(long pos) throws IOException {
        seekpos = pos;
    }

    public void close() throws IOException {
        // write unwritten buffer
        if (buffer == null) return;
        writeBuffer();
        ra.close();
        buffer = null;
    }
    
    public void finalize() {
        try {
            close();
        } catch (IOException e) {}
    }

    public static void main(String[] args) {
        try {
            kelondroRA file = new kelondroBufferedRA(new kelondroFileRA("testx"));
            file.seek(bufferSize - 2);
            byte[] b = new byte[]{65, 66, 77, 88};
            file.write(b);
            file.seek(bufferSize * 2 - 30);
            for (int i = 65; i < 150; i++) file.write(i);
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
