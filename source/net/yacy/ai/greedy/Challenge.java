// Challenge.java
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

public class Challenge<
                   SpecificRole extends Role,
                   SpecificFinding extends Finding<SpecificRole>,
                   SpecificModel extends Model<SpecificRole, SpecificFinding>
                  > implements
                  Comparator<Challenge<SpecificRole, SpecificFinding, SpecificModel>>,
                  Comparable<Challenge<SpecificRole, SpecificFinding, SpecificModel>> {
    
    private Agent<SpecificRole,SpecificFinding,SpecificModel> agent;
    private SpecificFinding finding;

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
    
    public int hashCode() {
        return this.agent.hashCode() + this.finding.hashCode();
    }
    
    @SuppressWarnings("unchecked")
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
