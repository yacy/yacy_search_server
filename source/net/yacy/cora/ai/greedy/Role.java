/**
 *  Role.java
 *  Copyright 2009 by Michael Peter Christen, Frankfurt a. M., Germany
 *  First published 03.12.2009 at http://yacy.net
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

package net.yacy.cora.ai.greedy;

/**
 * a Role object is usually an enumeration object that is produced
 * in a enumeration that implements the Role interface.
 * All role object instances must define that one specific other Role
 * follows the current role. If there are undefined role or multiple role
 * situations, these may be declared with special role instance constants
 */
public interface Role {

    /**
     * define which other role shall follow to the current role
     * this returns a cloned role
     * @return the next role
     */
    public Role nextRole();
    
    /**
     * necessary equals method for usage of Role in hash tables
     * @param obj
     * @return true if the current role and the given role are equal
     */
    @Override
    public boolean equals(Object obj);
    
    /**
     * necessary hashCode method for usage of Role in hash tables
     * @return a hash code of the role
     */
    @Override
    public int hashCode();
    
}
