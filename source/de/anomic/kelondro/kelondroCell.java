// kelondroCell.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 24.05.2006 on http://www.anomic.de
//
// This is a part of the kelondro database,
// which is a part of YaCy, a peer-to-peer based web search engine
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

public class kelondroCell {

    public static int celltype_undefined  = 0;
    public static int celltype_boolean    = 1;
    public static int celltype_bytes      = 2;
    public static int celltype_string     = 3;
    public static int celltype_cardinal   = 4;
    public static int celltype_real       = 5;
    
    private int celltype, dbwidth;
    private String nickname, description;
    
    public kelondroCell(int celltype, int dbwidth, String nickname, String description) {
        this.celltype = celltype;
        this.dbwidth = dbwidth;
        this.nickname = nickname;
        this.description = description;
    }

    public int celltype() {
        return this.celltype;
    }
    
    public int dbwidth() {
        return this.dbwidth;
    }
    
    public String nickname() {
        return this.nickname;
    }
    
    public String description() {
        return this.description;
    }
}
