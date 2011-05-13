import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.Formatter;

import de.anomic.search.QueryParams;
import de.anomic.search.SearchEvent;
import de.anomic.search.SearchEventCache;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class yacysearchlatestinfo {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        //Switchboard sb = (Switchboard) env;
        
        // find search event
        final String eventID = post.get("eventID", "");
        final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }
        final QueryParams theQuery = theSearch.getQuery();
        //if (sb.isGlobalMode() && !theQuery.isLocal()) try {Thread.sleep(1000);} catch (InterruptedException e) {}
        
        // dynamically update count values
        final int totalcount = theSearch.getRankingResult().getLocalIndexCount() - theSearch.getRankingResult().getMissCount() - theSearch.getRankingResult().getSortOutCount() + theSearch.getRankingResult().getRemoteIndexCount();
        final int offset = theQuery.neededResults() - theQuery.displayResults() + 1;
        prop.put("offset", offset);
        prop.put("itemscount", -1);
        prop.put("totalcount", Formatter.number(totalcount, true));
        prop.put("localResourceSize", Formatter.number(theSearch.getRankingResult().getLocalIndexCount(), true));
        prop.put("localMissCount", Formatter.number(theSearch.getRankingResult().getMissCount(), true));
        prop.put("remoteResourceSize", Formatter.number(theSearch.getRankingResult().getRemoteResourceSize(), true));
        prop.put("remoteIndexCount", Formatter.number(theSearch.getRankingResult().getRemoteIndexCount(), true));
        prop.put("remotePeerCount", Formatter.number(theSearch.getRankingResult().getRemotePeerCount(), true));
        
        return prop;
    }
    
}
