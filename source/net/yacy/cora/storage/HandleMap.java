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

import java.io.IOException;

import net.yacy.cora.util.SpaceExceededException;

public interface HandleMap extends ImmutableHandleMap {

    /**
     * Adds the key-value pair to the index.
     * @param key the index key
     * @param l the value
     * @return the previous entry of the index or -1 if the entry is new
     * @throws IOException
     * @throws SpaceExceededException
     */
    public long put(final byte[] key, final long l) throws SpaceExceededException; // mutable operation();

    public void putUnique(final byte[] key, final long l) throws SpaceExceededException; // mutable operation();

    public long add(final byte[] key, final long a) throws SpaceExceededException; // mutable operation();

    public long inc(final byte[] key) throws SpaceExceededException; // mutable operation();

    public long dec(final byte[] key) throws SpaceExceededException; // mutable operation();

}
