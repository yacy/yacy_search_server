// Identificator.java
// -----------------------
// (C) by Marc Nause; marc.nause@audioattack.de
// first published on http://www.yacy.net
// Braunschweig, Germany, 2008
//
// $LastChangedDate: 2008-05-23 23:00:00 +0200 (Fr, 23 Mai 2008) $
// $LastChangedRevision: 4824 $
// $LastChangedBy: low012 $
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

package de.anomic.language.identification;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * This class can try to identify the language a text is written in.
 */
public final class Identificator {

    private static final LanguageStatisticsHolder languages = LanguageStatisticsHolder.getInstance();
    
    private HashMap<Character, Integer> letter;
    private int letters;
    private String language;
    
    public Identificator() {
        letter = new HashMap<Character, Integer>();
        letters = 0;
        language = null;
    }

    /**
     * This method tries to return the language a text is written in. The method will only
     * use the first 100000 characters of the text which should be enough. Using more
     * characters probably only slows down the process without gaining much accuracy.
     * @param text the text that is to be analyzed
     * @return the language or "unknown" if the method was not able to find out the language
     */
    public static String getLanguage(final String text) {
        // only test the first 100000 characters of a text
        return getLanguage(text, 100000);
    }
    
    /**
     * This method tries to return the language a text is written in. The method will
     * use the number characters defined in the parameter limit.
     * @param text the text that is to be analyzed
     * @param limit the number of characters that are supposed to be considered
     * @return the language or null if the method was not able to find out the language
     */
    public static String getLanguage(final String text, final int limit) {
        
        int upperLimit = text.length();
        if (upperLimit > limit) {
            upperLimit = limit;
        }

        Identificator id = new Identificator();
        
        // count number of characters in text
        for (int i = 0; i < upperLimit; i++) id.inc(text.charAt(i));
        
        return id.getLanguage();
    }
    
    public void inc(final char c) {
        if (!Character.isLetter(c)) return;
        Character cc = Character.toLowerCase(c);
        Integer i = letter.get(cc);
        if (i == null) {
            letter.put(cc, 1);
        } else {
            letter.put(cc, i.intValue() + 1);
        }
        letters++;
    }
    
    public void add(String word) {
        if (word == null) return;
        for (int i = 0; i < word.length(); i++) inc(word.charAt(i));
    }
    
    public String getLanguage() {
        
        if (language != null) return language; // don't compute that twice
        if (letters == 0) return null; // not enough information available
        
        final LanguageStatistics testStat = new LanguageStatistics("text");
        
        // calculate percentage
        Character character;
        Character maxChar = null;
        int count = 0;
        int max = 0;
        for (Map.Entry<Character, Integer> e: letter.entrySet()) {
            character = e.getKey();
            count = e.getValue().intValue();
            if (count > max) {
                maxChar = character;
                max = count;
            }
            testStat.put(character, ((float) 100) * ((float) count) / ((float) letters));
        }
        
        // create list with relevant languages
        final List<Integer> relevantLanguages = new Vector <Integer>();
        for (int i = 0; i < languages.size(); i++) {
            
            // only languages that contain the most common character in the text will be tested
            if (languages.get(i).contains(maxChar)) {
                relevantLanguages.add(i);
            }
        }
        
        if (relevantLanguages.size() == 0) return null;
           
        // compare characters in text with characters in statistics
        final float[] offsetList = new float[relevantLanguages.size()];
        final int[] votesList = new int[relevantLanguages.size()];

        Iterator<Character> iter = testStat.keySet().iterator();
        float minimum;
        float offset = 0;
        float valueCharacter;
        int bestLanguage = -1;
        float value;
        
        while (iter.hasNext()) {
            minimum = 100.1f;
            character = iter.next();
            valueCharacter = testStat.get(character);
            for (int i = 0; i < relevantLanguages.size(); i++) {
                value = languages.get(relevantLanguages.get(i)).get(character);
                offset = Math.abs(value - valueCharacter);
                offsetList[i] = offsetList[i] + offset;
                if (offset < minimum) {
                    minimum = offset;
                    bestLanguage = i;
                }
            }
            votesList[bestLanguage] = ++votesList[bestLanguage];
        }
   
        // Now we can count how many votes each language got and how far it was away from the stats.
        // If 2 languages have the same amount of votes, the one with the smaller offset wins.
        int maxVotes = 0;
        float minOffset = 100.1f;
        for (int i = 0; i < votesList.length; i++) {
            if ((votesList[i] == maxVotes && offsetList[i] < minOffset) || (votesList[i] > maxVotes)) {
                maxVotes = votesList[i];
                minOffset = offsetList[i];
                bestLanguage = i;
            }
        }
        
        // Only return name of language of offset is smaller than 20%. This 
        // prevents a language beeing reported that has won the voting, but
        // is still not the right language.
        if (offset < 20) {
            language = languages.get(relevantLanguages.get(bestLanguage)).getName();
            return language;
        }
        
        return null;
        
    }
    
}
