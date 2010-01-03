// Role.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.12.2009 on http://yacy.net;
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-05-28 01:51:34 +0200 (Do, 28 Mai 2009) $
// $LastChangedRevision: 5988 $
// $LastChangedBy: orbiter $
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

package net.yacy.ai.greedy;

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
    public boolean equals(Object obj);
    
    /**
     * necessary hashCode method for usage of Role in hash tables
     * @return a hash code of the role
     */
    public int hashCode();
    
}
