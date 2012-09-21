package interaction_elements;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Document_part {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        final serverObjects prop = new serverObjects();

        prop.put("hash", post.get("hash", ""));
        prop.put("url", post.get("url", ""));
        prop.put("action", post.get("action", ""));

        return prop;
    }
}
