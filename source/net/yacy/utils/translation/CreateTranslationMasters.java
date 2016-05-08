// CreateTranslationMasters.java
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

/**
 * Utility to create a translation master file from all existing translation
 * with check file.exists and translation text exists.
 * Also can join existing translation with master (currently ristrictive,
 * means only translation text exist in master are included in resultin Map
 */
public class CreateTranslationMasters extends TranslatorXliff {

    /**
     * Helper to add a translation text to the map
     *
     * @param translation to add the text to
     * @param relFileName relative filename the translation belongs to
     * @param sourceLngTxt the english source text
     * @param targetLngTxt the translated text
     * @return true = if map was modified, otherwise false
     */
    protected boolean addTranslation(Map<String, Map<String, String>> translation, final String relFileName, final String sourceLngTxt, final String targetLngTxt) {
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
     * Create a master translation list by reading all translation files
     * If a masterOutputFile exists, content is preserved (loaded first)
     *
     * @param masterOutpuFile output file (xliff format)
     * @throws IOException
     */
    public void createMasterTranslationLists(File masterOutputFile) throws IOException {
        Map<String, Map<String, String>> xliffTrans;
        if (masterOutputFile.exists()) // if file exists, conserve existing master content (may be updated by external tool)
            xliffTrans = TranslatorXliff.loadTranslationsListsFromXliff(masterOutputFile);
        else
            xliffTrans = new TreeMap<String, Map<String, String>>();

        List<String> lngFiles = Translator.langFiles(new File("locales"));
        for (String filename : lngFiles) {
            // load translation list
            ConcurrentLog.info("TRANSLATOR", "include translation file " + filename);
            Map<String, Map<String, String>> origTrans = Translator.loadTranslationsLists(new File("locales", filename));

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
                        br.close();
                    } catch (final IOException e) {
                    } finally {
                        if (br != null) {
                            try {
                                br.close();
                            } catch (final Exception e) {
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
                                addTranslation(xliffTrans, transfilename, sourcetxt, null); // add to master, set target text null
                            }
                        }
                    }
                } else {
                    ConcurrentLog.fine("TRANSLATOR", "skip file for translation " + transfilename + " (from " + filename + ")");
                }
            }
        }
        // save as xliff file w/o language code
        saveAsXliff(null, masterOutputFile, xliffTrans);
    }

    /**
     * Joins translation master (xliff) and existing translation (lng)
     *
     * @param xlifmaster master (with en text to be translated)
     * @param lngfile existing translation
     * @return resulting map with all entries from master and translation from lngfile
     * @throws IOException
     */
    public Map<String, Map<String, String>> joinMasterTranslationLists(File xlifmaster, File lngfile) throws IOException {

        final String filename = lngfile.getName();
        Map<String, Map<String, String>> xliffTrans = TranslatorXliff.loadTranslationsListsFromXliff(xlifmaster);
        // load translation list
        System.out.println("join into master translation file " + filename);
        Map<String, Map<String, String>> origTrans = Translator.loadTranslationsLists(lngfile);

        for (String transfilename : origTrans.keySet()) { // get translation filename
            // compare translation list
            Map<String, String> origList = origTrans.get(transfilename);
            Map<String, String> masterList = xliffTrans.get(transfilename);
            for (String sourcetxt : origList.keySet()) {
                if ((masterList != null) && (masterList.isEmpty() || masterList.containsKey(sourcetxt))) { // only if included in master (as all languages are in there but checked for occuance
                    String origVal = origList.get(sourcetxt);
                    // it is possible that intentionally empty translation is given
                    // in this case xliff target is missing (=null)
                    if (origVal != null && !origVal.isEmpty()) {
                        addTranslation(xliffTrans, transfilename, sourcetxt, origVal);
                    }
                }
            }
        }
        return xliffTrans;
    }

    /**
     * for testing to create on master and joined translation results for all lang's
     *
     * @param args
     */
    public static void main(String args[]) {
        File outputdirectory = new File ("test/DATA");

        CreateTranslationMasters ctm = new CreateTranslationMasters();
        try {
            if (!outputdirectory.exists()) outputdirectory.mkdir();
            File xlfmaster = new File(outputdirectory, "master.lng.xlf");
            ctm.createMasterTranslationLists(xlfmaster); // write the language neutral translation master as xliff

            List<String> lngFiles = Translator.langFiles(new File("locales"));
            for (String filename : lngFiles) {
                Map<String, Map<String, String>> lngmaster = ctm.joinMasterTranslationLists(xlfmaster, new File("locales", filename)); // create individual language translation files from master
                File xlftmp = new File(outputdirectory, filename + ".xlf");
                System.out.println("output new master translation file " + xlftmp.toString() + " and " + filename);
                ctm.saveAsXliff(filename.substring(0, 2), xlftmp, lngmaster);
                ctm.saveAsLngFile(filename.substring(0, 2), new File(outputdirectory, filename), lngmaster);
            }
        } catch (IOException ex) {
            ConcurrentLog.logException(ex);
        }
        ConcurrentLog.shutdown();
    }
}
