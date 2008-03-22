// translator.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
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
	public static String translate(String source, Hashtable<String, String> translationList){
		Enumeration<String> keys = translationList.keys();
		String result = source;
		String key = "";
		while(keys.hasMoreElements()){
			key = keys.nextElement();
			Pattern pattern = Pattern.compile(key);
			Matcher matcher = pattern.matcher(result);
			if (matcher.find()) {
				result = matcher.replaceAll(translationList.get(key));
			} else {
				//Filename not available, but it will be printed in Log 
				//after all untranslated Strings as "Translated file: "
				serverLog.logFine("TRANSLATOR", "Unused String: "+key); 
			}
		}
		return result;
	}
	/**
	 * Load multiple translationLists from one File. Each List starts with #File: relative/path/to/file
	 * @param translationFile the File, which contains the Lists
	 * @return a Hashtable, which contains for each File a Hashtable with translations.
	 */
	public static Hashtable<String, Hashtable<String, String>> loadTranslationsLists(File translationFile){
		Hashtable<String, Hashtable<String, String>> lists = new Hashtable<String, Hashtable<String, String>>(); //list of translationLists for different files.
		Hashtable<String, String> translationList = new Hashtable<String, String>(); //current Translation Table
        
		ArrayList<String> list = listManager.getListArray(translationFile);
		Iterator<String> it = list.iterator();
		String line = "";
		String[] splitted;
		String forFile="";

		while(it.hasNext()){
			line = (String)it.next();
			if(! line.startsWith("#")){
				splitted = line.split("==", 2);
				if(splitted.length == 2){
					translationList.put(splitted[0], splitted[1]);
				}else{ //Invalid line
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

	public static boolean translateFile(File sourceFile, File destFile, File translationFile){
	    Hashtable<String, String> translationList = loadTranslationsLists(translationFile).get(sourceFile.getName());
		return translateFile(sourceFile, destFile, translationList);
	}
	
	public static boolean translateFile(File sourceFile, File destFile, Hashtable<String, String> translationList){

		String content = "";
		String line = "";
        BufferedReader br = null;
		try{
			br = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFile),"UTF-8"));
			while( (line = br.readLine()) != null){
				content += line + de.anomic.server.serverCore.CRLF_STRING;
			}
			br.close();
		}catch(IOException e){
			return false;
		} finally {
            if (br!=null)try{br.close();}catch(Exception e){}
        }
        
		content = translate(content, translationList);
        BufferedWriter bw = null;
		try{
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destFile),"UTF-8"));
			bw.write(content);
			bw.close();
		}catch(IOException e){
			return false;
		} finally {
            if(bw!=null)try{bw.close();}catch(Exception e){}
        }
		
		return true;
	}

	public static boolean translateFiles(File sourceDir, File destDir, File baseDir, File translationFile, String extensions){
			Hashtable<String, Hashtable<String, String>> translationLists = loadTranslationsLists(translationFile);
			return translateFiles(sourceDir, destDir, baseDir, translationLists, extensions);
	}

	public static boolean translateFiles(File sourceDir, File destDir, File baseDir, Hashtable<String, Hashtable<String, String>> translationLists, String extensions){
		destDir.mkdirs();
		File[] sourceFiles = sourceDir.listFiles();
        Vector<String> exts=listManager.string2vector(extensions);
        boolean rightExtension;
        Iterator<String> it;
        String relativePath;
		for(int i=0;i<sourceFiles.length;i++){
             it=exts.iterator();
             rightExtension=false;
             while(it.hasNext()){
                 if(sourceFiles[i].getName().endsWith((String) it.next())){
                     rightExtension=true;
                     break;
                 }
             }
			if(rightExtension){
                try{
                    relativePath=sourceFiles[i].getAbsolutePath().substring(baseDir.getAbsolutePath().length()+1); //+1 to get the "/"
                    relativePath = relativePath.replace(File.separatorChar, '/');
                }catch(IndexOutOfBoundsException e){
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
				}else{
						//serverLog.logInfo("TRANSLATOR", "No translation for file: "+relativePath);
				}
			}

		}
		return true;
	}

    public static boolean translateFilesRecursive(File sourceDir, File destDir, File translationFile, String extensions, String notdir){
        ArrayList<File> dirList=listManager.getDirsRecursive(sourceDir, notdir);
        dirList.add(sourceDir);
        Iterator<File> it=dirList.iterator();
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

    public static HashMap<String, String> langMap(serverSwitch<?> env) {
        String[] ms = env.getConfig("locale.lang", "").split(",");
        HashMap<String, String> map = new HashMap<String, String>();
        int p;
        for (int i = 0; i < ms.length; i++) {
            p = ms[i].indexOf("/");
            if (p > 0)
                map.put(ms[i].substring(0, p), ms[i].substring(p + 1));
        }
        return map;
    }
        
    public static boolean changeLang(serverSwitch<?> env, String langPath, String lang) {
        if ((lang.equals("default")) || (lang.equals("default.lng"))) {
            env.setConfig("locale.language", "default");
            return true;
        }
        String htRootPath = env.getConfig("htRootPath", "htroot");
        File sourceDir = new File(env.getRootPath(), htRootPath);
        File destDir = new File(env.getConfigPath("locale.translated_html","DATA/LOCALE/htroot"), lang.substring(0, lang.length() - 4));// cut
        // .lng
        //File destDir = new File(env.getRootPath(), htRootPath + "/locale/" + lang.substring(0, lang.length() - 4));// cut
        // .lng
        File translationFile = new File(langPath, lang);

        //if (translator.translateFiles(sourceDir, destDir, translationFile, "html")) {
        if(translator.translateFilesRecursive(sourceDir, destDir,
        translationFile, "html,template,inc", "locale")){
            env.setConfig("locale.language", lang.substring(0, lang.length() - 4));
            yFormatter.setLocale(env.getConfig("locale.language", "en"));
            try {
                BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                bw.write(env.getConfig("svnRevision", "Error getting Version"));
                bw.close();
            } catch (IOException e) {
                // Error
            }
            return true;
        }
        return false;
    }
}
