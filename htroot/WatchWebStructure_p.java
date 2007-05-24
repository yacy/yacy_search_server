
import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class WatchWebStructure_p {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        //plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        int width = 768;
        int height = 576;
        int depth = 3;
        String host = "auto";
        
        if (post != null) {
            width = post.getInt("width", 768);
            height = post.getInt("height", 576);
            depth = post.getInt("depth", 3);
            host = post.get("host", "auto");
        }
        
        prop.put("host", host);
        prop.put("depth", depth);
        prop.put("depthi", Math.min(8, depth + 1));
        prop.put("depthd", Math.max(0, depth - 1));
        prop.put("width", width);
        prop.put("height", height);
        
        return prop;
    }
}
