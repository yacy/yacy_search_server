

package net.yacy.htroot.api;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class version {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        prop.put("versionstring", yacyBuildProperties.getReleaseStub());
        prop.put("buildDate", yacyBuildProperties.getRepositoryVersionDate());
        prop.put("buildTime", yacyBuildProperties.getRepositoryVersionTime());
        prop.put("buildDateTime", yacyBuildProperties.getRepositoryVersionDate() + yacyBuildProperties.getRepositoryVersionTime());
        prop.put("buildHash", yacyBuildProperties.getRepositoryVersionHash());
        prop.put("buildVersion", yacyBuildProperties.getVersion());

        // return rewrite properties
        return prop;
    }

}



