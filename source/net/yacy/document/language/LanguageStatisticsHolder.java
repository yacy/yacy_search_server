// LanguageStatisticsHolder.java
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

import java.io.File;
import java.io.FilenameFilter;
import java.util.Vector;

import net.yacy.cora.util.ConcurrentLog;


/**
 * This class loads and provides several language statistics to the system.
 * It has been implemented as a Singleton since it has to access several
 * files on instanciation which should be avoided since it is very slow.
 */
public final class LanguageStatisticsHolder extends Vector<LanguageStatistics> {

    private static final long serialVersionUID = -887517724227204705L;

    private static final String languageDir = "langstats";  // directory that contains language files    
    
    private static LanguageStatisticsHolder instance;
    
    private LanguageStatisticsHolder() {
        addAllLanguagesInDirectory(languageDir);
    }
    
    /**
     * method to get an instance of this class, should be used instead of
     * <b>new LanguageStatisticsHolder()</b>
     * @return an instance of the class Identificator
     */
    public synchronized static LanguageStatisticsHolder getInstance() {
        if (instance == null) {
            instance = new LanguageStatisticsHolder();
        }
        return instance;
    }    
  
    /**
     * Reads all language files from a directory.
     * @param directory the directory that contains the language files
     */
    private void addAllLanguagesInDirectory(final String directory) {
        
        final File folder = new File(directory);
        if (!folder.exists()) {
            ConcurrentLog.warn("LanguageStatistics", "the language statistics folder " + directory + " cannot be found");
            return;
        }
        final FilenameFilter filter = new LanguageFilenameFilter();
        final File[] allLanguageFiles = folder.listFiles(filter);
        
        if (allLanguageFiles != null) {
            for (int i = 0; i < allLanguageFiles.length; i++) {
                if(allLanguageFiles[i].isFile()) {
                    this.add(new LanguageStatistics(allLanguageFiles[i]));
                }
            }
        }
    }    
    
}
