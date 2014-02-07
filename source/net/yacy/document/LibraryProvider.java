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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.geo.GeonamesLocation;
import net.yacy.cora.geo.OpenGeoDBLocation;
import net.yacy.cora.geo.OverarchingLocation;
import net.yacy.cora.language.synonyms.AutotaggingLibrary;
import net.yacy.cora.language.synonyms.SynonymLibrary;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.URLRewriterLibrary;
import net.yacy.kelondro.util.FileUtils;

public class LibraryProvider {

    public static final String path_to_source_dictionaries = "source";
    public static final String path_to_did_you_mean_dictionaries = "didyoumean";
    public static final String path_to_autotagging_dictionaries = "autotagging";
    public static final String path_to_synonym_dictionaries = "synonyms";
    public static final String path_to_rewriter_dictionaries = "rewriter";

    public static final String disabledExtension = ".disabled";

    public static WordCache dymLib = new WordCache(null);
    public static AutotaggingLibrary autotagging = null;
    public static SynonymLibrary synonyms = null;
    public static URLRewriterLibrary urlRewriter = null;
    public static OverarchingLocation geoLoc = new OverarchingLocation();
    private static File dictSource = null;
    private static File dictRoot = null;

    public static enum Dictionary {
        GEODB0( "geo0", "http://downloads.sourceforge.net/project/opengeodb/Data/0.2.5a/opengeodb-0.2.5a-UTF8-sql.gz" ),
        GEODB1( "geo1", "http://fa-technik.adfc.de/code/opengeodb/dump/opengeodb-02624_2011-10-17.sql.gz" ),
        GEON0( "geon0", "http://download.geonames.org/export/dump/cities1000.zip" ),
        GEON1( "geon1", "http://download.geonames.org/export/dump/cities5000.zip" ),
        GEON2( "geon2", "http://download.geonames.org/export/dump/cities15000.zip" ),
        DRW0( "drw0", "http://www.ids-mannheim.de/kl/derewo/derewo-v-100000t-2009-04-30-0.1.zip" ),
        PND0( "pnd0", "http://downloads.dbpedia.org/3.7-i18n/de/pnd_de.nt.bz2" );

        public String nickname, url, filename;

        private Dictionary(final String nickname, final String url) {
            try {
                this.filename = (new MultiProtocolURL(url)).getFileName();
            } catch (final MalformedURLException e ) {
                assert false;
            }
            this.nickname = nickname;
            this.url = url;
        }

        public File file() {
            return new File(dictSource, this.filename);
        }

        public File fileDisabled() {
            return new File(dictSource, this.filename + disabledExtension);
        }
    }

    /**
     * initialize the LibraryProvider as static class. This assigns default paths, and initializes the
     * dictionary classes Additionally, if default dictionaries are given in the source path, they are
     * translated into the input format inside the DATA/DICTIONARIES directory
     *
     * @param pathToSource
     * @param pathToDICTIONARIES
     */
    public static void initialize(final File rootPath) {
        dictSource = new File(rootPath, path_to_source_dictionaries);
        if ( !dictSource.exists() ) {
            dictSource.mkdirs();
        }
        dictRoot = rootPath;

        // initialize libraries
        initAutotagging();
        activateDeReWo();
        initDidYouMean();
        initSynonyms();
        initRewriter();
        integrateOpenGeoDB();
        integrateGeonames0(-1);
        integrateGeonames1(-1);
        integrateGeonames2(100000);
        Set<String> allTags = new HashSet<String>() ;
        allTags.addAll(autotagging.allTags()); // we must copy this into a clone to prevent circularity
        autotagging.addPlaces(geoLoc);
        //autotagging.addDictionaries(dymLib.getDictionaries()); // strange results with this: normal word lists are 'too full'
        WordCache.learn(allTags);
    }

    public static void integrateOpenGeoDB() {
        final File geo1 = Dictionary.GEODB1.file();
        final File geo0 = Dictionary.GEODB0.file();
        if ( geo1.exists() ) {
            if ( geo0.exists() ) {
                geo0.renameTo(Dictionary.GEODB0.fileDisabled());
            }
            geoLoc.activateLocation(Dictionary.GEODB1.nickname, new OpenGeoDBLocation(geo1, dymLib));
            return;
        }
        if ( geo0.exists() ) {
            geoLoc.activateLocation(Dictionary.GEODB0.nickname, new OpenGeoDBLocation(geo0, dymLib));
            return;
        }
    }

