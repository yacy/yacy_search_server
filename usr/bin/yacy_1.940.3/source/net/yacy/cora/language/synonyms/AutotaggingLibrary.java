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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.geo.Locations;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.ProbabilisticClassifier;

/**
 * Autotagging provides a set of tag/print-name properties which can be used to
 * - create tags from texts automatically
 * - create navigation entries for given tags
 */
public class AutotaggingLibrary {

    private final static ConcurrentLog log = new ConcurrentLog(AutotaggingLibrary.class.getName());
    private final static Object PRESENT = new Object();

    private final File autotaggingPath;
    /** mapping from vocabulary name to the tagging vocabulary */
    private final Map<String, Tagging> vocabularies;
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

    /**
     * Create a new Autotagging instance from the provided vocabularies. Can be used
     * for example for testing purpose.
     */
    protected AutotaggingLibrary(final Map<String, Tagging> vocabularies) {
        if(vocabularies != null) {
            this.vocabularies = vocabularies;
        } else {
            this.vocabularies = new ConcurrentHashMap<String, Tagging>();
        }
        this.allTags = new ConcurrentHashMap<String, Object>();
        this.autotaggingPath = null;
        for(final Tagging voc : this.vocabularies.values()) {
            for (final String t: voc.tags()) {
                this.allTags.put(t, PRESENT);
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

    public Set<String> getVocabularyNames() {
        // this must return a clone of the set to prevent that the vocabularies are destroyed in a side effect
        HashSet<String> names = new HashSet<>();
        names.addAll(this.vocabularies.keySet());
        return names;
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

    /**
     * Search a term in the given active vocabularies matching clear text words.
     * @param vocabularies the vocabularies names to search for term
     * @param term the word to search
     * @return a instance of Metatag from the first matching vocabulary, or null when no one was found
     */
    public Tagging.Metatag getTagFromTerm(final Set<String> vocabularies, String term) {
        if (this.vocabularies.isEmpty()) return null;
        Tagging.Metatag tag;
        term = Tagging.normalizeTerm(term);
        for (String vocabularyName: vocabularies) {
            Tagging t = this.vocabularies.get(vocabularyName);
            if (t != null && !t.isMatchFromLinkedData()) {
                tag = t.getMetatagFromSynonym(term);
                if (tag != null) return tag;
            }
        }
        return null;
    }
    
    /**
     * Search in the active vocabularies matching linked data for Metatag entries with objectspace + term
     * matching the given term URL. Returns at most one Metatag instance per
     * vocabulary.
     * 
     * @param termURL
     *            the vocabulary term identifier (an absolute URL) to search
     * @return a set of matching Metatag instances eventually empty
     */
    public Set<Tagging.Metatag> getTagsFromTermURL(final DigestURL termURL) {
        final Set<Tagging.Metatag> tags = new HashSet<>();
        if (termURL == null || this.vocabularies.isEmpty()) {
            return tags;
        }
        final String termURLStr = termURL.toNormalform(false);
        String termNamespace = null;

        /* If the objectLink URL has a fragment, this should be the vocabulary term */
        String term = termURL.getRef();
        if (term == null) {
            /*
             * No fragment in the URL : the term should then be the last segment of the URL
             */
            term = termURL.getFileName();
            if (StringUtils.isNotEmpty(term)) {
                final int lastPathSeparatorPos = termURLStr.lastIndexOf("/");
                if (lastPathSeparatorPos > 0) {
                    termNamespace = termURLStr.substring(0, lastPathSeparatorPos + 1);
                }
            }
        } else {
            final int fragmentPos = termURLStr.indexOf("#");
            if (fragmentPos > 0) {
                termNamespace = termURLStr.substring(0, fragmentPos + 1);
            }
        }
        if (StringUtils.isNotEmpty(term) && termNamespace != null) {
            final String alternativeTermNamespace;
            /*
             * http://example.org/ and https://example.org/ are considered equivalent forms
             * for the namespace URL
             */
            if (termURL.isHTTP()) {
                alternativeTermNamespace = "https" + termNamespace.substring("http".length());
            } else if (termURL.isHTTPS()) {
                alternativeTermNamespace = "http" + termNamespace.substring("https".length());
            } else {
                alternativeTermNamespace = null;
            }

            for (final Tagging vocabulary : this.vocabularies.values()) {
                if (vocabulary != null && vocabulary.isMatchFromLinkedData()) {
                    if ((termNamespace.equals(vocabulary.getObjectspace())) || (alternativeTermNamespace != null
                            && alternativeTermNamespace.equals(vocabulary.getObjectspace()))) {
                        final Tagging.Metatag tag = vocabulary.getMetatagFromTerm(term);
                        if (tag != null) {
                            tags.add(tag);
                        }
                    }
                }
            }
        }
        return tags;
    }

    public Tagging.Metatag metatag(String vocName, String term) {
        Tagging tagging = this.vocabularies.get(vocName);
        if (tagging == null) {
            if (ProbabilisticClassifier.getContextNames().contains(vocName)) {
                tagging = new Tagging(vocName);
            }
        }
        if (tagging == null) return null;
        return tagging.getMetatagFromTerm(Tagging.decodeMaskname(term));
    }

}
