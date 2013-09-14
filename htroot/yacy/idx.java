/**
 *  idx
 *  Copyright 2011 by Michael Peter Christen
 *  First released 16.05.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-03-08 02:51:51 +0100 (Di, 08 Mrz 2011) $
 *  $LastChangedRevision: 7567 $
 *  $LastChangedBy: low012 $
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

import java.util.Iterator;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.peers.Protocol;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.peers.graphics.WebStructureGraph.HostReference;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class idx {

    // example:
    // http://localhost:8090/yacy/idx.json?object=host

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();

        prop.put("list", 0);
        prop.put("rowdef","");
        prop.put("name","");
        if (post == null || env == null) return prop;

        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        if (sb.adminAuthenticated(header) < 2 && !Protocol.authentifyRequest(post, env)) {
            return prop;
        }

        if (post.get("object", "").equals("host")) {
            prop.put("name","host");
            final ReferenceContainerCache<HostReference> idx = sb.webStructure.incomingReferences();
            prop.put("rowdef", WebStructureGraph.hostReferenceFactory.getRow().toString());
            int count = 0;
            for (final ReferenceContainer<HostReference> references: idx) {
                prop.put("list_" + count + "_term", ASCII.String(references.getTermHash()));
                final Iterator<HostReference> referenceIterator = references.entries();
                final StringBuilder s = new StringBuilder(references.size() * 20); // pre-set of guessed size reduces expandCapacity() and increases performance
                HostReference reference;
                while (referenceIterator.hasNext()) {
                    reference = referenceIterator.next();
                    s.append(reference.toPropertyForm());
                    if (referenceIterator.hasNext()) s.append(",");
                }
                prop.put("list_" + count + "_references", s.toString());
                prop.put("list_" + count + "_comma", 1);
                count++;
            }
            prop.put("list_" + (count-1) + "_comma", 0);
            prop.put("list", count);
        }
        // return rewrite properties
        return prop;
    }

}
