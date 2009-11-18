// plasmaCondenser.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

// compile with javac -sourcepath source source/de/anomic/plasma/plasmaCondenser.java
// execute with java -cp source de.anomic.plasma.plasmaCondenser

package net.yacy.document;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.document.language.Identificator;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.util.SetTools;


public final class Condenser {

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
    public  static final int flag_cat_p2p           = 13; // p2p support, file-sharing archives etc.
    public  static final int flag_cat_sex           = 14; // sexual content
    public  static final int flag_cat_spam          = 15; // pages that anybody would consider as not interesting
    public  static final int flag_cat_linux         = 16; // pages about linux software
    public  static final int flag_cat_macos         = 17; // pages about macintosh, apple computers and the mac os
    public  static final int flag_cat_windows       = 18; // pages about windows os and software
    public  static final int flag_cat_osreserve     = 19; // reserve
    public  static final int flag_cat_hasimage      = 20; // the page refers to (at least one) images
    public  static final int flag_cat_hasaudio      = 21; // the page refers to (at least one) audio file
    public  static final int flag_cat_hasvideo      = 22; // the page refers to (at least one) videos
    public  static final int flag_cat_hasapp        = 23; // the page refers to (at least one) application file
    
    private final static int numlength = 5;

    //private Properties analysis;
    private Map<String, Word> words; // a string (the words) to (indexWord) - relation
    private final int wordminsize;
    private final int wordcut;

    //public int RESULT_NUMB_TEXT_BYTES = -1;
    public int RESULT_NUMB_WORDS = -1;
    public int RESULT_DIFF_WORDS = -1;
    public int RESULT_NUMB_SENTENCES = -1;
    public int RESULT_DIFF_SENTENCES = -1;
    public Bitfield RESULT_FLAGS = new Bitfield(4);
    Identificator languageIdentificator;
    
