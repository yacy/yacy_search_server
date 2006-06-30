// htmlFilterImageEntry.java
// -----------------------------
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.htmlFilter;

import java.net.URL;

public class htmlFilterImageEntry implements Comparable {

    private URL url;
    private String alt;
    private int width, height;

    public htmlFilterImageEntry(URL url, String alt, int width, int height) {
        this.url = url;
        this.alt = alt;
        this.width = width;
        this.height = height;
    }

    public URL url() {
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
        if ((width > 0) && (height > 0))
            return ((0xFFFF - (((width * height) >> 8) & 0xFFFF)) << 16) | (url.hashCode() & 0xFFFF);
        else
            return 0xFFFF0000 | (url.hashCode() & 0xFFFF);
    }
    
    public int compareTo(Object h) {
        // this is needed if this object is stored in a TreeSet
        assert (url != null);
        assert (h instanceof htmlFilterImageEntry);
        if (this.url.toString().equals(((htmlFilterImageEntry) h).url.toString())) return 0;
        int thc = this.hashCode();
        int ohc = ((htmlFilterImageEntry) h).hashCode();
        if (thc < ohc) return -1;
        if (thc > ohc) return 1;
        return 0;
    }
    
    public boolean equals(Object o) {
        if (!(o instanceof htmlFilterImageEntry)) return false;
        return compareTo(o) == 0;
    }
}
