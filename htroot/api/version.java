

import net.yacy.cora.protocol.RequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyBuildProperties;

public class version {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        
        prop.put("versionstring", yacyBuildProperties.getLongVersion());
        prop.put("svnRev", yacyBuildProperties.getSVNRevision());
        prop.put("buildDate", yacyBuildProperties.getBuildDate());
        // return rewrite properties
        return prop;
    }
    
}



