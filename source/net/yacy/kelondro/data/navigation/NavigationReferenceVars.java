// NavigationReferenceVars.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.05.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-03-20 16:44:59 +0100 (Fr, 20 Mrz 2009) $
// $LastChangedRevision: 5736 $
// $LastChangedBy: borg-0300 $
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

package net.yacy.kelondro.data.navigation;

import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.rwi.AbstractReference;
import net.yacy.kelondro.rwi.Reference;

public class NavigationReferenceVars  extends AbstractReference implements NavigationReference, Reference, Cloneable {

    public String refhash, termhash;
    public int hitcount, position;
    byte flags;
    
    public NavigationReferenceVars(
            final String   termhash,
            final String   refhash,
            final int      count,
            final int      pos,
            final byte     flags
    ) {
        this.refhash = refhash;
        this.termhash = termhash;
        this.hitcount = count;
        this.position = pos;
        this.flags = flags;
    }
    
    public NavigationReferenceVars(final NavigationReference e) {
        this.refhash = e.metadataHash();
        this.termhash = e.termHash();
        this.hitcount = e.hitcount();
        this.position = e.position(0);
        this.flags = e.flags();
    }
    
    public NavigationReferenceVars clone() {
        final NavigationReferenceVars c = new NavigationReferenceVars(
                this.termhash,
                this.refhash,
                this.hitcount,
                this.position,
                this.flags
        );
        return c;
    }
    
    public NavigationReferenceRow toRowEntry() {
        return new NavigationReferenceRow(
                this.termhash,
                this.refhash,
                this.hitcount,
                this.position,
                this.flags);
    }
    
    public String toPropertyForm() {
        return toRowEntry().toPropertyForm();
    }

    public Entry toKelondroEntry() {
        return toRowEntry().toKelondroEntry();
    }

    public String navigationHash() {
        return this.termhash + this.refhash;
    }
    
    public String metadataHash() {
        return this.refhash;
    }

    public String termHash() {
        return this.termhash;
    }
 
    public int hitcount() {
        return this.hitcount;
    }

    public int position(int p) {
        assert p == 0 : "p = " + p;
        return this.position;
    }
    
    public byte flags() {
        return this.flags;
    }
 
    public String toString() {
        return toPropertyForm();
    }
    
    public int hashCode() {
        return this.navigationHash().hashCode();
    }
    
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof NavigationReferenceVars)) return false;
        NavigationReferenceVars other = (NavigationReferenceVars) obj;
        return this.navigationHash().equals(other.navigationHash());
    }
    
    public boolean isOlder(Reference other) {
        return false;
    }

    
    // unsupported operations:

    public void join(Reference oe) {
        throw new UnsupportedOperationException();
    }

    public long lastModified() {
        throw new UnsupportedOperationException();
    }

    public int positions() {
        throw new UnsupportedOperationException();
    }
    
}
