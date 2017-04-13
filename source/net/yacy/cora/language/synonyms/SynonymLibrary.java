/**
 *  Stemming
 *  Copyright 2012 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 01.10.2012 on http://yacy.net
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

package net.yacy.cora.language.synonyms;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import net.yacy.cora.storage.Files;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;

/**
 * Stemming library: reads stemming files and creates a mapping from words to synonyms
 * Stemming files must have a list of synonym words in each line of the input file.
 * The words within one line must be separated by ','. Lines starting with '#' are
 * comment files and are ignored. Each line can (but does not need to) have a '{'
 * at the beginning of the line and '}' at the end (which would be the GSA format).
 */
public class SynonymLibrary {

    private final static ConcurrentLog log = new ConcurrentLog(SynonymLibrary.class.getName());
    private final static Map<String, List<Set<String>>> lib = new HashMap<String, List<Set<String>>>();

    public static void init(final File path) {
        lib.clear();
        if (!path.exists() || !path.isDirectory()) return;
        final String[] files = path.list();
        /* Global map of all known distinct words : thus enable reuse of the same word String instance
         * appearing multiple times in different synonyms sets */
        final Map<String, String> distinctWords = new HashMap<>();
        for (final String f: files) {
            File ff = new File(path, f);
            String line;
            try {
                BlockingQueue<String> list = Files.concurentLineReader(ff);
                while ((line = list.take()) != Files.POISON_LINE) {
                    line = line.trim();
                    if (line.length() == 0 || line.charAt(0) == '#') continue;
                    if (line.charAt(line.length() - 1) == '}') line = line.substring(0, line.length() - 1);
                    if (line.charAt(0) == '{') line = line.substring(1);
                    String[] words = CommonPattern.COMMA.split(line);
                    Set<String> synonyms = new HashSet<String>(words.length);
                    Set<String> keys = new HashSet<String>(words.length);
                    for (String word: words) {
                        word = word.trim();
                        if (word.length() < 2) continue;
                        String lowCaseWord = word.toLowerCase();
                        String kownWord = distinctWords.get(lowCaseWord);
                        if(kownWord != null) {
                        	/* This word is already known : let's use the existing String instance from the synonyms map to gain memory space */
                        	lowCaseWord = kownWord;
                        } else {
                        	/* First encounter of this word : let's add it to the global map of known words */
                        	distinctWords.put(lowCaseWord, lowCaseWord);
                        }
                        synonyms.add(lowCaseWord);
                        keys.add(lowCaseWord.substring(0, 2));
                    }
                    for (String key: keys) {
                        List<Set<String>> symsetlist = lib.get(key);
                        if (symsetlist == null) {
                            symsetlist = new ArrayList<Set<String>>();
                            lib.put(key, symsetlist);
                        }
                        symsetlist.add(synonyms);
                    }
                }
            } catch (final Throwable e) {
                log.warn("cannot read stemming file " + f, e);
            }
        }
    }
    
    public static int size() {
        return lib.size();
    }
    
    /**
     * for a given word, return a list of synonym words
     * @param word
     * @return a list of synonyms but without the requested word
     */
    public static Set<String> getSynonyms(String word) {
        if (word == null) return null;
        word = word.toLowerCase().trim();
        if (word.length() < 2) return null;
        String key = word.substring(0, 2);
        List<Set<String>> symsetlist = lib.get(key);
        if (symsetlist == null) return null;
        for (Set<String> symset: symsetlist) {
            if (symset.contains(word)) {
                // create a new set containing all but the one word
                Set<String> returnSet = new HashSet<String>();
                for (String synonym: symset) {
                    if (synonym.equals(word)) continue;
                    returnSet.add(synonym);
                }
                return returnSet;
            }
        }
        return null;
    }
    
}
