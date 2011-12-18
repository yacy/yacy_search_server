// BufferedIndex.java
// -----------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.3.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.kelondro.rwi;

import java.io.IOException;
import java.util.TreeSet;

import net.yacy.cora.order.CloneableIterator;


/*
 * a BufferedIndex is an integration of different index types, i.e.
 * - ReferenceContainerArray
 * - ReferenceContainerCache
 * - IndexCache (which is a wrapper of a ReferenceContainerCache)
 * - IndexCollection
 * This interface was created from the methods that are used in CachedIndexCollection
 * (which integrates IndexCache and IndexCollection)
 * and is applied to the new index integration class IndexCell
 * (which integrates ReferenceContainerArray and ReferenceContainerCache)
 * to make it possible to switch between the old and new index data structure
 */
public interface BufferedIndex<ReferenceType extends Reference> extends Index<ReferenceType> {

    /*
     *  methods for monitoring of the buffer
     */

    /**
     * set the size of the buffer, which can be defined with a given maximum number
     * of words that shall be stored. Because an arbitrary number of references can
     * be stored at each word entry, this does not limit the memory that the buffer
     * takes. It is possible to monitor the memory occupation of the buffer with the
     * getBufferSizeBytes() method, and then adopt the buffer size with a new word number
     * limit.
     */
    public void setBufferMaxWordCount(final int maxWords);

    /**
     * return the maximum number of references, that one buffer entry has stored
     * @return
     */
    public int getBufferMaxReferences();

    /**
     * return the date of the oldest buffer entry
     * @return a time as milliseconds from epoch
     */
    public long getBufferMinAge();

    /**
     * return the date of the most recent buffer entry
     * @return a time as milliseconds from epoch
     */
    public long getBufferMaxAge();

    /**
     * calculate the memory that is taken by the buffer.
     * This does not simply return a variable content. it is necessary
     * to iterate over all entries to get the whole size.
     * please use this method with great care
     * @return number of bytes that the buffer has allocated
     */
    public long getBufferSizeBytes();

    /**
     * get the size of the buffer content
     * @return number of word references
     */
    public int getBufferSize();

    /**
     * iterate over entries in index. this method differs from the iterator in an Index
     * object in such a way that it has the additional 'buffer' flag. When using this method,
     * the iteration goes only over the buffer content, or over the backend-content, but
     * not over a merged content.
     * @param startHash
     * @param rot
     * @param buffer
     * @return
     * @throws IOException
     */
    public CloneableIterator<ReferenceContainer<ReferenceType>> referenceContainerIterator(
                            byte[] startHash,
                            boolean rot,
                            boolean excludePrivate,
                            boolean buffer
                            ) throws IOException;


    /**
     * collect reference container in index. this method differs from the collector in an Index
     * object in such a way that it has the additional 'buffer' flag. When using this method,
     * the collection goes only over the buffer content, or over the backend-content, but
     * not over a merged content.
     * @param startHash
     * @param rot
     * @param count
     * @param buffer
     * @return
     * @throws IOException
     */
    public TreeSet<ReferenceContainer<ReferenceType>> referenceContainer(
                            byte[] startHash,
                            boolean rot,
                            boolean excludePrivate,
                            int count,
                            boolean buffer
                            ) throws IOException;

}
