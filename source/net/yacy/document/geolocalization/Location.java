/**
 *  Location.java
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 08.10.2009 on http://yacy.net
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

import java.util.Comparator;


public class Location extends Coordinates implements Comparable<Location>, Comparator<Location> {

    private String name;
    private int population;
    
    public Location(float lon, float lat) {
        super(lon, lat);
        this.name = null;
        this.population = 0;
    }
    
    public Location(float lon, float lat, String name) {
        super(lon, lat);
        this.name = name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getName() {
        return this.name;
    }
    
    public void setPopulation(int population) {
        this.population = population;
    }
    
    public int getPopulation() {
        return this.population;
    }
    
    public boolean equals(Object loc) {
        if (!(loc instanceof Location)) return false;
        if (this.name == null || ((Location) loc).name == null) return super.equals(loc);
        return super.equals(loc) && this.name.toLowerCase().equals(((Location) loc).name.toLowerCase());
    }

    /**
     * comparator that is needed to use the object inside TreeMap/TreeSet
     * a Location is smaller than another if it has a _greater_ population
     * this order is used to get sorted lists of locations where the first elements
     * have the greatest population
     */
    public int compareTo(Location o) {
        if (this.equals(o)) return 0;
        long s = (ph(this.getPopulation()) << 30) + this.hashCode();
        long t = (ph(o.getPopulation()) << 30) + o.hashCode();
        if (s > t) return -1;
        if (s < t) return  1;
        return 0;
    }
    
    private long ph(int population) {
        if (population > 10000) population -= 10000;
        return (long) population;
    }
    
    public int compare(Location o1, Location o2) {
        return o1.compareTo(o2);
    }
    
}
