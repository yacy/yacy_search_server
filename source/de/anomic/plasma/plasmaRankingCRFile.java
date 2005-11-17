// plasmaCRFile.java
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

package de.anomic.plasma;

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

import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.tools.bitfield;
import de.anomic.server.logging.serverLog;

public class plasmaRankingCRFile {
    
    private File file;
    private TreeMap entries;
    private Structure structure;
    private String name;
    private long created;
    private serverLog log;
    
    public plasmaRankingCRFile(File file) throws IOException {
        this.log = new serverLog("RANKING");
        this.file = file;
	this.structure = null;
        this.created = 0;
        this.name = "";
        this.entries = readCR(file);
    }

    public plasmaRankingCRFile(String name, String struct) {
        this.log = new serverLog("RANKING");
        this.file = null;
	this.structure = new Structure(struct);
        this.created = System.currentTimeMillis();
        this.name = name;
        this.entries = new TreeMap();
    }

    /*
    header.append("# Name=YaCy " + ((type.equals("crl")) ? "Local" : "Global") + " Citation Reference Ticket"); header.append((char) 13); header.append((char) 10);
    header.append("# Created=" + System.currentTimeMillis()); header.append((char) 13); header.append((char) 10);
    header.append("# Structure=<Referee-12>,'=',<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>,'|',*<Anchor-" + ((type.equals("crl")) ? "6" : "12") + ">"); header.append((char) 13); header.append((char) 10);
    header.append("# ---"); header.append((char) 13); header.append((char) 10);
    */  
        
