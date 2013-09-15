// ImageEntry.java
// -----------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.parser.html;

import java.util.Comparator;

import net.yacy.cora.document.id.AnchorURL;

public class ImageEntry implements Comparable<ImageEntry>, Comparator<ImageEntry> {

    private final AnchorURL imageurl;
    private AnchorURL linkurl;
    private final String alt;
    private String anchortext;
    private final int width, height;
    private final long fileSize;

    /**
     * an ImageEntry represents the appearance of an image in a document. It considers also that an image can be used as an button for a web link
     * and stores the web link also.
     * @param imageurl the link to the image
     * @param linkurl the link which is called when the image is pressed on a web browser. null if the image was not used as link button
     * @param anchortext the text inside the anchor body where the image link appears (including the image tag). null if the image was not used as link button
     * @param alt the als text in the alt tag
     * @param width the width of the image if known, or -1 if unknown
     * @param height the height of the image if known, or -1 if unknown
     * @param fileSize the number of bytes that the image uses on file or -1 if unknown
     */
    public ImageEntry(
            final AnchorURL imageurl,
            final String alt,
            final int width,
            final int height,
            long fileSize) {
        assert imageurl != null;
        this.imageurl = imageurl;
        this.linkurl = null;
        this.anchortext = null;
        this.alt = alt;
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
    }

    public AnchorURL url() {
        return this.imageurl;
    }

    public void setLinkurl(AnchorURL linkurl) {
        this.linkurl = linkurl;
    }
    
    public AnchorURL linkurl() {
        return this.linkurl;
    }

    public void setAnchortext(String anchortext) {
        this.anchortext = anchortext;
    }

    public String anchortext() {
        return this.anchortext;
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

    public long fileSize() {
        return this.fileSize;
    }

    @Override
    public String toString() {
        if (anchortext != null) return anchortext;
        return "<img url=\"" + this.imageurl.toNormalform(false) + "\"" +
               (this.alt != null && this.alt.length() > 0 ? " alt=\"" + this.alt + "\"" : "") +
               (this.width >= 0 ? " width=\"" + this.width + "\"" : "") +
               (this.height >= 0 ? " height=\"" + this.height + "\"" : "") +
               ">";
    }

    @Override
    public int hashCode() {
        // if htmlFilterImageEntry elements are stored in a TreeSet, the biggest images shall be listed first
        // this hash method therefore tries to compute a 'perfect hash' based on the size of the images
        // unfortunately it can not be ensured that all images get different hashes, but this should appear
        // only in very rare cases
        if (this.width < 0 || this.height < 0)
            return /*0x7FFF0000 |*/ (this.imageurl.hashCode() & 0xFFFF);
        return ((0x7FFF - (((this.width * this.height) >> 9) & 0x7FFF)) << 16) | (this.imageurl.hashCode() & 0xFFFF);
    }

    @Override
    public int compareTo(final ImageEntry h) {
        // this is needed if this object is stored in a TreeSet
        // this method uses the image-size ordering from the hashCode method
        // assuming that hashCode would return a 'perfect hash' this method would
        // create a total ordering on images with respect on the image size
        assert (this.imageurl != null);
        if (this.imageurl.toNormalform(true).equals((h).imageurl.toNormalform(true))) return 0;
        final int thc = this.hashCode();
        final int ohc = (h).hashCode();
        if (thc < ohc) return -1;
        if (thc > ohc) return 1;
        return this.imageurl.toString().compareTo((h).imageurl.toString());
    }

    @Override
    public int compare(ImageEntry o1, ImageEntry o2) {
        return o1.compareTo(o2);
    }

    @Override
    public boolean equals(final Object o) {
        if(o != null && o instanceof ImageEntry) {
            return compareTo((ImageEntry) o) == 0;
        }
        return super.equals(o);
    }
}
