// LibraryProvider.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 01.10.2009 on http://yacy.net
//
// This is a part of YaCy
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.yacy.document.geolocalization.OpenGeoDB;

public class LibraryProvider {

    private static final String path_to_source_dictionaries = "source";
    private static final String path_to_did_you_mean_dictionaries = "didyoumean";
    
    public static DidYouMeanLibrary dymLib = new DidYouMeanLibrary(null);
    public static OpenGeoDB geoDB = new OpenGeoDB(null);
    public static File dictSource = null;
    public static File dictRoot = null;
    
    /**
     * initialize the LibraryProvider as static class.
     * This assigns default paths, and initializes the dictionary classes
     * Additionally, if default dictionaries are given in the source path,
     * they are translated into the input format inside the DATA/DICTIONARIES directory
     * 
     * @param pathToSource
     * @param pathToDICTIONARIES
     */
    public static void initialize(File rootPath) {
    	dictSource = new File(rootPath, path_to_source_dictionaries);
    	if (!dictSource.exists()) dictSource.mkdirs();
    	dictRoot = rootPath;
        
        // initialize libraries
    	integrateDeReWo();
    	initDidYouMean();
    	integrateOpenGeoDB();
    }
    
    public static void integrateOpenGeoDB() {
        File ogdb = new File(dictSource, "opengeodb-0.2.5a-UTF8-sql.gz");
        if (ogdb.exists()) {
        	geoDB = new OpenGeoDB(ogdb);
        	return;
        }
        ogdb = new File(dictSource, "opengeodb-02513_2007-10-02.sql.gz");
        if (ogdb.exists()) {
        	geoDB = new OpenGeoDB(ogdb);
        	return;
        }
        ogdb = new File(dictSource, "opengeodb-02513_2007-10-02.sql");
        if (ogdb.exists()) {
        	geoDB = new OpenGeoDB(ogdb);
        	return;
        }
    }
    
    public static void initDidYouMean() {
    	File dymDict = new File(dictRoot, path_to_did_you_mean_dictionaries);
        if (!dymDict.exists()) dymDict.mkdirs();
        dymLib = new DidYouMeanLibrary(dymDict);
    }
    
    public static void integrateDeReWo() {
        // translate input files (once..)
        File dymDict = new File(dictRoot, path_to_did_you_mean_dictionaries);
        if (!dymDict.exists()) dymDict.mkdirs();
        File pathToSource = new File(dictRoot, path_to_source_dictionaries);
        File derewoInput = new File(pathToSource, "derewo-v-30000g-2007-12-31-0.1.txt");
        File derewoOutput = new File(dymDict, "derewo-v-30000g-2007-12-31-0.1.words");
        if (!derewoOutput.exists() && derewoInput.exists()) {
            // create the translation of the derewo file (which is easy in this case)
            ArrayList<String> derewo = loadDeReWo(derewoInput, true);
            try {
                writeWords(derewoOutput, derewo);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /*
    private static ArrayList<String> loadList(final File file, String comment, boolean toLowerCase) {
        final ArrayList<String> list = new ArrayList<String>();
        if (!file.exists()) return list;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if ((line.length() > 0) && (!(line.startsWith(comment)))) {
                    list.add((toLowerCase) ? line.trim().toLowerCase() : line.trim());
                }
            }
            reader.close();
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {}
        }
        return list;
    }
    */
    
    private static Set<String> sortUnique(List<String> list) {
        TreeSet<String> s = new TreeSet<String>();
        for (String t: list) s.add(t);
        return s;
    }
    
    private static void writeWords(File f, ArrayList<String> list) throws IOException {
        Set<String> s = sortUnique(list);
        PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        for (String t: s) w.println(t);
        w.close();
    }
    
    private static ArrayList<String> loadDeReWo(final File file, boolean toLowerCase) {
        final ArrayList<String> list = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            
            // read until text starts
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("-----")) break;
            }
            // read empty line
            line = reader.readLine();
            
            // read lines
            int p;
            int c;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                p = line.indexOf("\t");
                if (p > 0) {
                    c = Integer.parseInt(line.substring(p + 1));
                    if (c < 1) continue;
                    list.add((toLowerCase) ? line.substring(0, p).trim().toLowerCase() : line.substring(0, p).trim());
                }
            }
            reader.close();
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {}
        }
        return list;
    }
    
    public static void main(String[] args) {
        File here = new File("dummy").getParentFile();
        initialize(new File(here, "DATA/DICTIONARIES"));
        System.out.println("dymDict-size = " + dymLib.size());
        Set<String> r = dymLib.recommend("da");
        for (String s: r) {
            System.out.println("$ " + s);
        }
        System.out.println("recommendations: " + r.size());
    }
}
