// htmlFilterImageEntry.java
// -----------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created 04.04.2006
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

package de.anomic.htmlFilter;

import de.anomic.yacy.yacyURL;

public class htmlFilterImageEntry implements Comparable<htmlFilterImageEntry> {

    private final yacyURL url;
    private final String alt;
    private final int width, height;

    public htmlFilterImageEntry(final yacyURL url, final String alt, final int width, final int height) {
        this.url = url;
        this.alt = alt;
        this.width = width;
        this.height = height;
    }

    public yacyURL url() {
        return this.url;
    }
    
    public String alt() {
        return this.alt;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public String toString() {
        return "{" + url.toString() + ", " + alt + ", " + width + "/" + height + "}";
    }

    public int hashCode() {
        // if htmlFilterImageEntry elements are stored in a TreeSet, the biggest images shall be listed first
        // this hash method therefore tries to compute a 'perfect hash' based on the size of the images
        // unfortunately it can not be ensured that all images get different hashes, but this should appear
        // only in very rare cases
        if ((width >= 0) && (height >= 0))
            return ((0x7FFF - (((width * height) >> 9) & 0x7FFF)) << 16) | (url.hashCode() & 0xFFFF);
        else
            return 0x7FFF0000 | (url.hashCode() & 0xFFFF);
    }
    
    public int compareTo(final htmlFilterImageEntry h) {
        // this is needed if this object is stored in a TreeSet
        // this method uses the image-size ordering from the hashCode method
        // assuming that hashCode would return a 'perfect hash' this method would
        // create a total ordering on images with respect on the image size
        assert (url != null);
        if (this.url.toNormalform(true, true).equals((h).url.toNormalform(true, true))) return 0;
        final int thc = this.hashCode();
        final int ohc = (h).hashCode();
        if (thc < ohc) return -1;
        if (thc > ohc) return 1;
        return this.url.toString().compareTo((h).url.toString());
    }
    
    public boolean equals(final htmlFilterImageEntry o) {
        return compareTo(o) == 0;
    }
}
