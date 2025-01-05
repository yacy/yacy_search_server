// translator.java
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Alexander Schier
// last major change: 25.05.2005
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

package net.yacy.data;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.SentenceReader;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.Formatter;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverSwitch;
import net.yacy.utils.translation.ExtensionsFileFilter;

/**
 * Wordlist based translator
 *
 * Uses a Property like file with phrases or single words to translate a string or a file
 * */
public class Translator {

    public final static String LANG_FILENAME_FILTER = "^.*\\.lng$";

    /**
     * Translate source using entries in translationTable
     * @param source text to translate. Must be non null.
     * @param translationTable translation entries : text to translate -> translation
     * @return source translated
     */
    public String translate(final StringBuilder source,
            final Map<String, String> translationTable) {
        final Set<Map.Entry<String, String>> entries = translationTable.entrySet();
        StringBuilder builder = new StringBuilder(source);
        for (final Entry<String, String> entry : entries) {
            String key = entry.getKey();
            /* We have to check key is not empty or indexOf would always return a positive value */
            if (key != null && !key.isEmpty()) {
                String translation = entry.getValue();
                int index = builder.indexOf(key);
                if (index < 0 || translation == null ) {
                    // Filename not available, but it will be printed in Log
                    // after all untranslated Strings as "Translated file: "
                    if (ConcurrentLog.isFine("TRANSLATOR"))
                        ConcurrentLog.fine("TRANSLATOR", "Unused String: " + key);
                } else {
                    while (index >= 0) {

                        // check for word boundary before and after translation key
                        // to avoid translation just on char sequence  e.g. as in  key="bug" source="mybugfix"
                        boolean boundary = index + key.length() >= builder.length(); // eof text = end-bondary

                        if (!boundary) {
                            char c = builder.charAt(index + key.length() - 1);
                            char lc = builder.charAt(index + key.length());
                            boundary |= (SentenceReader.punctuation(c) || SentenceReader.invisible(c)); // special case, basically last char of key
                            boundary |= (SentenceReader.punctuation(lc) || SentenceReader.invisible(lc)); // char after key = end-boundary
                        }

                        // if end-boundary ok check begin-boundary
                        if (boundary && index > 0) {
                            char c = builder.charAt(index - 1); // char before key = begin-boundary
                            boundary = (SentenceReader.punctuation(c) || SentenceReader.invisible(c));
                            char fc = builder.charAt(index); // special case for key >name< , currently to allow  <label>name</label (basically fist char of key)
                            boundary |= (SentenceReader.punctuation(fc) || SentenceReader.invisible(fc));
                        }

                        if (boundary) { // boundary check ok -> translate
                            builder.replace(index, index + key.length(), translation);
                            index = builder.indexOf(key, index + translation.length());
                        } else { // otherwise just skip to next occurence
                            index = builder.indexOf(key, index + key.length());
                        }
                    }
                }
            }
        }
        return builder.toString();
    }

    /**
     * Load multiple translationLists from one File. Each List starts with #File: relative/path/to/file
     * (within each file section translation is done in order of the language file entries, on conflicts
     * put the shorter key to the end of the list)
     * @param translationFile the File, which contains the Lists
     * @return a HashMap, which contains for each File a HashMap with translations.
     */
    public Map<String, Map<String, String>> loadTranslationsLists(final File translationFile) {
        final Map<String, Map<String, String>> lists = new HashMap<String, Map<String, String>>(); //list of translationLists for different files.
        Map<String, String> translationList = new LinkedHashMap<String, String>(); //current Translation Table (maintaining input order)

        final List<String> list = FileUtils.getListArray(translationFile);
        String forFile = "";

        for (final String line : list) {
            if (!line.isEmpty()) {
                if (line.charAt(0) != '#') {
                    final String[] split = line.split("==", 2);
                    if (split.length == 2) {
                        translationList.put(split[0], split[1]);
                        //}else{ //Invalid line
                    }
                } else if (line.startsWith("#File:")) {
                    if (!forFile.isEmpty()) {
                        lists.put(forFile, translationList);
                    }
                    forFile = line.substring(6).trim(); //skip "#File:"
                    if (lists.containsKey(forFile)) {
                        translationList = lists.get(forFile);
                    } else {
                        translationList = new LinkedHashMap<String, String>();
                    }
                }
            }
        }
        lists.put(forFile, translationList);
        return lists;
    }

