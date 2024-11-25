/**
 *  Annotation.java
 *  Copyright 2004 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.01.2004 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General private
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General private License for more details.
 *
 *  You should have received a copy of the GNU Lesser General private License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.language.synonyms.SynonymLibrary;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.Bitfield;

public class Tokenizer {

    // this is the page analysis class
    public final static boolean pseudostemming = false; // switch for removal of words that appear in shortened form
    public final static int wordminsize = 2;
    public final static int wordcut = 2;

    // category flags that show how the page can be distinguished in different interest groups
    public  static final int flag_cat_indexof       =  0; // a directory listing page (i.e. containing 'index of')
    public  static final int flag_cat_haslocation   = 19; // the page has a location metadata attached
    public  static final int flag_cat_hasimage      = 20; // the page refers to (at least one) images
    public  static final int flag_cat_hasaudio      = 21; // the page refers to (at least one) audio file
    public  static final int flag_cat_hasvideo      = 22; // the page refers to (at least one) videos
    public  static final int flag_cat_hasapp        = 23; // the page refers to (at least one) application file

    //private Properties analysis;
    protected final Map<String, Word> words; // a string (the words) to (indexWord) - relation (key: words are lowercase)
    private final Set<String> synonyms; // a set of synonyms to the words
    protected final Map<String, Set<Tagging.Metatag>> tags = new HashMap<String, Set<Tagging.Metatag>>(); // a set of tags, discovered from Autotagging

    public int RESULT_NUMB_WORDS = -1;
    public int RESULT_NUMB_SENTENCES = -1;
    public Bitfield RESULT_FLAGS = new Bitfield(4);

    public Tokenizer(final DigestURL root, final String text, final WordCache meaningLib, boolean doAutotagging, final VocabularyScraper scraper) {
        this.words = new TreeMap<String, Word>(NaturalOrder.naturalComparator);
        this.synonyms = new LinkedHashSet<String>();
        assert text != null;
        final String[] wordcache = new String[LibraryProvider.autotagging.getMaxWordsInTerm() - 1];
        for (int i = 0; i < wordcache.length; i++) {
            wordcache[i] = "";
        }
        String k;
        int wordlen;
        int allwordcounter = 0;
        int allsentencecounter = 0;
        int wordInSentenceCounter = 1;
        boolean comb_indexof = false, last_last = false, last_index = false;
        //final Map<StringBuilder, Phrase> sentences = new HashMap<StringBuilder, Phrase>(100);
        if (LibraryProvider.autotagging.isEmpty()) doAutotagging = false;

        // read source
        WordTokenizer wordenum = new WordTokenizer(new SentenceReader(text), meaningLib);
        try {
            while (wordenum.hasMoreElements()) {
                String word = wordenum.nextElement().toString().toLowerCase(Locale.ENGLISH);
                // handle punktuation (start new sentence)
                if (word.length() == 1 && SentenceReader.punctuation(word.charAt(0))) {
                    // store sentence
                    if (wordInSentenceCounter > 1) // if no word in sentence repeated punktuation ".....", don't count as sentence
                        allsentencecounter++;
                    wordInSentenceCounter = 1;
                    continue;
                }
                if (word.length() < wordminsize) continue;

                // get tags from autotagging
                if (doAutotagging) {
                    Set<String> vocabularyNames = LibraryProvider.autotagging.getVocabularyNames();
                    extendVocabularies(root, scraper, vocabularyNames);
                    
                    extractAutoTagsFromText(wordcache, word, vocabularyNames);
                }
                // shift wordcache
                System.arraycopy(wordcache, 1, wordcache, 0, wordcache.length - 1);
                wordcache[wordcache.length - 1] = word;

                // check index.of detection
                if (last_last && comb_indexof && word.equals("modified")) {
                    this.RESULT_FLAGS.set(flag_cat_indexof, true);
                    wordenum.pre(true); // parse lines as they come with CRLF
                }
                if (last_index && (wordminsize > 2 || word.equals("of"))) comb_indexof = true;
                last_last = word.equals("last");
                last_index = word.equals("index");

                // store word
                allwordcounter++;
                Word wsp = this.words.get(word);
                if (wsp != null) {
                    // word already exists
                    wsp.inc();
                } else {
                    // word does not yet exist, create new word entry
                    wsp = new Word(allwordcounter, wordInSentenceCounter, allsentencecounter + 100); // nomal sentence start at 100 !
                    wsp.flags = this.RESULT_FLAGS.clone();
                    this.words.put(word, wsp);
                }
                // we now have the unique handle of the word, put it into the sentence:
                wordInSentenceCounter++;
            }
        } finally {
            wordenum.close();
            wordenum = null;
        }

        if (pseudostemming) {
            // we search for similar words and reorganize the corresponding sentences
            // a word is similar, if a shortened version is equal
            Iterator<Map.Entry<String, Word>> wi = this.words.entrySet().iterator(); // enumerates the keys in descending order?
            Map.Entry<String, Word> entry;
            wordsearch: while (wi.hasNext()) {
                entry = wi.next();
                String word = entry.getKey();
                wordlen = word.length();
                Word wsp = entry.getValue();
                for (int i = wordcut; i > 0; i--) {
                    if (wordlen > i) {
                        k = word.substring(0, wordlen - i);
                        Word wsp1 = this.words.get(k);
                        if (wsp1 != null) {
                            wsp1.count = wsp1.count + wsp.count; // update word counter
                            wi.remove(); // remove current word
                            continue wordsearch;
                        }
                    }
                }
            }
        }

        // create the synonyms set
        if (SynonymLibrary.size() > 0) {
            for (String word: this.words.keySet()) {
                Set<String> syms = SynonymLibrary.getSynonyms(word);
                if (syms != null) this.synonyms.addAll(syms);
            }
        }

        // store result
        this.RESULT_NUMB_WORDS = allwordcounter;
        // if text doesn't end with punktuation but has words after last found sentence, inc sentence count for trailing text.
        this.RESULT_NUMB_SENTENCES = allsentencecounter + (wordInSentenceCounter > 1 ? 1 : 0);
    }

    /**
     * Check whether a single word or multiple ones match tags
     * from the given autotagging vocabularies. Then fill this instance "tags" map
     * with the eventually matching tags found.
     * 
     * @param wordcache
     *            the words to be checked for matching a tag as a single word or as combination of words 
     * @param word
     *            an additional word to be considered for tag matching
     * @param vocabularyNames
     *            names of the autotagging vocabularies to check
     */
    protected void extractAutoTagsFromText(final String[] wordcache, final String word, final Set<String> vocabularyNames) {
        Tagging.Metatag tag;
        if (vocabularyNames.size() > 0) {
            for (int wordc = 1; wordc <= wordcache.length + 1; wordc++) {
                // wordc is number of words that are tested
                StringBuilder sb = new StringBuilder();
                if (wordc == 1) {
                    sb.append(word);
                } else {
                    for (int w = 0; w < wordc - 1; w++) {
                        sb.append(wordcache[wordcache.length - wordc + w + 1]).append(' ');
                    }
                    sb.append(word);
                }
                String testterm = sb.toString().trim();
                tag = LibraryProvider.autotagging.getTagFromTerm(vocabularyNames, testterm);
                if (tag != null) {
                    String navigatorName = tag.getVocabularyName();
                    Set<Tagging.Metatag> tagset = this.tags.get(navigatorName);
                    if (tagset == null) {
                        tagset = new HashSet<Tagging.Metatag>();
                        this.tags.put(navigatorName, tagset);
                    }
                    tagset.add(tag);
                }
            }
        }
    }

    /**
     * Extend the specified vocabularies, with terms eventually found by the
     * vocabulary scraper for these vocabularies. The scraper is emptied after
     * processing, and extended vocabularies names are removed from the
     * vocabularyNames.
     * 
     * @param root
     *            the document URL
     * @param scraper
     *            the vocabulary scraper, eventually containing new terms scraped
     *            for the registered vocabularies
     * @param vocabularyNames
     *            vocabularies names to be extended
     */
    protected void extendVocabularies(final DigestURL root, final VocabularyScraper scraper,
            final Set<String> vocabularyNames) {
        Tagging.Metatag tag;
        Map<String, String> vocMap = scraper == null ? null : scraper.removeVocMap(root);
        if (vocMap != null && vocMap.size() > 0) {
            for (Map.Entry<String, String> entry: vocMap.entrySet()) {
                String navigatorName = entry.getKey();
                String term = entry.getValue();
                vocabularyNames.remove(navigatorName); // prevent that this is used again for auto-annotation
                Tagging vocabulary = LibraryProvider.autotagging.getVocabulary(navigatorName);
                if (vocabulary != null) {
                    // extend the vocabulary
                    String obj = vocabulary.getObjectlink(term);
                    if (obj == null) {
                        try {
                            vocabulary.put(term, "", root.toNormalform(true));
                        } catch (IOException e) {} // this makes IO, be careful!
                    }
                    // create annotation
                    tag = vocabulary.getMetatagFromTerm(term);
                    Set<Tagging.Metatag> tagset = new HashSet<>();
                    tagset.add(tag);
                    this.tags.put(navigatorName, tagset);
                }
            }
        }
    }

    /**
     * @return returns the words as word/indexWord relation map. All words are lowercase.
     */
    public Map<String, Word> words() {
        // returns the words as word/indexWord relation map
        return this.words;
    }

    public static Map<String, Word> getWords(final String text, final WordCache meaningLib) {
        // returns a word/indexWord relation map
        if (text == null) return null;
        return new Tokenizer(null, text, meaningLib, false, null).words();
    }

    public List<String> synonyms() {
        ArrayList<String> l = new ArrayList<String>(this.synonyms.size());
        for (String s: this.synonyms) l.add(s);
        return l;
    }

    public Map<String, Set<Tagging.Metatag>> tags() {
        return this.tags;
    }

}
