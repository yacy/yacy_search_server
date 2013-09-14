// TablesColumnIndex.java
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

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.NaturalOrder;


/**
 * a mapping from a column name to maps with the value of the columns to the primary keys where the entry exist in the table
 */
public abstract class TablesColumnIndex {

	public static enum INDEXTYPE {RAM, BLOB}
	private INDEXTYPE type;
	// Map<ColumnName, Map<ColumnValue, T<PrimaryKey>>>
	// private final Map<String, Map<String, TreeSet<byte[]>>> index;
	
	protected final static Comparator<byte[]> NATURALORDER = new NaturalOrder(true);
    
	protected abstract void insertPK(final String columnName, final String columnValue, final byte[] pk);
	protected abstract void removePK(final byte[] pk);
	protected abstract void clear();
	
	public abstract Set<String> keySet(final String columnName);	
	public abstract boolean containsKey(final String columnName, final String key);	
	public abstract boolean hasIndex(final String columnName);	
	public abstract Collection<byte[]> get(final String columnName, final String key);	
	public abstract int size(final String columnName);
	public abstract int size();
	public abstract Collection<String> columns();
	public abstract void deleteIndex(final String columnName);
	
    public TablesColumnIndex(INDEXTYPE type) {
    	this.type = type;
    }
	
    public INDEXTYPE getType() {
    	return this.type;
    }
    
    /**
     * create an index for a given table and given columns
     * @param columns - a map of column names and booleans for 'valueIsArray' you want to build an index for
     * @param separator - a string value used to split column values into an array
     * @param table - an iterator over table rows which should be added to the index
     */  
    public synchronized void buildIndex(final Map<String,String> columns, final Iterator<Tables.Row> table) {
    	this.clear();
    	// loop through all rows of the table     
    	while (table.hasNext()) {
    		this.add(columns, table.next());
    	}
    }
	
	private void insertPK(final String columnName, final String[] columnValues, final byte[] pk) {
		for (String columnValue : columnValues) {						
			this.insertPK(columnName, columnValue, pk);
		}
	}
	
	public void delete(final byte[] pk) {
		this.removePK(pk);
	}
	
	public void update(final String columnName, final String separator, final Tables.Row row) {
		this.removePK(row.getPK());
		this.add(columnName, separator, row);
	}
	
	public void update(final Map<String,String> columns, final Tables.Row row) {
		this.removePK(row.getPK());
		this.add(columns, row);
	}

	public void add(final String columnName, final String separator, final Map<String,String> map, final byte[] pk) {
        if(separator.isEmpty())
        	this.insertPK(columnName, map.get(columnName), pk);
        else
        	this.insertPK(columnName, map.get(columnName).split(separator), pk);
	}
	
	public void add(final String columnName, final String separator, final Tables.Data row, final byte[] pk) {
        if(separator.isEmpty())
        	this.insertPK(columnName, UTF8.String(row.get(columnName)), pk);
        else
        	this.insertPK(columnName, UTF8.String(row.get(columnName)).split(separator), pk);
	}
	
	public void add(final String columnName, final String separator, final Tables.Row row) {
        if(separator.isEmpty())
        	this.insertPK(columnName, UTF8.String(row.get(columnName)), row.getPK());	
        else
        	this.insertPK(columnName, UTF8.String(row.get(columnName)).split(separator), row.getPK());
	}
	
	public void add(final Map<String,String> columns, final Map<String,String> map, final byte[] pk) {
		final Iterator<String> iter = columns.keySet().iterator();
		while (iter.hasNext()) {
			final String columnName = iter.next();
			if(columns.get(columnName).isEmpty())
				this.insertPK(columnName, map.get(columnName), pk);
            else
            	this.insertPK(columnName, map.get(columnName).split(columns.get(columnName)), pk);
		}
	}
	
	public void add(final Map<String,String> columns, final Tables.Data row, final byte[] pk) {
		final Iterator<String> iter = columns.keySet().iterator();
		while (iter.hasNext()) {
			final String columnName = iter.next();
			if(columns.get(columnName).isEmpty())
				this.insertPK(columnName, UTF8.String(row.get(columnName)), pk);
            else
            	this.insertPK(columnName, UTF8.String(row.get(columnName)).split(columns.get(columnName)), pk);
		}
	}
	
	public void add(final Map<String,String> columns, final Tables.Row row) {
		this.add(columns, row, row.getPK());
	}
}