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
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.index.indexPhrase;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexWord;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.yacy.yacyURL;

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

    // initialize array of invisible characters
    private static boolean[] invisibleChar = new boolean['z' - ' ' + 1];
    static {
        // initialize array of invisible charachters
        final String invisibleString = "\"$%&/()=`^+*#'-_:;,<>[]\\";
        for (int i = ' '; i <= 'z'; i++) {
            invisibleChar[i - ' '] = false;
        }
        for (int i = 0; i < invisibleString.length(); i++) {
            invisibleChar[invisibleString.charAt(i) - ' '] = true;
        }
    }
    
    //private Properties analysis;
    private TreeMap<String, indexWord> words; // a string (the words) to (indexWord) - relation
    private final int wordminsize;
    private final int wordcut;

    //public int RESULT_NUMB_TEXT_BYTES = -1;
    public int RESULT_NUMB_WORDS = -1;
    public int RESULT_DIFF_WORDS = -1;
    public int RESULT_NUMB_SENTENCES = -1;
    public int RESULT_DIFF_SENTENCES = -1;
    public kelondroBitfield RESULT_FLAGS = new kelondroBitfield(4);
    
    public plasmaCondenser(final plasmaParserDocument document, final boolean indexText, final boolean indexMedia) throws UnsupportedEncodingException {
        // if addMedia == true, then all the media links are also parsed and added to the words
        // added media words are flagged with the appropriate media flag
        this.wordminsize = 3;
        this.wordcut = 2;
        this.words = new TreeMap<String, indexWord>();
        this.RESULT_FLAGS = new kelondroBitfield(4);
        
        //System.out.println("DEBUG: condensing " + document.getMainLongTitle() + ", indexText=" + Boolean.toString(indexText) + ", indexMedia=" + Boolean.toString(indexMedia));

        insertTextToWords(document.dc_source().toNormalform(false, true), 0, indexRWIEntry.flag_app_dc_identifier, RESULT_FLAGS);
        
        Map.Entry<yacyURL, String> entry;
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
      
            insertTextToWords(document.dc_title(),    1, indexRWIEntry.flag_app_dc_title, RESULT_FLAGS);
            insertTextToWords(document.dc_description(), 3, indexRWIEntry.flag_app_dc_description, RESULT_FLAGS);
            insertTextToWords(document.dc_creator(),   4, indexRWIEntry.flag_app_dc_creator, RESULT_FLAGS);
            // missing: tags!
            final String[] titles = document.getSectionTitles();
            for (int i = 0; i < titles.length; i++) {
                insertTextToWords(titles[i], i + 10, indexRWIEntry.flag_app_emphasized, RESULT_FLAGS);
            }
            
            // anchors
            final Iterator<Map.Entry<yacyURL, String>> i = document.getAnchors().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                if ((entry == null) || (entry.getKey() == null)) continue;
                insertTextToWords(entry.getKey().toNormalform(false, false), 98, indexRWIEntry.flag_app_dc_identifier, RESULT_FLAGS);
                insertTextToWords(entry.getValue(), 98, indexRWIEntry.flag_app_dc_description, RESULT_FLAGS);
            }
        } else {
            this.RESULT_NUMB_WORDS = 0;
            this.RESULT_DIFF_WORDS = 0;
            this.RESULT_NUMB_SENTENCES = 0;
            this.RESULT_DIFF_SENTENCES = 0;
        }
        
        if (indexMedia) {
            // audio
            Iterator<Map.Entry<yacyURL, String>> i = document.getAudiolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasaudio, RESULT_FLAGS);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasaudio, RESULT_FLAGS);
            }

            // video
            i = document.getVideolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasvideo, RESULT_FLAGS);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasvideo, RESULT_FLAGS);
            }

            // applications
            i = document.getApplinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(entry.getKey().toNormalform(false, false), 99, flag_cat_hasapp, RESULT_FLAGS);
                insertTextToWords(entry.getValue(), 99, flag_cat_hasapp, RESULT_FLAGS);
            }

            // images
            final Iterator<htmlFilterImageEntry> j = document.getImages().values().iterator();
            htmlFilterImageEntry ientry;
            while (j.hasNext()) {
                ientry = j.next();
                insertTextToWords(ientry.url().toNormalform(false, false), 99, flag_cat_hasimage, RESULT_FLAGS);
                insertTextToWords(ientry.alt(), 99, flag_cat_hasimage, RESULT_FLAGS);
            }
        
            // finally check all words for missing flag entry
            final Iterator<Map.Entry<String, indexWord>> k = words.entrySet().iterator();
            indexWord wprop;
            Map.Entry<String, indexWord> we;
            while (k.hasNext()) {
                we = k.next();
                wprop = we.getValue();
                if (wprop.flags == null) {
                    wprop.flags = RESULT_FLAGS.clone();
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
    
    private void insertTextToWords(final String text, final int phrase, final int flagpos, final kelondroBitfield flagstemplate) {
        String word;
        indexWord wprop;
        sievedWordsEnum wordenum;
        try {
            wordenum = new sievedWordsEnum(new ByteArrayInputStream(text.getBytes()), "UTF-8", 3);
        } catch (final UnsupportedEncodingException e) {
            return;
        }
        int pip = 0;
        while (wordenum.hasMoreElements()) {
            word = (new String(wordenum.nextElement())).toLowerCase();
            wprop = words.get(word);
            if (wprop == null) wprop = new indexWord(0, pip, phrase);
            if (wprop.flags == null) wprop.flags = flagstemplate.clone();
            wprop.flags.set(flagpos, true);
            words.put(word, wprop);
            pip++;
            this.RESULT_NUMB_WORDS++;
            this.RESULT_DIFF_WORDS++;
        }
    }

    public plasmaCondenser(final InputStream text, final String charset) throws UnsupportedEncodingException {
        this(text, charset, 3, 2);
    }

    public plasmaCondenser(final InputStream text, final String charset, final int wordminsize, final int wordcut) throws UnsupportedEncodingException {
        this.wordminsize = wordminsize;
        this.wordcut = wordcut;
        // analysis = new Properties();
        words = new TreeMap<String, indexWord>();
        createCondensement(text, charset);
    }
    
    public int excludeWords(final TreeSet<String> stopwords) {
        // subtracts the given stopwords from the word list
        // the word list shrinkes. This returns the number of shrinked words
        final int oldsize = words.size();
        words = kelondroMSetTools.excludeConstructive(words, stopwords);
        return oldsize - words.size();
    }

    public Map<String, indexWord> words() {
        // returns the words as word/indexWord relation map
        return words;
    }

    public String intString(final int number, final int length) {
        String s = Integer.toString(number);
        while (s.length() < length) s = "0" + s;
        return s;
    }

    private void createCondensement(final InputStream is, final String charset) throws UnsupportedEncodingException {
        final HashSet<String> currsentwords = new HashSet<String>();
        StringBuffer sentence = new StringBuffer(100);
        String word = "";
        String k;
        int wordlen;
        indexWord wsp, wsp1;
        indexPhrase psp;
        int wordHandle;
        int wordHandleCount = 0;
        int sentenceHandleCount = 0;
        int allwordcounter = 0;
        int allsentencecounter = 0;
        int idx;
        int wordInSentenceCounter = 1;
        boolean comb_indexof = false, last_last = false, last_index = false;
        RandomAccessFile fa;
        final boolean dumpWords = false;
        final HashMap<StringBuffer, indexPhrase> sentences = new HashMap<StringBuffer, indexPhrase>();
        
        if (dumpWords) try {
            fa = new RandomAccessFile(new File("dump.txt"), "rw");
            fa.seek(fa.length());
        } catch (final IOException e) {
            e.printStackTrace();
            fa = null;
        }
        
        // read source
        final sievedWordsEnum wordenum = new sievedWordsEnum(is, charset, wordminsize);
        while (wordenum.hasMoreElements()) {
            word = (new String(wordenum.nextElement())).toLowerCase(); // TODO: does toLowerCase work for non ISO-8859-1 chars?
            //System.out.println("PARSED-WORD " + word);
            
            //This is useful for testing what YaCy "sees" of a website.
            if (dumpWords && fa != null) try {
				fa.writeBytes(word);
				fa.write(160);
			} catch (final IOException e) {
				e.printStackTrace();
			}
            
            // distinguish punctuation and words
            wordlen = word.length();
            Iterator<String> it;
            if ((wordlen == 1) && (htmlFilterContentScraper.punctuation(word.charAt(0)))) {
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
                        sentences.put(sentence, new indexPhrase(idx));
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
                    wsp = words.get(word);
                    wordHandle = wsp.posInText;
                    wsp.inc();
                } else {
                    // word does not yet exist, create new word entry
                    wordHandle = wordHandleCount++;
                    wsp = new indexWord(wordHandle, wordInSentenceCounter, sentences.size() + 100);
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
                sentences.put(sentence, new indexPhrase(sentenceHandleCount++));
            }
        }
        
        if (dumpWords && fa != null) try {
            fa.write('\n');
            fa.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        // -------------------

        // we reconstruct the sentence hashtable
        // and order the entries by the number of the sentence
        // this structure is needed to replace double occurring words in sentences
        final Object[] orderedSentences = new Object[sentenceHandleCount];
        String[] s;
        int wc;
        Object o;
        final Iterator<StringBuffer> sit = sentences.keySet().iterator();
        while (sit.hasNext()) {
            o = sit.next();
            if (o != null) {
                sentence = (StringBuffer) o;
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

        Map.Entry<String, indexWord> entry;
        // we search for similar words and reorganize the corresponding sentences
        // a word is similar, if a shortened version is equal
        final Iterator<Map.Entry<String, indexWord>> wi = words.entrySet().iterator(); // enumerates the keys in descending order
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
        // TODO: Bugfix for UTF-8: does this work for non ISO-8859-1 chars?
        if ((c < ' ') || (c > 'z')) return true;
        return invisibleChar[c - ' '];
    }

    public static Enumeration<StringBuffer> wordTokenizer(final String s, final String charset, final int minLength) {
        try {
            return new sievedWordsEnum(new ByteArrayInputStream(s.getBytes()), charset, minLength);
        } catch (final Exception e) {
            return null;
        }
    }
	
    public static class sievedWordsEnum implements Enumeration<StringBuffer> {
        // this enumeration removes all words that contain either wrong characters or are too short
        
        StringBuffer buffer = null;
        unsievedWordsEnum e;
        int ml;

        public sievedWordsEnum(final InputStream is, final String charset, final int minLength) throws UnsupportedEncodingException {
            e = new unsievedWordsEnum(is, charset);
            buffer = nextElement0();
            ml = minLength;
        }

        public void pre(final boolean x) {
            e.pre(x);
        }
        
        private StringBuffer nextElement0() {
            StringBuffer s;
            char c;
            loop: while (e.hasMoreElements()) {
                s = e.nextElement();
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

        public StringBuffer nextElement() {
            final StringBuffer r = buffer;
            buffer = nextElement0();
            return r;
        }

    }

    private static class unsievedWordsEnum implements Enumeration<StringBuffer> {
        // returns an enumeration of StringBuffer Objects
        StringBuffer buffer = null;
        sentencesFromInputStreamEnum e;
        StringBuffer s;

        public unsievedWordsEnum(final InputStream is, final String charset) throws UnsupportedEncodingException {
            e = new sentencesFromInputStreamEnum(is, charset);
            s = new StringBuffer(20);
            buffer = nextElement0();
        }

        public void pre(final boolean x) {
            e.pre(x);
        }
        
        private StringBuffer nextElement0() {
            StringBuffer r;
            StringBuffer sb;
            char c;
            while (s.length() == 0) {
                if (e.hasNext()) {
                    r = e.next();
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
            final int p = s.indexOf(" ");
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

        public StringBuffer nextElement() {
            final StringBuffer r = buffer;
            buffer = nextElement0();
            return r;
        }

    }
    
    static StringBuffer trim(StringBuffer sb) {
        while ((sb.length() > 0) && (sb.charAt(0) <= ' ')) sb = sb.deleteCharAt(0);
        while ((sb.length() > 0) && (sb.charAt(sb.length() - 1) <= ' ')) sb = sb.deleteCharAt(sb.length() - 1);
        return sb;
    }
    
    public static sentencesFromInputStreamEnum sentencesFromInputStream(final InputStream is, final String charset) {
        try {
            return new sentencesFromInputStreamEnum(is, charset);
        } catch (final UnsupportedEncodingException e) {
            return null;
        }
    }
    
    public static class sentencesFromInputStreamEnum implements Iterator<StringBuffer> {
        // read sentences from a given input stream
        // this enumerates StringBuffer objects
        
        StringBuffer buffer = null;
        BufferedReader raf;
        int counter = 0;
        boolean pre = false;

        public sentencesFromInputStreamEnum(final InputStream is, final String charset) throws UnsupportedEncodingException {
            raf = new BufferedReader((charset == null) ? new InputStreamReader(is) : new InputStreamReader(is, charset));
            buffer = nextElement0();
            counter = 0;
            pre = false;
        }

        public void pre(final boolean x) {
            this.pre = x;
        }
        
        private StringBuffer nextElement0() {
            try {
                final StringBuffer s = readSentence(raf, pre);
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

        public StringBuffer next() {
            if (buffer == null) {
                return null;
            } else {
                counter = counter + buffer.length() + 1;
                final StringBuffer r = buffer;
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

    static StringBuffer readSentence(final Reader reader, final boolean pre) throws IOException {
        final StringBuffer s = new StringBuffer(40);
        int nextChar;
        char c, lc = ' '; // starting with ' ' as last character prevents that the result string starts with a ' '
        
        // find sentence end
        while (true) {
            nextChar = reader.read();
            //System.out.print((char) nextChar); // DEBUG    
            if (nextChar < 0) {
                if (s.length() == 0) return null; else break;
            }
            c = (char) nextChar;
            if (pre && ((c == (char) 10) || (c == (char) 13))) break;
            if (c < ' ') c = ' ';
            if ((lc == ' ') && (c == ' ')) continue; // ignore double spaces
            s.append(c);
            if (htmlFilterContentScraper.punctuation(c)) break;
            lc = c;
        }
        
        if (s.length() == 0) return s;
        if (s.charAt(s.length() - 1) == ' ') {
            s.trimToSize();
            s.deleteCharAt(s.length() - 1);
        }
        return s;
    }

    public static Map<String, indexWord> getWords(final byte[] text, final String charset) throws UnsupportedEncodingException {
        // returns a word/indexWord relation map
        if (text == null) return null;
        final ByteArrayInputStream buffer = new ByteArrayInputStream(text);
        return new plasmaCondenser(buffer, charset, 2, 1).words();
    }
    
    public static Map<String, indexWord> getWords(final String text) {
        // returns a word/indexWord relation map
        if (text == null) return null;
        final ByteArrayInputStream buffer = new ByteArrayInputStream(text.getBytes());
        try {
            return new plasmaCondenser(buffer, "UTF-8", 2, 1).words();
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
            final StringBuffer sb = new StringBuffer();
            sb.append("{\n");
            for (int i = 0; i <= 15; i++) {
                sb.append('"');
                final String s = p.getProperty("keywords" + i);
                final String[] l = s.split(",");
                for (int j = 0; j < l.length; j++) {
                    sb.append(indexWord.word2hash(l[j]));
                }
                if (i < 15) sb.append(",\n");
            }
            sb.append("}\n");
            System.out.println(new String(sb));
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        
    }

}
