// plasmaWebStructure.java
// -----------------------------
// (C) 2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 15.05.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.net.URL;
import de.anomic.server.serverDate;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

public class plasmaWebStructure {

    public static int maxCRLDump = 500000;
    public static int maxCRGDump = 200000;

    private StringBuffer crg;     // global citation references
    private serverLog    log;
    private File         rankingPath, structureFile;
    private String       crlFile, crgFile;
    private TreeMap      structure; // String2String with <b64hash(6)>','<host> to <date-yyyymmdd(8)>{<target-b64hash(6)><target-count-hex(4)>}*
    
    public plasmaWebStructure(serverLog log, File rankingPath, String crlFile, String crgFile, File structureFile) {
        this.log = log;
        this.rankingPath = rankingPath;
        this.crlFile = crlFile;
        this.crgFile = crgFile;
        this.crg = new StringBuffer(maxCRGDump);
        this.structure = new TreeMap();
        this.structureFile = structureFile;
        Map loadedStructure = serverFileUtils.loadHashMap(this.structureFile);
        if (loadedStructure != null) this.structure.putAll(loadedStructure);
    }
    
    public Integer[] /*(outlinksSame, outlinksOther)*/ generateCitationReference(URL url, String baseurlhash, Date docDate, plasmaParserDocument document, plasmaCondenser condenser) {
        assert plasmaURL.urlHash(url).equals(baseurlhash);
        
        // generate citation reference
        Map hl = document.getHyperlinks();
        Iterator it = hl.entrySet().iterator();
        String nexturlhash;
        StringBuffer cpg = new StringBuffer(12 * (hl.size() + 1) + 1);
        StringBuffer cpl = new StringBuffer(12 * (hl.size() + 1) + 1);
        String lhp = baseurlhash.substring(6); // local hash part
        int GCount = 0;
        int LCount = 0;
        while (it.hasNext()) {
            nexturlhash = plasmaURL.urlHash((String) ((Map.Entry) it.next()).getKey());
            if (nexturlhash != null) {
                if (nexturlhash.substring(6).equals(lhp)) {
                    // this is a inbound link
                    cpl.append(nexturlhash.substring(0, 6)); // store only local part
                    LCount++;
                } else {
                    // this is a outbound link
                    cpg.append(nexturlhash); // store complete hash
                    GCount++;
                }
            }
        }
        
        // append this reference to buffer
        // generate header info
        String head = baseurlhash + "=" +
        plasmaWordIndex.microDateHoursStr(docDate.getTime()) +          // latest update timestamp of the URL
        plasmaWordIndex.microDateHoursStr(System.currentTimeMillis()) + // last visit timestamp of the URL
        kelondroBase64Order.enhancedCoder.encodeLongSmart(LCount, 2) +  // count of links to local resources
        kelondroBase64Order.enhancedCoder.encodeLongSmart(GCount, 2) +  // count of links to global resources
        kelondroBase64Order.enhancedCoder.encodeLongSmart(document.getImages().size(), 2) + // count of Images in document
        kelondroBase64Order.enhancedCoder.encodeLongSmart(0, 2) +       // count of links to other documents
        kelondroBase64Order.enhancedCoder.encodeLongSmart(document.getTextLength(), 3) +   // length of plain text in bytes
        kelondroBase64Order.enhancedCoder.encodeLongSmart(condenser.RESULT_NUMB_WORDS, 3) + // count of all appearing words
        kelondroBase64Order.enhancedCoder.encodeLongSmart(condenser.words().size(), 3) + // count of all unique words
        kelondroBase64Order.enhancedCoder.encodeLongSmart(0, 1); // Flags (update, popularity, attention, vote)
        
        //crl.append(head); crl.append ('|'); crl.append(cpl); crl.append((char) 13); crl.append((char) 10);
        crg.append(head); crg.append('|'); crg.append(cpg); crg.append((char) 13); crg.append((char) 10);
        
        learn(url, cpg);
        
        // if buffer is full, flush it.
        /*
        if (crl.length() > maxCRLDump) {
            flushCitationReference(crl, "crl");
            crl = new StringBuffer(maxCRLDump);
        }
         **/
        if (crg.length() > maxCRGDump) {
            flushCitationReference("crg");
            crg = new StringBuffer(maxCRGDump);
        }
        
        return new Integer[] {new Integer(LCount), new Integer(GCount)};
    }
    
