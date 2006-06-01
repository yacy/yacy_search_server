package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class kelondroFlexTable extends kelondroFlexWidthArray implements kelondroIndex {

    private HashMap index;
    
    public kelondroFlexTable(File path, String tablename, kelondroRow rowdef, boolean exitOnFail) throws IOException {
        super(path, tablename, rowdef, exitOnFail);
        
        // fill the index
        this.index = new HashMap();
    }

    public int columnSize(int column) {
        return rowdef.width(column);
    }
    
    public kelondroRow.Entry get(byte[] key) throws IOException {
        return null;
    }

    public byte[][] put(byte[][] row) throws IOException {
        return null;
    }
    
    public byte[][] remove(byte[] key) throws IOException {
        return null;
    }

}
