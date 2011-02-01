/**
 *  GeonamesLocalization.java
 *  Copyright 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 16.05.2010 on http://yacy.net
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

package net.yacy.document.geolocalization;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.yacy.kelondro.logging.Log;

public class GeonamesLocalization implements Localization {

    /*
        The main 'geoname' table has the following fields :
        ---------------------------------------------------
        geonameid         : integer id of record in geonames database
        name              : name of geographical point (utf8) varchar(200)
        asciiname         : name of geographical point in plain ascii characters, varchar(200)
        alternatenames    : alternatenames, comma separated varchar(5000)
        latitude          : latitude in decimal degrees (wgs84)
        longitude         : longitude in decimal degrees (wgs84)
        feature class     : see http://www.geonames.org/export/codes.html, char(1)
        feature code      : see http://www.geonames.org/export/codes.html, varchar(10)
        country code      : ISO-3166 2-letter country code, 2 characters
        cc2               : alternate country codes, comma separated, ISO-3166 2-letter country code, 60 characters
        admin1 code       : fipscode (subject to change to iso code), see exceptions below, see file admin1Codes.txt for display names of this code; varchar(20)
        admin2 code       : code for the second administrative division, a county in the US, see file admin2Codes.txt; varchar(80) 
        admin3 code       : code for third level administrative division, varchar(20)
        admin4 code       : code for fourth level administrative division, varchar(20)
        population        : bigint (8 byte int) 
        elevation         : in meters, integer
        gtopo30           : average elevation of 30'x30' (ca 900mx900m) area in meters, integer
        timezone          : the timezone id (see file timeZone.txt)
        modification date : date of last modification in yyyy-MM-dd format
     */
    
    // use a collator to relax when distinguishing between lowercase und uppercase letters
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }
    
    private final Map<Integer, Location>  id2loc;
    private final TreeMap<String, List<Integer>> name2ids;
    private final File file;
    
    public GeonamesLocalization(final File file) {
        // this is a processing of the cities1000.zip file from http://download.geonames.org/export/dump/

        this.file = file;
        this.id2loc    = new HashMap<Integer, Location>();
        this.name2ids  = new TreeMap<String, List<Integer>>(insensitiveCollator);
        
        if (file == null || !file.exists()) return;
        BufferedReader reader;
        try {
            ZipFile zf = new ZipFile(file);
            ZipEntry ze = zf.getEntry("cities1000.txt");
            InputStream is = zf.getInputStream(ze);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        } catch (IOException e) {
            Log.logException(e);
            return;
        }

        // when an error occurs after this line, just accept it and work on
        try {
            String line;
            String[] fields;
            Set<String> locnames;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) continue;
                fields = line.split("\t");
                int id = Integer.parseInt(fields[0]);
                locnames = new HashSet<String>();
                locnames.add(fields[1]);
                locnames.add(fields[2]);
                for (String s: fields[3].split(",")) locnames.add(s);
                Location c = new Location(Float.parseFloat(fields[5]), Float.parseFloat(fields[4]), fields[1]);
                c.setPopulation((int) Long.parseLong(fields[14]));
                this.id2loc.put(id, c);
                for (String name: locnames) {
                    List<Integer> locs = this.name2ids.get(name);
                    if (locs == null) locs = new ArrayList<Integer>(1);
                    locs.add(id);
                    this.name2ids.put(name, locs);
                }
            }
        } catch (IOException e) {
            Log.logException(e);
        }
    }

    public int locations() {
        return id2loc.size();
    }
    
    public TreeSet<Location> find(String anyname, boolean locationexact) {
        Set<Integer> r = new HashSet<Integer>();
        List<Integer> c;
        if (locationexact) {
            c = this.name2ids.get(anyname); if (c != null) r.addAll(c);
        } else {
            SortedMap<String, List<Integer>> cities = this.name2ids.tailMap(anyname);
            for (Map.Entry<String, List<Integer>> e: cities.entrySet()) {
                if (e.getKey().toLowerCase().startsWith(anyname.toLowerCase())) r.addAll(e.getValue()); else break;
            }
        }
        TreeSet<Location> a = new TreeSet<Location>();
        for (Integer e: r) {
            Location w = this.id2loc.get(e);
            if (w != null) a.add(w);
        }
        return a;
    }

    public Set<String> recommend(String s) {
        Set<String> a = new HashSet<String>();
        s = s.trim().toLowerCase();
        if (s.length() == 0) return a;
        SortedMap<String, List<Integer>> t = this.name2ids.tailMap(s);
        for (String r: t.keySet()) {
            r = r.toLowerCase();
            if (r.startsWith(s)) a.add(r); else break;
        }
        return a;
    }

    public String nickname() {
        return this.file.getName();
    }
    
    public int hashCode() {
        return this.nickname().hashCode();
    }
    
    public boolean equals(Object other) {
        if (!(other instanceof Localization)) return false;
        return this.nickname().equals(((Localization) other).nickname()); 
    }
}