    public Condenser(
            final Document document,
            final boolean indexText,
            final boolean indexMedia
            ) throws UnsupportedEncodingException {
        // if addMedia == true, then all the media links are also parsed and added to the words
        // added media words are flagged with the appropriate media flag
        this.wordminsize = 3;
        this.wordcut = 2;
        this.words = new HashMap<String, Word>();
        this.RESULT_FLAGS = new Bitfield(4);

        // construct flag set for document
        if (document.getImages().size() > 0) RESULT_FLAGS.set(flag_cat_hasimage, true);
        if (document.getAudiolinks().size() > 0) RESULT_FLAGS.set(flag_cat_hasaudio, true);
        if (document.getVideolinks().size() > 0) RESULT_FLAGS.set(flag_cat_hasvideo, true);
        if (document.getApplinks().size()   > 0) RESULT_FLAGS.set(flag_cat_hasapp,   true);
        
        this.languageIdentificator = new Identificator();
        
        
        Map.Entry<DigestURI, String> entry;
        if (indexText) {
            createCondensement(document.getText());        
            // the phrase counter:
            // phrase   0 are words taken from the URL
            // phrase   1 is the MainTitle
            // phrase   2 is <not used>
            // phrase   3 is the Document Abstract
            // phrase   4 is the Document Author
            // phrase   5 are the tags specified in document
            // phrase  10 and above are the section headlines/titles (88 possible)
            // phrase  98 is taken from the embedded anchor/hyperlinks description (REMOVED!)
            // phrase  99 is taken from the media Link url and anchor description
            // phrase 100 and above are lines from the text
      
            insertTextToWords(document.dc_title(),    1, WordReferenceRow.flag_app_dc_title, RESULT_FLAGS, true);
            insertTextToWords(document.dc_description(), 3, WordReferenceRow.flag_app_dc_description, RESULT_FLAGS, true);
            insertTextToWords(document.dc_creator(),   4, WordReferenceRow.flag_app_dc_creator, RESULT_FLAGS, true);
            // missing: tags!
            final String[] titles = document.getSectionTitles();
            for (int i = 0; i < titles.length; i++) {
                insertTextToWords(titles[i], i + 10, WordReferenceRow.flag_app_emphasized, RESULT_FLAGS, true);
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
        insertTextToWords(document.dc_source().toNormalform(false, true), 0, WordReferenceRow.flag_app_dc_identifier, RESULT_FLAGS, false);

        if (indexMedia) {
            // add anchor descriptions: here, we also add the url components
            // audio
            Iterator<Map.Entry<DigestURI, String>> i = document.getAudiolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasaudio, RESULT_FLAGS, false);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasaudio, RESULT_FLAGS, true);
            }

            // video
            i = document.getVideolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasvideo, RESULT_FLAGS, false);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasvideo, RESULT_FLAGS, true);
            }

            // applications
            i = document.getApplinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasapp, RESULT_FLAGS, false);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasapp, RESULT_FLAGS, true);
            }

            // images
            final Iterator<ImageEntry> j = document.getImages().values().iterator();
            ImageEntry ientry;
            while (j.hasNext()) {
                ientry = j.next();
                insertTextToWords(ientry.url().toNormalform(false, false), 99, flag_cat_hasimage, RESULT_FLAGS, false);
                insertTextToWords(ientry.alt(), 99, flag_cat_hasimage, RESULT_FLAGS, true);
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
    
    private void insertTextToWords(final String text, final int phrase, final int flagpos, final Bitfield flagstemplate, boolean useForLanguageIdentification) {
        String word;
        Word wprop;
        sievedWordsEnum wordenum;
        try {
            wordenum = new sievedWordsEnum(new ByteArrayInputStream(text.getBytes("UTF-8")));
        } catch (final UnsupportedEncodingException e) {
            return;
        }
        int pip = 0;
        while (wordenum.hasMoreElements()) {
            word = (new String(wordenum.nextElement())).toLowerCase(Locale.ENGLISH);
            if (useForLanguageIdentification) languageIdentificator.add(word);
            if (word.length() < 3) continue;
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

    public Condenser(final InputStream text) throws UnsupportedEncodingException {
        this(text, 3, 2);
    }

    public Condenser(final InputStream text, final int wordminsize, final int wordcut) throws UnsupportedEncodingException {
        this.wordminsize = wordminsize;
        this.wordcut = wordcut;
        this.languageIdentificator = null; // we don't need that here
        // analysis = new Properties();
        words = new TreeMap<String, Word>();
        createCondensement(text);
    }
    
    public int excludeWords(final TreeSet<String> stopwords) {
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

    public String intString(final int number, final int length) {
        String s = Integer.toString(number);
        while (s.length() < length) s = "0" + s;
        return s;
    }

    private void createCondensement(final InputStream is) throws UnsupportedEncodingException {
        final HashSet<String> currsentwords = new HashSet<String>();
        StringBuilder sentence = new StringBuilder(100);
        String word = "";
        String k;
        int wordlen;
        Word wsp, wsp1;
        Phrase psp;
        int wordHandle;
        int wordHandleCount = 0;
        int sentenceHandleCount = 0;
        int allwordcounter = 0;
        int allsentencecounter = 0;
        int idx;
        int wordInSentenceCounter = 1;
        boolean comb_indexof = false, last_last = false, last_index = false;
        final HashMap<StringBuilder, Phrase> sentences = new HashMap<StringBuilder, Phrase>();
        
        // read source
        final sievedWordsEnum wordenum = new sievedWordsEnum(is);
        while (wordenum.hasMoreElements()) {
            word = (new String(wordenum.nextElement())).toLowerCase(Locale.ENGLISH); // TODO: does toLowerCase work for non ISO-8859-1 chars?
            if (languageIdentificator != null) languageIdentificator.add(word);
            if (word.length() < wordminsize) continue;
            
            // distinguish punctuation and words
            wordlen = word.length();
            Iterator<String> it;
            if ((wordlen == 1) && (ContentScraper.punctuation(word.charAt(0)))) {
                // store sentence
                if (sentence.length() > 0) {
                    // we store the punctuation symbol as first element of the sentence vector
                    allsentencecounter++;
                    sentence.insert(0, word); // append at beginning
                    if (sentences.containsKey(sentence)) {
                        // sentence already exists
                        psp = sentences.get(sentence);
                        psp.inc();
                        idx = psp.handle();
                        sentences.put(sentence, psp);
                    } else {
                        // create new sentence
                        idx = sentenceHandleCount++;
                        sentences.put(sentence, new Phrase(idx));
                    }
                    // store to the words a link to this sentence
                    it = currsentwords.iterator();
                    while (it.hasNext()) {
                        k = it.next();
                        wsp = words.get(k);
                        wsp.check(idx);
                        words.put(k, wsp); // is that necessary?
                    }
                }
                sentence = new StringBuilder(100);
                currsentwords.clear();
                wordInSentenceCounter = 1;
            } else {
                // check index.of detection
                if ((last_last) && (comb_indexof) && (word.equals("modified"))) {
                    this.RESULT_FLAGS.set(flag_cat_indexof, true);
                    wordenum.pre(true); // parse lines as they come with CRLF
                }
                if ((last_index) && (wordminsize > 2 || (word.equals("of")))) comb_indexof = true;
                last_last = word.equals("last");
                last_index = word.equals("index");
                
                // store word
                allwordcounter++;
                currsentwords.add(word);
                if (words.containsKey(word)) {
                    // word already exists
                    wsp = words.get(word);
                    wordHandle = wsp.posInText;
                    wsp.inc();
                } else {
                    // word does not yet exist, create new word entry
                    wordHandle = wordHandleCount++;
                    wsp = new Word(wordHandle, wordInSentenceCounter, sentences.size() + 100);
                    wsp.flags = RESULT_FLAGS.clone();
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
                psp = sentences.get(sentence);
                psp.inc();
                sentences.put(sentence, psp);
            } else {
                sentences.put(sentence, new Phrase(sentenceHandleCount++));
            }
        }

        // we reconstruct the sentence hashtable
        // and order the entries by the number of the sentence
        // this structure is needed to replace double occurring words in sentences
        final Object[] orderedSentences = new Object[sentenceHandleCount];
        String[] s;
        int wc;
        Object o;
        final Iterator<StringBuilder> sit = sentences.keySet().iterator();
        while (sit.hasNext()) {
            o = sit.next();
            if (o != null) {
                sentence = (StringBuilder) o;
                wc = (sentence.length() - 1) / numlength;
                s = new String[wc + 2];
                psp = sentences.get(sentence);
                s[0] = intString(psp.occurrences(), numlength); // number of occurrences of this sentence
                s[1] = sentence.substring(0, 1); // the termination symbol of this sentence
                for (int i = 0; i < wc; i++) {
                    k = sentence.substring(i * numlength + 1, (i + 1) * numlength + 1);
                    s[i + 2] = k;
                }
                orderedSentences[psp.handle()] = s;
            }
        }

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
                        // we will delete the word 'word' and repoint the
                        // corresponding links
                        // in sentences that use this word
                        wsp1 = words.get(k);
                        final Iterator<Integer> it1 = wsp.phrases(); // we iterate over all sentences that refer to this word
                        while (it1.hasNext()) {
                            idx = it1.next().intValue(); // number of a sentence
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
                        wi.remove();
                        continue wordsearch;
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

    public final static boolean invisible(final char c) {
    	final int type = Character.getType(c);
    	if (
			   type == Character.LOWERCASE_LETTER
			|| type == Character.DECIMAL_DIGIT_NUMBER
			|| type == Character.UPPERCASE_LETTER
			|| type == Character.MODIFIER_LETTER
			|| type == Character.OTHER_LETTER
			|| type == Character.TITLECASE_LETTER
			|| ContentScraper.punctuation(c)) {
    		return false;
    	}
    	return true;
    }

    /**
     * tokenize the given sentence and generate a word-wordPos mapping
     * @param sentence the sentence to be tokenized
     * @return a ordered map containing word hashes as key and positions as value. The map is orderd by the hash ordering
     */
    public static TreeMap<byte[], Integer> hashSentence(final String sentence) {
        final TreeMap<byte[], Integer> map = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        final Enumeration<StringBuilder> words = wordTokenizer(sentence, "UTF-8");
        int pos = 0;
        StringBuilder word;
        byte[] hash;
        while (words.hasMoreElements()) {
            word = words.nextElement();
            hash = Word.word2hash(new String(word));
            if (!map.containsKey(hash)) map.put(hash, Integer.valueOf(pos)); // don't overwrite old values, that leads to too far word distances
            pos += word.length() + 1;
        }
        return map;
    }
    
    public static Enumeration<StringBuilder> wordTokenizer(final String s, final String charset) {
        try {
            return new sievedWordsEnum(new ByteArrayInputStream(s.getBytes(charset)));
        } catch (final Exception e) {
            return null;
        }
    }
	
    public static class sievedWordsEnum implements Enumeration<StringBuilder> {
        // this enumeration removes all words that contain either wrong characters or are too short
        
        StringBuilder buffer = null;
        unsievedWordsEnum e;

        public sievedWordsEnum(final InputStream is) throws UnsupportedEncodingException {
            e = new unsievedWordsEnum(is);
            buffer = nextElement0();
        }

        public void pre(final boolean x) {
            e.pre(x);
        }
        
        private StringBuilder nextElement0() {
            StringBuilder s;
            loop: while (e.hasMoreElements()) {
                s = e.nextElement();
                if ((s.length() == 1) && (ContentScraper.punctuation(s.charAt(0)))) return s;
                for (int i = 0; i < s.length(); i++) {
                    if (invisible(s.charAt(i))) continue loop;
                }
                return s;
            }
            return null;
        }

        public boolean hasMoreElements() {
            return buffer != null;
        }

        public StringBuilder nextElement() {
            final StringBuilder r = buffer;
            buffer = nextElement0();
            return r;
        }

    }
    
    private static class unsievedWordsEnum implements Enumeration<StringBuilder> {
        // returns an enumeration of StringBuilder Objects
        StringBuilder buffer = null;
        sentencesFromInputStreamEnum e;
        ArrayList<StringBuilder> s;
        int sIndex;

        public unsievedWordsEnum(final InputStream is) throws UnsupportedEncodingException {
            e = new sentencesFromInputStreamEnum(is);
            s = new ArrayList<StringBuilder>();
            sIndex = 0;
            buffer = nextElement0();
        }

        public void pre(final boolean x) {
            e.pre(x);
        }
        
        private StringBuilder nextElement0() {
            StringBuilder r;
            StringBuilder sb;
            char c;
            if (sIndex >= s.size()) {
                sIndex = 0;
                s.clear();
            }
            while (s.size() == 0) {
                if (!e.hasNext()) return null;
                r = e.next();
                if (r == null) return null;
                r = trim(r);
                sb = new StringBuilder(20);
                for (int i = 0; i < r.length(); i++) {
                    c = r.charAt(i);
                    if (invisible(c)) {
                        if (sb.length() > 0) {s.add(sb); sb = new StringBuilder(20);}
                    } else if (ContentScraper.punctuation(c)) {
                        if (sb.length() > 0) {s.add(sb); sb = new StringBuilder(1);}
                        sb.append(c);
                        s.add(sb);
                        sb = new StringBuilder(20);
                    } else {
                        sb = sb.append(c);
                    }
                }
            }
            r = s.get(sIndex++);
            return r;
        }

        public boolean hasMoreElements() {
            return buffer != null;
        }

        public StringBuilder nextElement() {
            final StringBuilder r = buffer;
            buffer = nextElement0();
            return r;
        }

    }
    
    static StringBuilder trim(StringBuilder sb) {
        int i = 0;
        while (i < sb.length() && sb.charAt(i) <= ' ') i++;
        if (i > 0) sb.delete(0, i);
        i = sb.length() - 1;
        while (i >= 0 && i < sb.length() && sb.charAt(i) <= ' ') i--;
        if (i > 0) sb.delete(i + 1, sb.length());
        return sb;
    }
    
    public static sentencesFromInputStreamEnum sentencesFromInputStream(final InputStream is) {
        try {
            return new sentencesFromInputStreamEnum(is);
        } catch (final UnsupportedEncodingException e) {
            return null;
        }
    }
    
    public static class sentencesFromInputStreamEnum implements Iterator<StringBuilder> {
        // read sentences from a given input stream
        // this enumerates StringBuilder objects
        
        StringBuilder buffer = null;
        BufferedReader raf;
        int counter = 0;
        boolean pre = false;

        public sentencesFromInputStreamEnum(final InputStream is) throws UnsupportedEncodingException {
            raf = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            buffer = nextElement0();
            counter = 0;
            pre = false;
        }

        public void pre(final boolean x) {
            this.pre = x;
        }
        
        private StringBuilder nextElement0() {
            try {
                final StringBuilder s = readSentence(raf, pre);
                //System.out.println(" SENTENCE='" + s + "'"); // DEBUG 
                if (s == null) {
                    raf.close();
                    return null;
                }
                return s;
            } catch (final IOException e) {
                try {
                    raf.close();
                } catch (final Exception ee) {
                }
                return null;
            }
        }

        public boolean hasNext() {
            return buffer != null;
        }

        public StringBuilder next() {
            if (buffer == null) {
                return null;
            }
            counter = counter + buffer.length() + 1;
            final StringBuilder r = buffer;
            buffer = nextElement0();
            return r;
        }

        public int count() {
            return counter;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    static StringBuilder readSentence(final Reader reader, final boolean pre) throws IOException {
        final StringBuilder s = new StringBuilder(80);
        int nextChar;
        char c, lc = ' '; // starting with ' ' as last character prevents that the result string starts with a ' '
        
        // find sentence end
        while (true) {
            nextChar = reader.read();
            //System.out.print((char) nextChar); // DEBUG    
            if (nextChar < 0) {
                if (s.length() == 0) return null;
                break;
            }
            c = (char) nextChar;
            if (pre && ((c == (char) 10) || (c == (char) 13))) break;
            if (c < ' ') c = ' ';
            if ((lc == ' ') && (c == ' ')) continue; // ignore double spaces
            s.append(c);
            if (ContentScraper.punctuation(lc) && invisible(c)) break;
            lc = c;
        }
        
        if (s.length() == 0) return s;
        if (s.charAt(s.length() - 1) == ' ') {
            s.trimToSize();
            s.deleteCharAt(s.length() - 1);
        }
        return s;
    }

    public static Map<String, Word> getWords(final String text) {
        // returns a word/indexWord relation map
        if (text == null) return null;
        ByteArrayInputStream buffer;
		try {
			buffer = new ByteArrayInputStream(text.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e1) {
			buffer = new ByteArrayInputStream(text.getBytes());
		}
        try {
            return new Condenser(buffer, 2, 1).words();
        } catch (final UnsupportedEncodingException e) {
            return null;
        }
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
                    sb.append(new String(Word.word2hash(l[j])));
                }
                if (i < 15) sb.append(",\n");
            }
            sb.append("}\n");
            System.out.println(new String(sb));
        } catch (final FileNotFoundException e) {
            Log.logException(e);
        } catch (final IOException e) {
            Log.logException(e);
        }
        
    }

}
