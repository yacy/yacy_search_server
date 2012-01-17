/**
 *  Autotagging
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

package net.yacy.document;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.document.WordCache.Dictionary;
import net.yacy.document.geolocalization.Localization;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;

/**
 * Autotagging provides a set of tag/print-name properties which can be used to
 * - create tags from texts automatically
 * - create navigation entries for given tags
 */
public class Autotagging {

    private final static Object PRESENT = new Object();

    public final char prefixChar;
    private final File autotaggingPath;
    private final Map<String, Vocabulary> vocabularies;
    private final Map<String, Object> allTags;

    public Autotagging(final File autotaggingPath, char prefixChar) {
        this.vocabularies = new ConcurrentHashMap<String, Vocabulary>();
        this.autotaggingPath = autotaggingPath;
        this.prefixChar = prefixChar;
        this.allTags = new ConcurrentHashMap<String, Object>();
        reload();
    }


    /**
     * scan the input directory and load all tag tables (again)
     * a tag table is a property file where
     * the key is the tag name
     * the value is the visible name for the tag (shown in a navigator)
     * properties without values are allowed (the value is then set to the key)
     * also the value can be used as a tag
     */
    public void reload() {
        this.vocabularies.clear();
        this.allTags.clear();
        if (this.autotaggingPath == null || !this.autotaggingPath.exists()) {
            return;
        }
        final String[] files = this.autotaggingPath.list();
        for (final String f: files) {
            if (f.endsWith(".vocabulary")) {
                try {
                    File ff = new File(this.autotaggingPath, f);
                    String vocName = ff.getName();
                    vocName = vocName.substring(0, vocName.length() - 11);
                    Vocabulary voc = new Vocabulary(vocName, ff);
                    this.vocabularies.put(vocName, voc);
                    for (String t: voc.tags()) {
                        this.allTags.put(t, PRESENT);
                    }
                } catch (final IOException e) {
                    Log.logException(e);
                }
            }
        }
    }

    public Collection<Vocabulary> getVocabularies() {
        return this.vocabularies.values();
    }

    public Set<String> allTags() {
        return this.allTags.keySet();
    }

    public void addDictionaries(Map<String, Dictionary> dictionaries) {
        for (Map.Entry<String, Dictionary> entry: dictionaries.entrySet()) {
            Vocabulary voc = new Vocabulary(entry.getKey(), entry.getValue());
            this.vocabularies.put(entry.getKey(), voc);
            for (String t: voc.tags()) {
                this.allTags.put(t, PRESENT);
            }
        }
    }

    public void addLocalization(Localization localization) {
        Vocabulary voc = new Vocabulary("Locale", localization);
        this.vocabularies.put("Locale", voc);
        for (String t: voc.tags()) {
            this.allTags.put(t, PRESENT);
        }
    }

    /**
     * produce a set of tags for a given text.
     * The set contains the names of the tags with a prefix character at the front
     * @param text
     * @return
     */
    public Set<String> getPrintTagsFromText(String text) {
        Set<String> as = new HashSet<String>();
        if (this.vocabularies.isEmpty()) return as;
        final WordTokenizer tokens = new WordTokenizer(new ByteArrayInputStream(UTF8.getBytes(text)), LibraryProvider.dymLib);
        String tag;
        while (tokens.hasMoreElements()) {
            tag = getPrintTagFromWord(tokens.nextElement().toString());
            if (tag != null) as.add(tag);
        }
        return as;
    }

    public String getPrintTagFromWord(String word) {
        if (this.vocabularies.isEmpty()) return null;
        Metatag tag;
        word = normalizeWord(word);
        for (Map.Entry<String, Vocabulary> v: this.vocabularies.entrySet()) {
            tag = v.getValue().getMetatag(word);
            if (tag != null) return tag.toString();
        }
        return null;
    }

    public class Vocabulary {

        final String navigatorName;
        final Map<String, String> tag2print, print2tag;

        public Vocabulary(String name) {
            this.navigatorName = name;
            this.tag2print = new ConcurrentHashMap<String, String>();
            this.print2tag = new ConcurrentHashMap<String, String>();
        }

        public Vocabulary(String name, File propFile) throws IOException {
            this(name);
            ArrayList<String> list = FileUtils.getListArray(propFile);
            String k, kn, v;
            String[] tags;
            int p;
            vocloop: for (String line: list) {
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue vocloop;
                }
                p = line.indexOf(':');
                if (p < 0) {
                    p = line.indexOf('=');
                }
                if (p < 0) {
                    p = line.indexOf('\t');
                }
                if (p < 0) {
                    this.tag2print.put(line, line);
                    this.print2tag.put(line, line);
                    continue vocloop;
                }
                k = line.substring(0, p).trim();
                k = k.replaceAll(" \\+", ", "); // remove symbols that are bad in a query attribute
                k = k.replaceAll(" /", ", ");
                k = k.replaceAll("\\+", ",");
                k = k.replaceAll("/", ",");
                k = k.replaceAll("  ", " ");
                v = line.substring(p + 1);
                tags = v.split(",");
                tagloop: for (String t: tags) {
                    t = normalizeWord(t);
                    if (t.length() == 0) {
                        continue tagloop;
                    }
                    this.tag2print.put(t, k);
                    this.print2tag.put(k, t);
                }
                kn = normalizeWord(k);
                this.tag2print.put(kn, k);
                this.print2tag.put(k, kn);
            }
        }