    private TreeMap readCR(File file) throws IOException {
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
                    log.logInfo("read CRFile " + file.toString() + ", key " + key + ": double occurrence");
                } else {
                    entries.put(key, line.substring(p + 1).trim());
                }
            }
        }
        br.close();
        
        return entries;
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
    
    public void addEntry(String referee, String attrseq) {
        entries.put(referee, attrseq);
    }
    
    public void addEntry(Entry entry) {
        entries.put(entry.referee, entry.toString());
    }
    
    public Entry getEntry(String referee) {
        String struct = (String) entries.get(referee);
        if (struct == null) return null;
        return new Entry(referee, struct);
    }
   
    public Entry newEntry(String referee, HashMap props, TreeSet seq) {
        return new Entry(referee, props, seq);
    }
   
    public class Structure {
        
        protected String   referee_name = null;
        protected int      referee_len = -1;
        protected String[] prop_names = null;
        protected int[]    prop_len = null, prop_pos = null;
        protected String   seq_name = null;
        protected int      seq_len = -1;
        
        // example:
        //# Structure=<Referee-12>,'=',<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>,'|',*<Anchor-12>

        public Structure(String structure) {
            // parse a structure string
            
            // parse referee definition:
            int p = structure.indexOf(",'='");
            if (p < 0) return;
            String referee = structure.substring(0, p);
            structure = structure.substring(p + 5);
            Object[] a = atom(referee);
            if (a == null) return;
            referee_name = (String) a[0];
            referee_len = ((Integer) a[1]).intValue();
            
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
            sb.append('<'); sb.append(referee_name); sb.append('-'); sb.append(Integer.toString(referee_len)); sb.append(">,'=',");
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
        String  referee;
        HashMap props;
        TreeSet seq;
        
        public Entry(String referee, HashMap props, TreeSet seq) {
            this.referee = referee;
            this.props = props;
            this.seq = seq;
        }
        
        public Entry(String referee, String attrseq) {
            this.referee = referee;
            props = new HashMap();
            seq = new TreeSet();
            for (int i = 0; i < structure.prop_names.length; i++) {
                props.put(structure.prop_names[i], new Integer((int) serverCodings.enhancedCoder.decodeBase64Long(attrseq.substring(structure.prop_pos[i], structure.prop_pos[i] + structure.prop_len[i]))));
            }
            
            int p = attrseq.indexOf('|');
            attrseq = attrseq.substring(p + 1);
            for (int i = 0; i < attrseq.length(); i = i + structure.seq_len) {
                seq.add(attrseq.substring(i, i + structure.seq_len));
            }
        }
        
        public String toString() {
            // creates only the attribute field and the sequence, not the referee
            StringBuffer sb = new StringBuffer(70);
            Integer val;
            for (int i = 0; i < structure.prop_names.length; i++) {
                val = (Integer) props.get(structure.prop_names[i]);
                sb.append(serverCodings.enhancedCoder.encodeBase64LongSmart((val == null) ? 0 : val.intValue(), structure.prop_len[i]));
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
        plasmaRankingCRFile crp = new plasmaRankingCRFile(from_file);
        //crp.toFile(new File(args[1]));
        plasmaRankingCRFile cro = new plasmaRankingCRFile(crp.name + "/Transcoded from " + crp.file.getName(), crp.structure.toString());
        Iterator i = crp.entries.keySet().iterator();
        String key;
        plasmaRankingCRFile.Entry entry;
        while (i.hasNext()) {
            key = (String) i.next();
            entry = crp.getEntry(key);
            cro.addEntry(entry);
        }
        cro.toFile(to_file);
    }
    
    
    private static boolean accumulate_upd(File f, plasmaRankingCRFile acc) {
        // open file
        plasmaRankingCRFile source_cr = null;
        try {
            source_cr = new plasmaRankingCRFile(f);
        } catch (IOException e) {
            return false;
        }
        
        // put elements in accumulator file
        Iterator el = source_cr.entries.keySet().iterator();
        String key;
        plasmaRankingCRFile.Entry new_entry, acc_entry;
        int FUDate, FDDate, LUDate, UCount, PCount, ACount, VCount, Vita;
        bitfield acc_flags, new_flags;
        while (el.hasNext()) {
            key = (String) el.next();
            new_entry = source_cr.getEntry(key);
            new_flags = new bitfield(serverCodings.enhancedCoder.encodeBase64Long((long) ((Integer) new_entry.props.get("Flags")).intValue(), 1).getBytes());
            // enrich information with additional values
            if (acc.entries.containsKey(key)) {
                acc_entry = acc.getEntry(key);
                acc.entries.remove(key); // will be replaced later
                FUDate = ((Integer) acc_entry.props.get("FUDate")).intValue();
                FDDate = ((Integer) acc_entry.props.get("FDDate")).intValue();
                LUDate = ((Integer) acc_entry.props.get("LUDate")).intValue();
                UCount = ((Integer) acc_entry.props.get("UCount")).intValue();
                PCount = ((Integer) acc_entry.props.get("PCount")).intValue();
                ACount = ((Integer) acc_entry.props.get("ACount")).intValue();
                VCount = ((Integer) acc_entry.props.get("VCount")).intValue();
                Vita   = ((Integer) acc_entry.props.get("Vita")).intValue();
                
                // update counters and dates
                acc_entry.seq = new_entry.seq; // need to be checked
                
                UCount++; // increase update counter
                PCount += (new_flags.get(1)) ? 1 : 0;
                ACount += (new_flags.get(2)) ? 1 : 0;
                VCount += (new_flags.get(3)) ? 1 : 0;
                
                // 'OR' the flags
                acc_flags = new bitfield(serverCodings.enhancedCoder.encodeBase64Long((long) ((Integer) acc_entry.props.get("Flags")).intValue(), 1).getBytes());
                for (int i = 0; i < 6; i++) {
                    if (new_flags.get(i)) acc_flags.set(i, true);
                }
                acc_entry.props.put("Flags", new Integer((int) serverCodings.enhancedCoder.decodeBase64Long(new String(acc_flags.getBytes()))));
            } else {
                // initialize counters and dates
                acc_entry = acc.newEntry(key, new_entry.props, new_entry.seq);
                FUDate = plasmaWordIndex.microDateHoursInt(System.currentTimeMillis()); // first update date
                FDDate = plasmaWordIndex.microDateHoursInt(System.currentTimeMillis()); // very difficult to compute; this is only a quick-hack
                LUDate = ((Integer) new_entry.props.get("VDate")).intValue();
                UCount = 0;
                PCount = (new_flags.get(1)) ? 1 : 0;
                ACount = (new_flags.get(2)) ? 1 : 0;
                VCount = (new_flags.get(3)) ? 1 : 0;
                Vita   = 0;
            }
            // make plausibility check?
            
            // insert into accumulator
            acc_entry.props.put("FUDate", new Integer(FUDate));
            acc_entry.props.put("FDDate", new Integer(FDDate));
            acc_entry.props.put("LUDate", new Integer(LUDate));
            acc_entry.props.put("UCount", new Integer(UCount));
            acc_entry.props.put("PCount", new Integer(PCount));
            acc_entry.props.put("ACount", new Integer(ACount));
            acc_entry.props.put("VCount", new Integer(VCount));
            acc_entry.props.put("Vita", new Integer(Vita));
            acc.addEntry(acc_entry);
        }
        
        return true;
    }
    
    public static void accumulate(File from_dir, File tmp_dir, File err_dir, File bkp_dir, File to_file) throws IOException {
        if (!(from_dir.isDirectory())) {
            System.out.println("source path " + from_dir + " is not a directory.");
            return;
        }
        if (!(tmp_dir.isDirectory())) {
            System.out.println("temporary path " + tmp_dir + " is not a directory.");
            return;
        }
        if (!(err_dir.isDirectory())) {
            System.out.println("error path " + err_dir + " is not a directory.");
            return;
        }
        if (!(bkp_dir.isDirectory())) {
            System.out.println("back-up path " + bkp_dir + " is not a directory.");
            return;
        }
        
        // open target file
        plasmaRankingCRFile acc = null;
        if (!(to_file.exists())) {
            acc = new plasmaRankingCRFile("Global Ranking Accumulator File",
                    "<Referee-12>,'='," +
                    "<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>," +
                    "<FUDate-3>,<FDDate-3>,<LUDate-3>,<UCount-2>,<PCount-2>,<ACount-2>,<VCount-2>,<Vita-2>," +
                    "'|',*<Anchor-12>");
            acc.toFile(to_file);
        }
        acc = new plasmaRankingCRFile(to_file);
        
        // collect source files
        plasmaRankingCRFile source_cr = null;
        File source_file = null;
        String[] files = from_dir.list();
        for (int i = 0; i < files.length; i++) {
            // open file
            source_file = new File(from_dir, files[i]);
            if (accumulate_upd(source_file, acc)) {
                // move cr file to temporary folder
                source_file.renameTo(new File(tmp_dir, files[i]));
            } else {
                // error case: the cr-file is not valid; move to error path
                source_file.renameTo(new File(err_dir, files[i]));
            }
        }
        
        // save accumulator to temporary file
        File tmp_file;
        if (to_file.toString().endsWith(".gz")) {
            tmp_file = new File(to_file.toString() + "." + (System.currentTimeMillis() % 1000) + ".tmp.gz");
        } else {
            tmp_file = new File(to_file.toString() + "." + (System.currentTimeMillis() % 1000) + ".tmp");
        }
        try {
            acc.toFile(tmp_file);
            // since this was successful, we remove the old file and move the new file to it
            to_file.delete();
            tmp_file.renameTo(to_file);
            serverFileUtils.moveAll(tmp_dir, bkp_dir);
        } catch (IOException e) {
            // move previously processed files back
            serverFileUtils.moveAll(tmp_dir, from_dir);
        }
        
    }
    
    public static long crFileCreated(File f) throws IOException {
        return (new plasmaRankingCRFile(f)).created;
    }
    
    public static void main(String[] args) {
        // java -classpath source de.anomic.plasma.plasmaRankingCRFile -transcode DATA/RANKING/GLOBAL/CRG-test-unsorted-original.cr DATA/RANKING/GLOBAL/CRG-test-generated.cr
        try {
            if ((args.length == 3) && (args[0].equals("-transcode"))) {
                transcode(new File(args[1]), new File(args[2]));
            }
            if ((args.length == 5) && (args[0].equals("-accumulate"))) {
                accumulate(new File(args[1]), new File(args[2]), new File(args[3]), new File(args[4]), new File(args[5]));
            }
            if ((args.length == 2) && (args[0].equals("-accumulate"))) {
                File root_path = new File(args[1]);
                File from_dir = new File(root_path, "DATA/RANKING/GLOBAL/014_othercr");
                File tmp_dir = new File(root_path, "DATA/RANKING/GLOBAL/016_tmp");
                File err_dir = new File(root_path, "DATA/RANKING/GLOBAL/017_err");
                File acc_dir = new File(root_path, "DATA/RANKING/GLOBAL/018_acc");
                File to_file = new File(root_path, "DATA/RANKING/GLOBAL/020_accumulator/CRG-a-acc.cr.gz");
                if (!(tmp_dir.exists())) tmp_dir.mkdirs();
                if (!(err_dir.exists())) err_dir.mkdirs();
                if (!(acc_dir.exists())) acc_dir.mkdirs();
                if (!(to_file.getParentFile().exists())) to_file.getParentFile().mkdirs();
                accumulate(from_dir, tmp_dir, err_dir, acc_dir, to_file);
            }
            if ((args.length == 3) && (args[0].equals("-recycle"))) {
                File root_path = new File(args[1]);
                int max_age_hours = Integer.parseInt(args[2]);
                File own_dir = new File(root_path, "DATA/RANKING/GLOBAL/010_owncr");
                File acc_dir = new File(root_path, "DATA/RANKING/GLOBAL/018_acc");
                File bkp_dir = new File(root_path, "DATA/RANKING/GLOBAL/019_bkp");
                if (!(own_dir.exists())) return;
                if (!(acc_dir.exists())) return;
                if (!(bkp_dir.exists())) bkp_dir.mkdirs();
                String[] list = acc_dir.list();
                long d;
                File f;
                for (int i = 0; i < list.length; i++) {
                    f = new File(acc_dir, list[i]);
                    try {
                        d = (System.currentTimeMillis() - crFileCreated(f)) / 3600000;
                        if (d > max_age_hours) {
                            // file is considered to be too old, it is not recycled
                            System.out.println("file " + f.getName() + " is old (" + d + " hours) and not recycled, only moved to backup");
                            f.renameTo(new File(bkp_dir, list[i]));
                        } else {
                            // file is fresh, it is duplicated and moved to be transferred to other peers again
                            System.out.println("file " + f.getName() + " is fresh (" + d + " hours old), recycled and moved to backup");
                            serverFileUtils.copy(f, new File(own_dir, list[i]));
                            f.renameTo(new File(bkp_dir, list[i]));
                        }
                    } catch (IOException e) {
                        // there is something wrong with this file; delete it
                        System.out.println("file " + f.getName() + " is corrupted and deleted");
                        f.delete();
                    }
                }
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
