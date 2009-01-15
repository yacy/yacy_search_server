

import de.anomic.http.httpRequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class version {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        
        prop.put("version", env.getConfig("version", "0.0"));
        prop.put("svnRev", env.getConfig("svnRevision", "0"));
        prop.put("buildDate", env.getConfig("vdate", "19700101"));
        // return rewrite properties
        return prop;
    }
    
}



