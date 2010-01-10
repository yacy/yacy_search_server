// ContextFactory.java
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
