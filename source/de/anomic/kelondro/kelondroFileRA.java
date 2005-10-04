// kelondroFileRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 05.02.2004
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.FileDescriptor;
import java.io.SyncFailedException;
import java.util.Map;
import java.util.Properties;

public class kelondroFileRA extends kelondroAbstractRA implements kelondroRA {

    protected RandomAccessFile RAFile;
    protected FileDescriptor   RADescriptor;
    
    public kelondroFileRA(String file) throws IOException, FileNotFoundException {
        this(new File(file));
    }

    public kelondroFileRA(File file) throws IOException, FileNotFoundException {
        this.name = file.getName();
	RAFile  = new RandomAccessFile(file, "rw");
        RADescriptor = RAFile.getFD();
    }

    /*
    private void sync() throws IOException {
        try {
            RADescriptor.sync();
            //try {Thread.currentThread().sleep(8);} catch (InterruptedException e) {return;}
        } catch (SyncFailedException e) {
            throw new IOException(e.getMessage());
        }
    }
    */
    
    // pseudo-native method read
    public int read() throws IOException {
        return RAFile.read();
    }

    // pseudo-native method write
    public void write(int b) throws IOException {
        RAFile.write(b);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return RAFile.read(b, off, len);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        // write to file
        RAFile.write(b, off, len);
    }

    public void seek(long pos) throws IOException {
	RAFile.seek(pos);
    }

    public void close() throws IOException {
        RAFile.close();
        RAFile = null;
    }

    protected void finalize() throws Throwable {
        if (RAFile != null) {
            this.close();
        }
        super.finalize();
    }
    
    // some static tools
    public static void writeProperties(File f, Properties props, String comment) throws IOException {
        File fp = f.getParentFile();
        if (fp != null) fp.mkdirs();
        kelondroRA kra = null;
        try {
            kra = new kelondroFileRA(f);
            kra.writeProperties(props, comment);
            kra.close();
        } finally {
            if (kra != null) try {kra.close();}catch(Exception e){}
        }
    }

    public static Properties readProperties(File f) throws IOException {
        kelondroRA kra = null;
        try {
            kra = new kelondroFileRA(f);
            Properties props = kra.readProperties();
            kra.close();
            return props;
        } finally {
            if (kra != null) try{kra.close();}catch(Exception e) {}
        }
    }

    public static void writeMap(File f, Map map, String comment) throws IOException {
        File fp = f.getParentFile();
        if (fp != null) fp.mkdirs();
        kelondroRA kra = null;
        try {
            kra = new kelondroFileRA(f);
            kra.writeMap(map, comment);
            kra.close();
        } finally {
            if (kra != null) try {kra.close();}catch(Exception e){}
        }
    }

    public static Map readMap(File f) throws IOException {
        kelondroRA kra = null;
        try {
            kra = new kelondroFileRA(f);
            Map map = kra.readMap();
            kra.close();
            return map;
        } finally {
            if (kra != null) try {kra.close();}catch(Exception e){}
        }
    }

}
