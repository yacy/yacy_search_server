// XMLTables.java
// -------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created 09.02.2006
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.util;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class XMLTables {

    private Map<String, Map<String, String>> tables;

    // tables is a hashtable that contains hashtables as values in the table
    private File propFile;
    private long timestamp;

    public XMLTables() {
        this.propFile = null;
        this.timestamp = System.currentTimeMillis();
        this.tables = new HashMap<String, Map<String, String>>();
    }

    @SuppressWarnings("unchecked")
    public XMLTables(final File file) throws IOException {
        this.propFile = file;
        this.timestamp = System.currentTimeMillis();
        if (propFile.exists()) {
            final XMLDecoder xmldec = new XMLDecoder(new FileInputStream(propFile));
            tables = (HashMap<String, Map<String, String>>) xmldec.readObject();
            xmldec.close();
        } else {
            tables = new HashMap<String, Map<String, String>>();
        }
    }

    public void commit(final File target) throws IOException {
        // this method is used if the Mircrotable was created without assigning
        // a file to it as an empty table the table then becomes file-based,
        // and write operation will be committed to the file
        this.propFile = target;
        commit(true);
    }

    private void commit(final boolean force) throws IOException {
        // this function commits the data to a file
        // it does not save the data until a specific waiting-time has been lasted
        if ((force) || (System.currentTimeMillis() - timestamp > 10000)) {
            // check error case: can only occur if logical programmic error
            // exists
            if (this.propFile == null)
                throw new RuntimeException("Microtables.commit: no file specified");

            // write first to a temporary file
            final File tmpFile = new File(this.propFile.toString() + ".prt");

            // write file
            final XMLEncoder xmlenc = new XMLEncoder(new FileOutputStream(tmpFile));
            xmlenc.writeObject(tables);
            xmlenc.close();
            
            // delete old file and rename tmp-file to old file's name
            FileUtils.deletedelete(this.propFile);
            tmpFile.renameTo(this.propFile);

            // set the new time stamp
            timestamp = System.currentTimeMillis();
        }
    }

    public boolean hasTable(final String table) {
        return (tables.get(table) != null);
    }

    public int sizeTable(final String table) {
        // returns number of entries in table; if table does not exist -1
        final Map<String, String> l = tables.get(table);
        if (l == null) return -1;
        return l.size();
    }

    public void createTable(final String table) throws IOException {
        // creates a new table
        final Map<String, String> l = tables.get(table);
        if (l != null)
            return; // we do not overwite
        tables.put(table, new HashMap<String, String>());
        if (this.propFile != null) commit(false);
    }

    public void set(final String table, final String key, String value) throws IOException {
        if (table != null) {
            final Map<String, String> l = tables.get(table);
            if (l == null) throw new RuntimeException("Microtables.set: table does not exist");
            if (value == null) value = "";
            l.put(key, value);
        }
        if (this.propFile != null)
            commit(false);
    }

    public String get(final String table, final String key, final String deflt) {
        if (table != null) {
            final Map<String, String> l = tables.get(table);
            if (l == null)
                throw new RuntimeException("Microtables.get: table does not exist");
            if (!l.containsKey(key))
                return deflt;
            return l.get(key);
        }
        return null;
    }

    public boolean has(final String table, final String key) {
        if (table != null) {
            final Map<String, String> l = tables.get(table);
            if (l == null)
                throw new RuntimeException("Microtables.has: table does not exist");
            return (l.containsKey(key));
        }
        return false;
    }

    public Iterator<String> keys(final String table) {
        if (table != null) {
            final Map<String, String> l = tables.get(table);
            if (l == null)
                throw new RuntimeException("Microtables.keys: table does not exist");
            return l.keySet().iterator();
        }
        return null;
    }

    public synchronized void close() throws IOException {
        commit(true);
    }

}
