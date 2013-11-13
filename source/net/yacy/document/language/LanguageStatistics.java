// LanguageStatistics.java
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.util.ConcurrentLog;


/**
 * This class can store statistical data of a language.
 */
public class LanguageStatistics {
    
    private final static ConcurrentLog logger = new ConcurrentLog("LANGUAGESTATISTICS");

    /** This variable holds the name of the language. */
    private String langName = null;

    /** This map holds the character statistics of the language. */
    private Map<Character, Float> stats = new HashMap<Character, Float>();

    LanguageStatistics(final File file) {
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
        Float f = stats.get(letter);
        if (f != null) {
            return f.floatValue();
        }
        return 0;
    }

    /**
     * This method allows to add the statistics a whole which might
     * be more convenient than adding them character by cahracter.
     * @param statistics the statistics
     */
    public final void setStatistics(final Map<Character, Float> statistics) {
        this.stats = statistics;
    }
    
    public final boolean loadStatisticsFromFile(final File file) {
        boolean ret = true;
        BufferedReader reader = null;
        String line;
        String splitLine[];
        try {
            reader = new BufferedReader(new FileReader(file));
            while(reader.ready()) {
                line = reader.readLine();
                if(line == null) {
                	// end of file
                	break;
                }
                line = line.trim();
                if (line.matches("^\\p{L}\\p{Z}+\\p{N}*\\p{P}{0,1}\\p{N}+$")) {
                    splitLine = line.split("\\p{Z}+");
                    this.put(splitLine[0].charAt(0), Float.parseFloat(splitLine[1]));
                }
            }
            
            if (!stats.isEmpty() && langName == null) {
                langName = file.getName().toLowerCase();
                langName = langName.substring(0, langName.lastIndexOf("."));
            }
        
        } catch (final FileNotFoundException ex) {
            ret = false;
            logger.warn("ERROR: file '" + file.getName() + "' not found", ex);
        } catch (final IOException ex) {
            logger.warn("ERROR: problems reading file '" + file.getName() + "'", ex);
        } finally {
            try { if(reader != null) {
                reader.close();
            }
            } catch (final IOException ex) {
                logger.warn("ERROR: IO trouble ", ex);
            }
        }
        return ret;
    }
    
    /**
     * This method tells if a language contains a character or not
     * @param character the character in question
     * @return true if language contains character, else false
     */
    public boolean contains(final Character character) {
        if (stats.containsKey(character)) {
            return true;
        }
        return false;
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
