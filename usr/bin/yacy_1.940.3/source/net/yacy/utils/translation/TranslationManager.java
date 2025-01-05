// TranslationManager.java
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Burkhard Buelte
// last major change: 2016-04-05
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

package net.yacy.utils.translation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.Translator;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

/**
 * Utility to create a translation master file from all existing translation
 * with check file.exists and translation text exists.
 * Also can join existing translation with master (currently ristrictive,
 * means only translation text exist in master are included in resultin Map
 */
public class TranslationManager extends TranslatorXliff {

    protected Map<String, Map<String, String>> mainTransLists; // current translation entries for one language
    protected String loadedLng; // language loaded in mainTransLists (2-letter code)

    public TranslationManager() {
        super();
    }

    public TranslationManager(final File langfile) {
        mainTransLists = loadTranslationsLists(langfile);
        int pos = langfile.getName().indexOf('.');
        if (pos >= 0) {
            loadedLng = langfile.getName().substring(0, pos);
        }
    }

    /**
     * Add a translation text to the current map map
     *
     * @param relFileName relative filename the translation belongs to
     * @param sourceLngTxt the english source text
     * @param targetLngTxt the translated text
     * @return true = if map was modified, otherwise false
     */
    public boolean addTranslation(final String relFileName, final String sourceLngTxt, final String targetLngTxt) {
        assert mainTransLists != null;
        return addTranslation (mainTransLists, relFileName, sourceLngTxt, targetLngTxt);
    }

    /**
     * Helper to add a translation text to the map
     *
     * @param translation to add the text to
     * @param relFileName relative filename the translation belongs to
     * @param sourceLngTxt the english source text
     * @param targetLngTxt the translated text
     * @return true = if map was modified, otherwise false
     */
    public boolean addTranslation(Map<String, Map<String, String>> translation, final String relFileName, final String sourceLngTxt, final String targetLngTxt) {
        boolean modified = false;

        Map<String, String> transFile;
        if (translation.containsKey(relFileName)) {
            transFile = translation.get(relFileName);
        } else {
            transFile = new LinkedHashMap<String, String>();
            translation.put(relFileName, transFile);
            modified = true;
        }

        String oldLngTxt = transFile.put(sourceLngTxt, targetLngTxt);
        if (oldLngTxt == null) {
            modified = targetLngTxt != null;
        } else if (!oldLngTxt.equals(targetLngTxt)) {
            modified = true;
        }
        return modified;
    }

    /**
     * Get the translation list for a ui/html file
     * @param filename relative path to htroot
     * @return translation map or null
     */
    public Map<String, String> getTranslationForFile(String filename) {
        return mainTransLists.get(filename);
    }

    /**
     * Get a translation target text
     * @param filename of the translation
     * @param source english source text
     * @return translated text or null
     */
    public String getTranslation (String filename, String source) {
       Map<String, String> tmp = mainTransLists.get(filename);
       if (tmp != null)
           return tmp.get(source);
       return null;
    }

