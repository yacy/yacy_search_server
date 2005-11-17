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

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Map;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.logging.Logger;

import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;

public class kelondroAttrSeq {
    
    // class objects
    private File file;
    private TreeMap entries;
    private Structure structure;
    private String name;
    private long created;
    
    // optional logger
    protected Logger theLogger = null;
    
    public kelondroAttrSeq(File file) throws IOException {
        this.file = file;
	this.structure = null;
        this.created = 0;
        this.name = "";
        this.entries = readPropFile(file);
    }

    public kelondroAttrSeq(String name, String struct) {
        this.file = null;
	this.structure = new Structure(struct);
        this.created = System.currentTimeMillis();
        this.name = name;
        this.entries = new TreeMap();
    }
        
    public void setLogger(Logger newLogger) {
        this.theLogger = newLogger;
    }
    
    public void logWarning(String message) {
        if (this.theLogger == null)
            System.err.println("KELONDRO WARNING for file " + this.file + ": " + message);
        else
            this.theLogger.warning("KELONDRO WARNING for file " + this.file + ": " + message);
    }
    
    private TreeMap readPropFile(File file) throws IOException {
        TreeMap entries = new TreeMap();
        BufferedReader br = null;
        int p;
        if (file.toString().endsWith(".gz")) {
            br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
        } else {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        }
        String line;
        String key;
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
                if (entries.containsKey(key)) {
                    logWarning("read PropFile " + file.toString() + ", key " + key + ": double occurrence");
                } else {
                    entries.put(key, line.substring(p + 1).trim());
                }
            }
        }
        br.close();
        
        return entries;
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
        String k,v;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            k = (String) entry.getKey();
            v = (String) entry.getValue();
            sb.append(k); sb.append('='); sb.append(v); sb.append((char) 13); sb.append((char) 10);
        }
        if (out.toString().endsWith(".gz")) {
            serverFileUtils.writeAndZip(sb.toString().getBytes(), out);
        } else {
            serverFileUtils.write(sb.toString().getBytes(), out);
        }
    }
    
    public Iterator keys() {
        return entries.keySet().iterator();
    }
    
    public Entry newEntry(String pivot, HashMap props, TreeSet seq) {
        return new Entry(pivot, props, seq);
    }
    
    public void addEntry(String pivot, String attrseq) {
        entries.put(pivot, attrseq);
    }
    
    public void addEntry(Entry entry) {
        entries.put(entry.pivot, entry.toString());
    }
    
    public Entry getEntry(String pivot) {
        String struct = (String) entries.get(pivot);
        if (struct == null) return null;
        return new Entry(pivot, struct);
    }
   
    public Entry removeEntry(String pivot) {
        String struct = (String) entries.remove(pivot);
        if (struct == null) return null;
        return new Entry(pivot, struct);
    }
   
    public class Structure {
        
        protected String   pivot_name = null;
        protected int      pivot_len = -1;
        protected String[] prop_names = null;
        protected int[]    prop_len = null, prop_pos = null;
        protected String   seq_name = null;
        protected int      seq_len = -1;
        
        // example:
        //# Structure=<pivot-12>,'=',<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>,'|',*<Anchor-12>

        public Structure(String structure) {
            // parse a structure string
            
            // parse pivot definition:
            int p = structure.indexOf(",'='");
            if (p < 0) return;
            String pivot = structure.substring(0, p);
            structure = structure.substring(p + 5);
            Object[] a = atom(pivot);
            if (a == null) return;
            pivot_name = (String) a[0];
            pivot_len = ((Integer) a[1]).intValue();
            
            // parse property part definition:
            p = structure.indexOf(",'|'");
            if (p < 0) return;
            ArrayList l = new ArrayList();
            String attr = structure.substring(0, p);
            String seqs = structure.substring(p + 5);
            StringTokenizer st = new StringTokenizer(attr, ",");
            while (st.hasMoreTokens()) {
                a = atom(st.nextToken());
                if (a == null) break;
                l.add(a);
            }
            prop_names = new String[l.size()];
            prop_len = new int[l.size()];
            prop_pos = new int[l.size()];
            p = 0;
            for (int i = 0; i < l.size(); i++) {
                a = (Object[]) l.get(i);
                prop_names[i] = (String) a[0];
                prop_len[i] = ((Integer) a[1]).intValue();
                prop_pos[i] = p;
                p += prop_len[i];
            }
            
            // parse sequence definition:
            a = atom(seqs);
            if (a == null) return;
            seq_name = (String) a[0];
            seq_len = ((Integer) a[1]).intValue();
        }
        
        private Object[] atom(String a) {
            if (a.startsWith("<")) {
                a = a.substring(1);
            } else if (a.startsWith("*<")) {
                a = a.substring(2);
            } else return null;
            if (a.endsWith(">")) {
                a = a.substring(0, a.length() - 1);
            } else return null;
            int p = a.indexOf('-');
            if (p < 0) return null;
            String name = a.substring(0, p);
            try {
                int x = Integer.parseInt(a.substring(p + 1));
                return new Object[]{name, new Integer(x)};
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        public String toString() {
            StringBuffer sb = new StringBuffer(70);
            sb.append('<'); sb.append(pivot_name); sb.append('-'); sb.append(Integer.toString(pivot_len)); sb.append(">,'=',");
            if (prop_names.length > 0) {
                for (int i = 0; i < prop_names.length; i++) {
                    sb.append('<'); sb.append(prop_names[i]); sb.append('-'); sb.append(Integer.toString(prop_len[i])); sb.append(">,");
                }
            }
            sb.append("'|',");
            sb.append("*<"); sb.append(seq_name); sb.append('-'); sb.append(Integer.toString(seq_len)); sb.append('>');
            return sb.toString();
        }
    }
    
    public class Entry {
        String  pivot;
        HashMap attrs;
        TreeSet seq;
        
        public Entry(String pivot, HashMap attrs, TreeSet seq) {
            this.pivot = pivot;
            this.attrs = attrs;
            this.seq = seq;
        }
        
        public Entry(String pivot, String attrseq) {
            this.pivot = pivot;
            attrs = new HashMap();
            seq = new TreeSet();
            for (int i = 0; i < structure.prop_names.length; i++) {
                attrs.put(structure.prop_names[i], new Long(serverCodings.enhancedCoder.decodeBase64Long(attrseq.substring(structure.prop_pos[i], structure.prop_pos[i] + structure.prop_len[i]))));
            }
            
            int p = attrseq.indexOf('|');
            attrseq = attrseq.substring(p + 1);
            for (int i = 0; i < attrseq.length(); i = i + structure.seq_len) {
                seq.add(attrseq.substring(i, i + structure.seq_len));
            }
        }
        
        public HashMap getAttrs() {
            return attrs;
        }
        
        public long getAttr(String key, long dflt) {
            Long i = (Long) attrs.get(key);
            if (i == null) return dflt; else return i.longValue();
        }
        
        public void setAttr(String key, long attr) {
            attrs.put(key, new Long(attr));
        }
        
        public TreeSet getSeq() {
            return seq;
        }
        
        public void setSeq(TreeSet seq) {
            this.seq = seq;
        }
        
        public String toString() {
            // creates only the attribute field and the sequence, not the pivot
            StringBuffer sb = new StringBuffer(70);
            Long val;
            for (int i = 0; i < structure.prop_names.length; i++) {
                val = (Long) attrs.get(structure.prop_names[i]);
                sb.append(serverCodings.enhancedCoder.encodeBase64LongSmart((val == null) ? 0 : val.longValue(), structure.prop_len[i]));
            }
            sb.append('|');
            Iterator q = seq.iterator();
            while (q.hasNext()) {
                sb.append((String) q.next());
            }
            return sb.toString();
        }
    }
    
    public static void transcode(File from_file, File to_file) throws IOException {
        kelondroAttrSeq crp = new kelondroAttrSeq(from_file);
        //crp.toFile(new File(args[1]));
        kelondroAttrSeq cro = new kelondroAttrSeq(crp.name + "/Transcoded from " + crp.file.getName(), crp.structure.toString());
        Iterator i = crp.entries.keySet().iterator();
        String key;
        kelondroAttrSeq.Entry entry;
        while (i.hasNext()) {
            key = (String) i.next();
            entry = crp.getEntry(key);
            cro.addEntry(entry);
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
    
    /*
      Class-A File format:
      
      UDate  : latest update timestamp of the URL (as virtual date, hours since epoch)
      VDate  : last visit timestamp of the URL (as virtual date, hours since epoch)
      LCount : count of links to local resources
      GCount : count of links to global resources
      ICount : count of links to images (in document)
      DCount : count of links to other documents
      TLength: length of the plain text content (bytes)
      WACount: total number of all words in content
      WUCount: number of unique words in content (removed doubles)
      Flags  : Flags (0=update, 1=popularity, 2=attention, 3=vote)
     
      Class-a File format is an extension of Class-A plus the following attributes
      FUDate : first update timestamp of the URL
      FDDate : first update timestamp of the domain
      LUDate : latest update timestamp of the URL
      UCount : Update Counter (of 'latest update timestamp')
      PCount : Popularity Counter (proxy clicks)
      ACount : Attention Counter (search result clicks)
      VCount : Votes
      Vita   : Vitality (normed number of updates per time)
     */
}
