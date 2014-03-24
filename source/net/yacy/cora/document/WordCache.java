/**
 *  WordCache
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 01.10.2009 on http://yacy.net
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

package net.yacy.cora.document;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.sorting.OrderedScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.StringBuilderComparator;

/**
 * provide a completion library for the did-you-mean class
 *
 */
public class WordCache {

    private final static ConcurrentLog log = new ConcurrentLog(WordCache.class.getName());
    
    // common word cache
    private static final int commonWordsMaxSize = 20000;  // maximum size of common word cache
    private static final int commonWordsMinLength = 5;    // words must have that length at minimum
    private static OrderedScoreMap<StringBuilder> commonWords = new OrderedScoreMap<StringBuilder>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);

    // dictionaries
    private final File dictionaryPath;
    final Map<String, Dictionary> dictionaries;

    public static class Dictionary {

        private final TreeSet<StringBuilder> dict; // the word dictionary
        private final TreeSet<StringBuilder> tcid; // the dictionary of reverse words

        public Dictionary(final File file) throws IOException {

            this.dict = new TreeSet<StringBuilder>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);
            this.tcid = new TreeSet<StringBuilder>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);

            InputStream is = new FileInputStream(file);
            if (file.getName().endsWith(".gz")) {
                is = new GZIPInputStream(is);
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String l;
            StringBuilder sb;
            try {
                while ((l = reader.readLine()) != null) {
                    if (l.isEmpty() || l.charAt(0) == '#') {
                        continue;
                    }
                    l = l.trim().toLowerCase();
                    if (l.length() < 4) {
                        continue;
                    }
                    sb = new StringBuilder(l);
                    this.dict.add(sb);
                    this.tcid.add(reverse(sb));
                }
            } catch (final IOException e) {
                // finish
            } finally {
                reader.close();
            }
        }

        /**
         * read the dictionary and construct a set of recommendations to a given string
         * @param s input value that is used to match recommendations
         * @return set that contains all words that start or end with the input value
         */
        public Set<StringBuilder> recommend(StringBuilder string) {
            final Set<StringBuilder> ret = new HashSet<StringBuilder>();
            SortedSet<StringBuilder> t = this.dict.tailSet(string);
            for (final StringBuilder r: t) {
                if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(r, string) && r.length() > string.length()) {
                    ret.add(r);
                } else {
                    break;
                }
            }
            string = reverse(string);
            t = this.tcid.tailSet(string);
            for (final StringBuilder r: t) {
                if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(r, string) && r.length() > string.length()) {
                    ret.add(reverse(r));
                } else {
                    break;
                }
            }
            return ret;
        }

        /**
         * check if the library contains the given word
         * @param s the given word
         * @return true if the library contains the word
         */
        public boolean contains(final StringBuilder s) {
            return this.dict.contains(s);
            // if the above case is true then it is also true for this.tcid and vice versa
            // that means it does not need to be tested as well
        }

        /**
         * check if the library supports the given word
         * A word is supported, if the library contains a word
         * that starts or ends with the given word
         * @param s the given word
         * @return true if the library supports the word
         */
        public boolean supports(StringBuilder string) {
            SortedSet<StringBuilder> t = this.dict.tailSet(string);
            for (final StringBuilder r: t) {
                if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(string, r)) {
                    return true;
                }
                break;
            }
            string = reverse(string);
            t = this.tcid.tailSet(string);
            for (final StringBuilder r: t) {
                if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(string, r)) {
                    return true;
                }
                break;
            }
            return false;
        }

        /**
         * the size of the dictionary
         * @return the number of words in the dictionary
         */
        public int size() {
            return this.dict.size();
        }


        /**
         * a property that is used during the construction of recommendation:
         * if the dictionary is too small, then the non-existence of constructed words
         * is not relevant for the construction of artificially constructed words
         * If this property returns true, all other words must be in the dictionary
         * @param minimumWords
         * @return
         */
        public boolean isRelevant(final int minimumWords) {
            return this.dict.size() >= minimumWords;
        }

        public Set<StringBuilder> getWords() {
            return this.dict;
        }

    }

    public Map<String, Dictionary> getDictionaries() {
        return this.dictionaries;
    }

    /**
     * create a new dictionary
     * This loads all files that ends with '.words'
     * The files must have one word per line
     * Comment lines may be given and are encoded as line starting with '#'
     * @param dictionaryPath path to a directory with library files
     */
    public WordCache(final File dictionaryPath) {
        this.dictionaryPath = dictionaryPath;
        this.dictionaries = new ConcurrentHashMap<String, Dictionary>();
        reload();
    }

    /**
     * add a word to the generic dictionary
     * @param word
     */
    public static void learn(final StringBuilder word) {
        if (word == null) {
            return;
        }
        if (word.length() < commonWordsMinLength) {
            return;
        }
        commonWords.inc(word);
        if (!(commonWords.sizeSmaller(commonWordsMaxSize))) {
            commonWords.shrinkToMaxSize(commonWordsMaxSize / 2);
        }
    }

    public static void learn(Collection<String> wordset) {
        for (String s: wordset) {
            learn(new StringBuilder(s));
        }
    }

    /**
     * scan the input directory and load all dictionaries (again)
     */
    public void reload() {
        if (this.dictionaryPath == null || !this.dictionaryPath.exists()) {
            return;
        }
        final String[] files = this.dictionaryPath.list();
        for (final String f: files) {
            if (f.endsWith(".words")) {
                try {
                    Dictionary dict = new Dictionary(new File(this.dictionaryPath, f));
                    this.dictionaries.put(f.substring(0, f.length() - 6), dict);
                } catch (final IOException e) {
                    log.warn(e);
                }
            }
        }
    }


    private static StringBuilder reverse(final StringBuilder s) {
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
        }
        return sb;
    }

    /**
     * read the dictionary and construct a set of recommendations to a given string
     * @param s input value that is used to match recommendations
     * @return set that contains all words that start or end with the input value
     */
    public Set<StringBuilder> recommend(StringBuilder string) {
        Set<StringBuilder> ret = new HashSet<StringBuilder>();
        for (Dictionary dict: this.dictionaries.values()) {
            ret.addAll(dict.recommend(string));
        }
        final SortedMap<StringBuilder, AtomicInteger> u = commonWords.tailMap(string);
        StringBuilder vv;
        try {
            for (final Map.Entry<StringBuilder, AtomicInteger> v: u.entrySet()) {
                vv = v.getKey();
                if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(vv, string) && vv.length() > string.length()) {
                    ret.add(vv);
                } else {
                    break;
                }
            }
        } catch (final ConcurrentModificationException e) {
            ConcurrentLog.logException(e);
        }
        return ret;
    }

    /**
     * check if the library contains the given word
     * @param s the given word
     * @return true if the library contains the word
     */
    public boolean contains(final StringBuilder s) {
        for (Dictionary dict: this.dictionaries.values()) {
            if (dict.contains(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * check if the library supports the given word
     * A word is supported, if the library contains a word
     * that starts or ends with the given word
     * @param s the given word
     * @return true if the library supports the word
     */
    public boolean supports(StringBuilder string) {
        for (Dictionary dict: this.dictionaries.values()) {
            if (dict.supports(string)) {
                return true;
            }
        }
        return false;
    }

    /**
     * the size of the dictionary
     * @return the number of words in the dictionary
     */
    public int size() {
        int size = 0;
        for (Dictionary dict: this.dictionaries.values()) {
            size += dict.size();
        }
        return size;
    }

    public static int sizeCommonWords() {
        return commonWords.size();
    }
    
    public static void clearCommonWords() {
        commonWords.clear();
    }

    /**
     * a property that is used during the construction of recommendation:
     * if the dictionary is too small, then the non-existence of constructed words
     * is not relevant for the construction of artificially constructed words
     * If this property returns true, all other words must be in the dictionary
     * @param minimumWords
     * @return
     */
    public boolean isRelevant(final int minimumWords) {
        for (Dictionary dict: this.dictionaries.values()) {
            if (dict.isRelevant(minimumWords)) {
                return true;
            }
        }
        return false;
    }

}
