/**
 *  Unirole.java
 *  Copyright 2010 by Michael Peter Christen, Frankfurt a. M., Germany
 *  First published 06.01.2010 at http://yacy.net
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file COPYING.LESSER.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.ai.greedy;

public enum Unirole implements Role {
    i;

    public Role nextRole() {
        return this;
    }
    
    public static Unirole unirole = Unirole.i;
}
