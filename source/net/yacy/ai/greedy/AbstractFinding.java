// AbstractFinding.java
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

public abstract class AbstractFinding<SpecificRole extends Role> implements Finding<SpecificRole>, Comparator<Finding<SpecificRole>>, Comparable<Finding<SpecificRole>>, Cloneable {

    private final SpecificRole role;
    private int priority;
    
    /**
     * create a new finding for a given role
     * the priority is fixed in the beginning because is results from a premise
     * that invoked a special finding computation set
     * @param role
     * @param priority
     */
    public AbstractFinding(SpecificRole role, int priority) {
        this.role = role;
        this.priority = priority;
    }
    
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
    
    public abstract boolean equals(Object other);
    public abstract int hashCode();  
}
