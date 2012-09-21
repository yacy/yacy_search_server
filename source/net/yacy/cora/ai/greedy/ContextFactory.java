/**
 *  ContextFactory.java
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

public class ContextFactory<
                            SpecificRole extends Role,
                            SpecificFinding extends Finding<SpecificRole>,
                            SpecificModel extends Model<SpecificRole, SpecificFinding>
                           >{

    private final Goal<SpecificRole, SpecificFinding, SpecificModel> goal;
    private final long timeoutForSnapshot;
    private final boolean feedAssetCache, useAssetCache;
    
    public ContextFactory(
            Goal<SpecificRole, SpecificFinding, SpecificModel> goal,
            long timeoutForSnapshot, boolean feedAssetCache, boolean useAssetCache) {
        this.goal = goal;
        this.timeoutForSnapshot = timeoutForSnapshot;
        this.feedAssetCache = feedAssetCache;
        this.useAssetCache = useAssetCache;
    }
    
    public Context<SpecificRole, SpecificFinding, SpecificModel> produceContext(SpecificModel startModel) {
        return new Context<SpecificRole, SpecificFinding, SpecificModel>(this.goal, startModel, timeoutForSnapshot, feedAssetCache, useAssetCache);
    }
}
