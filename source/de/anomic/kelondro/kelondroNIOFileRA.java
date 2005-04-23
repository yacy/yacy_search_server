// kelondroNIOFileRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2002
// last major change: 21.04.2004
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

import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.*;

public class kelondroNIOFileRA extends kelondroAbstractRA implements kelondroRA {

    protected final static long headSize = 1024;
    
    protected RandomAccessFile RAFile;
    protected FileChannel RAChannel;
    protected MappedByteBuffer bufferHead, bufferBody, bufferTail;
    protected long seekPos;
    protected long bodyOffset, tailOffset, tailCurrSize, tailMaxSize;
    protected boolean mapBody;
    protected boolean wroteHead, wroteBody, wroteTail;
    
    public kelondroNIOFileRA(String file, boolean mapBody, long tailMaxSize) throws IOException {
        this(new File(file), mapBody, tailMaxSize);
    }

    public kelondroNIOFileRA(File file, boolean mapBody, long tailMaxSize) throws IOException {
        this.name = file.getName();
        this.seekPos = 0;
        this.bodyOffset = headSize;
        if (bodyOffset >= file.length()) {
            bodyOffset = file.length();
            mapBody = false;
        }
        this.tailOffset = file.length();
        this.tailMaxSize = tailMaxSize;
        this.tailCurrSize = 0;
        this.mapBody = mapBody;
        this.RAFile     = new RandomAccessFile(file, "rw");
	this.RAChannel  = RAFile.getChannel();
	this.bufferHead = RAChannel.map(FileChannel.MapMode.READ_WRITE, 0, (int) bodyOffset);
        if (mapBody)
            this.bufferBody = RAChannel.map(FileChannel.MapMode.READ_WRITE, bodyOffset, (int) (tailOffset - bodyOffset));
        else
            this.bufferBody = null;
        this.bufferTail = null;
        this.wroteHead = false;
        this.wroteBody = false;
        this.wroteTail = false;
        System.out.println("initialized " + name + " mapBody = " + ((mapBody) ? "true" : "false") +
                           ", bodyOffset = " + bodyOffset + ", tailOffset = " + tailOffset);
    }

    private boolean growTail(long newPos) throws IOException {
        if (tailCurrSize >= tailMaxSize) {
            System.out.println("cannot grow " + name);
            return false;
        }
        if (tailCurrSize == 0) {
            // first grow
            this.tailCurrSize = tailMaxSize / 10;
            if (tailCurrSize < 1024) tailCurrSize = 1024;
            if (tailCurrSize > tailMaxSize) tailCurrSize = tailMaxSize;
        } else {
            // next grow
            tailCurrSize = tailCurrSize * 2;
            if (tailCurrSize > tailMaxSize) tailCurrSize = tailMaxSize;
            bufferTail.force();
        }
        System.out.println("growing " + name + " nextSize=" + tailCurrSize);
        bufferTail = RAChannel.map(FileChannel.MapMode.READ_WRITE, tailOffset, (int) tailCurrSize);
        wroteTail = false;
        return true;
    }
    
    // pseudo-native method read
    public int read() throws IOException {
        int r;
        if (seekPos < bodyOffset) {
            r = 0xFF & ((int) bufferHead.get((int) seekPos));
        } else if (seekPos < tailOffset) {
            if (mapBody) {
                r = 0xFF & ((int) bufferBody.get((int) (seekPos - bodyOffset)));
            } else {
                RAFile.seek(seekPos);
                r = RAFile.read();
            }
        } else if (seekPos < (tailOffset + tailCurrSize)) {
            r = 0xFF & ((int) bufferTail.get((int) (seekPos - tailOffset)));
        } else {
            r = -1;
            while (growTail(seekPos)) {
                if (seekPos < (tailOffset + tailCurrSize)) {
                    r = 0xFF & ((int) bufferTail.get((int) (seekPos - tailOffset)));
                    break;
                } else {
                    RAFile.seek(seekPos);
                    r = RAFile.read();
                    break;
                }
            }
        }
        seekPos++;
        return r;
    }

    // pseudo-native method write
    public void write(int b) throws IOException {
        if (seekPos < bodyOffset) {
            bufferHead.put((int) seekPos, (byte) (b & 0xff));
            wroteHead = true;
        } else if (seekPos < tailOffset) {
            if (mapBody) {
                bufferBody.put((int) (seekPos - bodyOffset), (byte) (b & 0xff));
                wroteBody = true;
            } else {
                RAFile.seek(seekPos);
                RAFile.write(b);
            }
        } else if (seekPos < (tailOffset + tailCurrSize)) {
            bufferTail.put((int) (seekPos - tailOffset), (byte) (b & 0xff));
            wroteTail = true;
        } else {
            while (growTail(seekPos)) {
                if (seekPos < (tailOffset + tailCurrSize)) {
                    bufferTail.put((int) (seekPos - tailOffset), (byte) (b & 0xff));
                    wroteTail = true;
                    break;
                } else {
                    RAFile.seek(seekPos);
                    RAFile.write(b);
                    break;
                }
            }
        }
        seekPos++;
    }

    public int read(byte[] b, int off, int len) throws IOException {
	for (int i = 0; i < len; i++) {
            b[off + i] = (byte) read();
        }
        return len;
    }

    public void write(byte[] b, int off, int len) throws IOException {
	for (int i = 0; i < len; i++) {
            write(b[off + i]);
        }
    }

    public void seek(long pos) throws IOException {
	seekPos = pos;
    }

    public void close() throws IOException {
        if (wroteHead) {
            bufferHead.force();
            System.out.println("wrote " + name + " head");
        }
        if ((wroteBody) && (mapBody)) {
            bufferBody.force();
            System.out.println("wrote " + name + " body");
        }
        if (wroteTail) {
            bufferTail.force();
            System.out.println("wrote " + name + " tail");
        }
        RAChannel.close();
        RAFile.close();
    }
    
    
    public static void test1(kelondroRA ra) throws IOException {
        for (int i = 0; i < 2048; i++) {
            ra.seek(i);
            ra.write(32);
        }
    }
    
    public static void main(String[] args) {
        // tests...
        File f = new File("/yacy/nio.test.txt");
        if (f.exists()) f.delete();
        
        System.out.println("* fill with blanks");
        try { kelondroRA ra = new kelondroNIOFileRA(f, true, 2046); test1(ra); ra.close();
        } catch (IOException e) { e.printStackTrace(); }

        System.out.println("* write in at head");
        try { kelondroRA ra = new kelondroNIOFileRA(f, true, 10);
            ra.seek(8); ra.write((byte) 'h');
            ra.close();
        } catch (IOException e) { e.printStackTrace(); }
        
        System.out.println("* write in at body");
        try { kelondroRA ra = new kelondroNIOFileRA(f, true, 10);
            ra.seek(1024); ra.write((byte) 'b');
            ra.close();
        } catch (IOException e) { e.printStackTrace(); }
        
        System.out.println("* write in at tail");
         try { kelondroRA ra = new kelondroNIOFileRA(f, true, 10);
            ra.seek(2048); ra.write((byte) 't');
            ra.close();
        } catch (IOException e) { e.printStackTrace(); }
        
        System.out.println("* write in behind tail");
        try { kelondroRA ra = new kelondroNIOFileRA(f, true, 10);
            ra.seek(2059); ra.write((byte) 'x');
            ra.close();
        } catch (IOException e) { e.printStackTrace(); }
    }

}
