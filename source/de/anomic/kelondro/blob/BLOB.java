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

package de.anomic.kelondro.blob;

import java.io.IOException;

import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;


public interface BLOB {
    
    /**
     * return a name of the BLOB; can be the file name
     * @return
     */
    public String name();
    
    /**
     * ask for the length of the primary key
     * @return the length of the key
     */
    public int keylength();
    
    /**
     * return the underlying odering
     * @return
     */
    public ByteOrder ordering();
    
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
    public CloneableIterator<byte[]> keys(boolean up, boolean rotating) throws IOException;
    
    /**
     * iterate over all keys
     * @param up
     * @param firstKey
     * @return
     * @throws IOException
     */
    public CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException;
    
    /**
     * check if a specific key is in the database
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    public boolean has(byte[] key);
    
    /**
     * retrieve the whole BLOB from the table
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    public byte[] get(byte[] key) throws IOException;
    
    /**
     * retrieve the size of the database
     * @param 
     * @return the size of the database in bytes
     * @throws IOException
     */
    public long length() throws IOException;
    
    /**
     * retrieve the size of the BLOB
     * @param key
     * @return the size of the BLOB or -1 if the BLOB does not exist
     * @throws IOException
     */
    public long length(byte[] key) throws IOException;
    
    /**
     * write a whole byte array as BLOB to the table
     * @param key  the primary key
     * @param b
     * @throws IOException
     */
    public void put(byte[] key, byte[] b) throws IOException;
    
    /**
     * replace an existing entry in the BLOB with a new entry
     * this method is similar to put, but it is necessary that a blob entry existed before
     * and contains an entry of same size or bigger than the new entry.
     * The old entry is then replaced by the new entry.
     * This method throws a IOException if the new element is bigger than the old element.
     * It is therefore necessary that it is known that the new entry will be smaller than the
     * old entry before calling this method.
     * @param key  the primary key
     * @param b
     * @return the number of bytes that the rewriter reduced the BLOB
     * @throws IOException
     */
    public int replace(byte[] key, Rewriter rewriter) throws IOException;
    
    /**
     * remove a BLOB
     * @param key  the primary key
     * @throws IOException
     */
    public void remove(byte[] key) throws IOException;
    
    /**
     * close the BLOB table
     */
    public void close(boolean writeIDX);
    
    public interface Rewriter {
        
        /**
         * a rewrite method that is used in the replace functionality of a BLOB
         * the result of such a rewrite must be always smaller or equal in size
         * if the input
         * @param b
         * @return an array that is equal or smaller in size than b
         */
        public byte[] rewrite(byte[] b);
        
    }
    
}
