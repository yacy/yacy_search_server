/**
 *  WordTokenizer
 *  Copyright 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 09.02.2011 on http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.order.Base64Order;
import net.yacy.kelondro.data.word.Word;


public class WordTokenizer implements Enumeration<StringBuilder> {
    // this enumeration removes all words that contain either wrong characters or are too short

    private StringBuilder buffer = null;
    private unsievedWordsEnum e;
    private final WordCache meaningLib;

    public WordTokenizer(final SentenceReader sr, final WordCache meaningLib) {
        assert sr != null;
        this.e = new unsievedWordsEnum(sr);
        this.buffer = nextElement0();
        this.meaningLib = meaningLib;
    }

    public void pre(final boolean x) {
        this.e.pre(x);
    }

    private StringBuilder nextElement0() {
        StringBuilder s;
        while (this.e.hasMoreElements()) {
            s = this.e.nextElement(); // next word (invisible chars filtered)
            return s;
        }
        return null;
    }

    @Override
    public boolean hasMoreElements() {
        return this.buffer != null;
    }

    @Override
    public StringBuilder nextElement() {
        final StringBuilder r = (this.buffer == null) ? null : this.buffer;
        this.buffer = nextElement0();
        // put word to words statistics cache
        if (this.meaningLib != null && r != null) WordCache.learn(r);
        return r;
    }

    public synchronized void close() {
        this.e.close();
        this.e = null;
        this.buffer = null;
    }

    /**
     * Enumeration implementation for unsieved words.
     * This class provides an enumeration of words (in the form of StringBuilders) that haven't been sieved or filtered.
     */
    private class unsievedWordsEnum implements Enumeration<StringBuilder> {
        // Buffer to hold the next element in the enumeration.
        private StringBuilder buffer;

        // Sentence reader instance to read sentences.
        private SentenceReader sr;

        // List to hold tokenized words from the sentence.
        private List<StringBuilder> s;

        // Index to traverse the tokenized words list.
        private int sIndex;

        /**
         * Constructor initializes the enumeration with a SentenceReader.
         *
         * @param sr0 The SentenceReader instance.
         */
        public unsievedWordsEnum(final SentenceReader sr0) {
            assert sr0 != null;
            this.sr = sr0;
            this.s = new ArrayList<StringBuilder>();
            this.sIndex = 0;

            // Populate the buffer with the first word.
            this.buffer = nextElement0();
        }

        /**
         * Pre-process method of the SentenceReader.
         *
         * @param x The boolean value for pre-processing.
         */
        public void pre(final boolean x) {
            this.sr.pre(x);
        }

        /**
         * Utility method to fetch the next unsieved word.
         *
         * @return The next word, or null if no more words are available.
         */
        private StringBuilder nextElement0() {
            StringBuilder r;
            StringBuilder sb;
            char c;
            if (this.sIndex >= this.s.size()) {
                this.sIndex = 0;
                this.s.clear();
            }
            while (this.s.isEmpty()) {
                if (!this.sr.hasNext()) return null;

                // Read the next sentence, including ending punctuation.
                r = this.sr.next();
                if (r == null) return null;
                r = trim(r);

                // Tokenize the sentence into words and punctuation marks.
                sb = new StringBuilder(20); // Initialize StringBuilder to capture tokens (words or punctuation) from the sentence.

                // A variable to track whether the previous character was a digit separator within a number.
                boolean wasDigitSep = false;

                // Iterate through each character in the sentence to tokenize it.
                for (int i = 0; i < r.length(); i++) { // tokenize one sentence
                    c = r.charAt(i);

                    // Check if the character is a minus sign and is followed by a letter or a digit. Treat it as part of the word/number.
                    if (c == '-' && i < r.length() - 1 && (Character.isLetter(r.charAt(i + 1)) || Character.isDigit(r.charAt(i + 1)))) {
                        sb.append(c);
                        continue;  // Skip further checks and continue to the next character.
                    }

                    // Check if the current character is a digit separator within a number.
                    if (SentenceReader.digitsep(c) && i > 0 && Character.isDigit(r.charAt(i - 1)) && (i < r.length() - 1) && Character.isDigit(r.charAt(i + 1))) {
                        sb.append(c);   // Add the digit separator to the current token.
                        wasDigitSep = true; // Set the flag to true.
                        continue; // Continue to the next character without further checks.
                    }

                    // Transition from digit (or digit separator) to a letter. Save the number as a token and start a new token for the word.
                    if (wasDigitSep && Character.isLetter(c)) {
                        if (sb.length() > 0) {
                            this.s.add(sb);
                            sb = new StringBuilder(20);
                        }
                        wasDigitSep = false;
                    }

                    // Check if the current character is a punctuation.
                    // Punctuation checks are prioritized over invisibles due to simplicity and speed.
                    if (SentenceReader.punctuation(c)) { // punctuation check is simple/quick, do it before invisible
                        // If the current token (sb) has content, add it to the list of tokens.
                        if (sb.length() > 0 && !wasDigitSep) {
                            this.s.add(sb);
                            sb = new StringBuilder(1); // Prepare to capture the punctuation.
                        }
                        sb.append(c); // Add the punctuation to the token.
                        this.s.add(sb); // Add the punctuation token to the list.
                        sb = new StringBuilder(20); // Reset token builder for the next token.
                        wasDigitSep = false; // Reset the digit separator flag.
                    }

                    // Check if the current character is invisible.
                    // Note: This check currently has overlap with punctuation check.
                    else if (SentenceReader.invisible(c)) { // ! currently punctuation again checked by invisible()
                        // If the current token (sb) has content, add it to the list and reset the token builder.
                        if (sb.length() > 0) {
                            this.s.add(sb);
                            sb = new StringBuilder(20);
                        }
                        wasDigitSep = false; // Reset the digit separator flag.
                    }
                    // If the character is not punctuation or invisible, add it to the current token.
                    else {
                        sb = sb.append(c);
                        // Check for transition from number to word, e.g., "4.7Ohm"
                        if (i < r.length() - 1 && Character.isDigit(c) && Character.isLetter(r.charAt(i + 1))) {
                            this.s.add(sb);
                            sb = new StringBuilder(20); // Start capturing the word as a new token.
                        }
                    }
                }

                // If there's any content left in the token builder after processing the sentence, add it to the list.
                if (sb.length() > 0) {
                    this.s.add(sb);
                    sb = null;
                }
            }
            r = this.s.get(this.sIndex++);
            return r;
        }

        @Override
        public boolean hasMoreElements() {
            return this.buffer != null;
        }

        @Override
        public StringBuilder nextElement() {
            final StringBuilder r = this.buffer;
            this.buffer = nextElement0();
            return r;
        }

        public synchronized void close() {
            this.sIndex = 0;
            this.s.clear();
            this.s = null;
            this.sr.close();
            this.sr = null;
        }
    }

    public static StringBuilder trim(final StringBuilder sb) {
        int i = 0;
        while (i < sb.length() && sb.charAt(i) <= ' ') {
            i++;
        }
        if (i > 0) {
            sb.delete(0, i);
        }
        i = sb.length() - 1;
        while (i >= 0 && i < sb.length() && sb.charAt(i) <= ' ') {
            i--;
        }
        if (i > 0) {
            sb.delete(i + 1, sb.length());
        }
        return sb;
    }

    /**
     * tokenize the given sentence and generate a word-wordPos mapping
     * @param sentence the sentence to be tokenized
     * @return a ordered map containing word hashes as key and positions as value. The map is orderd by the hash ordering
     */
    public static SortedMap<byte[], Integer> hashSentence(final String sentence, int maxlength) {
        final SortedMap<byte[], Integer> map = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        WordTokenizer words = new WordTokenizer(new SentenceReader(sentence), null);
        try {
            int pos = 0;
            StringBuilder word;
            byte[] hash;
            Integer oldpos;
            while (words.hasMoreElements() && maxlength-- > 0) {
                word = words.nextElement();
                hash = Word.word2hash(word);

                // don't overwrite old values, that leads to too far word distances
                oldpos = map.put(hash, LargeNumberCache.valueOf(pos));
                if (oldpos != null) {
                    map.put(hash, oldpos);
                }

                pos += word.length() + 1;
            }
            return map;
        } finally {
            words.close();
            words = null;
        }
    }

    /**
     * Tokenize the given sentence and generate a word-wordPos mapping
     * @param sentence the sentence to be tokenized
     * @return a ordered map containing word as key and position as value. The map is ordered by words.
     */
    public static SortedMap<String, Integer> tokenizeSentence(final String sentence, int maxlength) {
        final SortedMap<String, Integer> map = new TreeMap<String, Integer>();
        WordTokenizer words = new WordTokenizer(new SentenceReader(sentence), null);
        try {
            int pos = 0;
            String word;
            Integer oldpos;
            while (words.hasMoreElements() && maxlength-- > 0) {
                word = words.nextElement().toString().toLowerCase(Locale.ENGLISH);

                // don't overwrite old values, that leads to too far word distances
                oldpos = map.put(word, LargeNumberCache.valueOf(pos));
                if (oldpos != null) {
                    map.put(word, oldpos);
                }

                pos += word.length() + 1;
            }
            return map;
        } finally {
            words.close();
            words = null;
        }
    }
}
