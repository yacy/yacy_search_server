/**
 *  HandleMap
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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
import java.util.ArrayList;
import java.util.Map;

import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.util.SpaceExceededException;

public interface HandleMap extends Iterable<Map.Entry<byte[], Long>> {

    public long mem();

    public void optimize();

    /**
     * write a dump of the index to a file. All entries are written in order
     * which makes it possible to read them again in a fast way
     * @param file
     * @return the number of written entries
     * @throws IOException
     */
    public int dump(final File file) throws IOException;

    public void clear();

    public byte[] smallestKey();

    public byte[] largestKey();

    public boolean has(final byte[] key);

    public long get(final byte[] key);

    /**
     * Adds the key-value pair to the index.
     * @param key the index key
     * @param l the value
     * @return the previous entry of the index
     * @throws IOException
     * @throws SpaceExceededException
     */
    public long put(final byte[] key, final long l) throws SpaceExceededException;

    public void putUnique(final byte[] key, final long l) throws SpaceExceededException;

    public long add(final byte[] key, final long a) throws SpaceExceededException;

    public long inc(final byte[] key) throws SpaceExceededException;

    public long dec(final byte[] key) throws SpaceExceededException;

    public ArrayList<long[]> removeDoubles() throws SpaceExceededException;

    public ArrayList<byte[]> top(final int count);

    public long remove(final byte[] key);

    public long removeone();

    public int size();

    public boolean isEmpty();

    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey);

    public void close();

}
