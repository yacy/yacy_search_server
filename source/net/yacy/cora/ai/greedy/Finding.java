/**
 *  Finding.java
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

import java.util.Comparator;

/**
 * a Finding object defines one single alternative that a given role has
 * within the given current model instance. A set of Finding objects is the
 * set of possibilities that a specific role has in a given situation as
 * defined by the model.
 * Findings can be classified with priorities that may be computed by strategy rules.
 * A Finding priority does not mean a ranking of the finding outcome, but a ranking
 * on the set of possible findings.
 *
 * @param <SpecificRole> a Role
 */
public interface Finding<SpecificRole extends Role> extends Comparator<Finding<SpecificRole>>, Comparable<Finding<SpecificRole>>, Cloneable {

    /**
     * clone the model
     * @return a top-level cloned model
     * @throws CloneNotSupportedException
     */
    public Object clone() throws CloneNotSupportedException;
    
    /**
     * get the finding priority
     * @return
     */
    public int getPriority();
    
    /**
     * set the current priority
     * This may only be used internally as part of the engine process to create a result queue
     */
    public void setPriority(int newPriority);

    /**
     * get the finding role
     * @return
     */
    public SpecificRole getRole();
    
    /**
     * the equals method, necessary to place findings into hashtables
     * @param other
     * @return true if this finding is equal to the other finding
     */
    @Override
    public boolean equals(Object other);
    
    /**
     * the hash code computation, necessary to place findings into hashtables
     * @return a hash code for this object
     */
    @Override
    public int hashCode();
    
}
