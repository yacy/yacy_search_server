// Dao.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 25.05.2009 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.content.dao;

import java.util.ArrayList;
import java.util.Date;

import de.anomic.content.DCEntry;

/*
 * Database Access Objects are used to get a normalized view on database objects with java objects
 */
public interface Dao {

    // item-oriented retrieval
    
    /**
     * get the maximum number of items in the database
     */
    public int maxItems();
    
    /**
     * retrieve a single item from the database
     * @param item
     * @return
     */
    public DCEntry get(int item);
    
    /**
     * retrieve a list of entries in the database;
     * the object denoted with until is not contained in the list
     * @param from
     * @param until
     * @return
     */
    public ArrayList<DCEntry> get(int from, int until);
    
    
    // date-oriented retrieval
    
    /**
     * return the date of the first entry
     */
    public Date firstEntry();
    
    /**
     * return the date of the latest entry
     * @return
     */
    public Date latestEntry();
    
    /**
     * get a list of entries in the database;
     * the returned list contains all entries up to the most recent
     * @param from
     * @return
     */
    public ArrayList<DCEntry> get(Date from);
    
}
