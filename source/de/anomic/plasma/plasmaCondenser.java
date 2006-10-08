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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.kelondro.kelondroMSetTools;

public final class plasmaCondenser {

    private final static int numlength = 5;

    //private Properties analysis;
    private TreeMap words; // a string (the words) to (wordStatProp) - relation
    private HashMap sentences;
    private int wordminsize;
    private int wordcut;

    public int RESULT_NUMB_TEXT_BYTES = -1;
    public int RESULT_NUMB_WORDS = -1;
    public int RESULT_DIFF_WORDS = -1;
    public int RESULT_SIMI_WORDS = -1;
    public int RESULT_WORD_ENTROPHY = -1;
    public int RESULT_NUMB_SENTENCES = -1;
    public int RESULT_DIFF_SENTENCES = -1;
    public int RESULT_SIMI_SENTENCES = -1;
    
    public plasmaCondenser(InputStream text) {
        this(text, 3, 2);
    }

    public plasmaCondenser(InputStream text, int wordminsize, int wordcut) {
        this.wordminsize = wordminsize;
        this.wordcut = wordcut;
        // analysis = new Properties();
        words = new TreeMap();
        sentences = new HashMap();
        createCondensement(text);
    }

    public int excludeWords(TreeSet stopwords) {
        // subtracts the given stopwords from the word list
        // the word list shrinkes. This returns the number of shrinked words
        int oldsize = words.size();
        words = kelondroMSetTools.excludeConstructive(words, stopwords);
        return oldsize - words.size();
    }

    public Iterator words() {
        // returns an entry set iterator
        // key is a String (the word), value is a wordStatProp Object
        return words.entrySet().iterator();
    }
    
    public static class wordStatProp {
        // object carries statistics for words and sentences
        
        public int count;       // number of occurrences
        public int posInText;   // unique handle, is initialized with word position (excluding double occurring words)
        public int posInPhrase; //
        public int numOfPhrase;
        public HashSet hash;    //

        public wordStatProp(int handle, int pip, int nop) {
            this.count = 1;
            this.posInText = handle;
            this.posInPhrase = pip;
            this.numOfPhrase = nop;
            this.hash = new HashSet();
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

    private void createCondensement(InputStream is) {

        words = new TreeMap(/*kelondroNaturalOrder.naturalOrder*/);
        sentences = new HashMap();
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

        // read source
        sievedWordsEnum wordenum = new sievedWordsEnum(is, wordminsize);
        while (wordenum.hasMoreElements()) {
            word = ((String) wordenum.nextElement()).toLowerCase(); // TODO: does toLowerCase work for non ISO-8859-1 chars?
            // System.out.println("PARSED-WORD " + word);
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
                    wsp = new wordStatProp(wordHandle, wordInSentenceCounter, sentences.size() + 1);
                }
                words.put(word, wsp);
                // we now have the unique handle of the word, put it into the sentence:
                sentence.append(intString(wordHandle, numlength));
                wordInSentenceCounter++;
            }
        }
        // finnish last sentence
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
        this.RESULT_NUMB_TEXT_BYTES = wordenum.count();
        this.RESULT_NUMB_WORDS = allwordcounter;
        this.RESULT_DIFF_WORDS = wordHandleCount;
        this.RESULT_SIMI_WORDS = words.size();
        this.RESULT_WORD_ENTROPHY = (allwordcounter == 0) ? 0 : (255 * words.size() / allwordcounter);
        this.RESULT_NUMB_SENTENCES = allsentencecounter;
        this.RESULT_DIFF_SENTENCES = sentenceHandleCount;
        this.RESULT_SIMI_SENTENCES = sentences.size();
        //this.RESULT_INFORMATION_VALUE = (allwordcounter == 0) ? 0 : (wordenum.count() * words.size() / allwordcounter / 16);
    }

    public void print() {
        String[] s = sentences();

        // printout a reconstruction of the text
        for (int i = 0; i < s.length; i++) {
            if (s[i] != null) System.out.print("#T " + intString(i, numlength) + " " + s[i]);
        }
    }

