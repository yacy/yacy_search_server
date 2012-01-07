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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.document.geolocalization.Localization;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;

/**
 * Autotagging provides a set of tag/print-name properties which can be used to
 * - create tags from texts automatically
 * - create navigation entries for given tags
 */
public class Autotagging {

    final static Object PRESENT = new Object();

    final char prefixChar;
    final File autotaggingPath;
    final Map<String, Vocabulary> vocabularies;
    final Map<String, Object> allTags;

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

    /*
    public void addDidYouMean(WordCache wordCache) {

    }
     */

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
    public Set<String> tags(String text) {
        Set<String> as = new HashSet<String>();

        return as;
    }

    public static class Vocabulary {

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
            String k, v;
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
                v = line.substring(p + 1);
                tags = v.split(",");
                tagloop: for (String t: tags) {
                    t = t.trim().toLowerCase();
                    if (t.length() == 0) {
                        continue tagloop;
                    }
                    this.tag2print.put(t, k);
                    this.print2tag.put(k, t);
                }
                this.tag2print.put(k.toLowerCase(), k);
                this.print2tag.put(k, k.toLowerCase());
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

        public String getName() {
            return this.navigatorName;
        }

        public String getPrint(final String tag) {
            return this.tag2print.get(tag);
        }

        public String getTag(final String print) {
            return this.print2tag.get(print);
        }

        public Set<String> tags() {
            return this.tag2print.keySet();
        }

        @Override
        public String toString() {
            return this.print2tag.toString();
        }
    }

    public class Metatag {
        private final String vocName;
        private final String print;
        public Metatag(String vocName, String print) {
            this.vocName = vocName;
            this.print = print;
        }
        public Metatag(String metatag) {
            assert metatag.charAt(0) == Autotagging.this.prefixChar;
            int p = metatag.indexOf(':');
            assert p > 0;
            this.vocName = metatag.substring(1, p);
            this.print = metatag.substring(p + 1);
        }
        public String getVocabularyName() {
            return this.vocName;
        }
        public String getPrintName() {
            return this.print;
        }
        public String getMetatag() {
            return Autotagging.this.prefixChar + this.vocName + ":" + this.print.replaceAll(" ", "_");
        }
    }

    public Metatag metatag(String vocName, String print) {
        return new Metatag(vocName, print);
    }

    public Metatag metatag(String metatag) {
        return new Metatag(metatag);
    }

    public static void main(String[] args) {
        Autotagging a = new Autotagging(new File("DATA/DICTIONARIES/" + LibraryProvider.path_to_autotagging_dictionaries), '$');
        for (Map.Entry<String, Vocabulary> entry: a.vocabularies.entrySet()) {
            System.out.println(entry);
        }
    }

}
