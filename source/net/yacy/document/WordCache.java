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

package net.yacy.document;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.storage.OrderedScoreMap;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;

/**
 * provide a completion library for the did-you-mean class
 *
 */
public class WordCache {
    
    // common word cache
    private static final int commonWordsMaxSize = 100000; // maximum size of common word cache
    private static final int commonWordsMinLength = 5;    // words must have that length at minimum
    private OrderedScoreMap<String> commonWords = new OrderedScoreMap<String>(String.CASE_INSENSITIVE_ORDER);    
    
    // dictionaries
    private final File dictionaryPath;
    private TreeSet<String> dict; // the word dictionary
    private TreeSet<String> tcid; // the dictionary of reverse words

    /**
     * create a new dictionary
     * This loads all files that ends with '.words'
     * The files must have one word per line
     * Comment lines may be given and are encoded as line starting with '#'
     * @param dictionaryPath path to a directory with library files
     */
    public WordCache(final File dictionaryPath) {
        this.dictionaryPath = dictionaryPath;
        reload();
    }
    
    /**
     * add a word to the generic dictionary
     * @param word
     */
    public void learn(String word) {
        if (word == null) return;
        if (word.length() < commonWordsMinLength) return;
        if (MemoryControl.shortStatus()) commonWords.clear();
        commonWords.inc(word);
        if (!(commonWords.sizeSmaller(commonWordsMaxSize))) {
            commonWords.shrinkToMaxSize(commonWordsMaxSize / 2);
        }
    }
    
    /**
     * scan the input directory and load all dictionaries (again)
     */
    public void reload() {
        this.dict = new TreeSet<String>();
        this.tcid = new TreeSet<String>();
        if (dictionaryPath == null || !dictionaryPath.exists()) return;
        final String[] files = dictionaryPath.list();
        for (final String f: files) {
            if (f.endsWith(".words")) try {
                inputStream(new File(dictionaryPath, f));
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }
    
    private void inputStream(final File file) throws IOException {
    	InputStream is = new FileInputStream(file);
    	if (file.getName().endsWith(".gz")) {
            is = new GZIPInputStream(is);
        }
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String l;
        try {
            while ((l = reader.readLine()) != null) {
                if (l.length() == 0 || l.charAt(0) == '#') continue;
                l = l.trim().toLowerCase();
                this.dict.add(l);
                this.tcid.add(reverse(l));
            }
        } catch (IOException e) {
            // finish
        }
    }
    
    private static String reverse(final String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
    
    /**
     * read the dictionary and construct a set of recommendations to a given string
     * @param s input value that is used to match recommendations
     * @return set that contains all words that start or end with the input value
     */
    public Set<String> recommend(final String s) {
        final Set<String> ret = new HashSet<String>();
        String string = s.trim().toLowerCase();
        SortedSet<String> t = this.dict.tailSet(string);
        for (final String r: t) {
            if (r.startsWith(string) && r.length() > string.length()) ret.add(r); else break;
        }
        SortedMap<String, AtomicInteger> u = this.commonWords.tailMap(string);
        String vv;
        try {
            for (final Map.Entry<String, AtomicInteger> v: u.entrySet()) {
                vv = v.getKey();
                if (vv.startsWith(string) && vv.length() > string.length()) ret.add(vv); else break;
            }
        } catch (ConcurrentModificationException e) {}
        string = reverse(string);
        t = this.tcid.tailSet(string);
        for (final String r: t) {
            if (r.startsWith(string) && r.length() > string.length()) ret.add(reverse(r)); else break;
        }
        return ret;
    }
    
    /**
     * check if the library contains the given word
     * @param s the given word
     * @return true if the library contains the word
     */
    public boolean contains(final String s) {
        return this.dict.contains(s.trim().toLowerCase());
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
    public boolean supports(final String s) {
        String string = s.trim().toLowerCase();
        SortedSet<String> t = this.dict.tailSet(string);
        for (final String r: t) {
            if (string.startsWith(r)) return true; else break;
        }
        string = reverse(string);
        t = this.tcid.tailSet(string);
        for (final String r: t) {
            if (string.startsWith(r)) return true; else break;
        }
        return false;
    }
    
    /**
     * the size of the dictionay
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
    
}
