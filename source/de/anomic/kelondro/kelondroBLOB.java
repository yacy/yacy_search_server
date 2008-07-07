// kelondroBLOB.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 08.06.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package de.anomic.kelondro;

import java.io.IOException;

import de.anomic.kelondro.kelondroBLOBTree.keyIterator;

public interface kelondroBLOB {
    
    /**
     * ask for the length of the primary key
     * @return the length of the key
     */
    public int keylength();
    
    /**
     * clears the content of the database
     * @throws IOException
     */
    public void clear() throws IOException;
    
    /**
     * ask for the number of entries
     * @return the number of entries in the table
     */
    public int size();
    
    /**
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    public kelondroCloneableIterator<String> keys(boolean up, boolean rotating) throws IOException;
    
    /**
     * iterate over all keys
     * @param up
     * @param firstKey
     * @return
     * @throws IOException
     */
    public keyIterator keys(boolean up, byte[] firstKey) throws IOException;
    
    /**
     * check if a specific key is in the database
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    public boolean has(String key) throws IOException;
    
    /**
     * retrieve the whole BLOB from the table
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    public byte[] get(String key) throws IOException;
    
    /**
     * retrieve a fragment of a BLOB from the table
     * @param key  the primary key
     * @param pos  the position within the BLOB fragment
     * @param len  the length of the fragment
     * @return
     * @throws IOException
     */
    public byte[] get(String key, int pos, int len) throws IOException;
    
    /**
     * write a whole byte array as BLOB to the table
     * @param key  the primary key
     * @param b
     * @throws IOException
     */
    public void put(String key, byte[] b) throws IOException;
    
    /**
     * write a fragment of a BLOB to the table
     * @param key  the primary key
     * @param pos  the position of the BLOB fragment
     * @param b    a byte array
     * @param off  the offset within the array where the BLOB fragment starts
     * @param len  the length of the fragment
     * @throws IOException
     */
    public void put(String key, int pos, byte[] b, int off, int len) throws IOException;
    
    /**
     * remove a BLOB
     * @param key  the primary key
     * @throws IOException
     */
    public void remove(String key) throws IOException;
    
    /**
     * close the BLOB table
     */
    public void close();
    
}