        public Vocabulary(String name, Localization localization) {
            this(name);
            Set<String> locNames = localization.locationNames();
            for (String loc: locNames) {
                this.tag2print.put(loc.toLowerCase(), loc);
                this.print2tag.put(loc, loc.toLowerCase());
            }
        }

        public Vocabulary(String name, Dictionary dictionary) {
            this(name);
            Set<StringBuilder> words = dictionary.getWords();
            String s;
            for (StringBuilder word: words) {
                s = word.toString();
                this.tag2print.put(s.toLowerCase(), s);
                this.print2tag.put(s, s.toLowerCase());
            }
        }

        public String getName() {
            return this.navigatorName;
        }

        public Metatag getMetatag(final String word) {
            String printname = this.tag2print.get(word);
            if (printname == null) return null;
            return metatag(this.navigatorName, printname);
        }

        public Set<String> tags() {
            return this.tag2print.keySet();
        }

        @Override
        public String toString() {
            return this.print2tag.toString();
        }
    }

    private final static Pattern PATTERN_AE = Pattern.compile("\u00E4"); // german umlaute hack for better matching
    private final static Pattern PATTERN_OE = Pattern.compile("\u00F6");
    private final static Pattern PATTERN_UE = Pattern.compile("\u00FC");
    private final static Pattern PATTERN_SZ = Pattern.compile("\u00DF");
    private final static Pattern PATTERN_UL = Pattern.compile("_");
    private final static Pattern PATTERN_SP = Pattern.compile(" ");

    private static final String normalizeWord(String word) {
        word = word.trim().toLowerCase();
        word = PATTERN_AE.matcher(word).replaceAll("ae");
        word = PATTERN_OE.matcher(word).replaceAll("oe");
        word = PATTERN_UE.matcher(word).replaceAll("ue");
        word = PATTERN_SZ.matcher(word).replaceAll("ss");
        return word;
    }

    public class Metatag {
        private final String vocName;
        private final String print;
        public Metatag(String vocName, String print) {
            this.vocName = vocName;
            this.print = print;
        }
        public Metatag(String metatag) throws RuntimeException {
            assert metatag.charAt(0) == Autotagging.this.prefixChar;
            int p = metatag.indexOf(':');
            if (p < 0) throw new RuntimeException("bad metatag: metatag = " + metatag);
            this.vocName = metatag.substring(1, p);
            this.print = decodeMaskname(metatag.substring(p + 1));
        }
        public String getVocabularyName() {
            return this.vocName;
        }
        public String getPrintName() {
            return this.print;
        }
        @Override
        public String toString() {
            return Autotagging.this.prefixChar + this.vocName + ":" + encodePrintname(this.print);
        }
        @Override
        public boolean equals(Object m) {
            Metatag m0 = (Metatag) m;
            return this.vocName.equals(m0.vocName) && this.print.equals(m0.print);
        }
        @Override
        public int hashCode() {
            return this.vocName.hashCode() + this.print.hashCode();
        }
    }

    public static final String encodePrintname(String printname) {
        return PATTERN_SP.matcher(printname).replaceAll("_");
    }

    public static final String decodeMaskname(String maskname) {
        return PATTERN_UL.matcher(maskname).replaceAll(" ");
    }

    public Metatag metatag(String vocName, String print) {
        return new Metatag(vocName, print);
    }

    public Metatag metatag(String metatag) throws RuntimeException {
        return new Metatag(metatag);
    }

    public static boolean metatagAppearIn(final Metatag metatag, final String[] tags) {
        String tag = metatag.toString();
        for (String s: tags) {
            if (tag.equals(s)) return true;
        }
        return false;
    }

    public String cleanTagFromAutotagging(final String tagString) {
        if (tagString == null || tagString.length() == 0) return "";
        String[] tags = PATTERN_SP.split(tagString);
        StringBuilder sb = new StringBuilder(tagString.length());
        for (String tag : tags) {
            if (tag.length() > 0 && tag.charAt(0) != this.prefixChar) {
                sb.append(tag).append(' ');
            }
        }
        if (sb.length() == 0) return "";
        return sb.substring(0, sb.length() - 1);
    }

    public static void main(String[] args) {
        Autotagging a = new Autotagging(new File("DATA/DICTIONARIES/" + LibraryProvider.path_to_autotagging_dictionaries), '$');
        for (Map.Entry<String, Vocabulary> entry: a.vocabularies.entrySet()) {
            System.out.println(entry);
        }
        Set<String> tags = a.getPrintTagsFromText("In die Tueren und Fluchttueren muessen noch Schloesser eingebaut werden");
        System.out.println(tags);
    }

}
