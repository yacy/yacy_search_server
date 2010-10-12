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

package net.yacy.document.geolocalization;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class OverarchingLocalization implements Localization {

    private Map<String, Localization> services;
    
    /**
     * create a new overarching localization object
     */
    public OverarchingLocalization() {
        this.services = new HashMap<String, Localization>();
    }
    
    /**
     * add a localization service
     * @param nickname the nickname of the service
     * @param service the service
     */
    public void addLocalization(String nickname, Localization service) {
        this.services.put(nickname, service);
    }
    
    /**
     * remove a localization service
     * @param nickname
     */
    public void removeLocalization(String nickname) {
        this.services.remove(nickname);
    }

    public int locations() {
        int locations = 0;
        for (Localization service: this.services.values()) {
            locations += service.locations();
        }
        return locations;
    }
    
    /**
     * find (a set of) locations
     */
    public TreeSet<Location> find(String anyname, boolean locationexact) {
        TreeSet<Location> locations = new TreeSet<Location>();
        for (Localization service: this.services.values()) {
            locations.addAll(service.find(anyname, locationexact));
        }
        return locations;
    }

    /**
     * recommend location names
     */
    public Set<String> recommend(String s) {
        Set<String> recommendations = new HashSet<String>();
        if (s.length() == 0) return recommendations;
        for (Localization service: this.services.values()) {
            recommendations.addAll(service.recommend(s));
        }
        return recommendations;
    }

    public String nickname() {
        return "oa";
    }
    
    public int hashCode() {
        return this.nickname().hashCode();
    }
    
    public boolean equals(Object other) {
        if (!(other instanceof Localization)) return false;
        return this.nickname().equals(((Localization) other).nickname()); 
    }

}
