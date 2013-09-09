package interaction_elements;


import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Footer {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader requestHeader, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();

        prop.put("enabled_color", env.getConfig("color_tableheader", ""));

        int count = 0;

        prop.put("enabled_userlogonenabled", env.getConfigBool("interaction.userlogon.enabled", false) ? "1" : "0");
        if (env.getConfigBool("interaction.userlogon.enabled", false)) count++;

        if (count > 0) {
        	prop.put("enabled", "1");
        	prop.put("enabled_userlogonenabled_ratio", Math.round(100/count)-1);

        } else {
        	prop.put("enabled", "0");
        }


        return prop;
    }
}
