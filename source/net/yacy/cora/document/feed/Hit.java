/**
 *  Hit
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

import java.util.Date;
import java.util.List;

public interface Hit {

    public void setAuthor(String title);
    
    public void setCopyright(String title);
    
    public void setCategory(String title);
    
    public void setTitle(String title);
    
    public void setLink(String link);
    
    public void setReferrer(String title);
    
    public void setLanguage(String title);
    
    public void setDescription(String description);
    
    public void setPubDate(Date pubdate);
    
    public void setGuid(String guid);
    
    public void setDocs(String docs);

    public void setSubject(String[] tags);
    
    public void setSize(long size);
    
    public String getAuthor();
    
    public String getCopyright();
    
    public String getCategory();
    
    public String getTitle();
    
    public String getLink();
    
    public String getReferrer();
    
    public String getLanguage();
    
    public List<String> getDescriptions();
    
    public Date   getPubDate();
    
    public String getGuid();
    
    public String getDocs();

    public String[] getSubject();
    
    public long getSize();

    public float getLon();
    
    public float getLat();
}
