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

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Enumeration;

/**
 * Wordlist based translator
 *
 * Uses a Property file with phrases or single words to translate a string or a file
 * */
public class translator {
	public static String translate(String source, Properties translationList){
		Enumeration keys = translationList.propertyNames();
		String result = source;
		String key = "";
		while(keys.hasMoreElements()){
			key = (String)keys.nextElement();
			result = result.replaceAll(key, translationList.getProperty(key));
			System.out.println("Replaced \""+key+"\" by \""+translationList.getProperty(key)+"\"");
		}
		return result;
	}
	
	public static boolean translateFile(File sourceFile, File destFile, File translationFile){
		Properties translationList = new Properties();
		try{
			translationList.load(new FileInputStream(translationFile));
			return translateFile(sourceFile, destFile, translationList);
		}catch(IOException e){
			return false;
		}
	}
	
	public static boolean translateFile(File sourceFile, File destFile, Properties translationList){

		String content = "";
		String line = "";
		try{
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFile)));
			while( (line = br.readLine()) != null){
				content += line + de.anomic.server.serverCore.crlfString;
			}
			br.close();
		}catch(IOException e){
			return false;
		}
		content = translate(content, translationList);
		try{
			BufferedWriter bw = new BufferedWriter(new FileWriter(destFile));
			bw.write(content);
			bw.close();
		}catch(IOException e){
			return false;
		}
		
		return true;
	}

	public static boolean translateFiles(File sourceDir, File destDir, File translationFile, String extension){
		Properties translationList = new Properties();
		try{
			translationList.load(new FileInputStream(translationFile));
			return translateFiles(sourceDir, destDir, translationList, extension);
		}catch(IOException e){
			return false;
		}
	}

	public static boolean translateFiles(File sourceDir, File destDir, Properties translationList, String extension){
		destDir.mkdirs();
		File[] sourceFiles = sourceDir.listFiles();
		for(int i=0;i<sourceFiles.length;i++){
			
			if(sourceFiles[i].getName().endsWith(extension)){
				if(translateFile(sourceFiles[i], new File(destDir, sourceFiles[i].getName()), translationList)){
					System.out.println("Translated File: "+ sourceFiles[i].getName());
				}else{
					System.err.println("File Error while translating File "+sourceFiles[i].getName());
				}
			}

		}
		return true;
	}


}
