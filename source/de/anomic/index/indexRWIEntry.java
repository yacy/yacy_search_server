// indexRWIEntry.java
// (C) 2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 07.11.2007 on http://www.anomic.de
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

package de.anomic.index;

import de.anomic.kelondro.kelondroBitfield;

public interface indexRWIEntry {

    // appearance flags, used in RWI entry
    // some names are derived from the Dublin Core Metadata tag set
    // the flags 0..23 are identical to the category flags in plasmaCondenser
    public  static final int flag_app_dc_description= 24; // word appears in anchor description text (the reference to an url), or any alternative text field of a link
    public  static final int flag_app_dc_title      = 25; // word appears in title or headline or any description part
    public  static final int flag_app_dc_creator    = 26; // word appears in author
    public  static final int flag_app_dc_subject    = 27; // word appears in header tags or other descriptive part
    public  static final int flag_app_dc_identifier = 28; // word appears in url or document identifier
    public  static final int flag_app_emphasized    = 29; // word is emphasized in text (i.e. bold, italics, special size)

    public String toPropertyForm();

    public String urlHash();

    public int virtualAge();

    public long lastModified();
    
    public long freshUntil();

    public int hitcount();

    public int posintext();

    public int posinphrase();

    public int posofphrase();

    public int wordsintext();

    public int phrasesintext();

    public String getLanguage();

    public char getType();

    public int wordsintitle();
    
    public int llocal();
    
    public int lother();
    
    public int urllength();
    
    public int urlcomps();
    
    public kelondroBitfield flags();
    
    public double termFrequency();
    
    public String toString();
    
    public boolean isNewer(indexRWIEntry other);
 
    public boolean isOlder(indexRWIEntry other);

}