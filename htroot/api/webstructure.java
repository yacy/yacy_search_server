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

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWebStructure;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class webstructure {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final serverObjects prop = new serverObjects();
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final boolean latest = ((post == null) ? false : post.containsKey("latest"));
        String about = ((post == null) ? null : post.get("about", null));

        if (about != null) {
            yacyURL url = null;
            if (about.length() > 6) {
                try {
                    url = new yacyURL(about, null);
                    about = url.hash().substring(6);
                } catch (MalformedURLException e) {
                    about = null;
                }
            }
            if (about != null) {
                plasmaWebStructure.structureEntry sentry = sb.webStructure.references(about);
                if (sentry != null) {
                    reference(prop, 0, sentry, sb.webStructure);
                    prop.put("domains", 1);
                } else {
                    prop.put("domains", 0);
                }
            } else {
                prop.put("domains", 0);
            }
        } else {
            final Iterator<plasmaWebStructure.structureEntry> i = sb.webStructure.structureEntryIterator(latest);
            int c = 0;
            plasmaWebStructure.structureEntry sentry;
            while (i.hasNext()) {
                sentry = i.next();
                reference(prop, c, sentry, sb.webStructure);
                c++;
            }
            prop.put("domains", c);
            if (latest) sb.webStructure.joinOldNew();
        }
        prop.put("maxref", plasmaWebStructure.maxref);
        
        // return rewrite properties
        return prop;
    }
    
    public static void reference(serverObjects prop, int c, plasmaWebStructure.structureEntry sentry, plasmaWebStructure ws) {
        prop.put("domains_" + c + "_hash", sentry.domhash);
        prop.put("domains_" + c + "_domain", sentry.domain);
        prop.put("domains_" + c + "_date", sentry.date);
        Iterator<Map.Entry<String, Integer>> k = sentry.references.entrySet().iterator();
        Map.Entry<String, Integer> refentry;
        String refdom, refhash;
        Integer refcount;
        int d = 0;
        refloop: while (k.hasNext()) {
            refentry = k.next();
            refhash = refentry.getKey();
            refdom = ws.resolveDomHash2DomString(refhash);
            if (refdom == null) continue refloop;
            prop.put("domains_" + c + "_citations_" + d + "_refhash", refhash);
            prop.put("domains_" + c + "_citations_" + d + "_refdom", refdom);
            refcount = refentry.getValue();
            prop.put("domains_" + c + "_citations_" + d + "_refcount", refcount.intValue());
            d++;
        }
        prop.put("domains_" + c + "_citations", d);
    }
}
