package net.yacy.cora.storage;

import java.io.File;
import java.io.IOException;
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

}
