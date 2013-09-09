package interaction_elements;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class OverlayInteraction {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();

        prop.put("enabled", env.getConfigBool("interaction.overlayinteraction.enabled", false) ? "1" : "0");

        prop.put("enabled_url", post.get("url", ""));

        prop.put("enabled_urlhash", post.get("urlhash", ""));

        prop.put("enabled_action", post.get("action", ""));

        prop.put("enabled_color", env.getConfig("color_tableheader", ""));

        return prop;
    }
}
