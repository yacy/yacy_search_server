// suggest.java
// -----------------------
// (C) 2010 by Michael Peter Christen; mc@yacy.net
// first published 11.10.2010 in Frankfurt, Germany on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


package net.yacy.htroot;

import java.util.Collection;
import java.util.ConcurrentModificationException;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.DidYouMean;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

/**
 * for json format:
 * implementation of the opensearch suggestion extension, see
 * http://www.opensearch.org/Specifications/OpenSearch/Extensions/Suggestions/1.1
 * or
 * https://wiki.mozilla.org/Search_Service/Suggestions
 *
 * for xml format:
 * see Microsoft Search Suggestion Format
 * http://msdn.microsoft.com/en-us/library/cc848863%28VS.85%29.aspx
 * and
 * http://msdn.microsoft.com/en-us/library/cc848862%28v=VS.85%29.aspx
 */
public class suggest {

    private static final int meanMax = 30;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final servletProperties prop = new servletProperties();

        final String ext = header.get(HeaderFramework.CONNECTION_PROP_EXT, "");
        final boolean json = ext.equals("json");
        final boolean xml = ext.equals("xml");

        // get query
        final String originalquerystring = (post == null) ? "" : post.get("query", post.get("q", ""));
        final String querystring =  originalquerystring.trim();
        final int timeout = (post == null) ? 300 : post.getInt("timeout", 300);
        final int count = (post == null) ? 10 : Math.min(30, post.getInt("count", 20));

        int c = 0;
        final DidYouMean didYouMean = new DidYouMean(sb.index, querystring);
        final Collection<StringBuilder> suggestions = didYouMean.getSuggestions(timeout, count, sb.index.fulltext().collectionSize() < 2000000);
        //[#[query]#,[#{suggestions}##[text]##(eol)#,::#(/eol)##{/suggestions}#]]
        synchronized (suggestions) {
            for (final StringBuilder suggestion: suggestions) {
                if (c >= meanMax) break;
                try {
                    final String s = suggestion.toString();
                    if (json) {
                        prop.putJSON("suggestions_" + c + "_text", s);
                    } else if (xml) {
                        prop.putXML("suggestions_" + c + "_text", s);
                    } else {
                        prop.putHTML("suggestions_" + c + "_text", s);
                    }
                    prop.put("suggestions_" + c + "_eol", 0);
                    c++;
                } catch (final ConcurrentModificationException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }

        if (c > 0) {
            prop.put("suggestions_" + (c - 1) + "_eol", 1);
        }
        prop.put("suggestions", c);
        if (json) {
            prop.putJSON("query", originalquerystring);
        } else if (xml) {
            prop.putXML("query", originalquerystring);
        } else {
            prop.putHTML("query", originalquerystring);
        }

        // check for JSONP
        if (post != null && post.containsKey("callback") ) {
            final String jsonp = post.get("callback") + "([";
            prop.put("jsonp-start", jsonp);
            prop.put("jsonp-end", "])");
        } else {
            prop.put("jsonp-start", "");
            prop.put("jsonp-end", "");
        }

        // Adding CORS Access header for xml output
        if (xml) {
            final ResponseHeader outgoingHeader = new ResponseHeader(200);
            outgoingHeader.put(HeaderFramework.CORS_ALLOW_ORIGIN, "*");
            prop.setOutgoingHeader(outgoingHeader);
        }

        // return rewrite properties
        return prop;
    }

}
