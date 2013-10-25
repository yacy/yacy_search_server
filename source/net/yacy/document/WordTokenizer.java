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
        loop: while (this.e.hasMoreElements()) {
            s = this.e.nextElement();
            if ((s.length() == 1) && (SentenceReader.punctuation(s.charAt(0)))) return s;
            for (int i = 0; i < s.length(); i++) {
                if (SentenceReader.invisible(s.charAt(i))) continue loop;
            }
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
        if (this.meaningLib != null) WordCache.learn(r);
        return r;
    }

    public synchronized void close() {
    	this.e.close();
    	this.e = null;
    	this.buffer = null;
    }

    private static class unsievedWordsEnum implements Enumeration<StringBuilder> {
        // returns an enumeration of StringBuilder Objects
        private StringBuilder buffer = null;
        private SentenceReader sr;
        private List<StringBuilder> s;
        private int sIndex;

        public unsievedWordsEnum(final SentenceReader sr0) {
            assert sr0 != null;
            this.sr = sr0;
            this.s = new ArrayList<StringBuilder>();
            this.sIndex = 0;
            this.buffer = nextElement0();
        }

        public void pre(final boolean x) {
            this.sr.pre(x);
        }

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
                r = this.sr.next();
                if (r == null) return null;
                r = trim(r);
                sb = new StringBuilder(20);
                for (int i = 0; i < r.length(); i++) {
                    c = r.charAt(i);
                    if (SentenceReader.invisible(c)) {
                        if (sb.length() > 0) {this.s.add(sb); sb = new StringBuilder(20);}
                    } else if (SentenceReader.punctuation(c)) {
                        if (sb.length() > 0) {this.s.add(sb); sb = new StringBuilder(1);}
                        sb.append(c);
                        this.s.add(sb);
                        sb = new StringBuilder(20);
                    } else {
                        sb = sb.append(c);
                    }
                }
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
    public static SortedMap<byte[], Integer> hashSentence(final String sentence, final WordCache meaningLib, int maxlength) {
        final SortedMap<byte[], Integer> map = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
        WordTokenizer words = new WordTokenizer(new SentenceReader(sentence), meaningLib);
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
}
