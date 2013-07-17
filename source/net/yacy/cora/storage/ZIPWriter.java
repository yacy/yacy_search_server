/**
 *  ZIPWriter
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZIPWriter extends AbstractMap<String, ZipEntry> implements Map<String, ZipEntry>, Iterable<Map.Entry<String, ZipEntry>> {

    private final HashMap<String, ZipEntry> backup;
    private final ZipOutputStream zos;

    public ZIPWriter(File file) throws IOException {
        super();
        if (file.exists()) throw new IOException("ZIPWriter can only be used for new files");
        this.backup = new HashMap<String, ZipEntry>();
        this.zos = new ZipOutputStream(new FileOutputStream(file));
    }

    @Override
    public ZipEntry put(String key, ZipEntry value) {
        assert !this.backup.containsKey(key);
        try {
            this.zos.putNextEntry(value);
            this.backup.put(key, value);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ZipEntry get(Object key) {
        return this.backup.get(key);
    }

    @Override
    public Iterator<java.util.Map.Entry<String, ZipEntry>> iterator() {
        return this.backup.entrySet().iterator();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<java.util.Map.Entry<String, ZipEntry>> entrySet() {
        return this.backup.entrySet();
    }

    public void close() throws IOException {
        this.zos.close();
    }

    /**
     * create a zip file from a directory
     * @param inputDir
     * @param zipOut
     * @throws IOException
     */
    public static void zip(File inputDir, File zipOut) throws IOException {
        URI base = inputDir.toURI();
        Deque<File> queue = new LinkedList<File>();
        queue.push(inputDir);
        OutputStream out = new FileOutputStream(zipOut);
        ZipOutputStream zout = null;
        byte[] buffer = new byte[1024];
        int readCount;
        try {
            zout = new ZipOutputStream(out);
            while (!queue.isEmpty()) {
                inputDir = queue.pop();
                for (File lf : inputDir.listFiles()) {
                    String name = base.relativize(lf.toURI()).getPath();
                    if (lf.isDirectory()) {
                        queue.push(lf);
                        name = name.endsWith("/") ? name : name + "/";
                        zout.putNextEntry(new ZipEntry(name));
                    } else {
                        zout.putNextEntry(new ZipEntry(name));
                        InputStream in = new FileInputStream(lf);
                        try { while ((readCount = in.read(buffer)) > 0) zout.write(buffer, 0, readCount); } finally { in.close(); }
                        zout.closeEntry();
                    }
                }
            }
        } finally {
            zout.close();
            out.close();
        }
    }

}
