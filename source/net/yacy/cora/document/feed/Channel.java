/**
 *  Channel
 *  Copyright 2010 by Michael Peter Christen
 *  First released 10.5.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.document.feed;


public interface Channel extends Iterable<Hit> {

    public void setTitle(String title);
    
    public void setLink(String link);
    
    public void setDescription(String description);
    
    public void setImageURL(String imageUrl);
    
    public void setTotalResults(String totalResults);
    
    public void setStartIndex(String startIndex);
    
    public void setItemsPerPage(String itemsPerPage);
    
    public void setSearchTerms(String searchTerms);
}
