// TranslatorXliff.java
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Burkhard Buelte
// last major change: 2016-03-28
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

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.Translator;
import net.yacy.search.Switchboard;


/**
 * Wordlist based translator
 *
 * Translator which can read and write translation lists from a
 * <a href="http://docs.oasis-open.org/xliff/v1.2/os/xliff-core.html">XLIFF 1.2</a>
 * file with phrases or single words to translate a string or a file.
 *
 * On loading of translation files loaded data is merged with local (modified or downloaded)
 * translation data in DATA/LOCALE/
 */
public class TranslatorXliff extends Translator {

    /**
     * Load translationLists for one language from a Xliff File.
     *
     * @param xliffFile the File, which contains the Lists
     * @return a HashMap, which contains for each File a HashMap with
     * translations.
     */
    public Map<String, Map<String, String>> loadTranslationsListsFromXliff(final File xliffFile) {

        final Map<String, Map<String, String>> lngLists = new TreeMap<String, Map<String, String>>(); //list of translationLists for different files.
        /**
         * read xliff xml file into a xliff object
         * <xliff>
         * <file original="filename">
         * <body>
         * <trans-unit>
         * <source>text</source>
         * <target>text</target>
         * </trans-unit>
         * <trans-unit>....
         * </body>
         * </file>
         * <file>.....
         * </xliff>
         */

        try (FileInputStream fis = new FileInputStream(xliffFile)) { // try-with-resource to close inputstream

            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader xmlreader = factory.createXMLStreamReader(fis);

            Map<String, String> translationList = null; //current Translation Table (maintaining input order)
            String source = null;
            String target = null;
            String state = null;
            while (xmlreader.hasNext()) {
                int eventtype = xmlreader.next();

                if (eventtype == START_ELEMENT) {
                    String ename = xmlreader.getLocalName();

                    // setup for 'file' section (get or add translationlist for this file)
                    if (ename.equalsIgnoreCase("file")) {
                        String forFile = xmlreader.getAttributeValue(null, "original");
                        if (lngLists.containsKey(forFile)) {
                            translationList = lngLists.get(forFile);
                        } else {
                            translationList = new LinkedHashMap<String, String>(); //current Translation Table (maintaining input order)
                            lngLists.put(forFile, translationList);
                        }
                        source = null;
                        target = null;
                    } else if (ename.equalsIgnoreCase("trans-unit")) { // prepare for trans-unit
                        source = null;
                        target = null;
                    } else if (ename.equalsIgnoreCase("source")) { // get source text
                        source = xmlreader.getElementText();
                    } else if (ename.equalsIgnoreCase("target")) { // get target text
                        state = xmlreader.getAttributeValue(null, "state");
                        target = xmlreader.getElementText(); // TODO: in full blown xliff, target may contain sub-xml elements (but we use only text)
                    }
                } else if (eventtype == END_ELEMENT) {
                    String ename = xmlreader.getLocalName();

                    // store source/target on finish of trans-unit
                    if (ename.equalsIgnoreCase("trans-unit") && translationList != null) {
                        if (source != null) {
                            if (target != null) {
                                if ("translated".equals(state)) {
                                    translationList.put(source, target);
                                } else {
                                    translationList.put(source, null);
                                }
                            } else {
                                translationList.put(source, null);
                            }
                            source = null;
                        }
                        target = null;
                    }
                    // on file end-tag make sure nothing is added (on error in xml)
                    if (ename.equalsIgnoreCase("file")) {
                        translationList = null;
                    }
                }
            }
            xmlreader.close();
        } catch (IOException | XMLStreamException ex) {
            ConcurrentLog.warn("TRANSLATOR", "error reading " + xliffFile.getAbsolutePath() + " -> " + ex.getMessage());
        }
        return lngLists;
    }

