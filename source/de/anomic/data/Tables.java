package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.kelondro.blob.BEncodedHeapArray;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import de.anomic.server.serverObjects;

public class Tables {

    public final static String API_TYPE_CONFIGURATION = "configuration";
    public final static String API_TYPE_CRAWLER = "crawler";
    
    private BEncodedHeapArray tables;
    
    public Tables(File workPath) {
        this.tables = new BEncodedHeapArray(workPath, 12);
    }
    
    public boolean has(String table) {
        return tables.hasHeap(table);
    }
    
    public boolean has(String table, byte[] pk) {
        try {
            return tables.has(table, pk);
        } catch (IOException e) {
            Log.logException(e);
            return false;
        }
    }
    
    public Iterator<String> tables() {
        return this.tables.tables();
    }
    
    public List<String> columns(String table) {
        try {
            return this.tables.columns(table);
        } catch (IOException e) {
            Log.logException(e);
            return new ArrayList<String>(0);
        }
    }
    
    public void clear(String table) {
        try {
            this.tables.clear(table);
        } catch (IOException e) {
            Log.logException(e);
        }
    }
    
    public void delete(String table, byte[] pk) {
        try {
            this.tables.delete(table, pk);
        } catch (IOException e) {
            Log.logException(e);
        }
    }
    
    public int size(String table) {
        try {
            return this.tables.size(table);
        } catch (IOException e) {
            Log.logException(e);
            return 0;
        }
    }
    
    public Iterator<Map.Entry<byte[], Map<String, byte[]>>> iterator(String table) {
        try {
            return this.tables.iterator(table);
        } catch (IOException e) {
            Log.logException(e);
            return new TreeMap<byte[], Map<String, byte[]>>().entrySet().iterator();
        }
    }
    
    public void close() {
        this.tables.close();
    }
    
    public Map<String, byte[]> select(String table, byte[] pk) {
        try {
            return tables.select(table, pk);
        } catch (IOException e) {
            Log.logException(e);
            return new TreeMap<String, byte[]>();
        }
    }
    
    public void insert(String table, byte[] pk, Map<String, byte[]> map) {
        try {
            this.tables.insert(table, pk, map);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        } catch (IOException e) {
            Log.logException(e);
        }
    }
    
    public String createRow(String table) {
        try {
            return new String(this.tables.insert(table, new HashMap<String, byte[]>()));
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            return null;
        } catch (IOException e) {
            Log.logException(e);
            return null;
        }
    }
    
    public void recordAPICall(final serverObjects post, final String servletName, String type, String comment) {
        String apiurl = /*"http://localhost:" + getConfig("port", "8080") +*/ "/" + servletName + "?" + post.toString();
        try {
            this.tables.insert(
                    "api",
                    "type", type.getBytes(),
                    "comment", comment.getBytes(),
                    "date", DateFormatter.formatShortMilliSecond(new Date()).getBytes(),
                    "url", apiurl.getBytes()
                    );
        } catch (RowSpaceExceededException e2) {
            Log.logException(e2);
        } catch (IOException e2) {
            Log.logException(e2);
        }
        Log.logInfo("APICALL", apiurl);
    }
    
    
}