    public static void integrateGeonames0(long minPopulation) {
        final File geon = Dictionary.GEON0.file();
        if ( geon.exists() ) {
            geoLoc.activateLocation(Dictionary.GEON0.nickname, new GeonamesLocation(geon, dymLib, minPopulation));
            return;
        }
    }
    public static void integrateGeonames1(long minPopulation) {
        final File geon = Dictionary.GEON1.file();
        if ( geon.exists() ) {
            geoLoc.activateLocation(Dictionary.GEON1.nickname, new GeonamesLocation(geon, dymLib, minPopulation));
            return;
        }
    }
    public static void integrateGeonames2(long minPopulation) {
        final File geon = Dictionary.GEON2.file();
        if ( geon.exists() ) {
            geoLoc.activateLocation(Dictionary.GEON2.nickname, new GeonamesLocation(geon, dymLib, minPopulation));
            return;
        }
    }    
    public static void initDidYouMean() {
        final File dymDict = new File(dictRoot, path_to_did_you_mean_dictionaries);
        if ( !dymDict.exists() ) {
            dymDict.mkdirs();
        }
        dymLib = new WordCache(dymDict);
    }

    public static void initAutotagging() {
        final File autotaggingPath = new File(dictRoot, path_to_autotagging_dictionaries);
        if ( !autotaggingPath.exists() ) {
            autotaggingPath.mkdirs();
        }
        autotagging = new AutotaggingLibrary(autotaggingPath);
    }
    public static void initSynonyms() {
        final File synonymPath = new File(dictRoot, path_to_synonym_dictionaries);
        if ( !synonymPath.exists() ) {
            synonymPath.mkdirs();
        }
        synonyms = new SynonymLibrary(synonymPath);
    }
    public static void initRewriter() {
        final File rewriterPath = new File(dictRoot, path_to_rewriter_dictionaries);
        if ( !rewriterPath.exists() ) {
            rewriterPath.mkdirs();
        }
        urlRewriter = new URLRewriterLibrary(rewriterPath);
    }
    public static void activateDeReWo() {
        // translate input files (once..)
        final File dymDict = new File(dictRoot, path_to_did_you_mean_dictionaries);
        if ( !dymDict.exists() ) {
            dymDict.mkdirs();
        }
        final File derewoInput = LibraryProvider.Dictionary.DRW0.file();
        final File derewoOutput = new File(dymDict, derewoInput.getName() + ".words");
        if ( !derewoOutput.exists() && derewoInput.exists() ) {
            // create the translation of the derewo file (which is easy in this case)
            final ArrayList<String> derewo = loadDeReWo(derewoInput, true);
            try {
                writeWords(derewoOutput, derewo);
            } catch (final IOException e ) {
                ConcurrentLog.logException(e);
            }
        }
    }

    public static void deactivateDeReWo() {
        final File dymDict = new File(dictRoot, path_to_did_you_mean_dictionaries);
        final File derewoInput = LibraryProvider.Dictionary.DRW0.file();
        final File derewoOutput = new File(dymDict, derewoInput.getName() + ".words");
        FileUtils.deletedelete(derewoOutput);
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
        for ( final String t : list ) {
            s.add(t);
        }
        return s;
    }

    private static void writeWords(final File f, final ArrayList<String> list) throws IOException {
        final Set<String> s = sortUnique(list);
        final PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        for ( final String t : s ) {
            w.println(t);
        }
        w.close();
    }

    private static ArrayList<String> loadDeReWo(final File file, final boolean toLowerCase) {
        final ArrayList<String> list = new ArrayList<String>();

        // get the zip file entry from the file
        InputStream derewoTxtEntry;
        try {
            final ZipFile zip = new ZipFile(file);
            derewoTxtEntry = zip.getInputStream(zip.getEntry("derewo-v-100000t-2009-04-30-0.1"));
        } catch (final ZipException e ) {
            ConcurrentLog.logException(e);
            return list;
        } catch (final IOException e ) {
            ConcurrentLog.logException(e);
            return list;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(derewoTxtEntry, "UTF-8"));
            String line;

            // read until text starts
            while ( (line = reader.readLine()) != null ) {
                if ( line.startsWith("# -----") ) {
                    break;
                }
            }
            // read empty line
            line = reader.readLine();

            // read lines
            int p;
            //int c;
            String w;
            while ( (line = reader.readLine()) != null ) {
                line = line.trim();
                p = line.indexOf(' ', 0);
                if ( p > 0 ) {
                    //c = Integer.parseInt(line.substring(p + 1));
                    //if (c < 1) continue;
                    w =
                        (toLowerCase) ? line.substring(0, p).trim().toLowerCase() : line
                            .substring(0, p)
                            .trim();
                    if ( w.length() < 4 ) {
                        continue;
                    }
                    list.add(w);
                }
            }
            reader.close();
        } catch (final IOException e ) {
            ConcurrentLog.logException(e);
        } finally {
            if ( reader != null ) {
                try {
                    reader.close();
                } catch (final Exception e ) {
                }
            }
        }
        return list;
    }

    public static void main(final String[] args) {
        final File here = new File("dummy").getParentFile();
        initialize(new File(here, "DATA/DICTIONARIES"));
        System.out.println("dymDict-size = " + dymLib.size());
        final Set<StringBuilder> r = dymLib.recommend(new StringBuilder("da"));
        for ( final StringBuilder s : r ) {
            System.out.println("$ " + s);
        }
        System.out.println("recommendations: " + r.size());
    }
}
