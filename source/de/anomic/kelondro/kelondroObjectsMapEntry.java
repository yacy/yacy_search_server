// kelondroObjectsMapEntry.java
// ----------------------------
// (C) 29.01.2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2004 as part of kelondroMap on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

package de.anomic.kelondro;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class kelondroObjectsMapEntry implements kelondroObjectsEntry {

    protected Map<String, String> entry;
    
    public kelondroObjectsMapEntry() {
        this.entry = new HashMap<String, String>();
    }
    
    public kelondroObjectsMapEntry(Map<String, String> map) {
        this.entry = map;
    }
    
    public kelondroObjectsMapEntry(kelondroRA ra) {
        this.read(ra);
    }
    
    public void read(kelondroRA ra) {
        try {
            this.entry = ra.readMap();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(kelondroRA ra) {
        try {
            ra.writeMap(this.entry, "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public Map<String, String> map() {
        return this.entry;
    }

}
