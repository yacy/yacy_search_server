// kelondroXMLTables.java
// -------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
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

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

public class kelondroXMLTables {

    private Hashtable<String, Hashtable<String, String>> tables;

    // tables is a hashtable that contains hashtables as values in the table
    private File propFile;
    private long timestamp;

    public kelondroXMLTables() {
        this.propFile = null;
        this.timestamp = System.currentTimeMillis();
        this.tables = new Hashtable<String, Hashtable<String, String>>();
    }

    @SuppressWarnings("unchecked")
    public kelondroXMLTables(File file) throws IOException {
        this.propFile = file;
        this.timestamp = System.currentTimeMillis();
        if (propFile.exists()) {
            XMLDecoder xmldec = new XMLDecoder(new FileInputStream(propFile));
            tables = (Hashtable<String, Hashtable<String, String>>) xmldec.readObject();
            xmldec.close();
        } else {
            tables = new Hashtable<String, Hashtable<String, String>>();
        }
    }

    public void commit(File target) throws IOException {
        // this method is used if the Mircrotable was created without assigning
        // a file to it as an empty table the table then becomes file-based,
        // and write operation will be committed to the file
        this.propFile = target;
        commit(true);
    }

    private void commit(boolean force) throws IOException {
        // this function commits the data to a file
        // it does not save the data until a specific waiting-time has been lasted
        if ((force) || (System.currentTimeMillis() - timestamp > 10000)) {
            // check error case: can only occur if logical programmic error
            // exists
            if (this.propFile == null)
                throw new RuntimeException("Microtables.commit: no file specified");

            // write first to a temporary file
            File tmpFile = new File(this.propFile.toString() + ".tmp");

            // write file
            XMLEncoder xmlenc = new XMLEncoder(new FileOutputStream(tmpFile));
            xmlenc.writeObject(tables);
            xmlenc.close();
            
            // delete old file and rename tmp-file to old file's name
            this.propFile.delete();
            tmpFile.renameTo(this.propFile);

            // set the new time stamp
            timestamp = System.currentTimeMillis();
        }
    }

    public boolean hasTable(String table) {
        return (tables.get(table) != null);
    }

    public int sizeTable(String table) {
        // returns number of entries in table; if table does not exist -1
        Hashtable<String, String> l = tables.get(table);
        if (l == null) return -1;
        return l.size();
    }

    public void createTable(String table) throws IOException {
        // creates a new table
        Hashtable<String, String> l = tables.get(table);
        if (l != null)
            return; // we do not overwite
        tables.put(table, new Hashtable<String, String>());
        if (this.propFile != null) commit(false);
    }

    public void set(String table, String key, String value) throws IOException {
        if (table != null) {
            Hashtable<String, String> l = tables.get(table);
            if (l == null) throw new RuntimeException("Microtables.set: table does not exist");
            if (value == null) value = "";
            l.put(key, value);
        }
        if (this.propFile != null)
            commit(false);
    }

    public String get(String table, String key, String deflt) {
        if (table != null) {
            Hashtable<String, String> l = tables.get(table);
            if (l == null)
                throw new RuntimeException("Microtables.get: table does not exist");
            if (l.containsKey(key))
                return (String) l.get(key);
            else
                return deflt;
        }
        return null;
    }

    public boolean has(String table, String key) {
        if (table != null) {
            Hashtable<String, String> l = tables.get(table);
            if (l == null)
                throw new RuntimeException("Microtables.has: table does not exist");
            return (l.containsKey(key));
        }
        return false;
    }

    public Enumeration<String> keys(String table) {
        if (table != null) {
            Hashtable<String, String> l = tables.get(table);
            if (l == null)
                throw new RuntimeException("Microtables.keys: table does not exist");
            return l.keys();
        }
        return null;
    }

    public void close() throws IOException {
        finalize();
    }

    // we finalize the operation by saving everything throug the scheduler
    // this method is called by the java GC bevore it destroys the object
    protected void finalize() throws IOException {
        commit(true);
    }

}
