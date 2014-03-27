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
        prop.put("itemscount",Formatter.number(offset + theSearch.query.itemsPerPage >= theSearch.getResultCount() ? offset + theSearch.getResultCount() % theSearch.query.itemsPerPage - 1 : offset + theSearch.query.itemsPerPage - 1));
        prop.put("itemsperpage", theSearch.query.itemsPerPage);
        prop.put("totalcount", Formatter.number(theSearch.getResultCount(), true));
        prop.put("localResourceSize", Formatter.number(theSearch.local_rwi_stored.get() + theSearch.local_solr_stored.get(), true));
        prop.put("remoteResourceSize", Formatter.number(theSearch.remote_rwi_stored.get() + theSearch.remote_solr_stored.get(), true));
        prop.put("remoteIndexCount", Formatter.number(theSearch.remote_rwi_available.get() + theSearch.remote_solr_available.get(), true));
        prop.put("remotePeerCount", Formatter.number(theSearch.remote_rwi_peerCount.get() + theSearch.remote_solr_peerCount.get(), true));
        prop.putJSON("navurlBase", QueryParams.navurlBase(RequestHeader.FileType.HTML, theSearch.query, null, false).toString());

        return prop;
    }

}
