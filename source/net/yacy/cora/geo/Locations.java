/**
 *  Localization.java
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


package net.yacy.cora.geo;

import java.util.Set;
import java.util.TreeSet;

/**
 * location interface
 * @author Michael Peter Christen
 *
 */
public interface Locations {

    /**
     * the number of locations that this localization stores
     * @return the number of locations
     */
    public int size();

    /**
     * @return true if the number of locations that this localization stores is empty
     */
    public boolean isEmpty();

    /**
     * find a location by name
     * @param anyname - a name of a location
     * @param locationexact - if true, then only exact matched with the location are returned. if false also partially matching names
     * @return a set of locations, ordered by population (if this information is given)
     */
    public TreeSet<GeoLocation> find(String anyname, boolean locationexact);

    /**
     * produce a set of location names
     * @return a set of names
     */
    public Set<String> locationNames();

    /**
     * recommend a set of names according to a given name
     * @param s a possibly partially matching name
     * @return a set of names that match with the given name using the local dictionary of names
     */
    public Set<String> recommend(String s);

    /**
     * recommend a set of names according to a given name
     * @param s a possibly partially matching name
     * @return a set of names that match with the given name using the local dictionary of names
     */
    public Set<StringBuilder> recommend(StringBuilder s);

    /**
     * return an nickname of the localization service
     * @return the nickname
     */
    public String nickname();

    /**
     * hashCode that must be used to distinguish localization services in hash sets
     * @return the hash code, may be derived from the nickname
     */
    @Override
    public int hashCode();

    /**
     * compare localization services; to be used for hash sets with localization services
     * @param other
     * @return true if both objects are localization services and have the same nickname
     */
    @Override
    public boolean equals(Object other);
}
