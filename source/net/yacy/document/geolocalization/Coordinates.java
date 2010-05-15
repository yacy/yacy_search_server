/**
 *  Coordinates.java
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 04.10.2009 on http://yacy.net
 *  
 *  This file is part of YaCy Content Integration
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file COPYING.LESSER.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.geolocalization;

import java.util.Comparator;

public class Coordinates implements Comparable<Coordinates>, Comparator<Coordinates> {

	private static final double tenmeter = 90.0 / 1.0e6;
	
    private final double lon, lat;
    
    public Coordinates(double lon, double lat) {
        this.lon = lon;
        this.lat = lat;
    }
    
    public double lon() {
        return this.lon;
    }
    
    public double lat() {
        return this.lat;
    }
    
    private static final double bits30 = new Double(1L << 30).doubleValue(); // this is about one billion (US)
    private static final double upscale = bits30 / 360.0;
    
    private static final int coord2int(double coord) {
        return (int) ((coord + 180.0) * upscale);
    }
    
    /**
     * compute the hash code of a coordinate
     * this produces identical hash codes for locations that are close to each other
     */
    public int hashCode() {
        int lon1 = coord2int(this.lon) >> 15;
        int lat1 = coord2int(this.lat) >> 15;
        int h = (lon1 << 15) + lat1;
        //System.out.println("lon=" + this.lon + ", lat=" + this.lat + ", hash=" + h);
        return h;
    }
    
    /**
     * comparator that is needed to use the class inside TreeMap/TreeSet
     */
    public int compareTo(Coordinates o) {
    	if (this.equals(o)) return 0;
		int s = this.hashCode();
		int t = o.hashCode();
		if (s > t) return 1;
		if (s < t) return -1;
		return 0;
	}
    
    public int compare(Coordinates o1, Coordinates o2) {
        return o1.compareTo(o2);
    }
    
    /**
     * equality test that is needed to use the class inside HashMap/HashSet
     */
    public boolean equals(final Object o) {
    	if (!(o instanceof Coordinates)) return false;
    	Coordinates oo = (Coordinates) o;
    	if (this.lon == oo.lon && this.lat == oo.lat) return true;
    	// we access fuzzy values that are considered as equal if they are close to each other
    	return Math.abs(this.lon - oo.lon) < tenmeter && Math.abs(this.lat - oo.lat) < tenmeter;
    }
    
    public String toString() {
        return "[" + this.lon + "," + this.lat + "]";
    }
}
