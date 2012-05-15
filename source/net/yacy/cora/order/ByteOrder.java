/**
 *  ByteOrder
 *  (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 10.01.2008 on http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.order;

import java.io.Serializable;


public interface ByteOrder extends Order<byte[]>, Serializable {

    @Override
    public boolean wellformed(byte[] a);

    public boolean wellformed(byte[] a, int start, int len);

    @Override
    public int compare(byte[] a, byte[] b);

    public int compare(byte[] a, byte[] b, int len);

    public int compare(byte[] a, int astart, byte[] b, int bstart, int len);

    @Override
    public boolean equal(final byte[] a, final byte[] b);

    public boolean equal(final byte[] a, int astart, final byte[] b, int bstart, int length);

    public long cardinal(final byte[] a, int off, int len);

    public byte[] smallest(byte[] a, byte[] b);

    public byte[] largest(byte[] a, byte[] b);
}
