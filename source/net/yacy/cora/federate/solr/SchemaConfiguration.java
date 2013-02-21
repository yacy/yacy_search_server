/**
 *  SchemaConfiguration
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.06.2011 at http://yacy.net
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

package net.yacy.cora.federate.solr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.federate.solr.SchemaConfiguration.Entry;
import net.yacy.cora.storage.Files;

/**
 * this class reads configuration attributes as a list of keywords from a list
 * the list may contain lines with one keyword, comment lines, empty lines and out-commented keyword lines
 * when an attribute is changed here, the list is stored again with the original formatting
 *
 * the syntax of configuration files:
 * - all lines beginning with '##' are comments
 * - all non-empty lines not beginning with '#' are keyword lines
 * - all lines beginning with '#' and where the second character is not '#' are commented-out keyword lines
 * - all text after a '#' not at beginn of line is treated as comment (like 'key = value  # comment' )
 * - a line may contain a key only or a key=value pair
 * @author Michael Christen
 */
public class SchemaConfiguration extends TreeMap<String,Entry> implements Serializable {

    private final static long serialVersionUID=-5961730809008841258L;
    private final static Logger log = Logger.getLogger(SchemaConfiguration.class);
   
    private final File file;
    protected boolean lazy;

    public SchemaConfiguration() {
        this.file = null;
        this.lazy = false;
    }

