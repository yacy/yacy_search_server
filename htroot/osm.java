//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.visualization.RasterPlotter;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.graphics.OSMTile;

public class osm {

    public static RasterPlotter respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        int zoom = 10;
        float lat = 50.11670f;
        float lon = 8.68333f;
        int width = 3;
        int height = 3;
        
        if (post != null) {
            zoom = post.getInt("zoom", zoom);
            lat = post.getFloat("lat", lat);
            lon = post.getFloat("lon", lon);
            width = post.getInt("width", width);
            height = post.getInt("height", height);
        }
        
        final OSMTile.tileCoordinates coord = new OSMTile.tileCoordinates(lat, lon, zoom);
        return OSMTile.getCombinedTiles(coord, width, height);
   }

}