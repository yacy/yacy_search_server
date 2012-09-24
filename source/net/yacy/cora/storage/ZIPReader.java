/**
 *  ZIPReader
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 24.09.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZIPReader extends AbstractMap<String, ZipEntry> implements Map<String, ZipEntry>, Iterable<Map.Entry<String, ZipEntry>> {

    private final Set<String> filenames;
    private final ZipFile zipFile;

    public ZIPReader(File file) throws IOException {
        super();
        if (!file.exists()) throw new IOException("ZIPWriter can only be used for existing files");
        this.zipFile = new ZipFile(file);

        // read all entries
        this.filenames = new HashSet<String>();
        final Enumeration<? extends ZipEntry> e = this.zipFile.entries();
        while (e.hasMoreElements()) {
            ZipEntry z = e.nextElement();
            this.filenames.add(z.getName());
        }
    }

    @Override
    public Iterator<java.util.Map.Entry<String, ZipEntry>> iterator() {
        final Enumeration<? extends ZipEntry> e = this.zipFile.entries();
        return new Iterator<java.util.Map.Entry<String, ZipEntry>>() {

            @Override
            public boolean hasNext() {
                return e.hasMoreElements();
            }

            @Override
            public java.util.Map.Entry<String, ZipEntry> next() {
                ZipEntry z = e.nextElement();
                return new AbstractMap.SimpleImmutableEntry<String, ZipEntry>(z.getName(), z);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    @Override
    public int size() {
        return this.zipFile.size();
    }

    @Override
    public boolean isEmpty() {
        return this.zipFile.size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return this.filenames.contains(key);
    }

    @Override
    public ZipEntry get(Object key) {
        return this.zipFile.getEntry((String) key);
    }

    @Override
    public Set<String> keySet() {
        return this.filenames;
    }

    @Override
    public Set<java.util.Map.Entry<String, ZipEntry>> entrySet() {
        throw new UnsupportedOperationException();
    }

    public void close() throws IOException {
        this.zipFile.close();
    }

    /**
     * decompress a zip file and reconstruct full directory structure
     * @param zipIn
     * @param outDir
     * @throws IOException
     */
    public static void unzip(File zipIn, File outDir) throws IOException {
        ZipFile zfile = new ZipFile(zipIn);
        Enumeration<? extends ZipEntry> entries = zfile.entries();
        byte[] buffer = new byte[1024];
        int readCount;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File file = new File(outDir, entry.getName());
            if (entry.isDirectory()) {
                file.mkdirs();
            } else {
                file.getParentFile().mkdirs();
                InputStream in = zfile.getInputStream(entry);
                try {
                    OutputStream out = new FileOutputStream(file);
                    try { while ((readCount = in.read(buffer)) > 0) out.write(buffer, 0, readCount); } finally { out.close(); }
                } finally {
                    in.close();
                }
            }
        }
    }
}
