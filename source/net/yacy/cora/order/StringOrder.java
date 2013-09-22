/**
 *  StringOrder
 *  (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 10.01.2008 on http://yacy.net
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
import java.util.Comparator;

import net.yacy.cora.document.encoding.UTF8;

public class StringOrder implements Comparator<String>, Serializable {

    private static final long serialVersionUID=-5443022063770309585L;

    public ByteOrder baseOrder;

    public StringOrder(final ByteOrder base) {
        this.baseOrder = base;
    }

    public StringOrder(final Order<byte[]> base) {
        this.baseOrder = (ByteOrder) base;
    }

    @Override
    public final int compare(final String s1, final String s2) {
        return this.baseOrder.compare(UTF8.getBytes(s1), UTF8.getBytes(s2));
    }
}
