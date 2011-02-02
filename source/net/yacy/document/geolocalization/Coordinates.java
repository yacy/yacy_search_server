/**
 *  Coordinates.java
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 04.10.2009 on http://yacy.net
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

public class Coordinates {

	private static final float tenmeter = 90.0f / 1.0e6f;
	
    private final float lon, lat;
    
    public Coordinates(float lon, float lat) {
        this.lon = lon;
        this.lat = lat;
    }
    
    public float lon() {
        return this.lon;
    }
    
    public float lat() {
        return this.lat;
    }
    
    private static final double bits30 = new Double(1L << 30).doubleValue(); // this is about one billion (US)
    private static final double upscale = bits30 / 360.0;
    
    private static final int coord2int(double coord) {
        return (int) ((180.0 - coord) * upscale);
    }
    
    /**
     * compute the hash code of a coordinate
     * this produces identical hash codes for locations that are close to each other
     */
    public int hashCode() {
        return coord2int(this.lon) + (coord2int(this.lat) >> 15);
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
