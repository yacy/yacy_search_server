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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.Translator;

import org.oasis.xliff.core_12.Body;
import org.oasis.xliff.core_12.Target;
import org.oasis.xliff.core_12.TransUnit;
import org.oasis.xliff.core_12.Xliff;

/**
 * Wordlist based translator
 *
 * Translator which can read and write translation lists from a
 * <a href="http://docs.oasis-open.org/xliff/v1.2/os/xliff-core.html">XLIFF
 * 1.2</a> file with phrases or single words to translate a string or a file
 */
public class TranslatorXliff extends Translator {

    /**
     * Load translationLists for one language from a Xliff File.
     *
     * @param translationFile the File, which contains the Lists
     * @return a HashMap, which contains for each File a HashMap with
     * translations.
     */
    public static Map<String, Map<String, String>> loadTranslationsListsFromXliff(final File xliffFile) {

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
        Xliff xliffTranslation;
        try {
            FileInputStream fis = new FileInputStream(xliffFile);
            JAXBContext ctx = JAXBContext.newInstance(org.oasis.xliff.core_12.Xliff.class);
            Unmarshaller un = ctx.createUnmarshaller();
            Object obj = un.unmarshal(fis);
            if (obj instanceof org.oasis.xliff.core_12.Xliff) {
                xliffTranslation = (org.oasis.xliff.core_12.Xliff) obj;
            } else {
                return null;
            }

            List<Object> xlfFileList = xliffTranslation.getAnyAndFile();
            for (Object xlfobj : xlfFileList) {
                org.oasis.xliff.core_12.File xlfFileNode = (org.oasis.xliff.core_12.File) xlfobj;
                Map<String, String> translationList; //current Translation Table (maintaining input order)
                String forFile = xlfFileNode.getOriginal();
                if (lngLists.containsKey(forFile)) {
                    translationList = lngLists.get(forFile);
                } else {
                    translationList = new LinkedHashMap<String, String>(); //current Translation Table (maintaining input order)
                    lngLists.put(forFile, translationList);
                }

                Body xlfBody = xlfFileNode.getBody();
                List<Object> xlfTransunitList = xlfBody.getGroupOrTransUnitOrBinUnit();
                for (Object xlfTransunit : xlfTransunitList) {
                    if (xlfTransunit instanceof TransUnit) {
                        String source = ((TransUnit) xlfTransunit).getSource().getContent().get(0).toString();
                        Target target = ((TransUnit) xlfTransunit).getTarget();
                        if (target != null) {
                           if ("translated".equals(target.getState())) {
                                List<Object> targetContentList = target.getContent();
                                String targetContent = targetContentList.get(0).toString();
                                translationList.put(source, targetContent);
                            } else {
                                translationList.put(source, null);
                            }
                        } else {
                            translationList.put(source, null);
                        }
                    }
                }
            }
        } catch (JAXBException je) {
            ConcurrentLog.warn("TRANSKATOR",je.getMessage());
        } catch (FileNotFoundException ex) {
            ConcurrentLog.warn("TRANSLATOR", "File not found: " + xliffFile.getAbsolutePath());
        }
        return lngLists;
    }

    /**
     * Maps (overrides) Translator.loadTranslationsLists to read from xliff file
     * if file extension is .xlf or .xliff (otherwise load xx.lng file)
     *
     * @param xliffFile
     * @return translatio map
     */
    public static Map<String, Map<String, String>> loadTranslationsLists(final File xliffFile) {
        if (xliffFile.getName().toLowerCase().endsWith(".xlf") || xliffFile.getName().toLowerCase().endsWith(".xliff")) {
            return loadTranslationsListsFromXliff(xliffFile);
        } else {
            return Translator.loadTranslationsLists(xliffFile);
        }
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
        OutputStreamWriter output;

        try {
            output = new OutputStreamWriter(new FileOutputStream(xliffFile), StandardCharsets.UTF_8.name());
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
                    output.write("    <trans-unit id=\"" + Integer.toHexString(source.hashCode()) + "\" xml:space=\"preserve\" approved=\"no\">\n");
                    output.write("       <source>" + toXmlStr(source) + "</source>\n");
                    if (target != null && !target.isEmpty()) { // omitt target text if not available
                        output.write("       <target" + (target.equals(source) ? "" : " state='translated'") + ">" + toXmlStr(target) + "</target>\n");
                    }
                    output.write("    </trans-unit>\n");
                }
                output.write("  </body>\n");
                output.write("</file>\n\n");
            }

            output.write("</xliff>\n");
            output.close();
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
        output.write("#File: " + filename + "\n"
                + "#------------------------------\n"); // required in 1.2

        for (String source : textlist.keySet()) {
            String target = textlist.get(source);
            // we use hashCode of source string to get same id in different xliff files for same translation text
            if (target != null && !target.isEmpty()) { // omitt target text if not available
                if (source.equals(target)) {
                    output.write("#" + source + "==" + target + "\n"); // no translation needed (mark #)
                } else {
                    output.write(source + "==" + target + "\n");
                }
            } else {
                output.write("#"+source + "==" + source + "\n"); // no translation available (mark #)
            }
        }
        output.write("#------------------------------\n\n");
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
    public boolean saveAsLngFile(final String targetLanguageCode, File lngFile, Map<String, Map<String, String>> lng) {

        OutputStreamWriter output;

        try {
            output = new OutputStreamWriter(new FileOutputStream(lngFile), StandardCharsets.UTF_8.name());
            output.write("# " + (targetLanguageCode == null ? "master" : targetLanguageCode) + ".lng\n");
            output.write("# -----------------------\n");
            output.write("# This is a part of YaCy, a peer-to-peer based web search engine\n\n");
            output.write("# Each translation list starts with #File: relative/path/to/file\n");
            output.write("# followed by the translations  OriginalText==TranslatedText\n");
            output.write("# Comment lines start with #\n\n");

            // special handling of "ConfigLanguage_p.html" to list on top of all other
            // because of some important identifier
            Map<String, String> txtmap = lng.get("ConfigLanguage_p.html");
            writeFileSection("ConfigLanguage_p.html", txtmap, output);

            for (String afilemap : lng.keySet()) {
                txtmap = lng.get(afilemap);
                if (!"ConfigLanguage_p.html".equals(afilemap)) {
                    writeFileSection(afilemap, txtmap, output);
                }
            }
            output.write("# EOF");
            output.close();
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * Helper to make valid xml content text as text may contain html markup
     * (the reverse on read is done automatically)
     * @param html input string
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
}
