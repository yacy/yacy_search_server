/**
 *  HandleSet
 *  Copyright 2012 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 26.07.2012 on http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.util.SpaceExceededException;

public interface HandleSet extends Iterable<byte[]>, Cloneable, Serializable {

    public HandleSet clone();

    public byte[] export();

    public void optimize();

    /**
     * write a dump of the set to a file. All entries are written in order
     * which makes it possible to read them again in a fast way
     * @param file
     * @return the number of written entries
     * @throws IOException
     */
    public int dump(final File file) throws IOException;

    public byte[] smallestKey();

    public byte[] largestKey();

    public ByteOrder comparator();

    public void clear();

    public boolean has(final byte[] key);

    public void putAll(final HandleSet aset) throws SpaceExceededException;

    /**
     * Adds the key to the set
     * @param key
     * @return true if this set did _not_ already contain the given key.
     * @throws IOException
     * @throws SpaceExceededException
     */
    public boolean put(final byte[] key) throws SpaceExceededException;

    public void putUnique(final byte[] key) throws SpaceExceededException;

    public boolean remove(final byte[] key);

    public byte[] removeOne();

    /**
     * get one entry; objects are taken from the end of the list
     * a getOne(0) would return the same object as removeOne() would remove
     * @param idx
     * @return entry from the end of the list
     */
    public byte[] getOne(int idx);

    public boolean isEmpty();

    public int size();

    public int keylen();

    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey);

  //  public void excludeDestructive(final HandleSet other);
    public void excludeDestructive(final Set<byte[]> other); // used for stopwordhashes etc.
    
    @Override
    public Iterator<byte[]> iterator();

    public void close();

}
