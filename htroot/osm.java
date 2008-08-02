

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageMatrix;
import de.anomic.ymage.ymageOSM;

public class osm {

    public static ymageMatrix respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {

        int zoom = 10;
        double lat = 47.968056d;
        double lon = 7.909167d;
        
        if (post != null) {
            zoom = post.getInt("zoom", zoom);
            lat = post.getDouble("lat", lat);
            lon = post.getDouble("lon", lon);
        }
        
        final ymageOSM.tileCoordinates coord = new ymageOSM.tileCoordinates(lat, lon, zoom);
        return ymageOSM.getCombinedTiles(coord);
   }

}