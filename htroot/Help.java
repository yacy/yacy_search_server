//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

//dummy class
public class Help {

    public static servletProperties respond(@SuppressWarnings("unused") final RequestHeader requestHeader, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        return prop;
    }
}
