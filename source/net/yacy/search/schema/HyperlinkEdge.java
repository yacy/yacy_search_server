/**
 *  HyperlinkEdge
 *  Copyright 2014 by Michael Peter Christen
 *  First released 04.04.2014 at http://yacy.net
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

package net.yacy.search.schema;

import java.net.MalformedURLException;

import net.yacy.cora.document.id.MultiProtocolURL;

public class HyperlinkEdge {

    public static class Target extends MultiProtocolURL {
        private static final long serialVersionUID = 5746600160371492930L;
        public HyperlinkType type;

        public Target(final String url, final HyperlinkType type) throws MalformedURLException {
            super(url);
            this.type = type;
        }
        
        public Target(final MultiProtocolURL url, final HyperlinkType type) {
            super(url);
            this.type = type;
        }
    }

    public MultiProtocolURL source;
    public Target target;    

    public HyperlinkEdge(MultiProtocolURL source, Target target) {
        this.source = source;
        this.target = target;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(120);
        sb.append(this.source.toNormalform(true));
        sb.append(" -> ");
        sb.append(this.target.toNormalform(true));
        sb.append(" (");
        sb.append(this.target.type.name());
        sb.append(")");
        return sb.toString();
    }
    
}
