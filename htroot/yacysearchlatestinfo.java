import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.Formatter;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class yacysearchlatestinfo {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        //Switchboard sb = (Switchboard) env;

        // find search event
        final String eventID = post.get("eventID", "");
        final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist.
            // to avoid missing patterns, we return dummy values
            prop.put("offset", 0);
            prop.put("itemscount", 0);
            prop.put("itemsperpage", 10);
            prop.put("totalcount", 0);
            prop.put("localResourceSize", 0);
            prop.put("localMissCount", 0);
            prop.put("remoteResourceSize", 0);
            prop.put("remoteIndexCount", 0);
            prop.put("remotePeerCount", 0);
            return prop;
        }

        // dynamically update count values
        final int offset = theSearch.query.neededResults() - theSearch.query.itemsPerPage() + 1;
        prop.put("offset", offset);
        prop.put("itemscount",Formatter.number(offset + theSearch.query.itemsPerPage >= theSearch.query.getResultCount() ? offset + theSearch.query.getResultCount() % theSearch.query.itemsPerPage - 1 : offset + theSearch.query.itemsPerPage - 1));
        prop.put("itemsperpage", theSearch.query.itemsPerPage);
        prop.put("totalcount", Formatter.number(theSearch.query.getResultCount(), true));
        prop.put("localResourceSize", Formatter.number(theSearch.query.local_rwi_available.get() + theSearch.query.local_solr_available.get(), true));
        prop.put("localMissCount", Formatter.number(theSearch.query.misses.size(), true));
        prop.put("remoteResourceSize", Formatter.number(theSearch.query.remote_available.get(), true));
        prop.put("remoteIndexCount", Formatter.number(theSearch.query.remote_stored.get(), true));
        prop.put("remotePeerCount", Formatter.number(theSearch.query.remote_peerCount.get(), true));
        prop.putJSON("navurlBase", QueryParams.navurlBase("html", theSearch.query, null, theSearch.query.urlMask.toString()).toString());

        return prop;
    }

}
