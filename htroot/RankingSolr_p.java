
import java.util.Map;

import net.yacy.cora.federate.solr.Boost;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class RankingSolr_p {


    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // clean up all search events
        SearchEventCache.cleanupEvents(true);

        if (post != null && post.containsKey("EnterDoublecheck")) {
            Boost.RANKING.setMinTokenLen(post.getInt("minTokenLen", 3));
            Boost.RANKING.setQuantRate(post.getFloat("quantRate", 0.5f));
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_MINLENGTH, Boost.RANKING.getMinTokenLen());
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_QUANTRATE, Boost.RANKING.getQuantRate());
        }

        if (post != null && post.containsKey("ResetDoublecheck")) {
            Boost.RANKING.setMinTokenLen(3);
            Boost.RANKING.setQuantRate(0.5f);
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_MINLENGTH, Boost.RANKING.getMinTokenLen());
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_QUANTRATE, Boost.RANKING.getQuantRate());
        }
        
        if (post != null && post.containsKey("EnterRanking")) {
            StringBuilder boostString = new StringBuilder(); // SwitchboardConstants.SEARCH_RANKING_SOLR_BOOST;
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("boost")) {
                    String fieldName = entry.getKey().substring(6);
                    YaCySchema field = YaCySchema.valueOf(fieldName);
                    if (field == null) continue;
                    try {
                        float boost = Float.parseFloat(entry.getValue());
                        if (boostString.length() > 0) boostString.append(',');
                        boostString.append(field.getSolrFieldName()).append('^').append(Float.toString(boost));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
            }
            if (boostString.length() > 0) {
                String s = boostString.toString();
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_BOOST, s);
                Boost.RANKING.update(s);
            }
            
        }

        if (post != null && post.containsKey("ResetRanking")) {
            Boost.RANKING.initDefaults();
        }

        final serverObjects prop = new serverObjects();
        prop.put("minTokenLen", Boost.RANKING.getMinTokenLen());
        prop.put("quantRate", Boost.RANKING.getQuantRate());
        int i = 0;
        for (Map.Entry<YaCySchema, Float> entry: Boost.RANKING.entrySet()) {
            YaCySchema field = entry.getKey();
            float boost = entry.getValue();
            prop.put("boosts_" + i + "_field", field.getSolrFieldName());
            prop.put("boosts_" + i + "_boost", Float.toString(boost));
            i++;
        }
        prop.put("boosts", i);

        return prop;
    }

}