    /**
     * Translates one file. The relFilepath is the file name as given in the
     * translation source lists. The source (english) file is expected under
     * htroot path. The destination file is under DATA/LOCALE and calculated
     * using the language of loaded data.
     *
     * @param relFilepath file name releative to htroot
     * @return true on success
     */
    public boolean translateFile(String relFilepath) {
        assert loadedLng != null;
        assert mainTransLists != null;

        boolean result = false;
        if (mainTransLists.containsKey(relFilepath)) {
            Switchboard sb = Switchboard.getSwitchboard();
            if (sb != null) {
                final String htRootPath = sb.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT);
                final File sourceDir = new File(sb.getAppPath(), htRootPath);
                final File destDir = new File(sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), loadedLng);
                // get absolute file by adding relative filename
                final File sourceFile = new File(sourceDir, relFilepath);
                final File destFile = new File(destDir, relFilepath);
                result = translateFile(sourceFile, destFile, mainTransLists.get(relFilepath)); // do the translation
            }
        }
        return result;
    }

    /**
     * Create a master translation list by reading all translation files
     * If a masterOutputFile exists, content is preserved (loaded first)
     *
     * @param localesFolder folder containing *.lng translation files
     * @param masterOutputFile output file (xliff format). Must not be null.
     * @throws IOException
     */
    public void createMasterTranslationLists(final File localesFolder, final File masterOutputFile) throws IOException {
        if (masterOutputFile.exists()) // if file exists, conserve existing master content (may be updated by external tool)
            mainTransLists = loadTranslationsListsFromXliff(masterOutputFile);
        else
            mainTransLists = new TreeMap<String, Map<String, String>>();

        List<String> lngFiles = Translator.langFiles(localesFolder);
        for (String filename : lngFiles) {
            // load translation list
            ConcurrentLog.info("TRANSLATOR", "include translation file " + filename);
            Map<String, Map<String, String>> origTrans = loadTranslationsLists(new File(localesFolder, filename));

            for (String transfilename : origTrans.keySet()) { // get translation filename
                File checkfile = new File("htroot", transfilename);
                if (checkfile.exists()) { // include in master only if file exists
                    // load content to compare translation text is included
                    StringBuilder content = new StringBuilder();
                    BufferedReader br = null;
                    try {
                        br = new BufferedReader(new InputStreamReader(new FileInputStream(checkfile), StandardCharsets.UTF_8));
                        String line = null;
                        while ((line = br.readLine()) != null) {
                            content.append(line).append(net.yacy.server.serverCore.CRLF_STRING);
                        }
                    } catch (final IOException e) {
                    } finally {
                        if (br != null) {
                            try {
                                br.close();
                            } catch (final Exception e) {
                            	ConcurrentLog.fine("TRANSLATOR", "Could not close buffered reader on file " + checkfile);
                            }
                        }
                    }

                    // compare translation list
                    Map<String, String> origList = origTrans.get(transfilename);
                    for (String sourcetxt : origList.keySet()) {
                        if (content.indexOf(sourcetxt) >= 0) {
                            String origVal = origList.get(sourcetxt);
                            // it is possible that intentionally empty translation is given
                            // in this case xliff target is missing (=null)
                            if (origVal != null && !origVal.isEmpty()) { // if translation exists
                                addTranslation(transfilename, sourcetxt, null); // add to master, set target text null
                            }
                        }
                    }
                } else {
                    ConcurrentLog.fine("TRANSLATOR", "skip file for translation " + transfilename + " (from " + filename + ")");
                }
            }
        }
        // save as xliff file w/o language code
        saveAsXliff(null, masterOutputFile, mainTransLists);
    }

    /**
     * Joins translation master (xliff) and existing translation (lng).
     * Only texts existing in master are included from the lngfile,
     * the resulting map includes all keys from master with the matching translation
     * from lngfile.
     *
     * @param xlifmaster master (with en text to be translated)
     * @param lngfile existing translation
     * @return resulting map with all entries from master and translation from lngfile
     * @throws IOException
     */
    public Map<String, Map<String, String>> joinMasterTranslationLists(File xlifmaster, File lngfile) throws IOException {

        final String filename = lngfile.getName();
        mainTransLists = loadTranslationsListsFromXliff(xlifmaster);
        // load translation list
        ConcurrentLog.info("TRANSLATOR", "join into master translation file " + filename);
        Map<String, Map<String, String>> origTrans = loadTranslationsLists(lngfile);

        for (String transfilename : origTrans.keySet()) { // get translation filename
            // compare translation list
            Map<String, String> origList = origTrans.get(transfilename);
            Map<String, String> masterList = mainTransLists.get(transfilename);
            for (String sourcetxt : origList.keySet()) {
                if ((masterList != null) && (masterList.isEmpty() || masterList.containsKey(sourcetxt))) { // only if included in master (as all languages are in there but checked for occuance
                    String origVal = origList.get(sourcetxt);
                    // it is possible that intentionally empty translation is given
                    // in this case xliff target is missing (=null)
                    if (origVal != null && !origVal.isEmpty()) {
                        addTranslation(transfilename, sourcetxt, origVal);
                    }
                }
            }
        }
        return mainTransLists;
    }

    /**
     * Stores the loaded translations to a .lng file
     * @param lng
     * @param f
     * @return
     */
    public boolean saveXliff(final String lng, File f) {
        return this.saveAsXliff(lng, f, mainTransLists);
    }

    /**
     * Stores the loaded translations to a .xlf file
     * @param lng
     * @param f
     * @return
     */
    public boolean saveLng(final String lng, File f) {
        return this.saveAsLngFile(lng, f, mainTransLists);
    }

    /**
     * Total number of loaded translation entries
     * @return
     */
    public int size() {
        int i = 0;
        for (Map<String, String> trans : mainTransLists.values()) {
            i += trans.size();
        }
        return i;
    }
}
