// AbstractModel.java
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

public abstract class AbstractModel<SpecificRole extends Role, SpecificFinding extends Finding<SpecificRole>> implements
    Model<SpecificRole, SpecificFinding>, Cloneable {

    private SpecificRole currentRole;
    
    public AbstractModel(SpecificRole currentRole) {
        this.currentRole = currentRole;
    }

    public abstract Object clone();
    
    /**
     * the model contains a status about roles that may act next
     * @return the next role
     */
    public SpecificRole currentRole() {
        return this.currentRole;
    }
    
    /**
     * switch to the next role. The current role is migrated to the
     * new role status, or the current rule is replaced with a new role
     */
    @SuppressWarnings("unchecked")
    public void nextRole() {
        this.currentRole = (SpecificRole) this.currentRole.nextRole();
    }
    
    public abstract boolean equals(Object other);
    public abstract int hashCode();  
}
