/**
 *  LibraryProvider.java
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 01.10.2009 on http://yacy.net
 *  
 *  This file is part of YaCy Content Integration
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document;

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

import net.yacy.document.geolocalization.GeonamesLocalization;
import net.yacy.document.geolocalization.OpenGeoDBLocalization;
import net.yacy.document.geolocalization.OverarchingLocalization;
import net.yacy.kelondro.logging.Log;

public class LibraryProvider {

    private static final String path_to_source_dictionaries = "source";
    private static final String path_to_did_you_mean_dictionaries = "didyoumean";
    
    public static final String disabledExtension = ".disabled";
    
    public static WordCache dymLib = new WordCache(null);
    public static OverarchingLocalization geoLoc = new OverarchingLocalization();
    private static File dictSource = null;
    private static File dictRoot = null;
    
    public static enum Dictionary {
        GEODB0("geo0",
             "http://downloads.sourceforge.net/project/opengeodb/Data/0.2.5a/opengeodb-0.2.5a-UTF8-sql.gz",
             "opengeodb-0.2.5a-UTF8-sql.gz"),
        GEODB1("geo1",
             "http://fa-technik.adfc.de/code/opengeodb/dump/opengeodb-02621_2010-03-16.sql.gz",
             "opengeodb-02621_2010-03-16.sql.gz"),
        GEON0("geon0",
              "http://download.geonames.org/export/dump/cities1000.zip",
              "cities1000.zip");

        public String nickname, url, filename;
        private Dictionary(String nickname, String url, String filename) {
            this.nickname = nickname;
            this.url = url;
            this.filename = filename;
        }

        public File file() {
            return new File(dictSource, filename);
        }
        public File fileDisabled() {
            return new File(dictSource, filename + disabledExtension);
        }
    }

    /**
     * initialize the LibraryProvider as static class.
     * This assigns default paths, and initializes the dictionary classes
     * Additionally, if default dictionaries are given in the source path,
     * they are translated into the input format inside the DATA/DICTIONARIES directory
     * 
     * @param pathToSource
     * @param pathToDICTIONARIES
     */
    public static void initialize(final File rootPath) {
    	dictSource = new File(rootPath, path_to_source_dictionaries);
    	if (!dictSource.exists()) dictSource.mkdirs();
    	dictRoot = rootPath;
        
        // initialize libraries
    	integrateDeReWo();
    	initDidYouMean();
    	integrateOpenGeoDB();
    	integrateGeonames();
    }
    
    public static void integrateOpenGeoDB() {
        File geo1 = Dictionary.GEODB1.file();
        File geo0 = Dictionary.GEODB0.file();
        if (geo1.exists()) {
            if (geo0.exists()) geo0.renameTo(Dictionary.GEODB0.fileDisabled());
            geoLoc.addLocalization(Dictionary.GEODB1.nickname, new OpenGeoDBLocalization(geo1, false));
            return;
        }
        if (geo0.exists()) {
            geoLoc.addLocalization(Dictionary.GEODB0.nickname, new OpenGeoDBLocalization(geo0, false));
            return;
        }
    }
    
    public static void integrateGeonames() {
        File geon = Dictionary.GEON0.file();
        if (geon.exists()) {
            geoLoc.addLocalization(Dictionary.GEON0.nickname, new GeonamesLocalization(geon));
            return;
        }
    }
    
    public static void initDidYouMean() {
    	final File dymDict = new File(dictRoot, path_to_did_you_mean_dictionaries);
        if (!dymDict.exists()) dymDict.mkdirs();
        dymLib = new WordCache(dymDict);
    }
    
    public static void integrateDeReWo() {
        // translate input files (once..)
        final File dymDict = new File(dictRoot, path_to_did_you_mean_dictionaries);
        if (!dymDict.exists()) dymDict.mkdirs();
        final File pathToSource = new File(dictRoot, path_to_source_dictionaries);
        final File derewoInput = new File(pathToSource, "derewo-v-30000g-2007-12-31-0.1.txt");
        final File derewoOutput = new File(dymDict, "derewo-v-30000g-2007-12-31-0.1.words");
        if (!derewoOutput.exists() && derewoInput.exists()) {
            // create the translation of the derewo file (which is easy in this case)
            final ArrayList<String> derewo = loadDeReWo(derewoInput, true);
            try {
                writeWords(derewoOutput, derewo);
            } catch (IOException e) {
                Log.logException(e);
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
            Log.logException(e);
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {}
        }
        return list;
    }
    */
    
    private static Set<String> sortUnique(final List<String> list) {
        final Set<String> s = new TreeSet<String>();
        for (final String t: list) s.add(t);
        return s;
    }
    
    private static void writeWords(final File f, final ArrayList<String> list) throws IOException {
        final Set<String> s = sortUnique(list);
        final PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        for (final String t: s) w.println(t);
        w.close();
    }
    
    private static ArrayList<String> loadDeReWo(final File file, final boolean toLowerCase) {
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
            Log.logException(e);
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
