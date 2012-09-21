

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class version {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        prop.put("versionstring", yacyBuildProperties.getLongVersion());
        prop.put("svnRev", yacyBuildProperties.getSVNRevision());
        prop.put("buildDate", yacyBuildProperties.getBuildDate());
        // return rewrite properties
        return prop;
    }

}



