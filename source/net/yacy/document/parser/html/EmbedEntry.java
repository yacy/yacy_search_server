/**
 *  EmbedEntry
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 27.04.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.parser.html;

import net.yacy.cora.document.id.DigestURL;

public class EmbedEntry {

    private final DigestURL url;
    private final int width, height;
    private final String type, pluginspage;

    public EmbedEntry(final DigestURL url, int width, int height, String type, String pluginspage) {
        this.url = url;
        this.width = width;
        this.height = height;
        this.type = type;
        this.pluginspage = pluginspage;
    }

    public DigestURL getUrl() {
        return this.url;
    }

    public final int getWidth() {
        return this.width;
    }

    public final int getHeight() {
        return this.height;
    }

    public final String getType() {
        return this.type;
    }

    public final String getPluginspage() {
        return this.pluginspage;
    }

    @Override
    public String toString() {
        return "<embed url=\"" + this.url.toNormalform(false) + "\"" +
               (this.type != null && this.type.length() > 0 ? " type=\"" + this.type + "\"" : "") +
               (this.pluginspage != null && this.pluginspage.length() > 0 ? " pluginspage=\"" + this.pluginspage + "\"" : "") +
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
            return /*0x7FFF0000 |*/ (this.url.hashCode() & 0xFFFF);
        return ((0x7FFF - (((this.width * this.height) >> 9) & 0x7FFF)) << 16) | (this.url.hashCode() & 0xFFFF);
    }
}
