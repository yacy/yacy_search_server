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

package de.anomic.kelondro.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;


public final class FileRandomAccess extends AbstractRandomAccess implements RandomAccessInterface {

    private RandomAccessFile RAFile;

    public FileRandomAccess(final File file) throws IOException, FileNotFoundException {
        this.name = file.getName();
        this.file = file;
        RAFile = new RandomAccessFile(file, "rw");
    }   
    
    public synchronized long length() throws IOException {
        return this.RAFile.length();
    }
    
    public synchronized void setLength(long length) throws IOException {
        RAFile.setLength(length);
    }
    
    public synchronized long available() throws IOException {
        return this.length() - RAFile.getFilePointer();
    }

    public synchronized final void readFully(final byte[] b, final int off, int len) throws IOException {
        RAFile.readFully(b, off, len);
    }

    public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
        RAFile.write(b, off, len);
    }

    public synchronized void seek(final long pos) throws IOException {
        RAFile.seek(pos);
    }

    public synchronized void close() throws IOException {
        if (RAFile != null) RAFile.close();
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
        RandomAccessInterface kra = null;
        try {
            kra = new CachedRandomAccess(f);
            kra.writeMap(map, comment);
            kra.close();
        } finally {
            if (kra != null) try {kra.close();}catch(final Exception e){}
        }
    }

    public static Map<String, String> readMap(final File f) throws IOException {
        RandomAccessInterface kra = null;
        try {
            kra = new CachedRandomAccess(f);
            final Map<String, String> map = kra.readMap();
            kra.close();
            return map;
        } finally {
            if (kra != null) try {kra.close();}catch(final Exception e){}
        }
    }

}
