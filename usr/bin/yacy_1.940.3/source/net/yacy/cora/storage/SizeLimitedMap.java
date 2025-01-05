/**
 *  SizeLimitedMap
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 04.07.2012 at https://yacy.net
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

package net.yacy.cora.storage;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class SizeLimitedMap<K, V> extends LinkedHashMap<K, V> implements Map<K, V>, Cloneable, Serializable {

	private static final long serialVersionUID = 6088727126150060068L;

	private final int sizeLimit;
	
	/** Set to true when at least one eldest entry has been removed because the map size exceeded the size limit. */
	private boolean limitExceeded;
	
	public SizeLimitedMap(int sizeLimit) {
		this.sizeLimit = sizeLimit;
		this.limitExceeded = false;
	}

    @Override protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        boolean res = size() > this.sizeLimit;
        if(res) {
        	this.limitExceeded = true;
        }
        return res;
    }
    
    /**
     * @return true when the size limit has been exceeded at least one time
     */
    public boolean isLimitExceeded() {
		return this.limitExceeded;
	}
}
