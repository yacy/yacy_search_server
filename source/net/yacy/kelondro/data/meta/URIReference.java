/**
 *  URIReference
 *  Copyright 2012 by Michael Peter Christen
 *  First released 3.4.2012 at http://yacy.net
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

package net.yacy.kelondro.data.meta;

import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;

public interface URIReference {

	/**
	 * The hash of a URIReference is a unique key for the stored URL.
	 * It is in fact equal to url().hash()
	 * @return the hash of the stored url
	 */
    public byte[] hash();

    /**
     * The modification date of the URIReference is given if
     * the record was created first and is defined with the
     * creation date. If the record is modified later, the date shall change.
     * @return the modification date of this record
     */
    public Date moddate();
    
    /**
     * The DigestURI is the payload of the URIReference
     * @return the url as DigestURI with assigned URL hash according to the record hash
     */
    public DigestURI url();
    
    /**
     * check if the url matches agains a given matcher
     * @param matcher
     * @return true if the url() matches
     */
    public boolean matches(final Pattern matcher);
    
    /**
     * transform the record into a map which can be stored
     * @return
     */
    public Map<String, byte[]> toMap();
    
    /**
     * produce a visible representation of the record
     * @return a string for the url()
     */
    @Override
    public String toString();
}
