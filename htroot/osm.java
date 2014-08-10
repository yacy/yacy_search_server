//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.graphics.OSMTile;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.RasterPlotter;

public class osm {

    public static RasterPlotter respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        int zoom = 10;
        double lat = 50.11670d;
        double lon = 8.68333d;
        int width = 3;
        int height = 3;

        if (post != null) {
            zoom = post.getInt("zoom", zoom);
            lat = post.getDouble("lat", lat);
            lon = post.getDouble("lon", lon);
            width = post.getInt("width", width);
            height = post.getInt("height", height);
        }

        final OSMTile.tileCoordinates coord = new OSMTile.tileCoordinates(lat, lon, zoom);
        return OSMTile.getCombinedTiles(coord, width, height);
   }

}