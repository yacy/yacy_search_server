// Reference.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.11.2007 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.kelondro.text;

import de.anomic.kelondro.order.Bitfield;

public interface Reference {

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
    
    public Bitfield flags();
    
    public double termFrequency();
    
    public String toString();
    
    public boolean isNewer(Reference other);
 
    public boolean isOlder(Reference other);

    public int hashCode();
}
