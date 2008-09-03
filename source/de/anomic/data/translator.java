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

package de.anomic.data;


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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.yFormatter;

/**
 * Wordlist based translator
 *
 * Uses a Property like file with phrases or single words to translate a string or a file
 * */
public class translator {
	public static String translate(final String source, final Hashtable<String, String> translationList){
		final Enumeration<String> keys = translationList.keys();
		String result = source;
		String key = "";
		while(keys.hasMoreElements()){
			key = keys.nextElement();
			final Pattern pattern = Pattern.compile(key);
			final Matcher matcher = pattern.matcher(result);
			if (matcher.find()) {
				result = matcher.replaceAll(translationList.get(key));
			} else {
				//Filename not available, but it will be printed in Log 
				//after all untranslated Strings as "Translated file: "
				if (serverLog.isFine("TRANSLATOR")) serverLog.logFine("TRANSLATOR", "Unused String: "+key); 
			}
		}
		return result;
	}
	/**
	 * Load multiple translationLists from one File. Each List starts with #File: relative/path/to/file
	 * @param translationFile the File, which contains the Lists
	 * @return a Hashtable, which contains for each File a Hashtable with translations.
	 */
	public static Hashtable<String, Hashtable<String, String>> loadTranslationsLists(final File translationFile){
		final Hashtable<String, Hashtable<String, String>> lists = new Hashtable<String, Hashtable<String, String>>(); //list of translationLists for different files.
		Hashtable<String, String> translationList = new Hashtable<String, String>(); //current Translation Table
        
		final ArrayList<String> list = listManager.getListArray(translationFile);
		final Iterator<String> it = list.iterator();
		String line = "";
		String[] splitted;
		String forFile="";

		while(it.hasNext()){
			line = it.next();
			if(! line.startsWith("#")){
				splitted = line.split("==", 2);
				if(splitted.length == 2){
					translationList.put(splitted[0], splitted[1]);
				//}else{ //Invalid line
				}
			}else if(line.startsWith("#File: ")){
				if(!forFile.equals("")){
						lists.put(forFile, translationList);
				}
				if(line.charAt(6)==' '){
					forFile=line.substring(7);
				}else{
					forFile=line.substring(6);
				}
				if(lists.containsKey(forFile)){
					translationList=lists.get(forFile);
				}else{
					translationList=new Hashtable<String, String>();
				}
			}
		}
		lists.put(forFile, translationList);
		return lists;
	}

	public static boolean translateFile(final File sourceFile, final File destFile, final File translationFile){
	    final Hashtable<String, String> translationList = loadTranslationsLists(translationFile).get(sourceFile.getName());
		return translateFile(sourceFile, destFile, translationList);
	}
	
