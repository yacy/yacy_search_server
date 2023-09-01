/**
 *  Vocabulary
 *  Copyright 2012 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 07.01.2012 on http://yacy.net
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

package net.yacy.cora.lod.vocabulary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.geo.GeoLocation;
import net.yacy.cora.geo.Locations;
import net.yacy.cora.storage.Files;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;

public class Tagging {

    public final static String DEFAULT_NAMESPACE= "http://yacy.net/autotagging#";
    public final static String DEFAULT_PREFIX = "tags";

    /** Default value for the property matchFromLinkedData */
    public final static boolean DEFAULT_MATCH_FROM_LINKED_DATA = false;

    private final String navigatorName;
    private final Map<String, String> synonym2term;

    /** Terms associated to TagginEntry instances each having a synonym and an eventual object link */
    private final Map<String, TaggingEntry> term2entries;

    private File propFile;

    /** true if the vocabulary shall generate a navigation facet */
    private boolean isFacet;

    /**
     * True when this vocabulary terms should only be matched from linked data types
     * annotations (with microdata, RDFa, microformats...) instead of clear text
     * words
     */
    private boolean matchFromLinkedData;

    private String predicate, namespace, objectspace;

    /**
     * helper class: Synonym and Objectlink tuple
     */
    public static class SOTuple {
        private final String synonyms;
        private final String objectlink;

        public SOTuple(String synonyms, String objectlink) {
            this.synonyms = synonyms;
            this.objectlink = objectlink;
        }

        private SOTuple(String[] synonyms, String objectlink) {
            StringBuilder sb = new StringBuilder(synonyms.length * 10);
            for (String s: synonyms) sb.append(',').append(s);
            this.synonyms = sb.substring(1);
            this.objectlink = objectlink;
        }

        public String getSynonymsCSV() {
            return this.synonyms;
        }

        public String[] getSynonymsList() {
            return CommonPattern.COMMA.split(this.synonyms);
        }

        public String getObjectlink() {
            return this.objectlink;
        }

    }

    public Tagging(String name) {
        this.navigatorName = name;
        this.synonym2term = new ConcurrentHashMap<String, String>();
        this.term2entries= new ConcurrentHashMap<String, TaggingEntry>();
        this.namespace = DEFAULT_NAMESPACE;
        this.predicate = this.namespace + name;
        this.objectspace = null;
        this.propFile = null;
        this.isFacet = true;
        this.matchFromLinkedData = DEFAULT_MATCH_FROM_LINKED_DATA;
    }

    public Tagging(String name, File propFile) throws IOException {
        this(name);
        this.propFile = propFile;
        init();
    }

    /**
     * initialize a new Tagging file with a given table and objectspace url stub
     * @param name
     * @param propFile
     * @param objectspace
     * @param table
     * @throws IOException when an error occurred while writing table content to propFile
     */
    public Tagging(final String name, final File propFile, final String objectspace, final Map<String, SOTuple> table) throws IOException {
        this(name);
        this.propFile = propFile;
        this.objectspace = objectspace;
        if (propFile == null) {
            this.synonym2term.clear();
            this.term2entries.clear();
            this.namespace = DEFAULT_NAMESPACE;
            this.predicate = this.namespace + this.navigatorName;

            String term, v;
            String[] tags;
            vocloop: for (Map.Entry<String, SOTuple> e: table.entrySet()) {
                if (e.getValue().getSynonymsCSV() == null || e.getValue().getSynonymsCSV().isEmpty()) {
                    term = normalizeKey(e.getKey());
                    v = normalizeTerm(e.getKey());
                    this.synonym2term.put(v, term);
                    if (e.getValue().getObjectlink() != null && e.getValue().getObjectlink().length() > 0) {
                        this.term2entries.put(term, new TaggingEntryWithObjectLink(v, e.getValue().getObjectlink()));
                    } else {
                        this.term2entries.put(term, new SynonymTaggingEntry(v));
                    }
                        
                    continue vocloop;
                }
                term = normalizeKey(e.getKey());
                tags = e.getValue().getSynonymsList();
                final Set<String> synonyms = new HashSet<String>();
                synonyms.add(term);
                tagloop: for (String synonym: tags) {
                    if (synonym.isEmpty()) continue tagloop;
                    synonyms.add(synonym);
                    synonym = normalizeTerm(synonym);
                    if (synonym.isEmpty()) continue tagloop;
                    synonyms.add(synonym);
                    this.synonym2term.put(synonym, term);
                    this.term2entries.put(term, new SynonymTaggingEntry(synonym));
                }
                final String synonym = normalizeTerm(term);
                this.synonym2term.put(synonym, term);
                if (e.getValue().getObjectlink() != null && e.getValue().getObjectlink().length() > 0) {
                    this.term2entries.put(term, new TaggingEntryWithObjectLink(synonym, e.getValue().getObjectlink()));
                } else {
                    this.term2entries.put(term, new SynonymTaggingEntry(synonym));
                }
                synonyms.add(synonym);
            }
        } else {
            try (
                /* Resources automatically closed by this try-with-resources statement */
                final FileOutputStream outStream = new FileOutputStream(propFile);
                final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(outStream, StandardCharsets.UTF_8.name()));
            ) {
                if (objectspace != null && objectspace.length() > 0) w.write("#objectspace:" + objectspace + "\n");
                for (final Map.Entry<String, SOTuple> e: table.entrySet()) {
                    String s = e.getValue() == null ? "" : e.getValue().getSynonymsCSV();
                    String o = e.getValue() == null ? "" : e.getValue().getObjectlink();
                    w.write(e.getKey() + (s == null || s.isEmpty() ? "" : ":" + e.getValue().getSynonymsCSV()) + (o == null || o.isEmpty() || o.equals(objectspace + e.getKey()) ? "" : "#" + o) + "\n");
                }
            }
            init();
        }
    }

    public Tagging(String name, Locations location) {
        this(name);
        Set<String> locNames = location.locationNames();
        TreeSet<GeoLocation> geo;
        GeoLocation g;
        for (String loc: locNames) {
            String syn = normalizeTerm(loc);
            this.synonym2term.put(syn, loc);
            geo = location.find(loc, true);
            if (!geo.isEmpty()) {
                g = geo.iterator().next();
                this.term2entries.put(loc, new LocationTaggingEntry(syn, g));
            } else {
                this.term2entries.put(loc, new SynonymTaggingEntry(syn));
            }
        }
    }

    private void init() throws IOException {
        if (this.propFile == null) return;
        this.synonym2term.clear();
        this.term2entries.clear();
        this.namespace = DEFAULT_NAMESPACE;
        this.predicate = this.namespace + this.navigatorName;
        this.objectspace = null;

        ConcurrentLog.info("Tagging", "Started Vocabulary Initialization for " + this.propFile);
        long start = System.currentTimeMillis();
        long count = 0;
        BlockingQueue<String> list = Files.concurentLineReader(this.propFile);
        String term, v;
        String[] tags;
        int p;
        String line;
        try {
            String[] pl;
            vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                count++;
                line = line.trim();
                p = line.indexOf('#');
                if (p >= 0) {
                    String comment = line.substring(p + 1).trim();
                    if (comment.startsWith("namespace:")) {
                        this.namespace = comment.substring(10).trim();
                        if (!this.namespace.endsWith("/") && !this.namespace.endsWith("#") && this.namespace.length() > 0) this.namespace += "#";
                        this.predicate = this.namespace + this.navigatorName;
                        continue vocloop;
                    }
                    if (comment.startsWith("objectspace:")) {
                        this.objectspace = comment.substring(12).trim();
                        if (!this.objectspace.endsWith("/") && !this.objectspace.endsWith("#") && this.objectspace.length() > 0) this.objectspace += "#";
                        continue vocloop;
                    }
                }
                pl = parseLine(line);
                if (pl == null) continue vocloop;
                if (pl[1] == null) {
                    term = normalizeKey(pl[0]);
                    v = normalizeTerm(pl[0]);
                    this.synonym2term.put(v, term);
                    if (pl[2] != null && pl[2].length() > 0) {
                        this.term2entries.put(term, new TaggingEntryWithObjectLink(v, pl[2]));
                    } else {
                        this.term2entries.put(term, new SynonymTaggingEntry(v));
                    }
                    continue vocloop;
                }
                term = normalizeKey(pl[0]);
                v = pl[1];
                tags = CommonPattern.COMMA.split(v);
                Set<String> synonyms = new HashSet<String>();
                synonyms.add(term);
                tagloop: for (String synonym: tags) {
                    if (synonym.isEmpty()) continue tagloop;
                    synonyms.add(synonym);
                    synonym = normalizeTerm(synonym);
                    if (synonym.isEmpty()) continue tagloop;
                    synonyms.add(synonym);
                    this.synonym2term.put(synonym, term);
                    this.term2entries.put(term, new SynonymTaggingEntry(synonym));
                }
                String synonym = normalizeTerm(term);
                this.synonym2term.put(synonym, term);
                if (pl[2] != null && pl[2].length() > 0) {
                    this.term2entries.put(term, new TaggingEntryWithObjectLink(synonym, pl[2]));
                } else {
                    this.term2entries.put(term, new SynonymTaggingEntry(synonym));
                }
                synonyms.add(synonym);
            }
        } catch (final InterruptedException e) {
        }
        long time = Math.max(1, System.currentTimeMillis() - start);
        ConcurrentLog.info("Tagging", "Finished Vocabulary Initialization for " + this.propFile + "; " + count + " lines; " + time + " milliseconds; " + (1000L * count / time) + " lines / second");
    }

    public boolean isFacet() {
        return this.isFacet;
    }

    public void setFacet(boolean isFacet) {
        this.isFacet = isFacet;
    }

    /**
     * @return true when this vocabulary terms should be matched from linked data
     *         types annotations (with microdata, RDFa, microformats...) instead of
     *         clear text words
     */
    public boolean isMatchFromLinkedData() {
        return this.matchFromLinkedData;
    }

    /**
     * @param facetFromLinkedData
     *            true when this vocabulary terms should be matched from linked
     *            data types annotations (with microdata, RDFa, microformats...)
     *            instead of clear text words
     */
    public void setMatchFromLinkedData(final boolean facetFromLinkedData) {
        this.matchFromLinkedData = facetFromLinkedData;
    }

    public int size() {
        return this.term2entries.size();
    }

    public void put(String term, String synonyms, String objectlink) throws IOException {
        if (this.propFile == null) return;
        synchronized (this) {
            TempFile tmp = new TempFile();
            BlockingQueue<String> list = Files.concurentLineReader(this.propFile);
            String line;
            boolean written = false;
            try {
                vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                    String[] pl = parseLine(line);
                    if (pl == null) {
                        continue vocloop;
                    }
                    if (pl[0].equals(term)) {
                        tmp.writer.write(term + (synonyms == null || synonyms.isEmpty() ? "" : ":" + synonyms) + (objectlink == null || objectlink.isEmpty() || objectlink.equals(this.objectspace + term) ? "" : "#" + objectlink) + "\n");
                        written = true;
                    } else {
                        tmp.writer.write(pl[0] + (pl[1] == null || pl[1].isEmpty() ? "" : ":" + pl[1]) + (pl[2] == null || pl[2].isEmpty() || pl[2].equals(this.objectspace + pl[0]) ? "" : "#" + pl[2]) + "\n");
                    }
                }
                if (!written) {
                    tmp.writer.write(term + (synonyms == null || synonyms.isEmpty() ? "" : ":" + synonyms) + (objectlink == null || objectlink.isEmpty() || objectlink.equals(this.objectspace + term) ? "" : "#" + objectlink) + "\n");
                }
            } catch (final InterruptedException e) {
            }
            tmp.writer.close();
            this.propFile.delete();
            tmp.file.renameTo(this.propFile);
            init();
        }
    }

    public void delete(String term) throws IOException {
        if (this.propFile == null) return;
        TempFile tmp = new TempFile();
        BlockingQueue<String> list = Files.concurentLineReader(this.propFile);
        String line;
        try {
            vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                String[] pl = parseLine(line);
                if (pl == null) {
                    continue vocloop;
                }
                if (pl[0].equals(term)) {
                    continue vocloop;
                }
                tmp.writer.write(pl[0] + (pl[1] == null || pl[1].isEmpty() ? "" : ":" + pl[1]) + (pl[2] == null || pl[2].isEmpty() || pl[2].equals(this.objectspace + pl[0]) ? "" : "#" + pl[2]) + "\n");
            }
        } catch (final InterruptedException e) {
        }
        tmp.writer.close();
        this.propFile.delete();
        tmp.file.renameTo(this.propFile);
        init();
    }

    public void clear() throws IOException {
        if (this.propFile == null) return;
        TempFile tmp = new TempFile();
        tmp.writer.close();
        this.propFile.delete();
        tmp.file.renameTo(this.propFile);
        init();
    }

    public void setObjectspace(String os) throws IOException {
        if (this.propFile == null) return;
        if (os == null || os.length() == 0 || (this.objectspace != null && this.objectspace.equals(os))) return;
        this.objectspace = os;
        TempFile tmp = new TempFile();
        BlockingQueue<String> list = Files.concurentLineReader(this.propFile);
        String line;
        try {
            vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                String[] pl = parseLine(line);
                if (pl == null) {
                    continue vocloop;
                }
                tmp.writer.write(pl[0] + (pl[1] == null || pl[1].isEmpty() ? "" : ":" + pl[1]) + (pl[2] == null || pl[2].isEmpty() || pl[2].equals(this.objectspace + pl[0]) ? "" : "#" + pl[2]) + "\n");
            }
        } catch (final InterruptedException e) {
        }
        tmp.writer.close();
        this.propFile.delete();
        tmp.file.renameTo(this.propFile);
        init();
    }
    
    private class TempFile {
        public File file;
        public BufferedWriter writer;
        public TempFile() throws IOException {
            if (Tagging.this.propFile == null) throw new IOException("propfile = null");
            this.file = new File(Tagging.this.propFile.getAbsolutePath() + ".tmp");
            this.writer = new BufferedWriter(new FileWriter(this.file));
            if (Tagging.this.namespace != null && !Tagging.this.namespace.equals(DEFAULT_NAMESPACE)) writer.write("#namespace:" + Tagging.this.namespace + "\n");
            if (Tagging.this.objectspace != null && Tagging.this.objectspace.length() > 0) writer.write("#objectspace:" + Tagging.this.objectspace + "\n");
        }
    }

    private Map<String, Set<String>> reconstructionSets() {
        Map<String, Set<String>> r = new TreeMap<String, Set<String>>();
        for (Map.Entry<String, TaggingEntry> e: this.term2entries.entrySet()) {
            Set<String> s = r.get(e.getKey());
            if (s == null) {
                s = new TreeSet<String>();
                r.put(e.getKey(), s);
            }
            if (e.getValue() != null && e.getValue().getSynonym() != null && e.getValue().getSynonym().length() != 0) {
                s.add(e.getValue().getSynonym());
            }
        }
        for (Map.Entry<String, String> e: this.synonym2term.entrySet()) {
            Set<String> s = r.get(e.getValue());
            if (s == null) {
                s = new TreeSet<String>();
                r.put(e.getValue(), s);
            }
            s.add(e.getKey());
        }
        return r;
    }

    private Map<String, SOTuple> reconstructionLists() {
        Map<String, Set<String>> r = reconstructionSets();
        Map<String, SOTuple> map = new TreeMap<String, SOTuple>();
        for (Map.Entry<String, Set<String>> e: r.entrySet()) {
            TaggingEntry entry = this.term2entries.get(e.getKey());
            String objectLink = null;
            if(entry != null) {
                objectLink = entry.getObjectLink();
            }
            map.put(e.getKey(), new SOTuple(e.getValue().toArray(new String[e.getValue().size()]), objectLink == null ? "" : objectLink));
        }
        return map;
    }

    public String getObjectlink(String term) {
        TaggingEntry entry = this.term2entries.get(term);
        if(entry != null) {
            return entry.getObjectLink();
        }
        return null;
    }

    public Map<String, SOTuple> list() {
        if (this.propFile == null) {
            // create a virtual map for automatically generated vocabularies
            return reconstructionLists();
        }
        Map<String, SOTuple> map = new LinkedHashMap<String, SOTuple>();
        BlockingQueue<String> list;
        try {
            list=Files.concurentLineReader(this.propFile);
        } catch (final IOException e1) {
            return map;
        }
        String line;
        try {
            vocloop: while ((line = list.take()) != Files.POISON_LINE) {
                String[] pl = parseLine(line);
                if (pl == null) {
                    continue vocloop;
                }
                map.put(pl[0], new SOTuple(pl[1] == null || pl[1].isEmpty() ? "" : pl[1], pl[2] == null || pl[2].isEmpty() || pl[2].equals(this.objectspace + pl[0]) ? "" : pl[2]));
            }
        } catch (final InterruptedException e) {
        }
        return map;
    }

    private final static String[] parseLine(String line) {
        line = line.trim();
        int p = line.indexOf('#');
        String c = "";
        if (p >= 0) {
            c = line.substring(p + 1);
            line = line.substring(0, p).trim();
        }
        if (line.isEmpty()) {
            return null;
        }
        p = line.indexOf(':');
        if (p < 0) {
            p = line.indexOf('=');
        }
        if (p < 0) {
            p = line.indexOf('\t');
        }
        if (p < 0) {
            return new String[]{line, null, c};
        }
        return new String[]{line.substring(0, p), line.substring(p + 1), c};
    }

    /**
     * get the predicate name which already contains the prefix url stub
     * @return
     */
    public String getPredicate() {
        return this.predicate;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getObjectspace() {
        return this.objectspace;
    }

    private final static Pattern PATTERN_SPACESLASHPLUS = Pattern.compile(" (/|\\+)");
    private final static Pattern PATTERN_SLASHPLUS = Pattern.compile("/|\\+");
    private final static Pattern PATTERN_SPACESPACE = Pattern.compile("  ");

    private final String normalizeKey(String k) {
        k = k.trim();
        // remove symbols that are bad in a query attribute
        k = PATTERN_SPACESLASHPLUS.matcher(k).replaceAll(", ");
        k = PATTERN_SLASHPLUS.matcher(k).replaceAll(",");
        k = PATTERN_SPACESPACE.matcher(k).replaceAll(" ");
        return k;
    }

    /**
     * get the name of the navigator; this is part of the RDF predicate name (see: getPredicate())
     * @return
     */
    public String getName() {
        return this.navigatorName;
    }

    public File getFile() {
        return this.propFile;
    }

    /**
     * @param word
     *            a synonym to look for
     * @return a Metatag instance with the matching term, or null when the synonym
     *         is not in this vocabulary.
     */
    public Metatag getMetatagFromSynonym(final String word) {
        String printname = this.synonym2term.get(word);
        if (printname == null) return null;
        return new Metatag(printname);
    }

    /**
     * @param term
     *            a term to look for
     * @return a Metatag instance with the matching term, or null when it is not in
     *         this vocabulary.
     */
    public Metatag getMetatagFromTerm(final String term) {
        TaggingEntry entry = this.term2entries.get(term);
        if(entry == null) {
            return null;
        }
        return new Metatag(term);
    }

    /**
     * @param word
     *            the object of the Metatag
     * @return a new Metatag instance related to this vocabulary
     */
    public Metatag buildMetatagFromTerm(final String word) {
        return new Metatag(word);
    }
    
    public Set<String> tags() {
        return this.synonym2term.keySet();
    }

    @Override
    public boolean equals(Object m) {
        Tagging m0 = (Tagging) m;
        return this.navigatorName.equals(m0.navigatorName);
    }

    @Override
    public int hashCode() {
        return this.navigatorName.hashCode();
    }

    @Override
    public String toString() {
        return this.term2entries.toString();
    }

    private final static Pattern PATTERN_AE = Pattern.compile("\u00E4"); // german umlaute hack for better matching
    private final static Pattern PATTERN_OE = Pattern.compile("\u00F6");
    private final static Pattern PATTERN_UE = Pattern.compile("\u00FC");
    private final static Pattern PATTERN_SZ = Pattern.compile("\u00DF");

    public static final String normalizeTerm(String term) {
        term = term.trim().toLowerCase();
        term = PATTERN_AE.matcher(term).replaceAll("ae");
        term = PATTERN_OE.matcher(term).replaceAll("oe");
        term = PATTERN_UE.matcher(term).replaceAll("ue");
        term = PATTERN_SZ.matcher(term).replaceAll("ss");
        term = CommonPattern.COMMA.matcher(term).replaceAll(" ");
        return term;
    }

    /**
     * The metatag class contains the object value for a Linked Open Data RDF triple.
     * The metatag is created in a tagging environment, which already contains the
     * subject and the predicate. The metatag is the object of the RDF triple.
     */
    public class Metatag {
        private final String object;
        private Metatag(String object) {
            this.object = object;
        }

        public String getVocabularyName() {
            return Tagging.this.navigatorName;
        }

        public String getPredicate() {
            return Tagging.this.predicate;
        }

        public String getObject() {
            return this.object;
        }

        @Override
        public String toString() {
            return Tagging.this.navigatorName + ":" + encodePrintname(this.object);
        }

        @Override
        public boolean equals(Object m) {
            Metatag m0 = (Metatag) m;
            return Tagging.this.navigatorName.equals(m0.getVocabularyName()) && this.object.equals(m0.object);
        }

        @Override
        public int hashCode() {
            return Tagging.this.navigatorName.hashCode() + this.object.hashCode();
        }
    }

    public static final String encodePrintname(String printname) {
        return CommonPattern.SPACE.matcher(printname).replaceAll("_");
    }

    public static final String decodeMaskname(String maskname) {
        return CommonPattern.UNDERSCORE.matcher(maskname).replaceAll(" ");
    }

    public static String cleanTagFromAutotagging(final String tagString) {
        if (tagString == null || tagString.isEmpty()) return "";
        String[] tags = CommonPattern.SPACE.split(tagString);
        StringBuilder sb = new StringBuilder(tagString.length());
        for (String tag : tags) {
            if (tag.length() > 0) {
                sb.append(tag).append(' ');
            }
        }
        if (sb.length() == 0) return "";
        return sb.substring(0, sb.length() - 1);
    }

}