    /**
     * Translate sourceFile to destFile using translationList.
     * @param sourceFile file to translate
     * @param destFile file to write
     * @param translationList map of translations
     * @return true when destFile was sucessfully written, false otherwise
     */
    public boolean translateFile(final File sourceFile, final File destFile, final Map<String, String> translationList){

        StringBuilder content = new StringBuilder();
        BufferedReader br = null;
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFile), StandardCharsets.UTF_8));
            String line = null;
            while( (line = br.readLine()) != null){
                content.append(line).append(net.yacy.server.serverCore.CRLF_STRING);
            }
        } catch(final IOException e) {
            return false;
        } finally {
            if (br !=null) {
                try {
                    br.close();
                } catch (final Exception e) {}
            }
        }

        String processedContent = translate(content, translationList);
        try (
        	/* Resources automatically closed by this try-with-resources statement */
        	final FileOutputStream outStream = new FileOutputStream(destFile);
        	BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outStream, StandardCharsets.UTF_8));
        ) {
            bw.write(processedContent);
        } catch(final Exception e){
            return false;
        }

	return true;
    }

    /**
     * Translate files in sourceDir (relative path of baseDir) write result to destDir
     *
     * @param sourceDir relative path
     * @param destDir destination
     * @param baseDir base dir of source
     * @param translationLists translation to use
     * @param extensions file extension to include in translation
     * @return
     */
    public boolean translateFiles(final File sourceDir, final File destDir, final File baseDir, final Map<String, Map<String, String>> translationLists, final String extensions){
        destDir.mkdirs();
        final List<String> exts = ListManager.string2vector(extensions);
        final File[] sourceFiles = sourceDir.listFiles(new ExtensionsFileFilter(exts));
        String relativePath;
        for (final File sourceFile : sourceFiles) {
            try {
                relativePath=sourceFile.getAbsolutePath().substring(baseDir.getAbsolutePath().length()+1); //+1 to get the "/"
                relativePath = relativePath.replace(File.separatorChar, '/');
            } catch (final IndexOutOfBoundsException e) {
                ConcurrentLog.severe("TRANSLATOR", "Error creating relative Path for "+sourceFile.getAbsolutePath());
                relativePath = "wrong path"; //not in translationLists
            }
            if (translationLists.containsKey(relativePath)) {
                ConcurrentLog.info("TRANSLATOR", "Translating file: "+ relativePath);
                if(!translateFile(
                                  sourceFile,
                                  new File(destDir, sourceFile.getName().replace('/', File.separatorChar)),
                                  translationLists.get(relativePath)))
                {
                    ConcurrentLog.severe("TRANSLATOR", "File error while translating file "+relativePath);
                }
                //}else{
                    //serverLog.logInfo("TRANSLATOR", "No translation for file: "+relativePath);
            }
        }
        return true;
    }

    /**
     * Translate files starting with sourceDir and all subdirectories.
     *
     * @param sourceDir
     * @param destDir
     * @param translationFile translation language file
     * @param extensions extension of files to include in translation
     * @param notdir directory to exclude
     * @return true if all files translated (or none)
     */
    public boolean translateFilesRecursive(final File sourceDir, final File destDir, final File translationFile, final String extensions, final String notdir) {
        final List<File> dirList = FileUtils.getDirsRecursive(sourceDir, notdir);
        dirList.add(sourceDir);
        final Map<String, Map<String, String>> translationLists = loadTranslationsLists(translationFile);
        boolean erg = true;
        for (final File file : dirList) {
            if (file.isDirectory() && !file.getName().equals(notdir)) {
                //cuts the sourcePath and prepends the destPath
                File file2 = new File(destDir, file.getPath().substring(sourceDir.getPath().length()));
                erg &= translateFiles(file, file2, sourceDir, translationLists, extensions);
            }
        }
        return erg;
    }

    public static Map<String, String> langMap(@SuppressWarnings("unused") final serverSwitch env) {
        final String[] ms = CommonPattern.COMMA.split(
            "browser/Browser Language," +
            "default/English,de/Deutsch,fr/Fran&ccedil;ais,nl/Nederlands,it/Italiano,es/Espa&ntilde;ol,pt/Portug&ecirc;s,fi/Suomi,se/Svenska,dk/Dansk," +
            "el/E&lambda;&lambda;&eta;v&iota;&kappa;&alpha;,sk/Slovensky,zh/&#27721;&#35821;/&#28450;&#35486;," +
            "ru/&#1056;&#1091;&#1089;&#1089;&#1082;&#1080;&#1081;,uk/&#1059;&#1082;&#1088;&#1072;&#1111;&#1085;&#1089;&#1100;&#1082;&#1072;," + 
            "hi/&#2361;&#2367;&#2344;&#2381;&#2342;&#2368;,ja/&#26085;&#26412;&#35486;"
            );
        final Map<String, String> map = new HashMap<String, String>();
        for (final String element : ms) {
            int p = element.indexOf('/');
            if (p > 0)
                map.put(element.substring(0, p), element.substring(p + 1));
        }
        return map;
    }

    public boolean changeLang(final serverSwitch env, final File langPath, final String lang) {
        boolean ret = false;

        if ("default".equals(lang) || "default.lng".equals(lang)) {
            env.setConfig("locale.language", "default");
            ret = true;
        } else if ("browser".equals(lang) || "browser.lng".equals(lang)) {
            env.setConfig("locale.language", "browser");
            ret = true;
        } else {
            final String htRootPath = env.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT);
            final File sourceDir = new File(env.getAppPath(), htRootPath);
            final File destDir = new File(env.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), lang.substring(0, lang.length() - 4));// cut .lng
            final File translationFile = new File(langPath, lang);

            if (translateFilesRecursive(sourceDir, destDir, translationFile, "html,template,inc", "locale")) {
                env.setConfig("locale.language", lang.substring(0, lang.length() - 4));
                Formatter.setLocale(env.getConfig("locale.language", "en"));
                try {
                    final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                    bw.write(env.getConfig(Seed.VERSION, "Error getting Version"));
                    bw.close();
                } catch (final IOException e) {
                    // Error
                }
                ret = true;
            }
        }
        return ret;
    }

    public static List<String> langFiles(File langPath) {
        return FileUtils.getDirListing(langPath, Translator.LANG_FILENAME_FILTER);
    }

    /**
     * Helper to collect a list of available translations
     * This looks in the locale directory for the subdirectories created on translation
     * @return list of language-codes of available/active translations
     */
    public static List<String> activeTranslations() {
        final Switchboard sb = Switchboard.getSwitchboard();
        final File localePath;
        if (sb != null) {
            localePath = sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot");
        } else {
            localePath = new File ("DATA/LOCALE/htroot");
        }
        final List<String> dirlist = new ArrayList<String>(); // get list of language subdirectories
        if(localePath.isDirectory()) {
        	final File[] list = localePath.listFiles();
        	if(list != null) { // the list may be null on IO error
        		for (final File f : list) {
        			if (f.isDirectory()) {
        				dirlist.add(f.getName()); // filter directories to add to result
        			}
        		}
        	}
        }
        return dirlist;
    }
}