	public static boolean translateFile(final File sourceFile, final File destFile, final Hashtable<String, String> translationList){

		String content = "";
		String line = "";
        BufferedReader br = null;
		try{
			br = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFile),"UTF-8"));
			while( (line = br.readLine()) != null){
				content += line + de.anomic.server.serverCore.CRLF_STRING;
			}
			br.close();
		}catch(final IOException e){
			return false;
		} finally {
            if (br!=null)try{br.close();}catch(final Exception e){}
        }
        
		content = translate(content, translationList);
        BufferedWriter bw = null;
		try{
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destFile),"UTF-8"));
			bw.write(content);
			bw.close();
		}catch(final IOException e){
			return false;
		} finally {
            if(bw!=null)try{bw.close();}catch(final Exception e){}
        }
		
		return true;
	}

	public static boolean translateFiles(final File sourceDir, final File destDir, final File baseDir, final File translationFile, final String extensions){
			final Hashtable<String, Hashtable<String, String>> translationLists = loadTranslationsLists(translationFile);
			return translateFiles(sourceDir, destDir, baseDir, translationLists, extensions);
	}

	public static boolean translateFiles(final File sourceDir, final File destDir, final File baseDir, final Hashtable<String, Hashtable<String, String>> translationLists, final String extensions){
		destDir.mkdirs();
		final File[] sourceFiles = sourceDir.listFiles();
        final Vector<String> exts=listManager.string2vector(extensions);
        boolean rightExtension;
        Iterator<String> it;
        String relativePath;
		for(int i=0;i<sourceFiles.length;i++){
             it=exts.iterator();
             rightExtension=false;
             while(it.hasNext()){
                 if(sourceFiles[i].getName().endsWith(it.next())){
                     rightExtension=true;
                     break;
                 }
             }
			if(rightExtension){
                try{
                    relativePath=sourceFiles[i].getAbsolutePath().substring(baseDir.getAbsolutePath().length()+1); //+1 to get the "/"
                    relativePath = relativePath.replace(File.separatorChar, '/');
                }catch(final IndexOutOfBoundsException e){
					serverLog.logSevere("TRANSLATOR", "Error creating relative Path for "+sourceFiles[i].getAbsolutePath());
                    relativePath="wrong path"; //not in translationLists
                } 
				if(translationLists.containsKey(relativePath)){
                    serverLog.logInfo("TRANSLATOR", "Translating file: "+ relativePath);
					if(!translateFile(
                                      sourceFiles[i]
                                    , new File(destDir, sourceFiles[i].getName().replace('/', File.separatorChar))
                                    , translationLists.get(relativePath))){
						serverLog.logSevere("TRANSLATOR", "File error while translating file "+relativePath);
					}
				//}else{
						//serverLog.logInfo("TRANSLATOR", "No translation for file: "+relativePath);
				}
			}

		}
		return true;
	}

    public static boolean translateFilesRecursive(final File sourceDir, final File destDir, final File translationFile, final String extensions, final String notdir){
        final ArrayList<File> dirList=listManager.getDirsRecursive(sourceDir, notdir);
        dirList.add(sourceDir);
        final Iterator<File> it=dirList.iterator();
        File file=null;
        File file2=null;
        while(it.hasNext()){
            file=it.next();
            //cuts the sourcePath and prepends the destPath
            file2=new File(destDir, file.getPath().substring(sourceDir.getPath().length()));
            if(file.isDirectory() && !file.getName().equals(notdir)){
                translateFiles(file, file2, sourceDir, translationFile, extensions);
            }
        }
        return true;
    }

    public static HashMap<String, String> langMap(final serverSwitch<?> env) {
        final String[] ms = env.getConfig("locale.lang", "").split(",");
        final HashMap<String, String> map = new HashMap<String, String>();
        int p;
        for (int i = 0; i < ms.length; i++) {
            p = ms[i].indexOf("/");
            if (p > 0)
                map.put(ms[i].substring(0, p), ms[i].substring(p + 1));
        }
        return map;
    }
        
    public static boolean changeLang(final serverSwitch<?> env, final String langPath, final String lang) {
        if ((lang.equals("default")) || (lang.equals("default.lng"))) {
            env.setConfig("locale.language", "default");
            return true;
        }
        final String htRootPath = env.getConfig("htRootPath", "htroot");
        final File sourceDir = new File(env.getRootPath(), htRootPath);
        final File destDir = new File(env.getConfigPath("locale.translated_html","DATA/LOCALE/htroot"), lang.substring(0, lang.length() - 4));// cut
        // .lng
        //File destDir = new File(env.getRootPath(), htRootPath + "/locale/" + lang.substring(0, lang.length() - 4));// cut
        // .lng
        final File translationFile = new File(langPath, lang);

        //if (translator.translateFiles(sourceDir, destDir, translationFile, "html")) {
        if(translator.translateFilesRecursive(sourceDir, destDir,
        translationFile, "html,template,inc", "locale")){
            env.setConfig("locale.language", lang.substring(0, lang.length() - 4));
            yFormatter.setLocale(env.getConfig("locale.language", "en"));
            try {
                final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                bw.write(env.getConfig("svnRevision", "Error getting Version"));
                bw.close();
            } catch (final IOException e) {
                // Error
            }
            return true;
        }
        return false;
    }
}
