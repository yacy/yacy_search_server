// plasmaCondenser.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 09.01.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// compile with javac -sourcepath source source/de/anomic/plasma/plasmaCondenser.java
// execute with java -cp source de.anomic.plasma.plasmaCondenser

package de.anomic.plasma;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.index.indexRWIEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.server.serverCodings;
import de.anomic.yacy.yacySeedDB;

public final class plasmaCondenser {

    // this is the page analysis class
    
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
    public  static final int flag_cat_p2p           = 13; // p2p support, filesharing archives etc.
    public  static final int flag_cat_sex           = 14; // sexual content
    public  static final int flag_cat_spam          = 15; // pages that anybody would consider as not interesting
    public  static final int flag_cat_linux         = 16; // pages about linux software
    public  static final int flag_cat_macos         = 17; // pages about macintosh, apple computers and the mac os
    public  static final int flag_cat_windows       = 18; // pages about windows os and softare
    public  static final int flag_cat_osreserve     = 19; // reserve
    public  static final int flag_cat_hasimage      = 20; // the page refers to (at least one) images
    public  static final int flag_cat_hasaudio      = 21; // the page refers to (at least one) audio file
    public  static final int flag_cat_hasvideo      = 22; // the page refers to (at least one) videos
    public  static final int flag_cat_hasapp        = 23; // the page refers to (at least one) application file
    
    private final static int numlength = 5;

    //private Properties analysis;
    private TreeMap<String, wordStatProp> words; // a string (the words) to (wordStatProp) - relation
    private HashMap<StringBuffer, phraseStatProp> sentences;
    private int wordminsize;
    private int wordcut;

    //public int RESULT_NUMB_TEXT_BYTES = -1;
    public int RESULT_NUMB_WORDS = -1;
    public int RESULT_DIFF_WORDS = -1;
    public int RESULT_NUMB_SENTENCES = -1;
    public int RESULT_DIFF_SENTENCES = -1;
    public kelondroBitfield RESULT_FLAGS = new kelondroBitfield(4);
    
    public plasmaCondenser(plasmaParserDocument document, boolean indexText, boolean indexMedia) throws UnsupportedEncodingException {
        // if addMedia == true, then all the media links are also parsed and added to the words
        // added media words are flagged with the appropriate media flag
        this.wordminsize = 3;
        this.wordcut = 2;
        this.words = new TreeMap<String, wordStatProp>();
        this.sentences = new HashMap<StringBuffer, phraseStatProp>();
        this.RESULT_FLAGS = new kelondroBitfield(4);
        
        //System.out.println("DEBUG: condensing " + document.getMainLongTitle() + ", indexText=" + Boolean.toString(indexText) + ", indexMedia=" + Boolean.toString(indexMedia));

        insertTextToWords(document.getLocation().toNormalform(false, true), 0, indexRWIEntry.flag_app_url, RESULT_FLAGS);
        
        Map.Entry entry;
        if (indexText) {
            createCondensement(document.getText(), document.getCharset());        
            // the phrase counter:
            // phrase   0 are words taken from the URL
            // phrase   1 is the MainTitle
            // phrase   2 is <not used>
            // phrase   3 is the Document Abstract
            // phrase   4 is the Document Author
            // phrase   5 are the tags specified in document
            // phrase  10 and above are the section headlines/titles (88 possible)
            // phrase  98 is taken from the embedded anchor/hyperlinks description
            // phrase  99 is taken from the media Link url and anchor description
            // phrase 100 and above are lines from the text
      
            insertTextToWords(document.getTitle(),    1, indexRWIEntry.flag_app_descr, RESULT_FLAGS);
            insertTextToWords(document.getAbstract(), 3, indexRWIEntry.flag_app_descr, RESULT_FLAGS);
            insertTextToWords(document.getAuthor(),   4, indexRWIEntry.flag_app_descr, RESULT_FLAGS);
            // missing: tags!
            String[] titles = document.getSectionTitles();
            for (int i = 0; i < titles.length; i++) {
                insertTextToWords(titles[i], i + 10, indexRWIEntry.flag_app_emphasized, RESULT_FLAGS);
            }
            
            // anchors
            Iterator i = document.getAnchors().entrySet().iterator();
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                insertTextToWords((String) entry.getKey(), 98, indexRWIEntry.flag_app_reference, RESULT_FLAGS);
                insertTextToWords((String) entry.getValue(), 98, indexRWIEntry.flag_app_reference, RESULT_FLAGS);
            }
        } else {
            this.RESULT_NUMB_WORDS = 0;
            this.RESULT_DIFF_WORDS = 0;
            this.RESULT_NUMB_SENTENCES = 0;
            this.RESULT_DIFF_SENTENCES = 0;
        }
        
