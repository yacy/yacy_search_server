package net.yacy.cora.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
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
        } catch (IOException e) {
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

}