    public SchemaConfiguration(final File file) {
        this.file = file;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(this.file));
            String s;
            boolean enabled;
            String comment, key, value;
            int i;
            comment = null;
            while ((s = br.readLine()) != null) {

                if (s.startsWith("##") || s.isEmpty()){
                    // is comment line - do nothing
                    if (s.startsWith("##")) comment = s.substring(2);
                    continue;
                }
                if (s.startsWith("#")) {
                    enabled = false ;
                    s = s.substring (1).trim();
                } else {
                    enabled = true;
                }
                if (s.contains("#")) {
                    // second # = text afterwards is a comment
                    i = s.indexOf("#");
                    comment = s.substring(i+1);
                    s = s.substring(0,i).trim();
                } else {
                   // comment = null;
                }
                if (s.contains("=")) {
                    i = s.indexOf("=");
                    key = s.substring(0,i).trim();
                    value = s.substring(i+1).trim();
                    if (value.isEmpty()) value = null;

                } else {
                    key = s.trim();
                    value = null;
                }
                if (!key.isEmpty()) {
                    Entry entry = new Entry(key, value, enabled);
                    if (comment != null) {
                        entry.setComment(comment);
                        comment = null;
                    }
                    this.put(key, entry);
                }
            }
        } catch (final IOException e) {
            log.warn(e);
        } finally {
            if (br != null) try {br.close();} catch (IOException e) {}
        }
    }

    /**
     * override the abstract implementation because that is not stable in concurrent requests
     */
    public boolean contains(String key) {
        if (key == null) return false;
        Entry e = this.get(key);
        return e == null ? false : e.enabled();
    }
    public boolean containsDisabled(final String o) {
        if (o == null) return false;
        Entry e = this.get(o);
        return e == null ? false : !e.enabled();
    }

    public boolean add(final String key) {
        return add(key, null);
    }

    public boolean add(final String key, final String comment) {
        return add(key, comment, true);
    }

    public boolean add(final String key, final String comment, final boolean enabled) {
        boolean modified = false;
        Entry entry = get(key);
        if (entry == null) {
           entry = new Entry (key,enabled);
           if (comment != null) entry.setComment(comment);
           this.put (key,entry);
           modified = true;
        } else {
            if (entry.enabled() != enabled) {
                entry.setEnable(enabled);
                modified = true;
            }
            if ( (comment != null) && ( !comment.equals(entry.getComment()) )) {
                entry.setComment(comment);
                modified = true;
            }
        }

        try {
            if (modified) {
                commit();
            }

        } catch (final IOException e) {}
        return modified;
    }

    public void fill(final SchemaConfiguration other, final boolean defaultActivated) {
        final Iterator<Entry> i = other.entryIterator();
        Entry e, enew = null;
        while (i.hasNext()) {
            e = i.next();
            if (contains(e.key) || containsDisabled(e.key)) continue;
            // add as new entry
            enew = new Entry(e.key(),e.getValue(),defaultActivated && e.enabled());
            enew.setComment(e.getComment());
            this.put(e.key(),enew);
        }
        if (enew != null) {
            try {
                commit();
            } catch (IOException ex) {
                log.warn(ex);
            }
        }
    }

    public boolean contains(SchemaDeclaration field) {
        return this.contains(field.name());
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final String value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final Date value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.getTime() > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final String[] value) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final Integer[] value) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final List<?> values) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (values != null && !values.isEmpty()))) key.add(doc, values);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final int value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final long value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final boolean value) {
        assert !key.isMultiValued();
        if (isEmpty() || contains(key)) key.add(doc, value);
    }

    public static Date getDate(SolrInputDocument doc, final SchemaDeclaration key) {
        Date x = (Date) doc.getFieldValue(key.getSolrFieldName());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
    }
    
    /**
     * save the configuration back to the file
     * @throws IOException
     */
    public void commit() throws IOException {
        if (this.file == null) return;
        // create a temporary bak file, use it as template to preserve user comments
        File bakfile = new File (this.file.getAbsolutePath() + ".bak");
        try {
            Files.copy(this.file, bakfile);
        } catch (final IOException e) {
            this.file.createNewFile();
        }
        @SuppressWarnings("unchecked")
        TreeMap<String,Entry> tclone = (TreeMap<String,Entry>) this.clone(); // clone to write appended entries

        final BufferedWriter writer = new BufferedWriter(new FileWriter(this.file));
        try {
            final BufferedReader reader = new BufferedReader(new FileReader(bakfile));
            String s, sorig;
            String key;
            int i;
            while ((sorig = reader.readLine()) != null) {

                if (sorig.startsWith("##") || sorig.isEmpty()){
                    // is comment line - write as is
                    writer.write(sorig + "\n");
                    continue;
                }
                if (sorig.startsWith("#")) {
                    s = sorig.substring (1).trim();
                } else {
                    s = sorig;
                }
                if (s.contains("#")) {
                    // second # = is a line comment
                    i = s.indexOf("#");
                    s = s.substring(0,i).trim();
                }
                if (s.contains("=")) {
                    i = s.indexOf("=");
                    key = s.substring(0,i).trim();
                } else {
                    key = s.trim();
                }
                if (!key.isEmpty()) {
                    Entry e = this.get(key);
                    if (e != null) {
                        writer.write (e.toString());
                        tclone.remove(key); // remove written entries from clone
                    }
                    writer.write("\n");
                } else {
                    writer.write(sorig+"\n");
                }
            }
            reader.close();
            bakfile.delete();
        } catch (final IOException e) {}

        // write remainig entries (not already written)
        Iterator<Map.Entry<String,Entry>> ie = tclone.entrySet().iterator();
        while (ie.hasNext()) {
            Object e = ie.next();
            writer.write (e.toString() + "\n");
        }
        writer.close();
    }

    
    
    
    
    public Iterator<Entry> entryIterator() {
        return this.values().iterator();
    }

    public class Entry {
        private final String key;
        private String value;
        private boolean enabled;
        private String comment;

        public Entry(final String key, final boolean enabled) {
            this.enabled = enabled;
            // split in key, value if line contains a "=" (equal sign)   e.g.   myattribute = 123
            // for backward compatibility here the key parameter is checked to contain a "="
            if (key.contains("=")) {
                int i = key.indexOf("=");
                this.key = key.substring(0,i).trim();
                this.value = key.substring(i+1).trim();
            } else {
                this.key = key;
                this.value = null;
            }
        }
        public Entry (final String key, String value, final boolean enabled) {
            this.enabled = enabled;
            this.key = key;
            this.value = value;
        }
        public String key() {
            return this.key;
        }
        public void setValue(String theValue) {
            //empty string not wanted
            if ((theValue != null) && theValue.isEmpty()) {
                this.value = null;
            } else {
                this.value = theValue;
            }
        }
        public String getValue() {
            return this.value;
        }
        public void setComment(String comment) {
            this.comment = comment;
        }
        public String getComment() {
            return this.comment;
        }
        public void setEnable(boolean value){
            this.enabled = value;
        }
        public boolean enabled() {
            return this.enabled;
        }
        @Override
        public String toString(){
            // output string to write to config file
            return (this.enabled ? "" : "#") + (this.value != null ?  this.key + " = " + this.value : this.key ) + (this.comment != null ? "  #" + this.comment : "");
        }
    }

    public static void main(final String[] args) {
        if (args.length == 0) return;
        final File f = new File (args[0]);
        final SchemaConfiguration cs = new SchemaConfiguration(f);
        Iterator<Entry> i = cs.entryIterator();
        Entry k;
        System.out.println("\nall activated attributes:");
        while (i.hasNext()) {
            k = i.next();
            if (k.enabled()) System.out.println(k.toString());
        }
        i = cs.entryIterator();
        System.out.println("\nall deactivated attributes:");
        while (i.hasNext()) {
            k = i.next();
            if (!k.enabled()) System.out.println(k.toString() );
        }
    }

}
