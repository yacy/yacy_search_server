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
        plasmaWebStructure structure = sb.webStructure;
        Iterator i = structure.structureEntryIterator();
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
                refdom = structure.resolveDomHash2DomString(refhash);
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
        
        // return rewrite properties
        return prop;
    }
}
