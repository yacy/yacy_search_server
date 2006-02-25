package xml;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class snippet {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) throws MalformedURLException {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        // getting url
        String urlString = post.get("url", "");
        URL url = new URL(urlString);
        
        String querystring = post.get("search", "").trim();
        if ((querystring.length() > 2) && (querystring.charAt(0) == '"') && (querystring.charAt(querystring.length() - 1) == '"')) {
            querystring = querystring.substring(1, querystring.length() - 1).trim();
        }        
        final TreeSet query = plasmaSearchQuery.cleanQuery(querystring);
        
        // filter out stopwords
        final TreeSet filtered = kelondroMSetTools.joinConstructive(query, plasmaSwitchboard.stopwords);
        if (filtered.size() > 0) {
            kelondroMSetTools.excludeDestructive(query, plasmaSwitchboard.stopwords);
        }        
        
        // do the search
        Set queryHashes = plasmaSearchQuery.words2hashes(query);
        
        plasmaSnippetCache.result snippet = switchboard.snippetCache.retrieve(url, queryHashes, true, 260);
        prop.put("status",snippet.source);
        if (snippet.source < 11) {
            prop.put("text", (snippet.line != null)?snippet.line.trim():"unknown");
        } else {
            prop.put("text", (snippet.error != null)?snippet.error.trim():"unkown");
        }
        prop.put("urlHash",plasmaURL.urlHash(url));
        
        
        // return rewrite properties
        return prop;
    }
}
