// kelondroColumn.java
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

public class kelondroColumn {

    public static final int celltype_undefined  = 0;
    public static final int celltype_boolean    = 1;
    public static final int celltype_binary     = 2;
    public static final int celltype_string     = 3;
    public static final int celltype_cardinal   = 4;
    public static final int celltype_real       = 5;
    
    public static final int encoder_none   = 0;
    public static final int encoder_b64e   = 1;
    public static final int encoder_string = 2;
    public static final int encoder_bytes  = 3;
    public static final int encoder_char   = 4;
    
    private int celltype, cellwidth, encoder, encodedwidth;
    private String nickname, description;
    
    public kelondroColumn(String nickname, int celltype, int cellwidth, int encoder, int encodedwidth, String description) {
        this.celltype = celltype;
        this.cellwidth = cellwidth;
        this.encoder = encoder;
        this.encodedwidth = encodedwidth;
        this.nickname = nickname;
        this.description = description;
    }

    public int celltype() {
        return this.celltype;
    }
    
    public int cellwidth() {
        return this.cellwidth;
    }
    
    public int encoder() {
        return this.encoder;
    }
    
    public int encodedwidth() {
        return this.encodedwidth;
    }
    
    public String nickname() {
        return this.nickname;
    }
    
    public String description() {
        return this.description;
    }
    
}
