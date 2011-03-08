//ByteCount.java 
//-----------------------
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
// This file is contributed by Sebastian Gäbel
// last major change: $LastChangedDate$ by $LastChangedBy$
// Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.io;

import java.util.HashMap;

public final class ByteCount {

	public final static String PROXY = "PROXY";
	public final static String CRAWLER = "CRAWLER";
	
	private final static Object syncObject = new Object();
	private static long globalCount = 0;    
    private final static HashMap<String, Long> countMap = new HashMap<String, Long>(2);
    
    public final static long getGlobalCount() {
        return globalCount;
    }
    
    public final static long getAccountCount(final String accountName) {
        synchronized (syncObject) {
            if (countMap.containsKey(accountName)) {
                return (countMap.get(accountName)).longValue();
            }
            return 0;
        }
    }
    
    public final static void addAccountCount(final String accountName, final long count) {
    	synchronized (syncObject) {
    		globalCount += count;
    		if (accountName != null) {
    			long current = 0;
	            if (countMap.containsKey(accountName)) {
	            	current = (countMap.get(accountName)).longValue();
	            }
	            current += count;
	            countMap.put(accountName, current);
    		}
        }
    }
    
    public final static void resetCount() {
        synchronized (syncObject) {
            globalCount = 0;
            countMap.clear();
        }
    }
}
