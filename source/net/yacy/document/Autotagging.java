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
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.document.WordCache.Dictionary;
import net.yacy.document.geolocalization.Locations;
import net.yacy.kelondro.logging.Log;

/**
 * Autotagging provides a set of tag/print-name properties which can be used to
 * - create tags from texts automatically
 * - create navigation entries for given tags
 */
public class Autotagging {

    private final static Object PRESENT = new Object();

    public final char prefixChar;
    private final File autotaggingPath;
    private final Map<String, Tagging> vocabularies; // mapping from vocabulary name to the tagging vocabulary
    private final Map<String, Object> allTags;

    public Autotagging(final File autotaggingPath, char prefixChar) {
        this.vocabularies = new ConcurrentHashMap<String, Tagging>();
        this.autotaggingPath = autotaggingPath;
        this.prefixChar = prefixChar;
        this.allTags = new ConcurrentHashMap<String, Object>();
        init();
    }


    /**
     * scan the input directory and load all tag tables (again)
     * a tag table is a property file where
     * the key is the tag name
     * the value is the visible name for the tag (shown in a navigator)
     * properties without values are allowed (the value is then set to the key)
     * also the value can be used as a tag
     */
    public void init() {
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
                    Tagging voc = new Tagging(vocName, ff);
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

    public File getVocabularyFile(String name) {
        return new File(this.autotaggingPath, name + ".vocabulary");
    }

    public void deleteVocabulary(String name) {
        Tagging v = this.vocabularies.remove(name);
        if (v == null) return;
        v.getFile().delete();
    }

    public Tagging getVocabulary(String name) {
        return this.vocabularies.get(name);
    }

    public Collection<Tagging> getVocabularies() {
        return this.vocabularies.values();
    }

    public Set<String> allTags() {
        return this.allTags.keySet();
    }

    public void addVocabulary(Tagging voc) {
        this.vocabularies.put(voc.getName(), voc);
        for (String t: voc.tags()) {
            this.allTags.put(t, PRESENT);
        }
    }

    public void addDictionaries(Map<String, Dictionary> dictionaries) {
        for (Map.Entry<String, Dictionary> entry: dictionaries.entrySet()) {
            Tagging voc = new Tagging(entry.getKey(), entry.getValue());
            this.vocabularies.put(entry.getKey(), voc);
            for (String t: voc.tags()) {
                this.allTags.put(t, PRESENT);
            }
        }
    }

    public void addPlaces(Locations locations) {
    	if (locations.size() == 0) return; // otherwise we get a navigation that does nothing
        Tagging voc = new Tagging("Locations", locations);
        try {
            voc.setObjectspace("http://dbpedia.org/resource/");
        } catch (IOException e) {
        }
        this.vocabularies.put("Locations", voc);
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
            tag = getTagFromWord(tokens.nextElement().toString()).toString();
            if (tag != null) as.add(tag);
        }
        return as;
    }

    public Tagging.Metatag getTagFromWord(String word) {
        if (this.vocabularies.isEmpty()) return null;
        Tagging.Metatag tag;
        word = Tagging.normalizeWord(word);
        for (Map.Entry<String, Tagging> v: this.vocabularies.entrySet()) {
            tag = v.getValue().getMetatagFromSynonym(this.prefixChar, word);
            if (tag != null) return tag;
        }
        return null;
    }

    public static boolean metatagAppearIn(final Tagging.Metatag metatag, final String[] tags) {
        String tag = metatag.toString();
        for (String s: tags) {
            if (tag.equals(s)) return true;
        }
        return false;
    }

    public Tagging.Metatag metatag(String metatag) {
        int p = metatag.indexOf(':');
        if (p < 0) throw new RuntimeException("bad metatag: metatag = " + metatag);
        String vocName = metatag.substring(1, p);
        Tagging tagging = this.vocabularies.get(vocName);
        return tagging.getMetatagFromTerm(this.prefixChar, Tagging.decodeMaskname(metatag.substring(p + 1)));
    }

    public String cleanTagFromAutotagging(String tagString) {
    	return Tagging.cleanTagFromAutotagging(this.prefixChar, tagString);
    }

    public static void main(String[] args) {
        Autotagging a = new Autotagging(new File("DATA/DICTIONARIES/" + LibraryProvider.path_to_autotagging_dictionaries), '$');
        for (Map.Entry<String, Tagging> entry: a.vocabularies.entrySet()) {
            System.out.println(entry);
        }
        Set<String> tags = a.getPrintTagsFromText("In die Tueren und Fluchttueren muessen noch Schloesser eingebaut werden");
        System.out.println(tags);
    }

}
