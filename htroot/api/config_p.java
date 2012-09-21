
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class config_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        //plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        //change a Key
        if(post != null && post.containsKey("key") && post.containsKey("value")) {
            final String key = post.get("key");
            final String value = post.get("value");
            if(!"".equals(key)) {
                env.setConfig(key, value);
            }
        }

        final Iterator<String> keys = env.configKeys();

        final List<String> list = new ArrayList<String>(250);

        while (keys.hasNext()) {
            list.add(keys.next());
        }

        Collections.sort(list);

        int count=0;

        for (final String key : list) {
            prop.putHTML("options_" + count + "_key", key);
            prop.putHTML("options_" + count + "_value", env.getConfig(key, "ERROR"));
            count++;
        }
        prop.put("options", count);

        // return rewrite properties
        return prop;
    }

}
