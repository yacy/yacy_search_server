// Asset.java
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

public class Asset<
                   SpecificRole extends Role,
                   SpecificFinding extends Finding<SpecificRole>,
                   SpecificModel extends Model<SpecificRole, SpecificFinding>
                  > {
    
    private final SpecificModel model;
    private final SpecificFinding finding;

    public Asset(SpecificModel model, SpecificFinding finding) {
        this.model = model;
        this.finding = finding;
    }
    
    public SpecificModel getModel() {
        return this.model;
    }
    
    public SpecificFinding getFinding() {
        return this.finding;
    }
    
    public int hashCode() {
        return (this.finding == null) ? this.model.hashCode() : this.model.hashCode() + this.finding.hashCode();
    }
    
    @SuppressWarnings("unchecked")
    public boolean equals(Object other) {
        if (!(other instanceof Asset)) return false;
        Asset<SpecificRole, SpecificFinding, SpecificModel> a = (Asset<SpecificRole, SpecificFinding, SpecificModel>) other;
        if (!this.model.equals(a.model)) return false;
        if (this.finding == null && a.finding == null) return true;
        if (!this.finding.equals(a.finding)) return false;
        return true;
    }
    
}
