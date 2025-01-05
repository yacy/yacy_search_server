// Dao.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 25.05.2009 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.content.dao;

import java.io.File;
import java.sql.SQLException;
import java.util.Date;
import java.util.concurrent.BlockingQueue;

import net.yacy.document.content.DCEntry;


/*
 * Database Access Objects are used to get a normalized view on database objects with java objects
 */
public interface Dao {

    // item-oriented retrieval
    
    /**
     * get the maximum number of possible DCEntry items in the database
     * @throws SQLException 
     */
    public int size() throws SQLException;
    
    /**
     * retrieve a single item from the database
     * @param item
     * @return a single result entry in Dublin Core format
     */
    public DCEntry get(int item);
    
    /**
     * retrieve a set of entries in the database;
     * the object denoted with until is not contained in the result set
     * all retrieved objects are pushed concurrently to a blocking queue
     * @param from the first id
     * @param until the limit of the last id (the id is not included)
     * @param queueSize the maximum number of entries in the blocing queue
     * @return a quere where the results are written in concurrently
     */
    public BlockingQueue<DCEntry> query(int from, int until, int queueSize);
    
    
    // date-oriented retrieval
    
    /**
     * return the date of the first entry
     */
    public Date first();
    
    /**
     * return the date of the latest entry
     * @return the date of the latest entry
     */
    public Date latest();
    
    /**
     * retrieve a set of entries in the database;
     * the result set contains all entries up to the most recent
     * all retrieved objects are pushed to the blocking queue
     * @param from
     * @return a quere where the results are written in concurrently
     */
    public BlockingQueue<DCEntry> query(Date from, int queueSize);
    
    
    // export methods
    
    public int writeSurrogates(
                            BlockingQueue<DCEntry> queue,
                            File targetdir,
                            String versioninfo,
                            int maxEntriesInFile
                        );
    
    // workflow
    
    /**
     * close the connection to the database
     */
    public void close();

}
