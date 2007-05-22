// webstructure.java
// (C) 2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 22.05.2007 on http://yacy.net
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


package xml;

import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWebStructure;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class webstructure {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        Iterator i = sb.webStructure.structureEntryIterator();
        int c = 0, d;
        plasmaWebStructure.structureEntry sentry;
        Map.Entry refentry;
        String refdom, refhash;
        Integer refcount;
        Iterator k;
        while (i.hasNext()) {
            sentry = (plasmaWebStructure.structureEntry) i.next();
            prop.put("domains_" + c + "_hash", sentry.domhash);
            prop.put("domains_" + c + "_domain", sentry.domain);
            prop.put("domains_" + c + "_date", sentry.date);
            k = sentry.references.entrySet().iterator();
            d = 0;
            refloop: while (k.hasNext()) {
                refentry = (Map.Entry) k.next();
                refhash = (String) refentry.getKey();
                refdom = sb.webStructure.resolveDomHash2DomString(refhash);
                if (refdom == null) continue refloop;
                prop.put("domains_" + c + "_citations_" + d + "_refhash", refhash);
                prop.put("domains_" + c + "_citations_" + d + "_refdom", refdom);
                refcount = (Integer) refentry.getValue();
                prop.put("domains_" + c + "_citations_" + d + "_refcount", refcount.intValue());
                d++;
            }
            prop.put("domains_" + c + "_citations", d);
            c++;
        }
        prop.put("domains", c);
        prop.put("maxref", plasmaWebStructure.maxref);
        
        // return rewrite properties
        return prop;
    }
}