    /**
     * Maps (overrides) Translator.loadTranslationsLists to read from xliff file
     * if file extension is .xlf or .xliff (otherwise load xx.lng file).
     * Additionally if localy modified translation exists in DATA/LOCALE content
     * is merged into given translation.
     *
     * @param xliffFile
     * @return translation map
     */
    @Override
    public Map<String, Map<String, String>> loadTranslationsLists(final File xliffFile) {
        File locallng = getScratchFile(xliffFile);
        if (xliffFile.getName().toLowerCase(Locale.ROOT).endsWith(".xlf") || xliffFile.getName().toLowerCase(Locale.ROOT).endsWith(".xliff")) {
            if (locallng.exists()) {
                Map<String, Map<String, String>> mergedList = loadTranslationsListsFromXliff(xliffFile);
                Map<String, Map<String, String>> tmplist = loadTranslationsListsFromXliff(locallng);
                return mergeTranslationLists(mergedList, tmplist);
            }
			return loadTranslationsListsFromXliff(xliffFile);
        } else if (locallng.exists()) {
            Map<String, Map<String, String>> mergedList = super.loadTranslationsLists(xliffFile);
            Map<String, Map<String, String>> tmplist = super.loadTranslationsLists(locallng);
            return mergeTranslationLists(mergedList, tmplist);
        } else {
            return super.loadTranslationsLists(xliffFile);
        }
    }

    /**
     * Merges translations, values from localTrans overwrite entries in masterTrans.
     *
     * @param masterTrans master translation
     * @param localTrans translation to be merged to master
     * @return resulting map with all entries from master and localTrans
     */
    protected Map<String, Map<String, String>> mergeTranslationLists(Map<String, Map<String, String>> masterTrans, Map<String, Map<String, String>> localTrans) {
        if (localTrans != null && !localTrans.isEmpty()) {
            for (String transfilename : localTrans.keySet()) { // get translation filename

                Map<String, String> origList = localTrans.get(transfilename);
                if (masterTrans.containsKey(transfilename)) {
                    Map<String, String> xliffList = masterTrans.get(transfilename);
                    xliffList.putAll(origList);
                } else {
                    masterTrans.put(transfilename, origList);
                }
            }
        }
        return masterTrans;
    }
    
