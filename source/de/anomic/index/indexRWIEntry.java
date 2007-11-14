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
import de.anomic.kelondro.kelondroRow.Entry;

public interface indexRWIEntry {

    // appearance flags, used in RWI entry
    // the flags 0..23 are identical to the category flags in plasmaCondenser
    public  static final int flag_app_reference     = 24; // word appears in anchor description text (the reference to an url), or any alternative text field of a link
    public  static final int flag_app_descr         = 25; // word appears in headline (or any description part)
    public  static final int flag_app_author        = 26; // word appears in author
    public  static final int flag_app_tags          = 27; // word appears in header tags
    public  static final int flag_app_url           = 28; // word appears in url
    public  static final int flag_app_emphasized    = 29; // word is emphasized in text (i.e. bold, italics, special size)

    public String toPropertyForm();
    
    public Entry toKelondroEntry();

    public String urlHash();

    public int quality();

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
    
    public String toString();
    
    public void combineDistance(indexRWIEntry oe);

    public int worddistance();
    
    public boolean isNewer(indexRWIEntry other);
 
    public boolean isOlder(indexRWIEntry other);

}