
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import de.anomic.http.httpRequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class config_p {
    
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        //plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        String key; 
        
        //change a Key
        if(post != null && post.containsKey("key") && post.containsKey("value")){
            key=post.get("key");
            final String value=post.get("value");
            if(!key.equals("")){
                env.setConfig(key, value);
            }
        }
        
        Iterator<String> keys = env.configKeys();
        
        final List<String> list = new ArrayList<String>(250);
        while(keys.hasNext()){
            list.add(keys.next());
        }
        Collections.sort(list);
        keys = list.iterator();
        
        int count=0;
        while(keys.hasNext()){
            key = keys.next();
            prop.putHTML("options_"+count+"_key", key);
            prop.putHTML("options_"+count+"_value", env.getConfig(key, "ERROR"));
            count++;        
        }
        prop.put("options", count);
        
        // return rewrite properties
        return prop;
    }
    
}



