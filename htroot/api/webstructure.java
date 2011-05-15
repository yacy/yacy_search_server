// webstructure.java
// ------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 01.05.2008 on http://yacy.net
//
// $LastChangedDate: 2009-03-16 19:08:43 +0100 (Mo, 16 Mrz 2009) $
// $LastChangedRevision: 5723 $
// $LastChangedBy: borg-0300 $
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


import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.graphics.WebStructureGraph;

public class webstructure {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        String about = post == null ? null : post.get("about", null);
        prop.put("out", 0);
        prop.put("in", 0);
        if (about != null) {
            DigestURI url = null;
            if (about.length() > 6) {
                try {
                    url = new DigestURI(about);
                    about = UTF8.String(url.hash(), 6, 6);
                } catch (MalformedURLException e) {
                    about = null;
                }
            }
            if (url != null && about != null) {
                WebStructureGraph.StructureEntry sentry = sb.webStructure.outgoingReferences(about);
                if (sentry != null) {
                    reference(prop, "out", 0, sentry, sb.webStructure);
                    prop.put("out_domains", 1);
                    prop.put("out", 1);
                } else {
                    prop.put("out_domains", 0);
                    prop.put("out", 1);
                }
                sentry = sb.webStructure.incomingReferences(about);
                if (sentry != null) {
                    reference(prop, "in", 0, sentry, sb.webStructure);
                    prop.put("in_domains", 1);
                    prop.put("in", 1);
                } else {
                    prop.put("in_domains", 0);
                    prop.put("in", 1);
                }
            }
        } else if (sb.adminAuthenticated(header) >= 2) {
            // show a complete list of link structure informations in case that the user is authenticated
            final boolean latest = ((post == null) ? false : post.containsKey("latest"));
            final Iterator<WebStructureGraph.StructureEntry> i = sb.webStructure.structureEntryIterator(latest);
            int c = 0;
            WebStructureGraph.StructureEntry sentry;
            while (i.hasNext()) {
                sentry = i.next();
                reference(prop, "out", c, sentry, sb.webStructure);
                c++;
            }
            prop.put("out_domains", c);
            prop.put("out", 1);
            if (latest) sb.webStructure.joinOldNew();
        } else {
            // not-authenticated users show nothing
            prop.put("out_domains", 0);
            prop.put("out", 1);
        }
        prop.put("out_maxref", WebStructureGraph.maxref);
        prop.put("maxhosts", WebStructureGraph.maxhosts);
        
        // return rewrite properties
        return prop;
    }
    
    public static void reference(serverObjects prop, String prefix, int c, WebStructureGraph.StructureEntry sentry, WebStructureGraph ws) {
        prop.put(prefix + "_domains_" + c + "_hash", sentry.hosthash);
        prop.put(prefix + "_domains_" + c + "_domain", sentry.hostname);
        prop.put(prefix + "_domains_" + c + "_date", sentry.date);
        Iterator<Map.Entry<String, Integer>> k = sentry.references.entrySet().iterator();
        Map.Entry<String, Integer> refentry;
        String refdom, refhash;
        Integer refcount;
        int d = 0;
        refloop: while (k.hasNext()) {
            refentry = k.next();
            refhash = refentry.getKey();
            refdom = ws.hostHash2hostName(refhash);
            if (refdom == null) continue refloop;
            prop.put(prefix + "_domains_" + c + "_citations_" + d + "_refhash", refhash);
            prop.put(prefix + "_domains_" + c + "_citations_" + d + "_refdom", refdom);
            refcount = refentry.getValue();
            prop.put(prefix + "_domains_" + c + "_citations_" + d + "_refcount", refcount.intValue());
            d++;
        }
        prop.put(prefix + "_domains_" + c + "_citations", d);
    }
}
