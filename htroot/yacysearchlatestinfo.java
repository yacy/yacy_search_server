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
        final QueryParams theQuery = theSearch.getQuery();

        // dynamically update count values
        final int totalcount = theSearch.getRankingResult().getLocalIndexCount() - theSearch.getRankingResult().getMissCount() - theSearch.getRankingResult().getSortOutCount() + theSearch.getRankingResult().getRemoteIndexCount();
        final int offset = theQuery.neededResults() - theQuery.itemsPerPage() + 1;
        prop.put("offset", offset);
        prop.put("itemscount",Formatter.number(offset + theSearch.getQuery().itemsPerPage >= totalcount ? offset + totalcount % theSearch.getQuery().itemsPerPage - 1 : offset + theSearch.getQuery().itemsPerPage - 1));
        prop.put("itemsperpage", theSearch.getQuery().itemsPerPage);
        prop.put("totalcount", Formatter.number(totalcount, true));
        prop.put("localResourceSize", Formatter.number(theSearch.getRankingResult().getLocalIndexCount(), true));
        prop.put("localMissCount", Formatter.number(theSearch.getRankingResult().getMissCount(), true));
        prop.put("remoteResourceSize", Formatter.number(theSearch.getRankingResult().getRemoteResourceSize(), true));
        prop.put("remoteIndexCount", Formatter.number(theSearch.getRankingResult().getRemoteIndexCount(), true));
        prop.put("remotePeerCount", Formatter.number(theSearch.getRankingResult().getRemotePeerCount(), true));
        prop.putJSON("navurlBase", QueryParams.navurlBase("html", theQuery, null, theQuery.urlMask.toString(), theQuery.navigators).toString());

        return prop;
    }

}
