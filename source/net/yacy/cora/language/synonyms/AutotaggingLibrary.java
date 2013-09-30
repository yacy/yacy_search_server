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

package net.yacy.cora.language.synonyms;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.geo.Locations;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.util.ConcurrentLog;

/**
 * Autotagging provides a set of tag/print-name properties which can be used to
 * - create tags from texts automatically
 * - create navigation entries for given tags
 */
public class AutotaggingLibrary {

    private final static ConcurrentLog log = new ConcurrentLog(AutotaggingLibrary.class.getName());
    private final static Object PRESENT = new Object();

    private final File autotaggingPath;
    private final Map<String, Tagging> vocabularies; // mapping from vocabulary name to the tagging vocabulary
    private final Map<String, Object> allTags;

    /**
     * create a Autotagging object:
     * scan the input directory and load all tag tables.
     * A tag table is a property file where
     * the key is the tag name
     * the value is the visible name for the tag (shown in a navigator)
     * properties without values are allowed (the value is then set to the key)
     * also the value can be used as a tag
     */
    public AutotaggingLibrary(final File autotaggingPath) {
        this.vocabularies = new ConcurrentHashMap<String, Tagging>();
        this.autotaggingPath = autotaggingPath;
        this.allTags = new ConcurrentHashMap<String, Object>();
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
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    public File getVocabularyFile(String name) {
        return new File(this.autotaggingPath, name + ".vocabulary");
    }

    public void deleteVocabulary(String name) {
        Tagging v = this.vocabularies.remove(name);
        if (v == null || v.getFile() == null) return;
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

    public void addPlaces(Locations locations) {
        if (locations.isEmpty()) return; // otherwise we get a navigation that does nothing
        Tagging voc = new Tagging("Locations", locations);
        try {
            voc.setObjectspace("http://dbpedia.org/resource/");
        } catch (final IOException e) {
        }
        this.vocabularies.put("Locations", voc);
        for (String t: voc.tags()) {
            this.allTags.put(t, PRESENT);
        }
    }

    public void removePlaces() {
        this.vocabularies.remove("Locations");
    }

    public int size() {
    	return this.vocabularies.size();
    }

    public boolean isEmpty() {
    	return this.vocabularies.isEmpty();
    }

    /**
     * maximum number of compound tags (number of words in one tag)
     * @return
     */
    public int getMaxWordsInTerm() {
    	//TODO: calculate from database
    	return 4;
    }

    public Tagging.Metatag getTagFromTerm(String term) {
        if (this.vocabularies.isEmpty()) return null;
        Tagging.Metatag tag;
        term = Tagging.normalizeTerm(term);
        for (Map.Entry<String, Tagging> v: this.vocabularies.entrySet()) {
            tag = v.getValue().getMetatagFromSynonym(term);
            if (tag != null) return tag;
        }
        return null;
    }

    public Tagging.Metatag metatag(String vocName, String term) {
        Tagging tagging = this.vocabularies.get(vocName);
        return tagging.getMetatagFromTerm(Tagging.decodeMaskname(term));
    }

}
