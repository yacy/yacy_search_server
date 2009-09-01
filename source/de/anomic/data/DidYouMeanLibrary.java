// DidYouMeanLibrary.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 01.10.2009 on http://yacy.net
//
// This is a part of YaCy
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * provide a completion library for the did-you-mean class
 *
 */
public class DidYouMeanLibrary {

    private File dictionaryPath;
    private TreeSet<String> dict, tcid;
    
    /**
     * create a new dictionary
     * This loads all files that ends with '.words'
     * The files must have one word per line
     * Comment lines may be given and are encoded as line starting with '#'
     * @param dictionaryPath a path to a directory with library files
     */
    public DidYouMeanLibrary(File dictionaryPath) {
        this.dictionaryPath = dictionaryPath;
        reload();
    }
    
    /**
     * scan the input directory and load all dictionaries (again)
     */
    public void reload() {
        this.dict = new TreeSet<String>();
        this.tcid = new TreeSet<String>();
        String[] files = dictionaryPath.list();
        for (String f: files) {
            if (f.endsWith(".words")) try {
                importFile(new File(dictionaryPath, f));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void importFile(File f) throws FileNotFoundException {
        BufferedReader r = new BufferedReader(new FileReader(f));
        String l;
        try {
            while ((l = r.readLine()) != null) {
                if (l.length() == 0 || l.charAt(0) == '#') continue;
                l = l.trim().toLowerCase();
                this.dict.add(l);
                this.tcid.add(reverse(l));
            }
        } catch (IOException e) {
            // finish
        }
    }
    
    private static String reverse(String s) {
        StringBuilder r = new StringBuilder(s.length());
        for (int i = s.length() - 1; i >= 0; i--) r.append(s.charAt(i));
        return r.toString();
    }
    
    /**
     * read the dictionary and construct a set of recommendations to a given string 
     * @param s input value that is used to match recommendations
     * @return a set that contains all words that start or end with the input value
     */
    public Set<String> recommend(String s) {
        Set<String> a = new HashSet<String>();
        s = s.trim().toLowerCase();
        SortedSet<String> t = this.dict.tailSet(s);
        for (String r: t) {
            if (s.startsWith(r)) a.add(r); else break;
        }
        s = reverse(s);
        t = this.tcid.tailSet(s);
        for (String r: t) {
            if (s.startsWith(r)) a.add(reverse(r)); else break;
        }
        return a;
    }
    
    /**
     * check if the library contains the given word
     * @param s the given word
     * @return true if the library contains the word
     */
    public boolean contains(String s) {
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
    public boolean supports(String s) {
        s = s.trim().toLowerCase();
        SortedSet<String> t = this.dict.tailSet(s);
        for (String r: t) {
            if (s.startsWith(r)) return true; else break;
        }
        s = reverse(s);
        t = this.tcid.tailSet(s);
        for (String r: t) {
            if (s.startsWith(r)) return true; else break;
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
    public boolean isRelevant(int minimumWords) {
        return this.dict.size() >= minimumWords;
    }
    
}
