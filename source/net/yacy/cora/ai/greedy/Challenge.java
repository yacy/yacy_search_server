/**
 *  Challenge.java
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

public class Challenge<
                   SpecificRole extends Role,
                   SpecificFinding extends Finding<SpecificRole>,
                   SpecificModel extends Model<SpecificRole, SpecificFinding>
                  > implements
                  Comparator<Challenge<SpecificRole, SpecificFinding, SpecificModel>>,
                  Comparable<Challenge<SpecificRole, SpecificFinding, SpecificModel>> {
    
    private final Agent<SpecificRole,SpecificFinding,SpecificModel> agent;
    private final SpecificFinding finding;

    public Challenge() {
        this.agent = null;
        this.finding = null;
    }
    
    public Challenge(Agent<SpecificRole,SpecificFinding,SpecificModel> agent, SpecificFinding finding) {
        assert agent != null;
        assert finding != null;
        this.agent = agent;
        this.finding = finding;
    }
    
    public Agent<SpecificRole,SpecificFinding,SpecificModel> getAgent() {
        return this.agent;
    }
    
    public SpecificFinding getFinding() {
        return this.finding;
    }
    
    @Override
    public int hashCode() {
        return this.agent.hashCode() + this.finding.hashCode();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Challenge)) return false;
        Challenge<SpecificRole, SpecificFinding, SpecificModel> c = (Challenge<SpecificRole, SpecificFinding, SpecificModel>) other;
        if (!this.agent.equals(c.agent)) return false;
        if (!this.finding.equals(c.finding)) return false;
        return true;
    }
    

    public int compare(
            Challenge<SpecificRole, SpecificFinding, SpecificModel> c1,
            Challenge<SpecificRole, SpecificFinding, SpecificModel> c2) {
        
        // order of poison agents: they are the largest
        if (c1.agent == null) return 1;
        if (c2.agent == null) return -1;
        
        // compare based on priority of the finding
        return c1.finding.compareTo(c2.finding);
    }

    public int compareTo(Challenge<SpecificRole, SpecificFinding, SpecificModel> o) {
        return compare(this, o);
    }
}
