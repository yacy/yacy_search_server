/**
 *  OverarchingLocalization.java
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class OverarchingLocation implements Locations {

    public static int MINIMUM_NAME_LENGTH = 4;
    private final Map<String, Locations> services;

    /**
     * create a new overarching localization object
     */
    public OverarchingLocation() {
        this.services = new HashMap<String, Locations>();
    }

    /**
     * add a localization service
     * @param nickname the nickname of the service
     * @param service the service
     */
    public void activateLocation(final String nickname, final Locations service) {
        this.services.put(nickname, service);
    }

    /**
     * remove a localization service
     * @param nickname
     */
    public void deactivateLocalization(final String nickname) {
        this.services.remove(nickname);
    }

    /**
     * the number of locations that this localization stores
     * @return the number of locations
     */
    @Override
    public int size() {
        int locations = 0;
        for (final Locations service: this.services.values()) {
            locations += service.size();
        }
        return locations;
    }

	@Override
	public boolean isEmpty() {
        for (final Locations service: this.services.values()) {
        	if (!service.isEmpty()) return false;
        }
        return true;
	}
	
    /**
     * find a location by name
     * @param anyname - a name of a location
     * @param locationexact - if true, then only exact matched with the location are returned. if false also partially matching names
     * @return a set of locations, ordered by population (if this information is given)
     */
    @Override
    public TreeSet<GeoLocation> find(final String anyname, final boolean locationexact) {
        final TreeSet<GeoLocation> locations = new TreeSet<GeoLocation>();
        for (final Locations service: this.services.values()) {
            locations.addAll(service.find(anyname, locationexact));
        }
        return locations;
    }

    /**
     * produce a set of location names
     * @return a set of names
     */
    @Override
    public Set<String> locationNames() {
        final Set<String> locations = new HashSet<String>();
        for (final Locations service: this.services.values()) {
            locations.addAll(service.locationNames());
        }
        return locations;
    }

    /**
     * recommend a set of names according to a given name
     * @param s a possibly partially matching name
     * @return a set of names that match with the given name using the local dictionary of names
     */
    @Override
    public Set<String> recommend(final String s) {
        final Set<String> recommendations = new HashSet<String>();
        if (s.isEmpty()) {
            return recommendations;
        }
        for (final Locations service: this.services.values()) {
            recommendations.addAll(service.recommend(s));
        }
        return recommendations;
    }

    /**
     * recommend a set of names according to a given name
     * @param s a possibly partially matching name
     * @return a set of names that match with the given name using the local dictionary of names
     */
    @Override
    public Set<StringBuilder> recommend(final StringBuilder s) {
        final Set<StringBuilder> recommendations = new HashSet<StringBuilder>();
        if (s.length() == 0) {
            return recommendations;
        }
        for (final Locations service: this.services.values()) {
            recommendations.addAll(service.recommend(s));
        }
        return recommendations;
    }

    /**
     * return an nickname of the localization service
     * @return the nickname
     */
    @Override
    public String nickname() {
        return "oa";
    }

    /**
     * hashCode that must be used to distinguish localization services in hash sets
     * @return the hash code, may be derived from the nickname
     */
    @Override
    public int hashCode() {
        return nickname().hashCode();
    }

    /**
     * compare localization services; to be used for hash sets with localization services
     * @param other
     * @return true if both objects are localization services and have the same nickname
     */
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof Locations)) {
            return false;
        }
        return nickname().equals(((Locations) other).nickname());
    }

}
