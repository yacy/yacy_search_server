/**
 *  GeoPoint
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

package net.yacy.cora.geo;

/**
 * Geolocation storage may vary using different data structures for the points.
 * The reason to have different implementation is to save memory for the point storage.
 * With each version of a point storage comes a accuracy level which can be returned by the object.
 */
public interface GeoPoint {

    public static final double meter = 90.0d / 1.0e7d; // this is actually the definition of 'meter': 10 million meter shall be the distance from the equator to the pole

    /**
     * get the latitude of the point
     * @return
     */
    public double lat();

    /**
     * get the longitude of the point
     * @return
     */
    public double lon();

    /**
     * get the implementation-dependent accuracy of the latitude
     * @return
     */
    public double accuracyLat();

    /**
     * get the implementation-dependent accuracy of the longitude
     * @return
     */
    public double accuracyLon();

    /**
     * compute the hash code of a coordinate
     * this produces identical hash codes for locations that are close to each other
     */
    @Override
    public int hashCode();

    /**
     * equality test that is needed to use the class inside HashMap/HashSet
     */
    @Override
    public boolean equals(final Object o);

    /**
     * printout format of the point
     * @return
     */
    @Override
    public String toString();

}
