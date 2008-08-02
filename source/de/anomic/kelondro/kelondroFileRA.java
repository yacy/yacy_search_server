// kelondroFileRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.kelondro;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

public final class kelondroFileRA extends kelondroAbstractRA implements kelondroRA {

    protected RandomAccessFile RAFile;
    
    public kelondroFileRA(final String file) throws IOException, FileNotFoundException {
        this(new File(file));
    }

    public kelondroFileRA(final File file) throws IOException, FileNotFoundException {
        this.name = file.getName();
        this.file = file;
        RAFile = new RandomAccessFile(file, "rw");
    }	
    
    public long length() throws IOException {
        return RAFile.length();
    }
    
    public long available() throws IOException {
        return RAFile.length() - RAFile.getFilePointer();
    }
    
    // pseudo-native method read
    public int read() throws IOException {
        return RAFile.read();
    }

    // pseudo-native method write
    public void write(final int b) throws IOException {
        RAFile.write(b);
    }

    public int read(final byte[] b, final int off, final int len) throws IOException {
        return RAFile.read(b, off, len);
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
        // write to file
        RAFile.write(b, off, len);
    }

    public void seek(final long pos) throws IOException {
        RAFile.seek(pos);
    }

    public void close() throws IOException {
        if (RAFile != null) RAFile.close();
        RAFile = null;
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
