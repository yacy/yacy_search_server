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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;


import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.language.Identificator;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.util.SetTools;


public final class Condenser {
    
    // this is the page analysis class
    public final static boolean pseudostemming = false; // switch for removal of words that appear in shortened form
    public final static int wordminsize = 2;
    public final static int wordcut = 2;

    // category flags that show how the page can be distinguished in different interest groups
    public  static final int flag_cat_indexof       =  0; // a directory listing page (i.e. containing 'index of')
    public  static final int flag_cat_opencontent   =  1; // open source, any free stuff
    public  static final int flag_cat_business      =  2; // web shops, marketing, trade
    public  static final int flag_cat_stockfinance  =  3; // stock exchange (quotes), finance, economy
    public  static final int flag_cat_health        =  4; // health
    public  static final int flag_cat_sport         =  5; // any sport, cars etc.
    public  static final int flag_cat_lifestyle     =  6; // travel, lifestyle
    public  static final int flag_cat_politics      =  7; // politics
    public  static final int flag_cat_news          =  8; // blogs, news pages
    public  static final int flag_cat_children      =  9; // toys, childrens education, help for parents
    public  static final int flag_cat_entertainment = 10; // boulevard, entertainment, cultural content
    public  static final int flag_cat_knowledge     = 11; // science, school stuff, help for homework
    public  static final int flag_cat_computer      = 12; // any computer related stuff, networks, operation systems
    public  static final int flag_cat_p2p           = 13; // p2p support, file-sharing archives etc.
    public  static final int flag_cat_sex           = 14; // sexual content
    public  static final int flag_cat_spam          = 15; // pages that anybody would consider as not interesting
    public  static final int flag_cat_linux         = 16; // pages about linux software
    public  static final int flag_cat_macos         = 17; // pages about macintosh, apple computers and the mac os
    public  static final int flag_cat_windows       = 18; // pages about windows os and software
    public  static final int flag_cat_haslocation   = 19; // the page has a location metadata attached
    public  static final int flag_cat_hasimage      = 20; // the page refers to (at least one) images
    public  static final int flag_cat_hasaudio      = 21; // the page refers to (at least one) audio file
    public  static final int flag_cat_hasvideo      = 22; // the page refers to (at least one) videos
    public  static final int flag_cat_hasapp        = 23; // the page refers to (at least one) application file
    
    private final static int numlength = 5;
    
    //private Properties analysis;
    private Map<String, Word> words; // a string (the words) to (indexWord) - relation
    
    //public int RESULT_NUMB_TEXT_BYTES = -1;
    public int RESULT_NUMB_WORDS = -1;
    public int RESULT_DIFF_WORDS = -1;
    public int RESULT_NUMB_SENTENCES = -1;
    public int RESULT_DIFF_SENTENCES = -1;
    public Bitfield RESULT_FLAGS = new Bitfield(4);
    private Identificator languageIdentificator;
    private final NumberFormat intStringFormatter = NumberFormat.getIntegerInstance(); // use a new instance for each object for a better concurrency
    
