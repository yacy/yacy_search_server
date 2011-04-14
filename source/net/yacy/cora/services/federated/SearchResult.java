/**
 *  SearchResult
 *  Copyright 2011 by Michael Peter Christen
 *  First released 13.4.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-03-08 02:51:51 +0100 (Di, 08 Mrz 2011) $
 *  $LastChangedRevision: 7567 $
 *  $LastChangedBy: low012 $
 *
 *  This file is part of YaCy Content Integration
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

package net.yacy.cora.services.federated;

import de.anomic.search.ResultEntry;
import net.yacy.cora.storage.WeakPriorityBlockingQueue;

public class SearchResult extends WeakPriorityBlockingQueue<ResultEntry> {

    public SearchResult(int maxsize) {
        super(maxsize);
    }

    private static final long serialVersionUID = -4865225874936938082L;
    
    private long numFound = 0;
    private long start = 0;
    private Float maxScore = null;

    protected void setNumFound(long numFound) {
        this.numFound = numFound;
    }

    public long getNumFound() {
        return numFound;
    }

    protected void setStart(long start) {
        this.start = start;
    }

    public long getStart() {
        return start;
    }

    protected void setMaxScore(Float maxScore) {
        this.maxScore = maxScore;
    }

    public Float getMaxScore() {
        return maxScore;
    }

    public String toString() {
        return "{count=" + numFound + ", offset=" + start + (maxScore != null ? ", maxScore=" + maxScore : "") + ", docs=" + super.toString() + "}";
    }
}