package interaction_elements;

import net.yacy.cora.protocol.RequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class OverlayInteraction {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();

        prop.put("enabled", env.getConfigBool("interaction.overlayinteraction.enabled", false) ? "1" : "0");

        prop.put("enabled_url", post.get("url", ""));

        prop.put("enabled_urlhash", post.get("urlhash", ""));

        return prop;
    }
}
