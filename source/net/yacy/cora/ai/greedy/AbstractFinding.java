/**
 *  AbstractFinding
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

public abstract class AbstractFinding<SpecificRole extends Role> implements Finding<SpecificRole>, Comparator<Finding<SpecificRole>>, Comparable<Finding<SpecificRole>>, Cloneable {

    private final SpecificRole role;
    private int priority;
    
    /**
     * create a new finding for a given role
     * the priority can be fixed in the beginning
     * @param role
     * @param priority
     */
    public AbstractFinding(SpecificRole role, int priority) {
        this.role = role;
        this.priority = priority;
    }
    
    /**
     * create a new finding for a given role
     * the priority should be assigned afterward
     * @param role
     */
    public AbstractFinding(SpecificRole role) {
        this.role = role;
        this.priority = 0;
    }
    
    @Override
    public abstract Object clone();
    
    /**
     * get the finding priority
     * @return
     */
    public int getPriority() {
        return this.priority;
    }

    /**
     * set the current priority
     * This may only be used internally as part of the engine process to create a result queue
     */
    public void setPriority(final int newPriority) {
        this.priority = newPriority;
    }

    /**
     * get the finding role
     * @return
     */
    public SpecificRole getRole() {
        return this.role;
    }
    
    public int compare(final Finding<SpecificRole> f1, final Finding<SpecificRole> f2) {
        final int p1 = f1.getPriority();
        final int p2 = f2.getPriority();
        if (p1 < p2) return 1;
        if (p1 > p2) return -1;
        return 0;
    }

    public int compareTo(final Finding<SpecificRole> o) {
        return compare(this, o);
    }
    
    @Override
    public abstract boolean equals(Object other);
    @Override
    public abstract int hashCode();  
}