    public Condenser(
            final Document document,
            final boolean indexText,
            final boolean indexMedia,
            final WordCache meaningLib
            ) {
        Thread.currentThread().setName("condenser-" + document.dc_identifier()); // for debugging
        // if addMedia == true, then all the media links are also parsed and added to the words
        // added media words are flagged with the appropriate media flag
        this.intStringFormatter.setMinimumIntegerDigits(numlength);
        this.intStringFormatter.setMaximumIntegerDigits(numlength);
        this.words = new HashMap<String, Word>();
        this.RESULT_FLAGS = new Bitfield(4);

        // construct flag set for document
        if (!document.getImages().isEmpty())     RESULT_FLAGS.set(flag_cat_hasimage, true);
        if (!document.getAudiolinks().isEmpty()) RESULT_FLAGS.set(flag_cat_hasaudio, true);
        if (!document.getVideolinks().isEmpty()) RESULT_FLAGS.set(flag_cat_hasvideo, true);
        if (!document.getApplinks().isEmpty())   RESULT_FLAGS.set(flag_cat_hasapp,   true);
        if (document.lat() != 0.0f && document.lon() != 0.0f) RESULT_FLAGS.set(flag_cat_haslocation, true);
        
        this.languageIdentificator = new Identificator();
        
        
        Map.Entry<MultiProtocolURI, String> entry;
        if (indexText) {
            assert document.getText() != null : document.dc_identifier();
            createCondensement(document.getText(), meaningLib);
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
      
            insertTextToWords(document.dc_title(),       1, WordReferenceRow.flag_app_dc_title, RESULT_FLAGS, true, meaningLib);
            insertTextToWords(document.dc_description(), 3, WordReferenceRow.flag_app_dc_description, RESULT_FLAGS, true, meaningLib);
            insertTextToWords(document.dc_creator(),     4, WordReferenceRow.flag_app_dc_creator, RESULT_FLAGS, true, meaningLib);
            insertTextToWords(document.dc_publisher(),   5, WordReferenceRow.flag_app_dc_creator, RESULT_FLAGS, true, meaningLib);
            insertTextToWords(document.dc_subject(' '),  6, WordReferenceRow.flag_app_dc_description, RESULT_FLAGS, true, meaningLib);
            // missing: tags!
            final String[] titles = document.getSectionTitles();
            for (int i = 0; i < titles.length; i++) {
                insertTextToWords(titles[i], i + 10, WordReferenceRow.flag_app_emphasized, RESULT_FLAGS, true, meaningLib);
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
        
        // add the URL components to the word list
        insertTextToWords(document.dc_source().toNormalform(false, true), 0, WordReferenceRow.flag_app_dc_identifier, RESULT_FLAGS, false, meaningLib);

        if (indexMedia) {
            // add anchor descriptions: here, we also add the url components
            // audio
            Iterator<Map.Entry<MultiProtocolURI, String>> i = document.getAudiolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasaudio, RESULT_FLAGS, false, meaningLib);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasaudio, RESULT_FLAGS, true, meaningLib);
            }

            // video
            i = document.getVideolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasvideo, RESULT_FLAGS, false, meaningLib);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasvideo, RESULT_FLAGS, true, meaningLib);
            }

            // applications
            i = document.getApplinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasapp, RESULT_FLAGS, false, meaningLib);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasapp, RESULT_FLAGS, true, meaningLib);
            }

            // images
            final Iterator<ImageEntry> j = document.getImages().values().iterator();
            ImageEntry ientry;
            MultiProtocolURI url;
            while (j.hasNext()) {
                ientry = j.next();
                url = ientry.url();
                if (url == null) continue;
                insertTextToWords(url.toNormalform(false, false), 99, flag_cat_hasimage, RESULT_FLAGS, false, meaningLib);
                insertTextToWords(ientry.alt(), 99, flag_cat_hasimage, RESULT_FLAGS, true, meaningLib);
            }
        
            // finally check all words for missing flag entry
            final Iterator<Map.Entry<String, Word>> k = words.entrySet().iterator();
            Word wprop;
            Map.Entry<String, Word> we;
            while (k.hasNext()) {
                we = k.next();
                wprop = we.getValue();
                if (wprop.flags == null) {
                    wprop.flags = RESULT_FLAGS.clone();
                    words.put(we.getKey(), wprop);
                }
            }
        }
    }
    
    private void insertTextToWords(
            final String text,
            final int phrase,
            final int flagpos,
            final Bitfield flagstemplate,
            final boolean useForLanguageIdentification,
            final WordCache meaningLib) {
        if (text == null) return;
        String word;
        Word wprop;
        WordTokenizer wordenum;
        wordenum = new WordTokenizer(new ByteArrayInputStream(UTF8.getBytes(text)), meaningLib);
        int pip = 0;
        while (wordenum.hasMoreElements()) {
            word = (wordenum.nextElement().toString()).toLowerCase(Locale.ENGLISH);
            if (useForLanguageIdentification) languageIdentificator.add(word);
            if (word.length() < 2) continue;
            wprop = words.get(word);
            if (wprop == null) wprop = new Word(0, pip, phrase);
            if (wprop.flags == null) wprop.flags = flagstemplate.clone();
            wprop.flags.set(flagpos, true);
            words.put(word, wprop);
            pip++;
            this.RESULT_NUMB_WORDS++;
            this.RESULT_DIFF_WORDS++;
        }
    }

    public Condenser(final InputStream text, final WordCache meaningLib) {
        this.languageIdentificator = null; // we don't need that here
        // analysis = new Properties();
        words = new TreeMap<String, Word>();
        createCondensement(text, meaningLib);
    }
    
    public int excludeWords(final SortedSet<String> stopwords) {
        // subtracts the given stopwords from the word list
        // the word list shrinkes. This returns the number of shrinked words
        final int oldsize = words.size();
        SetTools.excludeDestructive(words, stopwords);
        return oldsize - words.size();
    }

    public Map<String, Word> words() {
        // returns the words as word/indexWord relation map
        return words;
    }
    
    public String language() {
        return this.languageIdentificator.getLanguage();
    }

    private void createCondensement(final InputStream is, final WordCache meaningLib) {
        assert is != null;
        final Set<String> currsentwords = new HashSet<String>();
        String word = "";
        String k;
        int wordlen;
        Word wsp, wsp1;
        int wordHandle;
        int wordHandleCount = 0;
        int sentenceHandleCount = 0;
        int allwordcounter = 0;
        int allsentencecounter = 0;
        int wordInSentenceCounter = 1;
        boolean comb_indexof = false, last_last = false, last_index = false;
        final Map<StringBuilder, Phrase> sentences = new HashMap<StringBuilder, Phrase>(100);
        
        // read source
        final WordTokenizer wordenum = new WordTokenizer(is, meaningLib);
        while (wordenum.hasMoreElements()) {
            word = wordenum.nextElement().toLowerCase(Locale.ENGLISH);
            if (languageIdentificator != null) languageIdentificator.add(word);
            if (word.length() < wordminsize) continue;
            
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
                wsp = words.get(word);
                if (wsp != null) {
                    // word already exists
                    wordHandle = wsp.posInText;
                    wsp.inc();
                } else {
                    // word does not yet exist, create new word entry
                    wordHandle = wordHandleCount++;
                    wsp = new Word(wordHandle, wordInSentenceCounter, sentences.size() + 100);
                    wsp.flags = RESULT_FLAGS.clone();
                    words.put(word, wsp);
                }
                // we now have the unique handle of the word, put it into the sentence:
                wordInSentenceCounter++;
            }
        }

        if (pseudostemming) {
            Map.Entry<String, Word> entry;
            // we search for similar words and reorganize the corresponding sentences
            // a word is similar, if a shortened version is equal
            final Iterator<Map.Entry<String, Word>> wi = words.entrySet().iterator(); // enumerates the keys in descending order
            wordsearch: while (wi.hasNext()) {
                entry = wi.next();
                word = entry.getKey();
                wordlen = word.length();
                wsp = entry.getValue();
                for (int i = wordcut; i > 0; i--) {
                    if (wordlen > i) {
                        k = word.substring(0, wordlen - i);
                        if (words.containsKey(k)) {
                            // update word counter
                            wsp1.count = wsp1.count + wsp.count;
                            words.put(k, wsp1);
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
    
    public static Map<String, Word> getWords(final String text, final WordCache meaningLib) {
        // returns a word/indexWord relation map
        if (text == null) return null;
        ByteArrayInputStream buffer;
		buffer = new ByteArrayInputStream(UTF8.getBytes(text));
        return new Condenser(buffer, meaningLib).words();
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
                for (int j = 0; j < l.length; j++) {
                    sb.append(UTF8.String(Word.word2hash(l[j])));
                }
                if (i < 15) sb.append(",\n");
            }
            sb.append("}\n");
            System.out.println(sb.toString());
        } catch (final FileNotFoundException e) {
            Log.logException(e);
        } catch (final IOException e) {
            Log.logException(e);
        }
        
    }

}
