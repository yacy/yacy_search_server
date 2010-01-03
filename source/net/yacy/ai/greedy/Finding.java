// Finding.java
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
    public boolean equals(Object other);
    
    /**
     * the hash code computation, necessary to place findings into hashtables
     * @return a hash code for this object
     */
    public int hashCode();
    
}
