// BLOB.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 08.06.2008 on http://yacy.net
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

import java.io.IOException;

import net.yacy.cora.util.SpaceExceededException;


public interface BLOB extends ImmutableBLOB {
    
    /**
     * replace an existing entry in the BLOB with a new entry
     * this method is similar to put, but it is necessary that a blob entry existed before
     * and contains an entry of same size or bigger than the new entry.
     * The old entry is then replaced by the new entry.
     * This method throws a IOException if the new element is bigger than the old element.
     * It is therefore necessary that it is known that the new entry will be smaller than the
     * old entry before calling this method.
     * @param key  the primary key
     * @param rewriter
     * @return the number of bytes that the rewriter reduced the BLOB
     * @throws IOException
     * @throws SpaceExceededException 
     */
    public int replace(byte[] key, Rewriter rewriter) throws IOException, SpaceExceededException;

    /**
     * write a whole byte array as BLOB to the table
     * @param key  the primary key
     * @param b
     * @throws IOException
     * @throws SpaceExceededException 
     */
    public void insert(byte[] key, byte[] b) throws IOException;
    
}
