// kelondroAttrSeq.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 15.11.2005
//
// $LastChangedDate: 2005-10-22 15:28:04 +0200 (Sat, 22 Oct 2005) $
// $LastChangedRevision: 968 $
// $LastChangedBy: theli $
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;

public class kelondroAttrSeq {
    
    // class objects
    private File file;
    private Map entries;
    protected Structure structure;
    private String name;
    private long created;
    
    // optional logger
    protected Logger theLogger = null;
    
    public kelondroAttrSeq(File file, boolean tree) throws IOException {
        this.file = file;
	this.structure = null;
        this.created = -1;
        this.name = "";
        this.entries = (tree) ? (Map) new TreeMap() : (Map) new HashMap();
        readAttrFile(file);
    }

    public kelondroAttrSeq(String name, String struct, boolean tree) {
        this.file = null;
	this.structure = new Structure(struct);
        this.created = System.currentTimeMillis();
        this.name = name;
        this.entries = (tree) ? (Map) new TreeMap() : (Map) new HashMap();
    }
        
    public void setLogger(Logger newLogger) {
        this.theLogger = newLogger;
    }
    
    public void logInfo(String message) {
        if (this.theLogger == null)
            System.err.println("ATTRSEQ INFO for file " + this.file + ": " + message);
        else
            this.theLogger.info("ATTRSEQ INFO for file " + this.file + ": " + message);
    }
    
    public void logWarning(String message) {
        if (this.theLogger == null)
            System.err.println("ATTRSEQ WARNING for file " + this.file + ": " + message);
        else
            this.theLogger.warning("ATTRSEQ WARNING for file " + this.file + ": " + message);
    }
    
