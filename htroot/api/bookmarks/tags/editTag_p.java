
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class editTag_p {
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final Switchboard switchboard = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        boolean isAdmin = false;
        isAdmin = switchboard.verifyAuthentication(header);

        prop.put("result", "0");//error
        //rename tags
        if(post != null && isAdmin && post.containsKey("old") && post.containsKey("new")){
            if(switchboard.bookmarksDB.renameTag(post.get("old"), post.get("new")))
                prop.put("result", "1");//success
        }
        // return rewrite properties
        return prop;
    }

}