        if (indexMedia) {
            // audio
            Iterator i = document.getAudiolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                insertTextToWords((String) entry.getKey(), 99, flag_cat_hasaudio, RESULT_FLAGS);
                insertTextToWords((String) entry.getValue(), 99, flag_cat_hasaudio, RESULT_FLAGS);
            }

            // video
            i = document.getVideolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                insertTextToWords((String) entry.getKey(), 99, flag_cat_hasvideo, RESULT_FLAGS);
                insertTextToWords((String) entry.getValue(), 99, flag_cat_hasvideo, RESULT_FLAGS);
            }

            // applications
            i = document.getApplinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                insertTextToWords((String) entry.getKey(), 99, flag_cat_hasapp, RESULT_FLAGS);
                insertTextToWords((String) entry.getValue(), 99, flag_cat_hasapp, RESULT_FLAGS);
            }

            // images
            i = document.getImages().iterator();
            htmlFilterImageEntry ientry;
            while (i.hasNext()) {
                ientry = (htmlFilterImageEntry) i.next();
                insertTextToWords(ientry.url().toNormalform(false, true), 99, flag_cat_hasimage, RESULT_FLAGS);
                insertTextToWords(ientry.alt(), 99, flag_cat_hasimage, RESULT_FLAGS);
            }
        
            // finally check all words for missing flag entry
            Iterator<Map.Entry<String, wordStatProp>> j = words.entrySet().iterator();
            wordStatProp wprop;
            Map.Entry<String, wordStatProp> we;
            while (j.hasNext()) {
                we = j.next();
                wprop = (wordStatProp) we.getValue();
                if (wprop.flags == null) {
                    wprop.flags = (kelondroBitfield) RESULT_FLAGS.clone();
                    words.put(we.getKey(), wprop);
                }
            }
        }
        
        // construct flag set for document
        if (document.getImages().size() > 0) RESULT_FLAGS.set(flag_cat_hasimage, true);
        if (document.getAudiolinks().size() > 0) RESULT_FLAGS.set(flag_cat_hasaudio, true);
        if (document.getVideolinks().size() > 0) RESULT_FLAGS.set(flag_cat_hasvideo, true);
        if (document.getApplinks().size()   > 0) RESULT_FLAGS.set(flag_cat_hasapp,   true);
    }
    
    private void insertTextToWords(String text, int phrase, int flagpos, kelondroBitfield flagstemplate) {
        String word;
        wordStatProp wprop;
        sievedWordsEnum wordenum;
        try {
            wordenum = new sievedWordsEnum(new ByteArrayInputStream(text.getBytes()), "UTF-8", 3);
        } catch (UnsupportedEncodingException e) {
            return;
        }
        int pip = 0;
        while (wordenum.hasMoreElements()) {
            word = (new String((StringBuffer) wordenum.nextElement())).toLowerCase();
            wprop = (wordStatProp) words.get(word);
            if (wprop == null) wprop = new wordStatProp(0, pip, phrase);
            if (wprop.flags == null) wprop.flags = (kelondroBitfield) flagstemplate.clone();
            wprop.flags.set(flagpos, true);
            words.put(word, wprop);
            pip++;
            this.RESULT_NUMB_WORDS++;
            this.RESULT_DIFF_WORDS++;
        }
    }

    public plasmaCondenser(InputStream text, String charset) throws UnsupportedEncodingException {
        this(text, charset, 3, 2);
    }

    public plasmaCondenser(InputStream text, String charset, int wordminsize, int wordcut) throws UnsupportedEncodingException {
        this.wordminsize = wordminsize;
        this.wordcut = wordcut;
        // analysis = new Properties();
        words = new TreeMap<String, wordStatProp>();
        sentences = new HashMap<StringBuffer, phraseStatProp>();
        createCondensement(text, charset);
    }
    
    // create a word hash
    public static final String word2hash(String word) {
        return kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(word.toLowerCase())).substring(0, yacySeedDB.commonHashLength);
    }
    
    public static final Set<String> words2hashSet(String[] words) {
        TreeSet<String> hashes = new TreeSet<String>(kelondroBase64Order.enhancedComparator);
        for (int i = 0; i < words.length; i++) hashes.add(word2hash(words[i]));
        return hashes;
    }

    public static final String words2hashString(String[] words) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < words.length; i++) sb.append(word2hash(words[i]));
        return new String(sb);
    }

    public static final TreeSet<String> words2hashes(Set<String> words) {
        Iterator<String> i = words.iterator();
        TreeSet<String> hashes = new TreeSet<String>(kelondroBase64Order.enhancedComparator);
        while (i.hasNext()) hashes.add(word2hash(i.next()));
        return hashes;
    }
    
    public int excludeWords(TreeSet<String> stopwords) {
        // subtracts the given stopwords from the word list
        // the word list shrinkes. This returns the number of shrinked words
        int oldsize = words.size();
        words = kelondroMSetTools.excludeConstructive(words, stopwords);
        return oldsize - words.size();
    }

    public Map<String, wordStatProp> words() {
        // returns the words as word/wordStatProp relation map
        return words;
    }
    
    public Map<StringBuffer, phraseStatProp> sentences() {
        return sentences;
    }
    
    public static class wordStatProp {
        // object carries statistics for words and sentences
        
        public int              count;       // number of occurrences
        public int              posInText;   // unique handle, is initialized with word position (excluding double occurring words)
        public int              posInPhrase; // position of word in phrase
        public int              numOfPhrase; // number of phrase. 'normal' phrases begin with number 100
        public HashSet          hash;        // a set of handles to all sentences where this word appears
        public kelondroBitfield flags;       // the flag bits for each word

        public wordStatProp(int handle, int pip, int nop) {
            this.count = 1;
            this.posInText = handle;
            this.posInPhrase = pip;
            this.numOfPhrase = nop;
            this.hash = new HashSet();
            this.flags = null;
        }

        public void inc() {
            count++;
        }

        public void check(int i) {
            hash.add(Integer.toString(i));
        }

    }
    
    public static class phraseStatProp {
        // object carries statistics for words and sentences
        
        public int count;       // number of occurrences
        public int handle;      // unique handle, is initialized with sentence counter
        public HashSet hash;    //

        public phraseStatProp(int handle) {
            this.count = 1;
            this.handle = handle;
            this.hash = new HashSet();
        }

        public void inc() {
            count++;
        }

        public void check(int i) {
            hash.add(Integer.toString(i));
        }

    }


    public String intString(int number, int length) {
        String s = Integer.toString(number);
        while (s.length() < length) s = "0" + s;
        return s;
    }

    private void createCondensement(InputStream is, String charset) throws UnsupportedEncodingException {
        HashSet currsentwords = new HashSet();
        StringBuffer sentence = new StringBuffer(100);
        String word = "";
        String k;
        int wordlen;
        wordStatProp wsp, wsp1;
        phraseStatProp psp;
        int wordHandle;
        int wordHandleCount = 0;
        int sentenceHandleCount = 0;
        int allwordcounter = 0;
        int allsentencecounter = 0;
        int idx;
        int wordInSentenceCounter = 1;
        Iterator it, it1;
        boolean comb_indexof = false, last_last = false, last_index = false;
        RandomAccessFile fa;
        final boolean dumpWords = false;
        
        if (dumpWords) try {
            fa = new RandomAccessFile(new File("dump.txt"), "rw");
            fa.seek(fa.length());
        } catch (IOException e) {
            e.printStackTrace();
            fa = null;
        }
        
        // read source
        sievedWordsEnum wordenum = new sievedWordsEnum(is, charset, wordminsize);
        while (wordenum.hasMoreElements()) {
            word = (new String((StringBuffer) wordenum.nextElement())).toLowerCase(); // TODO: does toLowerCase work for non ISO-8859-1 chars?
            //System.out.println("PARSED-WORD " + word);
            
            //This is useful for testing what YaCy "sees" of a website.
            if (dumpWords && fa != null) try {
				fa.writeBytes(word);
				fa.write(160);
			} catch (IOException e) {
				e.printStackTrace();
			}
            
            // distinguish punctuation and words
            wordlen = word.length();
            if ((wordlen == 1) && (htmlFilterContentScraper.punctuation(word.charAt(0)))) {
                // store sentence
                if (sentence.length() > 0) {
                    // we store the punctuation symbol as first element of the sentence vector
                    allsentencecounter++;
                    sentence.insert(0, word); // append at beginning
                    if (sentences.containsKey(sentence)) {
                        // sentence already exists
                        psp = (phraseStatProp) sentences.get(sentence);
                        psp.inc();
                        idx = psp.handle;
                        sentences.put(sentence, psp);
                    } else {
                        // create new sentence
                        idx = sentenceHandleCount++;
                        sentences.put(sentence, new phraseStatProp(idx));
                    }
                    // store to the words a link to this sentence
                    it = currsentwords.iterator();
                    while (it.hasNext()) {
                        k = (String) it.next();
                        wsp = (wordStatProp) words.get(k);
                        wsp.check(idx);
                        words.put(k, wsp);
                    }
                }
                sentence = new StringBuffer(100);
                currsentwords.clear();
                wordInSentenceCounter = 1;
            } else {
                // check index.of detection
                if ((last_last) && (comb_indexof) && (word.equals("modified"))) {
                    this.RESULT_FLAGS.set(flag_cat_indexof, true);
                    wordenum.pre(true); // parse lines as they come with CRLF
                }
                if ((last_index) && (word.equals("of"))) comb_indexof = true;
                last_last = word.equals("last");
                last_index = word.equals("index");
                
                // store word
                allwordcounter++;
                currsentwords.add(word);
                if (words.containsKey(word)) {
                    // word already exists
                    wsp = (wordStatProp) words.get(word);
                    wordHandle = wsp.posInText;
                    wsp.inc();
                } else {
                    // word does not yet exist, create new word entry
                    wordHandle = wordHandleCount++;
                    wsp = new wordStatProp(wordHandle, wordInSentenceCounter, sentences.size() + 100);
                    wsp.flags = (kelondroBitfield) RESULT_FLAGS.clone();
                }
                words.put(word, wsp);
                // we now have the unique handle of the word, put it into the sentence:
                sentence.append(intString(wordHandle, numlength));
                wordInSentenceCounter++;
            }
        }
        // finish last sentence
        if (sentence.length() > 0) {
            allsentencecounter++;
            sentence.insert(0, "."); // append at beginning
            if (sentences.containsKey(sentence)) {
                psp = (phraseStatProp) sentences.get(sentence);
                psp.inc();
                sentences.put(sentence, psp);
            } else {
                sentences.put(sentence, new phraseStatProp(sentenceHandleCount++));
            }
        }
        
        if (dumpWords && fa != null) try {
            fa.write('\n');
            fa.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // -------------------

        // we reconstruct the sentence hashtable
        // and order the entries by the number of the sentence
        // this structure is needed to replace double occurring words in sentences
        Object[] orderedSentences = new Object[sentenceHandleCount];
        String[] s;
        int wc;
        Object o;
        it = sentences.keySet().iterator();
        while (it.hasNext()) {
            o = it.next();
            if (o != null) {
                sentence = (StringBuffer) o;
                wc = (sentence.length() - 1) / numlength;
                s = new String[wc + 2];
                psp = (phraseStatProp) sentences.get(sentence);
                s[0] = intString(psp.count, numlength); // number of occurrences of this sentence
                s[1] = sentence.substring(0, 1); // the termination symbol of this sentence
                for (int i = 0; i < wc; i++) {
                    k = sentence.substring(i * numlength + 1, (i + 1) * numlength + 1);
                    s[i + 2] = k;
                }
                orderedSentences[psp.handle] = s;
            }
        }

        Map.Entry entry;
        // we search for similar words and reorganize the corresponding sentences
        // a word is similar, if a shortened version is equal
        it = words.entrySet().iterator(); // enumerates the keys in descending order
        wordsearch: while (it.hasNext()) {
            entry = (Map.Entry) it.next();
            word = (String) entry.getKey();
            wordlen = word.length();
            wsp = (wordStatProp) entry.getValue();
            for (int i = wordcut; i > 0; i--) {
                if (wordlen > i) {
                    k = word.substring(0, wordlen - i);
                    if (words.containsKey(k)) {
                        // we will delete the word 'word' and repoint the
                        // corresponding links
                        // in sentences that use this word
                        wsp1 = (wordStatProp) words.get(k);
                        it1 = wsp.hash.iterator(); // we iterate over all sentences that refer to this word
                        while (it1.hasNext()) {
                            idx = Integer.parseInt((String) it1.next()); // number of a sentence
                            s = (String[]) orderedSentences[idx];
                            for (int j = 2; j < s.length; j++) {
                                if (s[j].equals(intString(wsp.posInText, numlength)))
                                    s[j] = intString(wsp1.posInText, numlength);
                            }
                            orderedSentences[idx] = s;
                        }
                        // update word counter
                        wsp1.count = wsp1.count + wsp.count;
                        words.put(k, wsp1);
                        // remove current word
                        it.remove();
                        continue wordsearch;
                    }
                }
            }
        }

        // depending on the orderedSentences structure, we rebuild the sentence
        // HashMap to eliminate double occuring sentences
        sentences = new HashMap();
        int le;
        for (int i = 0; i < orderedSentences.length; i++) {
            le = ((String[]) orderedSentences[i]).length;
            sentence = new StringBuffer(le * 10);
            for (int j = 1; j < le; j++)
                sentence.append(((String[]) orderedSentences[i])[j]);
            if (sentences.containsKey(sentence)) {
                // add sentence counter to counter of found sentence
                psp = (phraseStatProp) sentences.get(sentence);
                psp.count = psp.count + Integer.parseInt(((String[]) orderedSentences[i])[0]);
                sentences.put(sentence, psp);
                // System.out.println("Found double occurring sentence " + i + "
                // = " + sp.handle);
            } else {
                // create new sentence entry
                psp = new phraseStatProp(i);
                psp.count = Integer.parseInt(((String[]) orderedSentences[i])[0]);
                sentences.put(sentence, psp);
            }
        }

        // store result
        //this.RESULT_NUMB_TEXT_BYTES = wordenum.count();
        this.RESULT_NUMB_WORDS = allwordcounter;
        this.RESULT_DIFF_WORDS = wordHandleCount;
        this.RESULT_NUMB_SENTENCES = allsentencecounter;
        this.RESULT_DIFF_SENTENCES = sentenceHandleCount;
    }

    public void print() {
        String[] s = sentenceReconstruction();

        // printout a reconstruction of the text
        for (int i = 0; i < s.length; i++) {
            if (s[i] != null) System.out.print("#T " + intString(i, numlength) + " " + s[i]);
        }
    }

    private String[] sentenceReconstruction() {
        // we reconstruct the word hashtable
        // and order the entries by the number of the sentence
        // this structure is only needed to reconstruct the text
        String word;
        wordStatProp wsp;
        Map.Entry entry;
        Iterator it;
        String[] orderedWords = new String[words.size() + 99]; // uuiiii, the '99' is only a quick hack...
        it = words.entrySet().iterator(); // enumerates the keys in ascending order
        while (it.hasNext()) {
            entry = (Map.Entry) it.next();
            word = (String) entry.getKey();
            wsp = (wordStatProp) entry.getValue();
            orderedWords[wsp.posInText] = word;
        }

        Object[] orderedSentences = makeOrderedSentences();

        // create a reconstruction of the text
        String[] result = new String[orderedSentences.length];
        String s;
        for (int i = 0; i < orderedSentences.length; i++) {
            if (orderedSentences[i] != null) {
                // TODO: bugfix for UTF-8: avoid this form of string concatenation
                s = "";
                for (int j = 2; j < ((String[]) orderedSentences[i]).length; j++) {
                    s += " " + orderedWords[Integer.parseInt(((String[]) orderedSentences[i])[j])];
                }
                s += ((String[]) orderedSentences[i])[1];
                result[i] = (s.length() > 1) ? s.substring(1) : s;
            } else {
                result[i] = "";
            }
        }
        return result;
    }
        
    private Object[] makeOrderedSentences() {
        // we reconstruct the sentence hashtable again and create by-handle ordered entries
        // this structure is needed to present the strings in the right order in a printout
        int wc;
        Iterator it;
        phraseStatProp psp;
        String[] s;
        StringBuffer sentence;
        Object[] orderedSentences = new Object[sentences.size()];
        for (int i = 0; i < sentences.size(); i++)
            orderedSentences[i] = null; // this array must be initialized
        it = sentences.keySet().iterator();
        while (it.hasNext()) {
            sentence = (StringBuffer) it.next();
            wc = (sentence.length() - 1) / numlength;
            s = new String[wc + 2];
            psp = (phraseStatProp) sentences.get(sentence);
            s[0] = intString(psp.count, numlength); // number of occurrences of this sentence
            s[1] = sentence.substring(0, 1); // the termination symbol of this sentence
            for (int i = 0; i < wc; i++)
                s[i + 2] = sentence.substring(i * numlength + 1, (i + 1) * numlength + 1);
            orderedSentences[psp.handle] = s;
        }
        return orderedSentences;
    }

    public final static boolean invisible(char c) {
        // TODO: Bugfix for UTF-8: does this work for non ISO-8859-1 chars?
        if ((c < ' ') || (c > 'z')) return true;
        return ("$%&/()=\"$%&/()=`^+*~#'-_:;,|<>[]\\".indexOf(c) >= 0);
    }

    public static Enumeration wordTokenizer(String s, String charset, int minLength) {
        try {
            return new sievedWordsEnum(new ByteArrayInputStream(s.getBytes()), charset, minLength);
        } catch (Exception e) {
            return null;
        }
    }
	
    public static class sievedWordsEnum implements Enumeration {
        // this enumeration removes all words that contain either wrong characters or are too short
        
        Object buffer = null;
        unsievedWordsEnum e;
        int ml;

        public sievedWordsEnum(InputStream is, String charset, int minLength) throws UnsupportedEncodingException {
            e = new unsievedWordsEnum(is, charset);
            buffer = nextElement0();
            ml = minLength;
        }

        public void pre(boolean x) {
            e.pre(x);
        }
        
        private Object nextElement0() {
            StringBuffer s;
            char c;
            loop: while (e.hasMoreElements()) {
                s = (StringBuffer) e.nextElement();
                if ((s.length() == 1) && (htmlFilterContentScraper.punctuation(s.charAt(0)))) return s;
                if ((s.length() < ml) && (!(s.equals("of")))) continue loop;
                for (int i = 0; i < s.length(); i++) {
                    c = s.charAt(i);
                    // TODO: Bugfix needed for UTF-8
                    if (((c < 'a') || (c > 'z')) &&
                        ((c < 'A') || (c > 'Z')) &&
                        ((c < '0') || (c > '9')))
                       continue loop; // go to next while loop
                }
                return s;
            }
            return null;
        }

        public boolean hasMoreElements() {
            return buffer != null;
        }

        public Object nextElement() {
            Object r = buffer;
            buffer = nextElement0();
            return r;
        }

    }

    private static class unsievedWordsEnum implements Enumeration {
        // returns an enumeration of StringBuffer Objects
        Object buffer = null;
        sentencesFromInputStreamEnum e;
        StringBuffer s;

        public unsievedWordsEnum(InputStream is, String charset) throws UnsupportedEncodingException {
            e = new sentencesFromInputStreamEnum(is, charset);
            s = new StringBuffer();
            buffer = nextElement0();
        }

        public void pre(boolean x) {
            e.pre(x);
        }
        
        private StringBuffer nextElement0() {
            StringBuffer r;
            StringBuffer sb;
            char c;
            while (s.length() == 0) {
                if (e.hasNext()) {
                    r = (StringBuffer) e.next();
                    if (r == null) return null;
                    r = trim(r);
                    sb = new StringBuffer(r.length() * 2);
                    for (int i = 0; i < r.length(); i++) {
                        c = r.charAt(i);
                        if (invisible(c)) sb = sb.append(' '); // TODO: Bugfix needed for UTF-8
                        else if (htmlFilterContentScraper.punctuation(c)) sb = sb.append(' ').append(c).append(' ');
                        else sb = sb.append(c);
                    }
                    s = trim(sb); 
                    //System.out.println("PARSING-LINE '" + r + "'->'" + s + "'");
                } else {
                    return null;
                }
            }
            int p = s.indexOf(" ");
            if (p < 0) {
                r = s;
                s = new StringBuffer();
                return r;
            }
            r = trim(new StringBuffer(s.substring(0, p)));
            s = trim(s.delete(0, p + 1));
            return r;
        }

        public boolean hasMoreElements() {
            return buffer != null;
        }

        public Object nextElement() {
            Object r = buffer;
            buffer = nextElement0();
            return r;
        }

    }
    
    public static StringBuffer trim(StringBuffer sb) {
        synchronized (sb) {
            while ((sb.length() > 0) && (sb.charAt(0) <= ' ')) sb = sb.deleteCharAt(0);
            while ((sb.length() > 0) && (sb.charAt(sb.length() - 1) <= ' ')) sb = sb.deleteCharAt(sb.length() - 1);
        }
        return sb;
    }
    
    public static sentencesFromInputStreamEnum sentencesFromInputStream(InputStream is, String charset) {
        try {
            return new sentencesFromInputStreamEnum(is, charset);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }
    
    public static class sentencesFromInputStreamEnum implements Iterator {
        // read sentences from a given input stream
        // this enumerates StringBuffer objects
        
        StringBuffer buffer = null;
        BufferedReader raf;
        int counter = 0;
        boolean pre = false;

        public sentencesFromInputStreamEnum(InputStream is, String charset) throws UnsupportedEncodingException {
            raf = new BufferedReader((charset == null) ? new InputStreamReader(is) : new InputStreamReader(is, charset));
            buffer = nextElement0();
            counter = 0;
            pre = false;
        }

        public void pre(boolean x) {
            this.pre = x;
        }
        
        private StringBuffer nextElement0() {
            try {
                StringBuffer s = readSentence(raf, pre);
                //System.out.println(" SENTENCE='" + s + "'"); // DEBUG 
                if (s == null) {
                    raf.close();
                    return null;
                }
                return s;
            } catch (IOException e) {
                try {
                    raf.close();
                } catch (Exception ee) {
                }
                return null;
            }
        }

        public boolean hasNext() {
            return buffer != null;
        }

        public Object next() {
            if (buffer == null) {
                return null;
            } else {
                counter = counter + buffer.length() + 1;
                StringBuffer r = buffer;
                buffer = nextElement0();
                return r;
            }
        }

        public int count() {
            return counter;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    static StringBuffer readSentence(Reader reader, boolean pre) throws IOException {
        StringBuffer s = new StringBuffer();
        int nextChar;
        char c;
        
        // find sentence end
        for (;;) {
            nextChar = reader.read();
            //System.out.print((char) nextChar); // DEBUG    
            if (nextChar < 0) {
                if (s.length() == 0) return null; else break;
            }
            c = (char) nextChar;
            s.append(c);
            if (pre) {
                if ((c == (char) 10) || (c == (char) 13)) break;
            } else {
                if (htmlFilterContentScraper.punctuation(c)) break;
            }
        }

        // replace line endings and tabs by blanks
        for (int i = 0; i < s.length(); i++) {
            if ((s.charAt(i) == (char) 10) || (s.charAt(i) == (char) 13) || (s.charAt(i) == (char) 8)) s.setCharAt(i, ' ');
        }
        // remove all double-spaces
        int p; while ((p = s.indexOf("  ")) >= 0) s.deleteCharAt(p);
        return s;
    }

    public static Map<String, wordStatProp> getWords(byte[] text, String charset) throws UnsupportedEncodingException {
        // returns a word/wordStatProp relation map
        if (text == null) return null;
        ByteArrayInputStream buffer = new ByteArrayInputStream(text);
        return new plasmaCondenser(buffer, charset, 2, 1).words();
    }
    
    public static Map<String, wordStatProp> getWords(String text) {
        // returns a word/wordStatProp relation map
        if (text == null) return null;
        ByteArrayInputStream buffer = new ByteArrayInputStream(text.getBytes());
        try {
            return new plasmaCondenser(buffer, "UTF-8", 2, 1).words();
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }
    
    public static void main(String[] args) {
        // read a property file and convert them into configuration lines
        try {
            File f = new File(args[0]);
            Properties p = new Properties();
            p.load(new FileInputStream(f));
            StringBuffer sb = new StringBuffer();
            sb.append("{\n");
            for (int i = 0; i <= 15; i++) {
                sb.append('"');
                String s = p.getProperty("keywords" + i);
                String[] l = s.split(",");
                for (int j = 0; j < l.length; j++) {
                    sb.append(word2hash(l[j]));
                }
                if (i < 15) sb.append(",\n");
            }
            sb.append("}\n");
            System.out.println(new String(sb));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

}