    private void readAttrFile(File loadfile) throws IOException {
        BufferedReader br = null;
        int p;
        if (loadfile.toString().endsWith(".gz")) {
            br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(loadfile))));
        } else {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(loadfile)));
        }
        String line, key, oldvalue, newvalue;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) continue;
            if (line.startsWith("#")) {
                if (line.startsWith("# Structure=")) {
                    structure = new Structure(line.substring(12));
                }
                if (line.startsWith("# Name=")) {
                    name = line.substring(7);
                }
                if (line.startsWith("# Created=")) {
                    created = Long.parseLong(line.substring(10));
                }
                continue;
            }
            if ((p = line.indexOf('=')) > 0) {
                key = line.substring(0, p).trim();
                newvalue = line.substring(p + 1).trim();
                oldvalue = (String) entries.get(key);
                if (oldvalue != null) {
                    if (newvalue.equals(oldvalue)) {
                        //logWarning("key " + key + ": double occurrence. values are equal. second appearance is ignored");
                    } else {
                        if (newvalue.length() < oldvalue.length()) {
                            if (oldvalue.substring(0, newvalue.length()).equals(newvalue)) {
                                logWarning("key " + key + ": double occurrence. new value is subset of old value. second appearance is ignored");
                            } else {
                                logWarning("key " + key + ": double occurrence. new value is shorter than old value, but not a subsequence. old = " + oldvalue + ", new = " + newvalue);
                            }
                        } else if (newvalue.length() > oldvalue.length()) {
                            if (newvalue.substring(0, oldvalue.length()).equals(oldvalue)) {
                                logWarning("key " + key + ": double occurrence. old value is subset of new value. first appearance is ignored");
                            } else {
                                logWarning("key " + key + ": double occurrence. old value is shorter than new value, but not a subsequence. old = " + oldvalue + ", new = " + newvalue);
                            }
                            entries.put(key, newvalue);
                        } else {
                            logWarning("key " + key + ": double occurrence. old and new value have equal length but are not equal. old = " + oldvalue + ", new = " + newvalue);
                            //entries.put(key, newvalue);
                        }
                    }
                } else {
                    entries.put(key, newvalue);
                }
            }
        }
        br.close();
        if (structure == null) throw new IOException("file contains no structure tag");
        if (name == null) throw new IOException("file contains no name tag");
        if (created == -1) throw new IOException("file contains no created tag");
    }
    
    public int size() {
        return entries.size();
    }
    
    public long created() {
        return this.created;
    }
    
    public void toFile(File out) throws IOException {
        // generate header
        StringBuffer sb = new StringBuffer(2000);
        sb.append("# Name=" + this.name); sb.append((char) 13); sb.append((char) 10);
        sb.append("# Created=" + this.created); sb.append((char) 13); sb.append((char) 10);
        sb.append("# Structure=" + this.structure.toString()); sb.append((char) 13); sb.append((char) 10);
        sb.append("# ---"); sb.append((char) 13); sb.append((char) 10);
        Iterator i = entries.entrySet().iterator();
        Map.Entry entry;
        String k;
        Object v;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            k = (String) entry.getKey();
            v = entry.getValue();
            sb.append(k); sb.append('=');
            if (v instanceof String) sb.append((String) v);
            if (v instanceof Entry) sb.append(((Entry) v).toString());
            sb.append((char) 13); sb.append((char) 10);
        }
        if (out.toString().endsWith(".gz")) {
            serverFileUtils.writeAndGZip((new String(sb)).getBytes(), out);
        } else {
            serverFileUtils.write((new String(sb)).getBytes(), out);
        }
    }
    
    public Iterator keys() {
        return entries.keySet().iterator();
    }
    
    public Entry newEntry(String pivot, boolean tree) {
        return new Entry(pivot, new HashMap(), (tree) ? (Set) new TreeSet() : (Set) new HashSet());
    }
    
    public Entry newEntry(String pivot, HashMap props, Set seq) {
        return new Entry(pivot, props, seq);
    }
    
    /*
    public void putEntry(String pivot, String attrseq) {
        entries.put(pivot, attrseq);
    }
    */
    
    public void putEntry(Entry entry) {
        if (shortmem())
            entries.put(entry.pivot, entry.toString());
        else
            entries.put(entry.pivot, entry);
    }
    
    public void putEntrySmall(Entry entry) {
        entries.put(entry.pivot, entry.toString());
    }
    
    public Entry getEntry(String pivot) {
        Object e = entries.get(pivot);
        if (e == null) return null;
        if (e instanceof String) return new Entry(pivot, (String) e, false);
        if (e instanceof Entry) return (Entry) e;
        return null;
    }
   
    public Entry removeEntry(String pivot) {
        Object e = entries.remove(pivot);
        if (e == null) return null;
        if (e instanceof String) return new Entry(pivot, (String) e, false);
        if (e instanceof Entry) return (Entry) e;
        return null;
    }
   
    public class Structure {
        
        protected String   pivot_name = null;
        protected int      pivot_len = -1;
        protected String[] prop_names = null;
        protected int[]    prop_len = null, prop_pos = null;
        protected String[] seq_names = null;
        protected int[]    seq_len = null, seq_pos = null;
        protected kelondroRow seqrow;
        // example:
        //# Structure=<pivot-12>,'=',<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>,'|',*<Anchor-12>

        public Structure(String structure) {
            // parse a structure string
            
            // parse pivot definition:
            int p = structure.indexOf(",'='");
            if (p < 0) return;
            String pivot = structure.substring(0, p);
            structure = structure.substring(p + 5);
            kelondroColumn a = new kelondroColumn(pivot);
            pivot_name = a.nickname;
            pivot_len = a.cellwidth;
            
            // parse property part definition:
            p = structure.indexOf(",'|'");
            if (p < 0) return;
            ArrayList l = new ArrayList();
            String attr = structure.substring(0, p);
            String seqs = structure.substring(p + 5);
            StringTokenizer st = new StringTokenizer(attr, ",");
            while (st.hasMoreTokens()) {
                a = new kelondroColumn(st.nextToken());
                if (a == null) break;
                l.add(a);
            }
            prop_names = new String[l.size()];
            prop_len = new int[l.size()];
            prop_pos = new int[l.size()];
            p = 0;
            for (int i = 0; i < l.size(); i++) {
                a = (kelondroColumn) l.get(i);
                prop_names[i] = a.nickname;
                prop_len[i] = a.cellwidth;
                prop_pos[i] = p;
                p += prop_len[i];
            }
            
            // parse sequence definition:
            if (seqs.startsWith("*")) seqs = seqs.substring(1);
            l = new ArrayList();
            st = new StringTokenizer(seqs, ",");
            while (st.hasMoreTokens()) {
                a = new kelondroColumn(st.nextToken());
                if (a == null) break;
                l.add(a);
            }
            seq_names = new String[l.size()];
            seq_len = new int[l.size()];
            seq_pos = new int[l.size()];
            p = 0;
            for (int i = 0; i < l.size(); i++) {
                a = (kelondroColumn) l.get(i);
                seq_names[i] = a.nickname;
                seq_len[i] = a.cellwidth;
                seq_pos[i] = p;
                p += seq_len[i];
            }
            
            // generate rowdef for seq row definition
            StringBuffer rowdef = new StringBuffer();
            rowdef.append("byte[] ");
            rowdef.append(seq_names[0]);
            rowdef.append('-');
            rowdef.append(seq_len[0]);
            
            for (int i = 1; i < seq_names.length; i++) {
                rowdef.append(", byte[] ");
                rowdef.append(seq_names[i]);
                rowdef.append('-');
                rowdef.append(seq_len[i]);
            }
            seqrow = new kelondroRow(new String(rowdef), null, 0);
        }
        
        public String toString() {
            StringBuffer sb = new StringBuffer(100);
            sb.append('<'); sb.append(pivot_name); sb.append('-'); sb.append(Integer.toString(pivot_len)); sb.append(">,'=',");
            if (prop_names.length > 0) {
                for (int i = 0; i < prop_names.length; i++) {
                    sb.append('<'); sb.append(prop_names[i]); sb.append('-'); sb.append(Integer.toString(prop_len[i])); sb.append(">,");
                }
            }
            sb.append("'|'");
            if (seq_names.length > 0) {
                for (int i = 0; i < seq_names.length; i++) {
                    sb.append(",<"); sb.append(seq_names[i]); sb.append('-'); sb.append(Integer.toString(seq_len[i])); sb.append('>');
                }
            }
            return new String(sb);
        }
    }
    
    public class Entry {
        String  pivot;
        HashMap attrs;
        Set     seq;
        
        public Entry(String pivot, HashMap attrs, Set seq) {
            this.pivot = pivot;
            this.attrs = attrs;
            this.seq = seq;
        }
        
        public Entry(String pivot, String attrseq, boolean tree) {
            this.pivot = pivot;
            attrs = new HashMap();
            seq = (tree) ? (Set) new TreeSet() : (Set) new HashSet();
            for (int i = 0; i < structure.prop_names.length; i++) {
                attrs.put(structure.prop_names[i], new Long(kelondroBase64Order.enhancedCoder.decodeLong(attrseq.substring(structure.prop_pos[i], structure.prop_pos[i] + structure.prop_len[i]))));
            }
            
            int p = attrseq.indexOf('|') + 1;
            //long[] seqattrs = new long[structure.seq_names.length - 1];
            String seqname;
            while (p + structure.seq_len[0] <= attrseq.length()) {
                seqname = attrseq.substring(p, p + structure.seq_len[0]);
                p += structure.seq_len[0];
                for (int i = 1; i < structure.seq_names.length; i++) {
                    //seqattrs[i - 1] = kelondroBase64Order.enhancedCoder.decodeLong(attrseq.substring(p, p + structure.seq_len[i]));
                    p += structure.seq_len[i];
                }
                seq.add(seqname/*, seqattrs*/);
            }
        }
        
        public HashMap getAttrs() {
            return attrs;
        }
        
        public long getAttr(String key, long dflt) {
            Long i = (Long) attrs.get(key);
            if (i == null) return dflt;
            return i.longValue();
        }
        
        public void setAttr(String key, long attr) {
            attrs.put(key, new Long(attr));
        }
        
        public Set getSeqSet() {
            return seq;
        }
        
        public kelondroRowCollection getSeqCollection() {
            kelondroRowCollection collection = new kelondroRowCollection(structure.seqrow, seq.size());
            Iterator i = seq.iterator();
            while (i.hasNext()) {
                collection.addUnique(structure.seqrow.newEntry(((String) i.next()).getBytes()));
            }
            return collection;
        }
        
        public void setSeq(Set seq) {
            this.seq = seq;
        }
        
        public void addSeq(String s/*, long[] seqattrs*/) {
            this.seq.add(s/*, seqattrs*/);
        }
        
        public String toString() {
            // creates only the attribute field and the sequence, not the pivot
            StringBuffer sb = new StringBuffer(100 + structure.seq_len[0] * seq.size());
            Long val;
            for (int i = 0; i < structure.prop_names.length; i++) {
                val = (Long) attrs.get(structure.prop_names[i]);
                sb.append(kelondroBase64Order.enhancedCoder.encodeLongSmart((val == null) ? 0 : val.longValue(), structure.prop_len[i]));
            }
            sb.append('|');
            Iterator q = seq.iterator();
            //long[] seqattrs;
            while (q.hasNext()) {
                sb.append((String) q.next());
                //seqattrs = (long[]) entry.getValue();
                /*
                for (int i = 1; i < structure.seq_names.length; i++) {
                    sb.append(kelondroBase64Order.enhancedCoder.encodeLong(seqattrs[i - 1], structure.seq_len[i]));
                }
                */
            }
            return new String(sb);
        }
    }
    
    private static final long cc = 0;
    private static boolean shortmemstate = false;
    private static boolean shortmem() {
        if ((cc % 300) == 0) {
            shortmemstate = (serverMemory.available() < 20000000L);
        }
        return shortmemstate;
    }
    
    public static void transcode(File from_file, File to_file) throws IOException {
        kelondroAttrSeq crp = new kelondroAttrSeq(from_file, true);
        //crp.toFile(new File(args[1]));
        kelondroAttrSeq cro = new kelondroAttrSeq(crp.name + "/Transcoded from " + crp.file.getName(), crp.structure.toString(), true);
        Iterator i = crp.entries.keySet().iterator();
        String key;
        kelondroAttrSeq.Entry entry;
        while (i.hasNext()) {
            key = (String) i.next();
            entry = crp.getEntry(key);
            cro.putEntry(entry);
        }
        cro.toFile(to_file);
    }
    
    public static void main(String[] args) {
        // java -classpath source de.anomic.kelondro.kelondroPropFile -transcode DATA/RANKING/GLOBAL/CRG-test-unsorted-original.cr DATA/RANKING/GLOBAL/CRG-test-generated.cr
        try {
            if ((args.length == 3) && (args[0].equals("-transcode"))) {
                transcode(new File(args[1]), new File(args[2]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
