// migrateurldb_p.java

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.migration;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class migrateurldb_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        int cnt;

        if ((cnt = migration.migrateUrldbtoSolr(sb)) > 0) {
            prop.put("count", cnt);

            if (post != null && post.containsKey("dorefresh")) {
                int lastcount = post.getInt("lastcount", 0);
                Long t = post.getLong("lasttime", 1);

                Double difft = (System.currentTimeMillis() - t) / 60000.0d;
                int diff = (int)((lastcount - cnt) / difft) ;
                prop.put("speed", diff);
                prop.put("lasttime", t);
                prop.put("lastcount", lastcount);

            } else {
                prop.put("speed", "?");
                prop.put("lastcount",cnt);
                prop.put("lasttime", System.currentTimeMillis());
            }
        } else {
            prop.put("speed", "");
            prop.put("count", "no urldb index available");
        }


        // return rewrite properties
        return prop;
    }
}