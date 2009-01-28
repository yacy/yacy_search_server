
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class editTag {
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        prop.put("result", "0");//error
        //rename tags
        if(post != null && post.containsKey("old") && post.containsKey("new")){
            if(switchboard.bookmarksDB.renameTag(post.get("old"), post.get("new")))
                prop.put("result", "1");//success
        }
        // return rewrite properties
        return prop;
    }
    
}