    public void flushCitationReference(String type) {
        if (crg.length() < 12) return;
        String filename = type.toUpperCase() + "-A-" + new serverDate().toShortString(true) + "." + crg.substring(0, 12) + ".cr.gz";
        File path = new File(rankingPath, (type.equals("crl")) ? crlFile : crgFile);
        path.mkdirs();
        File file = new File(path, filename);
        
        // generate header
        StringBuffer header = new StringBuffer(200);
        header.append("# Name=YaCy " + ((type.equals("crl")) ? "Local" : "Global") + " Citation Reference Ticket"); header.append((char) 13); header.append((char) 10);
        header.append("# Created=" + System.currentTimeMillis()); header.append((char) 13); header.append((char) 10);
        header.append("# Structure=<Referee-12>,'=',<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>,'|',*<Anchor-" + ((type.equals("crl")) ? "6" : "12") + ">"); header.append((char) 13); header.append((char) 10);
        header.append("# ---"); header.append((char) 13); header.append((char) 10);
        crg.insert(0, header.toString());
        try {
            serverFileUtils.writeAndGZip(crg.toString().getBytes(), file);
            log.logFine("wrote citation reference dump " + file.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static TreeMap refstr2map(String refs) {
        if ((refs == null) || (refs.length() <= 8)) return new TreeMap();
        TreeMap map = new TreeMap();
        String c;
        assert (refs.length() - 8) % 10 == 0;
        int refsc = (refs.length() - 8) / 10;
        for (int i = 0; i < refsc; i++) {
            c = refs.substring(8 + i * 10, 8 + (i + 1) * 10);
            map.put(c.substring(0, 6), new Integer(Integer.parseInt(c.substring(6), 16)));
        }
        return map;
    }
    
    private static String map2refstr(TreeMap map) {
        StringBuffer s = new StringBuffer(map.size() * 10);
        s.append(plasmaURL.shortDayFormatter.format(new Date()));
        Iterator i = map.entrySet().iterator();
        Map.Entry entry;
        String h;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            s.append((String) entry.getKey());
            h = Integer.toHexString(((Integer) entry.getValue()).intValue());
            if (h.length() == 0) {
                s.append("0000");
            } else if (h.length() == 1) {
                s.append("000").append(h);
            } else if (h.length() == 2) {
                s.append("00").append(h);
            } else if (h.length() == 3) {
                s.append('0').append(h);
            } else if (h.length() == 4) {
                s.append(h);
            } else {
                s.append("FFFF");
            }
        }
        return s.toString();
    }
    
    public TreeMap references(String domhash) {
        assert domhash.length() == 6;
        SortedMap tailMap = structure.tailMap(domhash);
        if ((tailMap == null) || (tailMap.size() == 0)) return new TreeMap();
        String key = (String) tailMap.firstKey();
        if (key.startsWith(domhash)) {
            return refstr2map((String) tailMap.get(key));
        } else {
            return new TreeMap();
        }
    }
    
    public String resolveDomHash2DomString(String domhash) {
        // returns the domain as string, null if unknown
        assert domhash.length() == 6;
        SortedMap tailMap = structure.tailMap(domhash);
        if ((tailMap == null) || (tailMap.size() == 0)) return null;
        String key = (String) tailMap.firstKey();
        if (key.startsWith(domhash)) {
            return key.substring(7);
        } else {
            return null;
        }
    }
    
    private void learn(URL url, StringBuffer reference /*string of b64(12digits)-hashes*/) {
        String domhash = plasmaURL.urlHash(url).substring(6);
        TreeMap refs = references(domhash);
        assert reference.length() % 12 == 0;
        String dom;
        int c;
        for (int i = 0; i < reference.length() / 12; i++) {
            dom = reference.substring(i * 12 + 6, (i + 1) * 12);
            c = 0;
            if (refs.containsKey(dom)) {
                c = ((Integer) refs.get(dom)).intValue();
            }
            refs.put(dom, new Integer(++c));
        }
        structure.put(domhash + "," + url.getHost(), map2refstr(refs));
        
    }
    
    public void saveWebStructure() {
        try {
            serverFileUtils.saveMap(this.structureFile, this.structure, "Web Structure Syntax: <b64hash(6)>','<host> to <date-yyyymmdd(8)>{<target-b64hash(6)><target-count-hex(4)>}*");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public Iterator structureEntryIterator() {
        // iterates objects of type structureEntry
        return new structureIterator();
    }
    
    public class structureIterator implements Iterator {

        private Iterator i;
        private structureEntry nextentry;
        
        public structureIterator() {
            i = structure.entrySet().iterator();
            next0();
        }
        
        public boolean hasNext() {
            return nextentry != null;
        }

        private void next0() {
            Map.Entry entry = null;
            String dom = null, ref;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                dom = (String) entry.getKey();
                if (dom.length() >= 8) break;
                if (!i.hasNext()) {
                    nextentry = null;
                    return;
                }
            }
            if ((entry == null) || (dom == null)) {
                nextentry = null;
                return;
            }
            ref = (String) entry.getValue();
            nextentry = new structureEntry(dom.substring(0, 6), dom.substring(7), ref.substring(0, 8), refstr2map(ref));
        }
        
        public Object next() {
            structureEntry r = nextentry;
            next0();
            return r;
        }

        public void remove() {
            throw new UnsupportedOperationException("not implemented");
        }
        
    }
    
    public class structureEntry {
        public String domhash, domain, date;
        public Map references;
        public structureEntry(String domhash, String domain, String date, Map references) {
            this.domhash = domhash;
            this.domain = domain;
            this.date = date;
            this.references = references;
        }
    }
    
    public void close() {
        log.logInfo("Saving Web Structure File");
        saveWebStructure();
    }
}
