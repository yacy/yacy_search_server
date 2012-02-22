// Identificator.java
// -----------------------
// (C) by Marc Nause; marc.nause@audioattack.de
// first published on http://www.yacy.net
// Braunschweig, Germany, 2008
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.language;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class can try to identify the language a text is written in.
 */
public final class Identificator {

    private static final LanguageStatisticsHolder languages = LanguageStatisticsHolder.getInstance();

    private final Map<Character, AtomicInteger> letter;
    private int letters;
    private String language;

    public Identificator() {
        this.letter = new HashMap<Character, AtomicInteger>();
        this.letters = 0;
        this.language = null;
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

        final Identificator id = new Identificator();

        // count number of characters in text
        for (int i = 0; i < upperLimit; i++) id.inc(text.charAt(i));

        return id.getLanguage();
    }

    public void inc(final char c) {
        if (!Character.isLetter(c)) return;
        final Character cc = Character.toLowerCase(c);
        final AtomicInteger i = this.letter.get(cc);
        if (i == null) {
            this.letter.put(cc, new AtomicInteger(1));
        } else {
            i.incrementAndGet();
        }
        this.letters++;
    }

    public void add(final String word) {
        if (word == null) return;
        for (int i = 0; i < word.length(); i++) inc(word.charAt(i));
    }

    //modified by copperdust; Ukraine, 2012
    public String getLanguage() {

    	if (this.language != null) return this.language; // don't compute that twice
        if (this.letters == 0) return null; // not enough information available

        final LanguageStatistics testStat = new LanguageStatistics("text");

        // calculate percentage
        Character character;
        Character maxChar = null;
        int count = 0;
        int max = 0;
        for (final Map.Entry<Character, AtomicInteger> e: this.letter.entrySet()) {
            character = e.getKey();
            count = e.getValue().intValue();
            if (count > max) {
                maxChar = character;
                max = count;
            }
            testStat.put(character, ((float) 100) * ((float) count) / (this.letters));
        }

        // create list with relevant languages
        final List<Integer> relevantLanguages = new Vector <Integer>();
        for (int i = 0; i < languages.size(); i++) {
            // only languages that contain the most common character in the text will be tested
            if (languages.get(i).contains(maxChar)) {
                relevantLanguages.add(i);
            }
        }

        if (relevantLanguages.isEmpty()) return null;

        // compare characters in text with characters in statistics
        final float[] offsetList = new float[relevantLanguages.size()];
        final float[] sumList = new float[relevantLanguages.size()];

        final Iterator<Character> iter = testStat.keySet().iterator();
        float offset = 0;
        float valueCharacter;
        float value;

        while (iter.hasNext()) {
            character = iter.next();
            valueCharacter = testStat.get(character);
            for (int i = 0; i < relevantLanguages.size(); i++) {
                value = languages.get(relevantLanguages.get(i)).get(character);
                if (value > 0) {
                	offset = Math.abs(value - valueCharacter);
                	offsetList[i] = offsetList[i] + offset;
                	sumList[i] = sumList[i] + value;// accumulation processed characters
                	// normally must be 100 after loop for language written in
                }
            }
        }
        //50/50
        //abs(50-40) + abs(50-10) = 10 + 40 = 50 -- 50 = 0 [60 must be]
        //abs(50-25) + abs(50-25) = 25 + 25 = 50 -- 50 = 0 [0 must be]
        
        //75/25
        //abs(50-60) + abs(50-15) = 10 + 35 = 45 -- 25 = 20 [60 must be]
        //abs(50-12,5) + abs(50-12,5) = 37,5 + 37,5 = 75 -- 75 = 0 [0 must be]
        
        //25/75
        //abs(50-20) + abs(50-5) = 30 + 45 = 75 -- 75 = 0 [60 must be]
        //abs(50-37,5) + abs(50-37,5) = 12,5 + 12,5 = 25 -- 25 = 0 [0 must be]

        // Now we can count how closer each language to current pattern.
        float minOffset = 100.1f;
        int bestLanguage = -1;
        for (int i = 0; i < sumList.length; i++) {
            offset = offsetList[i] + 100 - sumList[i];// actual difference
            if (offset < minOffset) {
                minOffset = offset;
                bestLanguage = i;
            }
        }

        // Return name of language only if offset is smaller than 30%.
        // Prevents wrong language detection due to actual language not in langstats.
        if (minOffset < 30) {
            this.language = languages.get(relevantLanguages.get(bestLanguage)).getName();
            return this.language;
        }

        return null;

    }

}
