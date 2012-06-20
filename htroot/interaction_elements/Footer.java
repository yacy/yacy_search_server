package interaction_elements;


import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Footer {
    
    public static serverObjects respond(final RequestHeader requestHeader, final serverObjects post, final serverSwitch env) {
    	
    	final Switchboard sb = (Switchboard) env;
    	
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
