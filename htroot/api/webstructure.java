

import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWebStructure;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class webstructure {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final serverObjects prop = new serverObjects();
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final boolean latest = ((post == null) ? false : post.containsKey("latest"));
        final Iterator<plasmaWebStructure.structureEntry> i = sb.webStructure.structureEntryIterator(latest);
        int c = 0, d;
        plasmaWebStructure.structureEntry sentry;
        Map.Entry<String, Integer> refentry;
        String refdom, refhash;
        Integer refcount;
        Iterator<Map.Entry<String, Integer>> k;
        while (i.hasNext()) {
            sentry = i.next();
            prop.put("domains_" + c + "_hash", sentry.domhash);
            prop.put("domains_" + c + "_domain", sentry.domain);
            prop.put("domains_" + c + "_date", sentry.date);
            k = sentry.references.entrySet().iterator();
            d = 0;
            refloop: while (k.hasNext()) {
                refentry = k.next();
                refhash = refentry.getKey();
                refdom = sb.webStructure.resolveDomHash2DomString(refhash);
                if (refdom == null) continue refloop;
                prop.put("domains_" + c + "_citations_" + d + "_refhash", refhash);
                prop.put("domains_" + c + "_citations_" + d + "_refdom", refdom);
                refcount = refentry.getValue();
                prop.put("domains_" + c + "_citations_" + d + "_refcount", refcount.intValue());
                d++;
            }
            prop.put("domains_" + c + "_citations", d);
            c++;
        }
        prop.put("domains", c);
        prop.put("maxref", plasmaWebStructure.maxref);
        if (latest) sb.webStructure.joinOldNew();
        
        // return rewrite properties
        return prop;
    }
}
