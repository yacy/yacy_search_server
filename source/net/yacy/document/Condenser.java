/**
 *  Condenser.java
 *  Copyright 2004 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.01.2004 at http://yacy.net
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import org.apache.solr.common.params.MapSolrParams;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.analysis.EnhancedTextProfileSignature;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.language.synonyms.SynonymLibrary;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.language.Identificator;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.SetTools;


public final class Condenser {

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
    private final SortedMap<String, Word> words; // a string (the words) to (indexWord) - relation
    private final Map<String, Set<Tagging.Metatag>> tags = new HashMap<String, Set<Tagging.Metatag>>(); // a set of tags, discovered from Autotagging
    private final Set<String> synonyms; // a set of synonyms to the words
    private long fuzzy_signature = 0, exact_signature = 0; // signatures for double-check detection
    private String fuzzy_signature_text = null; // signatures for double-check detection
    
    public int RESULT_NUMB_WORDS = -1;
    public int RESULT_DIFF_WORDS = -1;
    public int RESULT_NUMB_SENTENCES = -1;
    public int RESULT_DIFF_SENTENCES = -1;
    public Bitfield RESULT_FLAGS = new Bitfield(4);
    private final Identificator languageIdentificator;

    public Condenser(
            final Document document,
            final boolean indexText,
            final boolean indexMedia,
            final WordCache meaningLib,
            final SynonymLibrary synlib,
            final boolean doAutotagging
            ) {
        Thread.currentThread().setName("condenser-" + document.dc_identifier()); // for debugging
        // if addMedia == true, then all the media links are also parsed and added to the words
        // added media words are flagged with the appropriate media flag
        this.words = new TreeMap<String, Word>(NaturalOrder.naturalComparator);
        this.synonyms = new LinkedHashSet<String>();
        this.RESULT_FLAGS = new Bitfield(4);

        // construct flag set for document
        ContentDomain contentDomain = document.getContentDomain();
        if (contentDomain == ContentDomain.IMAGE || !document.getImages().isEmpty())     this.RESULT_FLAGS.set(flag_cat_hasimage, true);
        if (contentDomain == ContentDomain.AUDIO || !document.getAudiolinks().isEmpty()) this.RESULT_FLAGS.set(flag_cat_hasaudio, true);
        if (contentDomain == ContentDomain.VIDEO || !document.getVideolinks().isEmpty()) this.RESULT_FLAGS.set(flag_cat_hasvideo, true);
        if (contentDomain == ContentDomain.APP   || !document.getApplinks().isEmpty())   this.RESULT_FLAGS.set(flag_cat_hasapp,   true);
        if (document.lat() != 0.0 && document.lon() != 0.0) this.RESULT_FLAGS.set(flag_cat_haslocation, true);

        this.languageIdentificator = new Identificator();

        // add the URL components to the word list
        insertTextToWords(new SentenceReader(document.dc_source().toTokens()), 0, WordReferenceRow.flag_app_dc_identifier, this.RESULT_FLAGS, false, meaningLib);

        Map.Entry<AnchorURL, String> entry;
        if (indexText) {
            createCondensement(document.getTextString(), meaningLib, doAutotagging);
            // the phrase counter:
            // phrase   0 are words taken from the URL
            // phrase   1 is the MainTitle
            // phrase   2 is <not used>
            // phrase   3 is the Document Abstract
            // phrase   4 is the Document Author
            // phrase   5 is the Document Publisher
            // phrase   6 are the tags specified in document
            // phrase  10 and above are the section headlines/titles (88 possible)
            // phrase  98 is taken from the embedded anchor/hyperlinks description (REMOVED!)
            // phrase  99 is taken from the media Link url and anchor description
            // phrase 100 and above are lines from the text
            insertTextToWords(new SentenceReader(document.dc_title()),       1, WordReferenceRow.flag_app_dc_title, this.RESULT_FLAGS, true, meaningLib);
            for (String description: document.dc_description()) {
                insertTextToWords(new SentenceReader(description), 3, WordReferenceRow.flag_app_dc_description, this.RESULT_FLAGS, true, meaningLib);
            }
            insertTextToWords(new SentenceReader(document.dc_creator()),     4, WordReferenceRow.flag_app_dc_creator, this.RESULT_FLAGS, true, meaningLib);
            insertTextToWords(new SentenceReader(document.dc_publisher()),   5, WordReferenceRow.flag_app_dc_creator, this.RESULT_FLAGS, true, meaningLib);
            insertTextToWords(new SentenceReader(document.dc_subject(' ')),  6, WordReferenceRow.flag_app_dc_description, this.RESULT_FLAGS, true, meaningLib);
            // missing: tags!
            final String[] titles = document.getSectionTitles();
            for (int i = 0; i < titles.length; i++) {
                insertTextToWords(new SentenceReader(titles[i]), i + 10, WordReferenceRow.flag_app_emphasized, this.RESULT_FLAGS, true, meaningLib);
            }

            // anchors: for text indexing we add only the anchor description
            // REMOVED! Reason:
            // words from the anchor description should appear as normal text in the output from the parser
            // to flag these words as appearance in dc_description would confuse, since the user expects such word as titles of
            // pages that are shown in the search result. The words from the URLS should also not appear as part of the index, because they
            // are not visible in the text and could be used to crate fake-content
            /*
            final Iterator<Map.Entry<yacyURL, String>> i = document.getAnchors().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                if ((entry == null) || (entry.getKey() == null)) continue;
                insertTextToWords(entry.getValue(), 98, indexRWIEntry.flag_app_dc_description, RESULT_FLAGS, true);
            }
            */
        } else {
            this.RESULT_NUMB_WORDS = 0;
            this.RESULT_DIFF_WORDS = 0;
            this.RESULT_NUMB_SENTENCES = 0;
            this.RESULT_DIFF_SENTENCES = 0;
        }

        if (indexMedia) {
            // add anchor descriptions: here, we also add the url components
            // audio
            Iterator<Map.Entry<AnchorURL, String>> i = document.getAudiolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(new SentenceReader(entry.getKey().toNormalform(true)), 99, flag_cat_hasaudio, this.RESULT_FLAGS, false, meaningLib);
                insertTextToWords(new SentenceReader(entry.getValue()), 99, flag_cat_hasaudio, this.RESULT_FLAGS, true, meaningLib);
            }

            // video
            i = document.getVideolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(new SentenceReader(entry.getKey().toNormalform(true)), 99, flag_cat_hasvideo, this.RESULT_FLAGS, false, meaningLib);
                insertTextToWords(new SentenceReader(entry.getValue()), 99, flag_cat_hasvideo, this.RESULT_FLAGS, true, meaningLib);
            }

            // applications
            i = document.getApplinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(new SentenceReader(entry.getKey().toNormalform(true)), 99, flag_cat_hasapp, this.RESULT_FLAGS, false, meaningLib);
                insertTextToWords(new SentenceReader(entry.getValue()), 99, flag_cat_hasapp, this.RESULT_FLAGS, true, meaningLib);
            }

            // images
            final Iterator<ImageEntry> j = document.getImages().values().iterator();
            ImageEntry ientry;
            MultiProtocolURL url;
            while (j.hasNext()) {
                ientry = j.next();
                url = ientry.url();
                if (url == null) continue;
                insertTextToWords(new SentenceReader(url.toNormalform(true)), 99, flag_cat_hasimage, this.RESULT_FLAGS, false, meaningLib);
                insertTextToWords(new SentenceReader(ientry.alt()), 99, flag_cat_hasimage, this.RESULT_FLAGS, true, meaningLib);
            }

            // finally check all words for missing flag entry
            final Iterator<Map.Entry<String, Word>> k = this.words.entrySet().iterator();
            Word wprop;
            Map.Entry<String, Word> we;
            while (k.hasNext()) {
                we = k.next();
                wprop = we.getValue();
                if (wprop.flags == null) {
                    wprop.flags = this.RESULT_FLAGS.clone();
                    this.words.put(we.getKey(), wprop);
                }
            }
        }

        // extend the tags in the document object with autotagging tags
        if (!this.tags.isEmpty()) {
            document.addMetatags(this.tags);
        }

        if (synlib != null && synlib.size() > 0) {
            for (String word: this.words.keySet()) {
                Set<String> syms = synlib.getSynonyms(word);
                if (syms != null) this.synonyms.addAll(syms);
            }
        }
        String text = document.getTextString();
        
        // create the synonyms set
        if (synonyms != null && synlib.size() > 0) {
            for (String word: this.words.keySet()) {
                Set<String> syms = synlib.getSynonyms(word);
                if (syms != null) this.synonyms.addAll(syms);
            }
        }
        
        // create hashes for duplicate detection
        // check dups with http://localhost:8090/solr/select?q=*:*&start=0&rows=3&fl=sku,fuzzy_signature_text_t,fuzzy_signature_l,fuzzy_signature_unique_b
        EnhancedTextProfileSignature fuzzySignatureFactory = new EnhancedTextProfileSignature();
        Map<String,String> sp = new HashMap<String,String>();
        sp.put("quantRate", Float.toString(Ranking.getQuantRate())); // for minTokenLen = 2 the value should not be below 0.24; for minTokenLen = 3 the value must be not below 0.5!
        sp.put("minTokenLen", Integer.toString(Ranking.getMinTokenLen()));
        fuzzySignatureFactory.init(new MapSolrParams(sp));
        fuzzySignatureFactory.add(text);
        this.fuzzy_signature = EnhancedTextProfileSignature.getSignatureLong(fuzzySignatureFactory);
        this.fuzzy_signature_text = fuzzySignatureFactory.getSignatureText().toString();
        this.exact_signature = EnhancedTextProfileSignature.getSignatureLong(text);
    }

    private Condenser(final String text, final WordCache meaningLib, boolean doAutotagging) {
        this.languageIdentificator = null; // we don't need that here
        // analysis = new Properties();
        this.words = new TreeMap<String, Word>();
        this.synonyms = new HashSet<String>();
        createCondensement(text, meaningLib, doAutotagging);
    }

    private void insertTextToWords(
            final SentenceReader text,
            final int phrase,
            final int flagpos,
            final Bitfield flagstemplate,
            final boolean useForLanguageIdentification,
            final WordCache meaningLib) {
        if (text == null) return;
        String word;
        Word wprop;
        WordTokenizer wordenum = new WordTokenizer(text, meaningLib);
        try {
	        int pip = 0;
	        while (wordenum.hasMoreElements()) {
	            word = (wordenum.nextElement().toString()).toLowerCase(Locale.ENGLISH);
	            if (useForLanguageIdentification) this.languageIdentificator.add(word);
	            if (word.length() < 2) continue;
	            wprop = this.words.get(word);
	            if (wprop == null) wprop = new Word(0, pip, phrase);
	            if (wprop.flags == null) wprop.flags = flagstemplate.clone();
	            wprop.flags.set(flagpos, true);
	            this.words.put(word, wprop);
	            pip++;
	            this.RESULT_NUMB_WORDS++;
	            this.RESULT_DIFF_WORDS++;
	        }
        } finally {
        	wordenum.close();
        	wordenum = null;
        }
    }

    public int excludeWords(final SortedSet<String> stopwords) {
        // subtracts the given stopwords from the word list
        // the word list shrinkes. This returns the number of shrinked words
        final int oldsize = this.words.size();
        SetTools.excludeDestructive(this.words, stopwords);
        return oldsize - this.words.size();
    }

    public SortedMap<String, Word> words() {
        // returns the words as word/indexWord relation map
        return this.words;
    }
    
    public List<String> synonyms() {
        ArrayList<String> l = new ArrayList<String>(this.synonyms.size());
        for (String s: this.synonyms) l.add(s);
        return l;
    }

    public long fuzzySignature() {
        return this.fuzzy_signature;
    }

    public String fuzzySignatureText() {
        return this.fuzzy_signature_text;
    }
    
    public long exactSignature() {
        return this.exact_signature;
    }
    
    public String language() {
        return this.languageIdentificator.getLanguage();
    }

    private void createCondensement(final String text, final WordCache meaningLib, boolean doAutotagging) {
        assert text != null;
        final Set<String> currsentwords = new HashSet<String>();
        String word = "";
        String[] wordcache = new String[LibraryProvider.autotagging.getMaxWordsInTerm() - 1];
        for (int i = 0; i < wordcache.length; i++) wordcache[i] = "";
        String k;
        Tagging.Metatag tag;
        int wordlen;
        Word wsp;
        final Word wsp1;
        int wordHandle;
        int wordHandleCount = 0;
        final int sentenceHandleCount = 0;
        int allwordcounter = 0;
        final int allsentencecounter = 0;
        int wordInSentenceCounter = 1;
        boolean comb_indexof = false, last_last = false, last_index = false;
        final Map<StringBuilder, Phrase> sentences = new HashMap<StringBuilder, Phrase>(100);
        if (LibraryProvider.autotagging.isEmpty()) doAutotagging = false;

        // read source
        WordTokenizer wordenum = new WordTokenizer(new SentenceReader(text), meaningLib);
        try {
	        while (wordenum.hasMoreElements()) {
	            word = wordenum.nextElement().toString().toLowerCase(Locale.ENGLISH);
	            if (this.languageIdentificator != null) this.languageIdentificator.add(word);
	            if (word.length() < wordminsize) continue;

	            // get tags from autotagging
	            if (doAutotagging) {
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
	            		//System.out.println("Testing: " + testterm);
		                tag = LibraryProvider.autotagging.getTagFromTerm(testterm);
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
	            // shift wordcache
	            System.arraycopy(wordcache, 1, wordcache, 0, wordcache.length - 1);
	            wordcache[wordcache.length - 1] = word;

	            // distinguish punctuation and words
	            wordlen = word.length();
	            if (wordlen == 1 && SentenceReader.punctuation(word.charAt(0))) {
	                // store sentence
	                currsentwords.clear();
	                wordInSentenceCounter = 1;
	            } else {
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
	                currsentwords.add(word);
	                wsp = this.words.get(word);
	                if (wsp != null) {
	                    // word already exists
	                    wordHandle = wsp.posInText;
	                    wsp.inc();
	                } else {
	                    // word does not yet exist, create new word entry
	                    wordHandle = wordHandleCount++;
	                    wsp = new Word(wordHandle, wordInSentenceCounter, sentences.size() + 100);
	                    wsp.flags = this.RESULT_FLAGS.clone();
	                    this.words.put(word, wsp);
	                }
	                // we now have the unique handle of the word, put it into the sentence:
	                wordInSentenceCounter++;
	            }
	        }
        } finally {
        	wordenum.close();
        	wordenum = null;
        }

        if (pseudostemming) {
            Map.Entry<String, Word> entry;
            // we search for similar words and reorganize the corresponding sentences
            // a word is similar, if a shortened version is equal
            final Iterator<Map.Entry<String, Word>> wi = this.words.entrySet().iterator(); // enumerates the keys in descending order
            wordsearch: while (wi.hasNext()) {
                entry = wi.next();
                word = entry.getKey();
                wordlen = word.length();
                wsp = entry.getValue();
                for (int i = wordcut; i > 0; i--) {
                    if (wordlen > i) {
                        k = word.substring(0, wordlen - i);
                        if (this.words.containsKey(k)) {
                            // update word counter
                            wsp1.count = wsp1.count + wsp.count;
                            this.words.put(k, wsp1);
                            // remove current word
                            wi.remove();
                            continue wordsearch;
                        }
                    }
                }
            }
        }

        // store result
        //this.RESULT_NUMB_TEXT_BYTES = wordenum.count();
        this.RESULT_NUMB_WORDS = allwordcounter;
        this.RESULT_DIFF_WORDS = wordHandleCount;
        this.RESULT_NUMB_SENTENCES = allsentencecounter;
        this.RESULT_DIFF_SENTENCES = sentenceHandleCount;
    }

    public static SortedMap<String, Word> getWords(final String text, final WordCache meaningLib) {
        // returns a word/indexWord relation map
        if (text == null) return null;
        return new Condenser(text, meaningLib, false).words();
    }

    public static void main(final String[] args) {
        // read a property file and convert them into configuration lines
        try {
            final File f = new File(args[0]);
            final Properties p = new Properties();
            p.load(new FileInputStream(f));
            final StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            for (int i = 0; i <= 15; i++) {
                sb.append('"');
                final String s = p.getProperty("keywords" + i);
                final String[] l = s.split(",");
                for (final String element : l) {
                    sb.append(ASCII.String(Word.word2hash(element)));
                }
                if (i < 15) sb.append(",\n");
            }
            sb.append("}\n");
            System.out.println(sb.toString());
        } catch (final FileNotFoundException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

    }

}
