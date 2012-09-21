/**
 *  Memory
 *  Copyright 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 22.09.2005 on http://yacy.net
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

package net.yacy.cora.util;

public class Memory {

    private static final Runtime runtime = Runtime.getRuntime();

    /**
     * memory that is free without increasing of total memory taken from os
     * @return bytes
     */
    public static final long free() {
        return runtime.freeMemory();
    }

    /**
     * memory that is available including increasing total memory up to maximum
     * @return bytes
     */
    public static final long available() {
        return maxMemory() - total() + free();
    }

    /**
     * maximum memory the Java virtual will allocate machine; may vary over time in some cases
     * @return bytes
     */
    public static final long maxMemory() {
        return runtime.maxMemory();
    }

    /**
     * currently allocated memory in the Java virtual machine; may vary over time
     * @return bytes
     */
    public static final long total() {
        return runtime.totalMemory();
    }

    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
    public static final long used() {
        return total() - free();
    }

}
