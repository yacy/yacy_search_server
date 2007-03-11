import java.awt.Image;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaGrafics;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class PeerLoadPicture {
	
	public static Image respond(httpHeader header, serverObjects post, serverSwitch env) {

        int width = 800;
        int height = 600;
        boolean showidle = true;
        
        if (post != null) {
            width = post.getInt("width", 800);
            height = post.getInt("height", 600);
            showidle = post.get("showidle", "true").equals("true");
        }
        
        //too small values lead to an error, too big to huge CPU/memory consumption, resulting in possible DOS.
        if (width < 400 ) width = 400;
        if (width > 1920) width = 1920;
        if (height < 300) height = 300;
        if (height > 1440) height = 1440;
        return plasmaGrafics.getPeerLoadPicture(5000, width, height, showidle);
    }
}
