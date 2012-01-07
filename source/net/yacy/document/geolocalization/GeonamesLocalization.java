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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.yacy.document.StringBuilderComparator;
import net.yacy.kelondro.logging.Log;

public class GeonamesLocalization implements Localization
{

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

    private final Map<Integer, Location> id2loc;
    private final TreeMap<StringBuilder, List<Integer>> name2ids;
    private final File file;

    public GeonamesLocalization(final File file) {
        // this is a processing of the cities1000.zip file from http://download.geonames.org/export/dump/

        this.file = file;
        this.id2loc = new HashMap<Integer, Location>();
        this.name2ids =
            new TreeMap<StringBuilder, List<Integer>>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);

        if ( file == null || !file.exists() ) {
            return;
        }
        BufferedReader reader;
        try {
            final ZipFile zf = new ZipFile(file);
            final ZipEntry ze = zf.getEntry("cities1000.txt");
            final InputStream is = zf.getInputStream(ze);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        } catch ( final IOException e ) {
            Log.logException(e);
            return;
        }

        // when an error occurs after this line, just accept it and work on
        try {
            String line;
            String[] fields;
            Set<StringBuilder> locnames;
            while ( (line = reader.readLine()) != null ) {
                if ( line.length() == 0 ) {
                    continue;
                }
                fields = line.split("\t");
                final int id = Integer.parseInt(fields[0]);
                locnames = new HashSet<StringBuilder>();
                locnames.add(new StringBuilder(fields[1]));
                locnames.add(new StringBuilder(fields[2]));
                for ( final String s : fields[3].split(",") ) {
                    locnames.add(new StringBuilder(s));
                }
                final Location c =
                    new Location(Float.parseFloat(fields[5]), Float.parseFloat(fields[4]), fields[1]);
                c.setPopulation((int) Long.parseLong(fields[14]));
                this.id2loc.put(id, c);
                for ( final StringBuilder name : locnames ) {
                    List<Integer> locs = this.name2ids.get(name);
                    if ( locs == null ) {
                        locs = new ArrayList<Integer>(1);
                    }
                    locs.add(id);
                    this.name2ids.put(name, locs);
                }
            }
        } catch ( final IOException e ) {
            Log.logException(e);
        }
    }

    @Override
    public int locations() {
        return this.id2loc.size();
    }

    @Override
    public TreeSet<Location> find(final String anyname, final boolean locationexact) {
        final Set<Integer> r = new HashSet<Integer>();
        List<Integer> c;
        final StringBuilder an = new StringBuilder(anyname);
        if ( locationexact ) {
            c = this.name2ids.get(an);
            if ( c != null ) {
                r.addAll(c);
            }
        } else {
            final SortedMap<StringBuilder, List<Integer>> cities = this.name2ids.tailMap(an);
            for ( final Map.Entry<StringBuilder, List<Integer>> e : cities.entrySet() ) {
                if ( StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(e.getKey(), an) ) {
                    r.addAll(e.getValue());
                } else {
                    break;
                }
            }
        }
        final TreeSet<Location> a = new TreeSet<Location>();
        for ( final Integer e : r ) {
            final Location w = this.id2loc.get(e);
            if ( w != null ) {
                a.add(w);
            }
        }
        return a;
    }

    /**
     * produce a set of location names
     * @return a set of names
     */
    @Override
    public Set<String> locationNames() {
        Set<String> locations = new HashSet<String>();
        Set<StringBuilder> l = this.name2ids.keySet();
        for (StringBuilder s: l) {
            locations.add(s.toString());
        }
        return locations;
    }

    @Override
    public Set<String> recommend(final String s) {
        final Set<String> a = new HashSet<String>();
        final StringBuilder an = new StringBuilder(s);
        if ( s.length() == 0 ) {
            return a;
        }
        final SortedMap<StringBuilder, List<Integer>> t = this.name2ids.tailMap(an);
        for ( final StringBuilder r : t.keySet() ) {
            if ( StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(r, an) ) {
                a.add(r.toString());
            } else {
                break;
            }
        }
        return a;
    }

    @Override
    public Set<StringBuilder> recommend(final StringBuilder s) {
        final Set<StringBuilder> a = new HashSet<StringBuilder>();
        if ( s.length() == 0 ) {
            return a;
        }
        final SortedMap<StringBuilder, List<Integer>> t = this.name2ids.tailMap(s);
        for ( final StringBuilder r : t.keySet() ) {
            if ( StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(r, s) ) {
                a.add(r);
            } else {
                break;
            }
        }
        return a;
    }

    @Override
    public String nickname() {
        return this.file.getName();
    }

    @Override
    public int hashCode() {
        return nickname().hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        if ( !(other instanceof Localization) ) {
            return false;
        }
        return nickname().equals(((Localization) other).nickname());
    }
}
