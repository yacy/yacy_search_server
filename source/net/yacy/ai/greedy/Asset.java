/**
 *  Asset.java
 *  Copyright 2009 by Michael Peter Christen, Frankfurt a. M., Germany
 *  First published 03.12.2009 at http://yacy.net
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file COPYING.LESSER.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

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
