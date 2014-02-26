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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.Formatter;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverSwitch;

import java.util.*;

/**
 * Wordlist based translator
 *
 * Uses a Property like file with phrases or single words to translate a string or a file
 * */
public class Translator {

    public final static String LANG_FILENAME_FILTER = "^.*\\.lng$";

    public static String translate(final String source, final Map<String, String> translationTable){
        final Set<String> keys = translationTable.keySet();
        String result = source;
        for (final String key : keys){
            final Pattern pattern = Pattern.compile(key);
            final Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                result = matcher.replaceAll(translationTable.get(key));
            } else {
                //Filename not available, but it will be printed in Log
                //after all untranslated Strings as "Translated file: "
                if (ConcurrentLog.isFine("TRANSLATOR")) ConcurrentLog.fine("TRANSLATOR", "Unused String: "+key);
            }
        }
        return result;
    }

    /**
     * Load multiple translationLists from one File. Each List starts with #File: relative/path/to/file
     * (within each file section translation is done in order of the language file entries, on conflicts
     * put the shorter key to the end of the list)
     * @param translationFile the File, which contains the Lists
     * @return a HashMap, which contains for each File a HashMap with translations.
     */
    public static Map<String, Map<String, String>> loadTranslationsLists(final File translationFile){
        final Map<String, Map<String, String>> lists = new HashMap<String, Map<String, String>>(); //list of translationLists for different files.
        Map<String, String> translationList = new LinkedHashMap<String, String>(); //current Translation Table (maintaining input order)

        final List<String> list = FileUtils.getListArray(translationFile);
        String forFile = "";

        for (final String line : list){
            if (line.isEmpty() || line.charAt(0) != '#'){
                final String[] split = line.split("==", 2);
                if (split.length == 2) {
                    translationList.put(split[0], split[1]);
                //}else{ //Invalid line
                }
            } else if (line.startsWith("#File: ")) {
                if (!forFile.equals("")){
                    lists.put(forFile, translationList);
                }
                if (line.charAt(6) == ' ') {
                    forFile=line.substring(7);
                } else {
                    forFile=line.substring(6);
                }
                if (lists.containsKey(forFile)) {
                    translationList = lists.get(forFile);
                } else {
                    translationList = new LinkedHashMap<String, String>();
                }
            }
        }
        lists.put(forFile, translationList);
        return lists;
    }

    public static boolean translateFile(final File sourceFile, final File destFile, final File translationFile){
        return translateFile(sourceFile, destFile, loadTranslationsLists(translationFile).get(sourceFile.getName()));
    }

    public static boolean translateFile(final File sourceFile, final File destFile, final Map<String, String> translationList){

        StringBuilder content = new StringBuilder();
        BufferedReader br = null;
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFile),"UTF-8"));
            String line = null;
            while( (line = br.readLine()) != null){
                content.append(line).append(net.yacy.server.serverCore.CRLF_STRING);
            }
            br.close();
        } catch(final IOException e) {
            return false;
        } finally {
            if (br !=null) {
                try {
                    br.close();
                } catch (final Exception e) {}
            }
        }

        content = new StringBuilder(translate(content.toString(), translationList));
        BufferedWriter bw = null;
        try{
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destFile),"UTF-8"));
            bw.write(content.toString());
            bw.close();
        }catch(final IOException e){
            return false;
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (final Exception e) {}
            }
        }

	return true;
    }

    public static boolean translateFiles(final File sourceDir, final File destDir, final File baseDir, final File translationFile, final String extensions){
        return translateFiles(sourceDir, destDir, baseDir, loadTranslationsLists(translationFile), extensions);
    }

    public static boolean translateFiles(final File sourceDir, final File destDir, final File baseDir, final Map<String, Map<String, String>> translationLists, final String extensions){
        destDir.mkdirs();
        final File[] sourceFiles = sourceDir.listFiles();
        final List<String> exts = ListManager.string2vector(extensions);
        boolean rightExtension;
        String relativePath;
        for (final File sourceFile : sourceFiles) {
             rightExtension=false;
             for (final String ext : exts) {
                 if (sourceFile.getName().endsWith(ext)) {
                     rightExtension=true;
                     break;
                 }
             }
            if (rightExtension) {
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
        }
        return true;
    }

    public static boolean translateFilesRecursive(final File sourceDir, final File destDir, final File translationFile, final String extensions, final String notdir){
        final List<File> dirList=FileUtils.getDirsRecursive(sourceDir, notdir);
        dirList.add(sourceDir);
        for (final File file : dirList) {
            if(file.isDirectory() && !file.getName().equals(notdir)) {
                //cuts the sourcePath and prepends the destPath
                File file2 = new File(destDir, file.getPath().substring(sourceDir.getPath().length()));
                translateFiles(file, file2, sourceDir, translationFile, extensions);
            }
        }
        return true;
    }

    public static Map<String, String> langMap(@SuppressWarnings("unused") final serverSwitch env) {
        final String[] ms = (
            "default/English,de/Deutsch,fr/Fran&ccedil;ais,nl/Nederlands,it/Italiano,es/Espa&ntilde;ol,pt/Portug&ecirc;s,fi/Suomi,se/Svenska,dk/Dansk," +
            "gr/E&lambda;&lambda;&eta;v&iota;&kappa;&alpha;,sk/Slovensky,cn/&#27721;&#35821;/&#28450;&#35486;," +
            "ru/&#1056;&#1091;&#1089;&#1089;&#1082;&#1080;&#1081;,uk/&#1059;&#1082;&#1088;&#1072;&#1111;&#1085;&#1089;&#1100;&#1082;&#1072;," + 
            "hi/&#2361;&#2367;&#2344;&#2381;&#2342;&#2368;"
            ).split(",");
        final Map<String, String> map = new HashMap<String, String>();
        for (final String element : ms) {
            int p = element.indexOf('/');
            if (p > 0)
                map.put(element.substring(0, p), element.substring(p + 1));
        }
        return map;
    }

    public static boolean changeLang(final serverSwitch env, final File langPath, final String lang) {
        boolean ret = false;

        if ("default".equals(lang) || "default.lng".equals(lang)) {
            env.setConfig("locale.language", "default");
            ret = true;
        } else {
            final String htRootPath = env.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT);
            final File sourceDir = new File(env.getAppPath(), htRootPath);
            final File destDir = new File(env.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), lang.substring(0, lang.length() - 4));// cut
            // .lng
            //File destDir = new File(env.getRootPath(), htRootPath + "/locale/" + lang.substring(0, lang.length() - 4));// cut
            // .lng
            final File translationFile = new File(langPath, lang);

            //if (translator.translateFiles(sourceDir, destDir, translationFile, "html")) {
            if (Translator.translateFilesRecursive(sourceDir, destDir, translationFile, "html,template,inc", "locale")) {
                env.setConfig("locale.language", lang.substring(0, lang.length() - 4));
                Formatter.setLocale(env.getConfig("locale.language", "en"));
                try {
                    final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                    bw.write(env.getConfig("svnRevision", "Error getting Version"));
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
}
