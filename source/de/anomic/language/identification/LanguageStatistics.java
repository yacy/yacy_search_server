// LanguageStatistics.java
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

import de.anomic.server.logging.serverLog;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class can store statistical data of a language.
 */
public class LanguageStatistics {
    
    private static serverLog logger = new serverLog("LANGUAGESTATISTICS");

    /** This variable holds the name of the language. */
    private String langName = null;

    /** This map holds the character statistics of the language. */
    private Map<Character, Float> stats = new HashMap<Character, Float>();

    LanguageStatistics(File file) {
        loadStatisticsFromFile(file);
    }

    /**
     * This class provides means to store statistics about how often
     * a letter occurs in a text in a language.
     * @param name name of the language
     */
    LanguageStatistics(final String name) {
        this.langName = name;
    }

    /**
     * This class provides means to store statistics about how often
     * a letter occurs in a text in a language.
     * @param name name of the language
     * @param statistics statistics about occurence of characters
     */
    LanguageStatistics(final String name, final Map<Character, Float> statistics) {
        this.langName = name;
        this.stats = statistics;
    }

    /**
     * This method can be used to add a character and its number
     * of average occuences in text in a language in percent.
     * @param letter the letter
     * @param percent percentage of occurence
     */
    public final void put(final char letter, final float percent) {
        stats.put(letter, percent);
    }

    /**
     * Gets the percantage of occurences of a letter in an average
     * text in a language in percent.
     * @param letter the letter
     * @return the percentage
     */
    public final float get(final char letter) {
        if (stats.containsKey(letter)) {
           return stats.get(letter);
        } else {
            return 0;
        }
    }

    /**
     * This method allows to add the statistics a whole which might
     * be more convenient than adding them character by cahracter.
     * @param statistics the statistics
     */
    public final void setStatistics(final Map<Character, Float> statistics) {
        this.stats = statistics;
    }
    
    public final boolean loadStatisticsFromFile(File file) {
        boolean ret = true;
        BufferedReader reader = null;
        String line;
        String splitLine[] = new String[2];
        try {
            reader = new BufferedReader(new FileReader(file));
            while(reader.ready()) {
                line = reader.readLine().trim();
                if (line.matches("^\\p{L}\\p{Z}+\\p{N}*\\p{P}{0,1}\\p{N}+$")) {
                    splitLine = line.split("\\p{Z}+");
                    this.put(splitLine[0].charAt(0), Float.parseFloat(splitLine[1]));
                }
            }
            
            if (!stats.isEmpty() && langName == null) {
                langName = file.getName().toLowerCase();
                langName = langName.substring(0, langName.lastIndexOf("."));
            }
        
        } catch (FileNotFoundException ex) {
            ret = false;
            logger.logWarning("ERROR: file '" + file.getName() + "' not found", ex);
        } catch (IOException ex) {
            logger.logWarning("ERROR: problems reading file '" + file.getName() + "'", ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                logger.logWarning("ERROR: IO trouble ", ex);
            }
        }
        return ret;
    }
    
    /**
     * This method tells if a language contains a character or not
     * @param character the character in question
     * @return true if language contains character, else false
     */
    public boolean contains(Character character) {
        if (stats.containsKey(character)) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * This method is needed to crteate an iterator over a language
     * @return all characters of the language
     */
    public Set<Character> keySet() {
        return stats.keySet();
    }
    
    /**
     * This method tells the name of the language.
     * @return the name of the language
     */
    public String getName() {
        return langName;
    }

}