    /**
     * Saves the internal translation map as XLIFF 1.2 file
     *
     * @param targetLanguage the target language code, if null target is omitted
     * in output file and only source text stored
     * @param xliffFile name of the output XLIFF file (typically with .xlf
     * extension)
     * @param lng the YaCy translation for one language
     *
     * @return true on success
     */
    public boolean saveAsXliff(final String targetLanguageCode, File xliffFile, Map<String, Map<String, String>> lng) {

        final String sourceLanguage = "en"; // source language is always English

        try (
        	/* Resources automatically closed by this try-with-resources statement */
        	final FileOutputStream fileOutStream = new FileOutputStream(xliffFile);
            final OutputStreamWriter output = new OutputStreamWriter(fileOutStream, StandardCharsets.UTF_8.name());
        ) {
            output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            output.write("<xliff version='1.2' xmlns='urn:oasis:names:tc:xliff:document:1.2'> \n");
            for (String afilemap : lng.keySet()) {
                output.write("<file original=\"" + afilemap + "\" " // original required in xliff 1.2
                        + " source-language=\"" + sourceLanguage + "\" "); // required in xliff 1.2
                if (targetLanguageCode != null && !targetLanguageCode.isEmpty()) {
                    output.write(" target-language=\"" + targetLanguageCode + "\" "); // required in xliff 1.2
                }
                output.write(" datatype=\"html\">\n"); // required in xliff 1.2

                output.write("  <body>\n");
                Map<String, String> txtmap = lng.get(afilemap);
                for (String source : txtmap.keySet()) {
                    String target = txtmap.get(source);
                    // we use hashCode of source string to get same id in different xliff files for same translation text
                    output.write("    <trans-unit id=\"" + Integer.toHexString(source.hashCode()) + "\" xml:space=\"preserve\" approved=\"no\"");
                    if (target == null || target.isEmpty()) { // omitt target text if not available
                        output.write(" translate=\"yes\">\n");
                        output.write("       <source>" + toXmlStr(source) + "</source>\n");
                    } else {
                        output.write(">\n");
                        output.write("       <source>" + toXmlStr(source) + "</source>\n");
                        output.write("       <target" + (target.equals(source) ? "" : " state='translated'") + ">" + toXmlStr(target) + "</target>\n");
                    }
                    output.write("    </trans-unit>\n");
                }
                output.write("  </body>\n");
                output.write("</file>\n\n");
            }

            output.write("</xliff>\n");
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * Helper to write translation entries for one file
     *
     * @param filename relative path file name
     * @param textlist the translation list for filename
     * @param output output file
     * @throws IOException
     */
    private void writeFileSection(final String filename, final Map<String, String> textlist, OutputStreamWriter output) throws IOException {
        if (!filename.isEmpty()) {
            output.write("#File: " + filename + "\n"
                    + "#---------------------------\n");

            for (String source : textlist.keySet()) {
                String target = textlist.get(source);
                if (target != null && !target.isEmpty()) { // omitt target text if not available
                    if (source.equals(target)) {
                        output.write("#" + source + "==" + target + "\n"); // no translation needed (mark #)
                    } else {
                        output.write(source + "==" + target + "\n");
                    }
                } else {
                    output.write("#" + source + "==" + source + "\n"); // no translation available (mark #)
                }
            }
            output.write("#-----------------------------\n\n");
        }
    }

    /**
     * Saves the internal translation map as XLIFF 1.2 file
     *
     * @param targetLanguageCode the target language code, if null target is omitted
     * in output file and only source text stored
     * @param lngFile name of the output XLIFF file (typically with .xlf
     * extension)
     * @param lng the YaCy translation for one language
     *
     * @return true on success
     */
    public boolean saveAsLngFile(final String targetLanguageCode, File lngFile, Map<String, Map<String, String>> lng) {

        try (
        	/* Resources automatically closed by this try-with-resources statement */
        	final FileOutputStream fileOutStream = new FileOutputStream(lngFile);
            final OutputStreamWriter output = new OutputStreamWriter(fileOutStream, StandardCharsets.UTF_8.name());
        ) {
            output.write("# " + (targetLanguageCode == null ? "master" : targetLanguageCode) + ".lng\n");
            output.write("# -----------------------\n");
            output.write("# This is a part of YaCy, a peer-to-peer based web search engine\n\n");
            output.write("# Each translation list starts with #File: relative/path/to/file\n");
            output.write("# followed by the translations  OriginalText==TranslatedText (in one line)\n");
            output.write("# Comment lines or not translated lines start with #\n\n");

            // special handling of "ConfigLanguage_p.html" to list on top of all other
            // because of some important identifier
            Map<String, String> txtmap = lng.get("ConfigLanguage_p.html");
            if (txtmap != null) writeFileSection("ConfigLanguage_p.html", txtmap, output);

            for (String afilemap : lng.keySet()) {
                txtmap = lng.get(afilemap);
                if (!"ConfigLanguage_p.html".equals(afilemap)) {
                    writeFileSection(afilemap, txtmap, output);
                }
            }
            output.write("# EOF");
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * Helper to make valid xml content text as text may contain html markup
     * (the reverse on read is done automatically)
     * @param s input string
     * @return xml string
     */
    private String toXmlStr(String s) {

        int control = s.indexOf("&");
        while (control >= 0) {
            s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
            if (control < s.length()) {
                control++;
            }
            control = s.indexOf("&", control);
        }

        control = s.indexOf("<");
        while (control >= 0) {
            s = s.substring(0, control) + "&lt;" + s.substring(control + 1);
            if (control < s.length()) {
                control++;
            }
            control = s.indexOf("<", control);
        }

        control = s.indexOf(">");
        while (control >= 0) {
            s = s.substring(0, control) + "&gt;" + s.substring(control + 1);
            if (control < s.length()) {
                control++;
            }
            control = s.indexOf(">", control);
        }
        return s;
    }

    /**
     * Get the path to a work/scratch file in the DATA/LOCALE directory with the
     * same name as given in the langPath
     *
     * @param langFile the path with filename to the language file
     * @return a path to DATA/LOCALE/langFile.filename()
     */
    public File getScratchFile(final File langFile) {
        if (Switchboard.getSwitchboard() != null) { // for debug and testing were switchboard is null
            File f = Switchboard.getSwitchboard().getDataPath("locale.translated_html", "DATA/LOCALE");
            return new File(f.getParentFile(), langFile.getName());
        }
		return langFile;
    }
}
