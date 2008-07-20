// kelondroNIOFileRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

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
        this.file = file;
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
        if (newPos >= tailMaxSize) {
            System.out.println("cannot grow " + name);
            return false;
        }
        if (tailCurrSize == 0) {
            // first grow
            this.tailCurrSize = newPos;
            if (tailCurrSize < 1024) tailCurrSize = 1024;
            if (tailCurrSize > tailMaxSize) tailCurrSize = tailMaxSize;
        } else {
            // next grow
            tailCurrSize = tailCurrSize * 2;
            if (tailCurrSize < newPos) tailCurrSize = newPos;
            if (tailCurrSize > tailMaxSize) tailCurrSize = tailMaxSize;
            bufferTail.force();
        }
        System.out.println("growing " + name + " nextSize=" + tailCurrSize);
        bufferTail = RAChannel.map(FileChannel.MapMode.READ_WRITE, tailOffset, (int) tailCurrSize);
        wroteTail = false;
        return true;
    }
    
    public long length() throws IOException {
        return RAFile.length();
    }
    
    public long available() throws IOException {
        return RAFile.length() - RAFile.getFilePointer();
    }
    
    // pseudo-native method read
    public int read() throws IOException {
        int r;
        if (seekPos < bodyOffset) {
            r = 0xFF & (bufferHead.get((int) seekPos));
        } else if (seekPos < tailOffset) {
            if (mapBody) {
                r = 0xFF & (bufferBody.get((int) (seekPos - bodyOffset)));
            } else {
                RAFile.seek(seekPos);
                r = RAFile.read();
            }
        } else if (seekPos < (tailOffset + tailCurrSize)) {
            r = 0xFF & (bufferTail.get((int) (seekPos - tailOffset)));
        } else {
            r = -1;
            while (growTail(seekPos)) {
                if (seekPos < (tailOffset + tailCurrSize)) {
                    r = 0xFF & (bufferTail.get((int) (seekPos - tailOffset)));
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
        RAChannel = null;
        RAFile.close(); 
        RAFile = null;
    }
    
    protected void finalize() throws Throwable {
        if (RAChannel != null) {
            try {RAChannel.close();}catch(Exception e){}
        }
        if (RAFile != null) {
            try {RAFile.close();}catch(Exception e){}
        }
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