    public String[] sentences() {
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

    public void writeMapToFile(File out) throws IOException {
        Map.Entry entry;
        String k;
        String word;
        Iterator it;
        wordStatProp wsp;

        Object[] orderedSentences = makeOrderedSentences();

        // we reconstruct the word hashtable
        // and sort the entries by the number of occurrences
        // this structure is needed to print out a sorted list of words
        TreeMap sortedWords = new TreeMap(/*kelondroNaturalOrder.naturalOrder*/);
        it = words.entrySet().iterator(); // enumerates the keys in ascending order
        while (it.hasNext()) {
            entry = (Map.Entry) it.next();
            word = (String) entry.getKey();
            wsp = (wordStatProp) entry.getValue();
            sortedWords.put(intString(wsp.count, numlength) + intString(wsp.posInText, numlength), word);
        }

        // start writing of words and sentences
        FileWriter writer = new FileWriter(out);
        writer.write("\r\n");
        it = sortedWords.entrySet().iterator(); // enumerates the keys in descending order
        while (it.hasNext()) {
            entry = (Map.Entry) it.next();
            k = (String) entry.getKey();            
            writer.write("#W " + k.substring(numlength) + " " + k.substring(0, numlength) + " " + ((String) entry.getValue()) + "\r\n");
        }
        for (int i = 0; i < orderedSentences.length; i++) {
            if (orderedSentences[i] != null) {
                writer.write("#S " + intString(i, numlength) + " ");
                for (int j = 0; j < ((String[]) orderedSentences[i]).length; j++) {
                    writer.write(((String[]) orderedSentences[i])[j] + " ");
                }
                writer.write("\r\n");
            }
        }
        writer.close();
    }

    public final static boolean invisible(char c) {
        // TODO: Bugfix for UTF-8: does this work for non ISO-8859-1 chars?
        if ((c < ' ') || (c > 'z')) return true;
        return ("$%&/()=\"$%&/()=`^+*~#'-_:;,|<>[]\\".indexOf(c) >= 0);
    }

    public static Enumeration wordTokenizer(String s, int minLength) {
        try {
            // TODO: Bugfix for UTF-8 needed
            return new sievedWordsEnum(new ByteArrayInputStream(s.getBytes()), minLength);
        } catch (Exception e) {
            return null;
        }
    }
	
    public static class sievedWordsEnum implements Enumeration {
        // this enumeration removes all words that contain either wrong characters or are too short
        
        Object buffer = null;
        unsievedWordsEnum e;
        int ml;

        public sievedWordsEnum(InputStream is, int minLength) {
            e = new unsievedWordsEnum(is);
            buffer = nextElement0();
            ml = minLength;
        }

	    private Object nextElement0() {
            String s;
            char c;
            loop: while (e.hasMoreElements()) {
                s = (String) e.nextElement();
                if ((s.length() == 1) && (htmlFilterContentScraper.punctuation(s.charAt(0)))) return s;
                if (s.length() < ml) continue loop;
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

        public int count() {
            return e.count();
        }
    }

    private static class unsievedWordsEnum implements Enumeration {
        
        Object buffer = null;
        linesFromFileEnum e;
        String s;

        public unsievedWordsEnum(InputStream is) {
            e = new linesFromFileEnum(is);
            s = "";
            buffer = nextElement0();
        }

        private Object nextElement0() {
            String r;
            StringBuffer sb;
            char c;
            while (s.length() == 0) {
                if (e.hasMoreElements()) {
                    r = (String) e.nextElement();
                    if (r == null) return null;
                    r = r.trim();
                    sb = new StringBuffer(r.length() * 2);
                    for (int i = 0; i < r.length(); i++) {
                        c = r.charAt(i);
                        if (invisible(c)) sb = sb.append(' '); // TODO: Bugfix needed for UTF-8
                        else if (htmlFilterContentScraper.punctuation(c)) sb = sb.append(' ').append(c).append(' ');
                        else sb = sb.append(c);
                    }
                    s = sb.toString().trim(); 
                    //System.out.println("PARSING-LINE '" + r + "'->'" + s + "'");
                } else {
                    return null;
                }
            }
            int p = s.indexOf(" ");
            if (p < 0) {
                r = s;
                s = "";
                return r;
            }
            r = s.substring(0, p);
            s = s.substring(p + 1).trim();
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

        public int count() {
            return e.count();
        }
    }

    private static class linesFromFileEnum implements Enumeration {
        // read in lines from a given input stream
        // every line starting with a '#' is treated as a comment.

        Object buffer = null;
        BufferedReader raf;
        int counter = 0;

        public linesFromFileEnum(InputStream is) {
            raf = new BufferedReader(new InputStreamReader(is)); // TODO: bugfix needed for UTF-8, use charset for reader
            buffer = nextElement0();
            counter = 0;
        }

        private Object nextElement0() {
            try {
                String s;
                while (true) {
                    s = raf.readLine();
                    if (s == null) {
                        raf.close();
                        return null;
                    }
                    if (!(s.startsWith("#"))) return s;
                }
            } catch (IOException e) {
                try {
                    raf.close();
                } catch (Exception ee) {
                }
                return null;
            }
        }

        public boolean hasMoreElements() {
            return buffer != null;
        }

        public Object nextElement() {
            if (buffer == null) {
                return null;
            } else {
                counter = counter + ((String) buffer).length() + 1;
                Object r = buffer;
                buffer = nextElement0();
                return r;
            }
        }

        public int count() {
            return counter;
        }
    }
    
    public static Enumeration sentencesFromInputStream(InputStream is, String charset) {
        try {
            return new sentencesFromInputStreamEnum(is, charset);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }
    
    private static class sentencesFromInputStreamEnum implements Enumeration {
        // read sentences from a given input stream
        // this enumerates String objects
        
        Object buffer = null;
        BufferedReader raf;
        int counter = 0;

        public sentencesFromInputStreamEnum(InputStream is, String charset) throws UnsupportedEncodingException {
            raf = new BufferedReader((charset == null) ? new InputStreamReader(is) : new InputStreamReader(is, charset));
            buffer = nextElement0();
            counter = 0;
        }

        private Object nextElement0() {
            try {
                String s = readSentence(raf);
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

        public boolean hasMoreElements() {
            return buffer != null;
        }

        public Object nextElement() {
            if (buffer == null) {
                return null;
            } else {
                counter = counter + ((String) buffer).length() + 1;
                Object r = buffer;
                buffer = nextElement0();
                return r;
            }
        }

        public int count() {
            return counter;
        }
    }

    static String readSentence(Reader reader) throws IOException {
        StringBuffer s = new StringBuffer();
        int nextChar;
        char c;
        
        // find sentence end
        for (;;) {
            nextChar = reader.read();
            if (nextChar < 0) return null;
            c = (char) nextChar;
            s.append(c);
            if (htmlFilterContentScraper.punctuation(c)) break;
        }

        // replace line endings and tabs by blanks
        for (int i = 0; i < s.length(); i++) {
            if ((s.charAt(i) == (char) 10) || (s.charAt(i) == (char) 13) || (s.charAt(i) == (char) 8)) s.setCharAt(i, ' ');
        }
        // remove all double-spaces
        int p; while ((p = s.indexOf("  ")) >= 0) s.deleteCharAt(p);
        return new String(s);
        
    }
    /*
    private static void addLineSearchProp(Properties prop, String s, String[] searchwords, HashSet foundsearch) {
        // we store lines containing a key in search vector
        int p;
        String r;
        s = " " + s.toLowerCase() + " ";
        for (int i = 0; i < searchwords.length; i++) {
            if (!(foundsearch.contains(searchwords[i]))) {
                p = s.indexOf((String) searchwords[i]);
                if (p >= 0) {
                    // we found one key in the result text
                    // prepare a line and put it to the property
                    r = s.substring(0, p) + "<B>" + s.substring(p, p + searchwords[i].length()) + "</B>" + s.substring(p + searchwords[i].length());
                    prop.setProperty("key-" + searchwords[i], r);
                    // remember that we found this
                    foundsearch.add(searchwords[i]);
                }
            }
        }
    }
    */
    
    public static Iterator getWords(InputStream input) {
        if (input == null) return null;
        plasmaCondenser condenser = new plasmaCondenser(input);
        return condenser.words();        
    }
    
    public static Iterator getWords(byte[] text) {
        if (text == null) return null;
        ByteArrayInputStream buffer = new ByteArrayInputStream(text);
        return getWords(buffer);
    }
        
    public static void main(String[] args) {
//        if ((args.length == 0) || (args.length > 3))
//            System.out.println("wrong number of arguments: plasmaCondenser -text|-html <infile> <outfile>");
//        else
//            try {
//                plasmaCondenser pc = null;
//
//                // read and analyse file
//                File file = new File(args[1]);
//                InputStream textStream = null;
//                if (args[0].equals("-text")) {
//                    // read a text file
//                    textStream = new FileInputStream(file);
//                } else if (args[0].equals("-html")) {
//                    // read a html file
//                    htmlFilterContentScraper cs = new htmlFilterContentScraper(new de.anomic.net.URL("http://localhost/"));
//                    htmlFilterOutputStream fos = new htmlFilterOutputStream(null, cs, null, false);
//                    FileInputStream fis = new FileInputStream(file);
//                    byte[] buffer = new byte[512];
//                    int i;
//                    while ((i = fis.read(buffer)) > 0) fos.write(buffer, 0, i);
//                    fis.close();
//                    fos.close();
//                    // cs.print();
//                    // System.out.println("TEXT:" + new String(cs.getText()));
//                    textStream = new ByteArrayInputStream(cs.getText());
//                } else {
//                    System.out.println("first argument must be either '-text' or '-html'");
//                    System.exit(-1);
//                }
//                
//                // call condenser
//                pc = new plasmaCondenser(textStream, 1, 0);
//                textStream.close();
//                
//                // output result
//                pc.writeMapToFile(new File(args[2]));
//                pc.print();
//                //System.out.println("ANALYSIS:" + pc.getAnalysis().toString());
//            } catch (IOException e) {
//                System.out.println("Problem with input file: " + e.getMessage());
//            }
    }

}
