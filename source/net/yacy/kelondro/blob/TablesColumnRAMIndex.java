// TablesColumnRAMIndex.java
// (C) 2012 by Stefan Foerster, sof@gmx.de, Norderstedt, Germany
// first published 2012 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.blob;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.order.NaturalOrder;

public class TablesColumnRAMIndex extends TablesColumnIndex{

	// Map<ColumnName, Map<ColumnValue, T<PrimaryKey>>>
	private final Map<String, Map<String, TreeSet<byte[]>>> index;
	
	private final static Comparator<byte[]> NATURALORDER = new NaturalOrder(true);
	
    public TablesColumnRAMIndex() {    	
    	super(TablesColumnIndex.INDEXTYPE.RAM);
    	this.index = new ConcurrentHashMap<String, Map<String, TreeSet<byte[]>>>();
    }
    
    @Override
    public void deleteIndex(final String columnName) {
    	this.index.remove(columnName);
    }
         
	@Override
    protected void insertPK(final String columnName, final String columnValue, final byte[] pk) {
		Map<String, TreeSet<byte[]>> valueIdxMap;
		TreeSet<byte[]> PKset;		
		if(this.index.containsKey(columnName)) {
			valueIdxMap = this.index.get(columnName);
		}
		else {
			valueIdxMap = new ConcurrentHashMap<String, TreeSet<byte[]>>();
			this.index.put(columnName, valueIdxMap);
		}		
		if(valueIdxMap.containsKey(columnValue)) {
			PKset = valueIdxMap.get(columnValue);	
		}
		else {
			PKset = new TreeSet<byte[]>(NATURALORDER);
			valueIdxMap.put(columnValue, PKset);			
		}
		PKset.add(pk);			
	}
	
	@Override
    protected synchronized void removePK(final byte[] pk) {
		for(Map.Entry<String, Map<String, TreeSet<byte[]>>> columnName : this.index.entrySet()) {
			final Iterator<Map.Entry<String, TreeSet<byte[]>>> viter = columnName.getValue().entrySet().iterator();
			while(viter.hasNext()) {
				final Map.Entry<String, TreeSet<byte[]>> columnValue = viter.next();
				columnValue.getValue().remove(pk);
				if(columnValue.getValue().isEmpty())
					viter.remove();
			}					
		}
	}
	
	@Override
    public void clear() {
		this.index.clear();
	}
	
	@Override
    public Collection<String> columns() {
		return this.index.keySet();
	}
	
	@Override
    public Set<String> keySet(final String columnName) {
		// a TreeSet is used to get sorted set of keys (e.g. folders)
		if(this.index.containsKey(columnName)) {
			return new TreeSet<String>(this.index.get(columnName).keySet());
		}		
		return new TreeSet<String>();
	}
	
	@Override
    public boolean containsKey(final String columnName, final String columnValue) {
		if(this.index.containsKey(columnName)) {
			return this.index.get(columnName).containsKey(columnValue);
		}
		return false;
	}
	
	@Override
    public boolean hasIndex(final String columnName) {
		return this.index.containsKey(columnName);
	}
	
	@Override
    public Collection<byte[]> get(final String columnName, final String key) {
		return this.index.get(columnName).get(key);
	}
	
	@Override
    public int size(final String columnName) {
		if(this.index.containsKey(columnName)) {
			return this.index.get(columnName).size();
		}
		return -1;
	}
	
	@Override
    public int size() {
		return this.index.size();
	}	
}