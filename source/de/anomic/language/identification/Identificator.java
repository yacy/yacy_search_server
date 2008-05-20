// Identificator.java
// -----------------------
// (C) by Marc Nause; marc.nause@audioattack.de
// first published on http://www.yacy.net
// Braunschweig, Germany, 2008
//
// $LastChangedDate: 2008-05-18 23:00:00 +0200 (Di, 18 Mai 2008) $
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

package de.anomic.language.identification;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * This class can try to identify the language a text is written in.
 * It has been implemented as a Singleton since it has to access several
 * files on instanciation which should be avoided since it is very slow.
 */
public final class Identificator {

    private List<LanguageStatistics> languages = new Vector<LanguageStatistics>();
    private String languageDir = "langstats";  // directory that contains language files
    
    private static Identificator instance;
    
    private Identificator() {
        addAllLanguagesInDirectory(languageDir);
    }
    
    /**
     * method to get an instance of this class, should be used insted of <b>new Identificator()</b>
     * @return an instance of the class Identificator
     */
    public synchronized static Identificator getInstance() {
        if (instance == null) {
            instance = new Identificator();
        }
        return instance;
    }
    
    /**
     * This method tries to return the language a text is written in. The method will only
     * use the first 100000 characters of the text which should be enough. Using more
     * characters probably only slows down the process without gaining much accuracy.
     * @param text the text that is to be analyzed
     * @return the language or "unknown" if the method was not able to find out the language
     */
    public String getLanguage(String text) {
        // only test the first 100000 characters of a text
        return getLanguage(text, 100000);
    }
    
    /**
     * This method tries to return the language a text is written in. The method will
     * use the number characters defined in the parameter limit.
     * @param text the text that is to be analyzed
     * @param limit the number of characters that are supposed to be considered
     * @return the language or "unknown" if the method was not able to find out the language
     */
    public String getLanguage(String text, int limit) {
        
        String ret = null;
        
        LanguageStatistics testStat = new LanguageStatistics("text");
        char[] letter = new char[1];
        float letters = 0;
        int upperLimit = text.length();
        if (upperLimit > limit) {
            upperLimit = limit;
        }

        // count number of characters in text
        for (int i = 0; i < upperLimit; i++) {
            text.getChars(i, i + 1, letter, 0);
            // only count if character is a letter
            if ((letter[0]+"").matches("\\p{L}")) {
                letter[0] = Character.toLowerCase(letter[0]);
                testStat.put(letter[0], testStat.get(letter[0]) + 1);
                letters++;
            }
        }
        
        // calculate percentage
        Iterator<Character> iter = testStat.keySet().iterator();
        Character character;
        Character maxChar = null;
        float value = 0;
        float max = 0;
        while (iter.hasNext()) {
            character = (Character) iter.next();
            value = testStat.get(character);
            if (value > max) {
                maxChar = character;
                max = value;
            }
            testStat.put(character, (value / letters) * 100);
        }

        // create list with relevant languages
        List<Integer> relevantLanguages = new Vector <Integer>();
        for (int i = 0; i < languages.size(); i++) {
            
            // only languages that contain the most common character in the text will be tested
            if (languages.get(i).contains(maxChar)) {
                relevantLanguages.add(i);
            }
        }
        
        if (relevantLanguages.size() > 0) {
           
            // compare characters in text with characters in statistics
            float[] offsetList = new float[relevantLanguages.size()];
            int[] votesList = new int[relevantLanguages.size()];

            iter = testStat.keySet().iterator();
            float minimum;
            float offset = 0;
            float valueCharacter;
            int bestLanguage = -1;

            while (iter.hasNext()) {
                minimum = 100.1f;
                character = (Character) iter.next();
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
                ret = languages.get(relevantLanguages.get(bestLanguage)).getName();
            }

        }

        return ret;
        
    }
    
    /**
     * Reads all language files from a directory.
     * @param directory the directory that contains the language files
     */
    private void addAllLanguagesInDirectory(String directory) {
        
        File folder = new File(directory);
        FilenameFilter filter = new LanguageFilenameFilter();
        File[] allLanguageFiles = folder.listFiles(filter);
        
        for (int i = 0; i < allLanguageFiles.length; i++) {
            if(allLanguageFiles[i].isFile()) {
                languages.add(new LanguageStatistics(allLanguageFiles[i]));
            }
        }
    }
    
    
}
