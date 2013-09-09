/**
 *  AbstractModel.java
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

public abstract class AbstractModel<SpecificRole extends Role, SpecificFinding extends Finding<SpecificRole>> implements
    Model<SpecificRole, SpecificFinding>, Cloneable {

    private SpecificRole currentRole;
    
    public AbstractModel(SpecificRole currentRole) {
        this.currentRole = currentRole;
    }

    @Override
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
    
    @Override
    public abstract boolean equals(Object other);
    @Override
    public abstract int hashCode();  
}
