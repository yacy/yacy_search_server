package net.yacy.htroot.proxymsg;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.BookmarksDB.Bookmark;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


/**
 * Servlet to be included as header (via iframe) on top of a page viewed via urlproxyservlet
 */
public class urlproxyheader {

    public static serverObjects respond(final RequestHeader requestHeader, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        String proxyurlstr = post.get("url",""); // the url of remote page currently viewed
        final boolean hasRights = sb.verifyAuthentication(requestHeader);
        prop.put("allowbookmark", hasRights);

        if (post.containsKey("addbookmark")) {
            proxyurlstr = post.get("bookmark");
            final Bookmark bmk = sb.bookmarksDB.createorgetBookmark(proxyurlstr, null);
            if (bmk != null) {
                bmk.setPublic(false);
                bmk.addTag("/proxy"); // add to bookmark folder
                sb.bookmarksDB.saveBookmark(bmk);
            }
        }

        prop.put("proxyurl", proxyurlstr);
        prop.put("allowbookmark_proxyurl", proxyurlstr);

        if (proxyurlstr.startsWith("https") && !requestHeader.getScheme().equalsIgnoreCase("https")) {
            prop.put("httpsAlertMsg", "1");
        } else {
            prop.put("httpsAlertMsg", "0");
        }

        // TODO: get some index data to display
        /*
        if (post.containsKey("hash")) {
            try {
                String hashstr = post.get("hash");
                final SolrDocument idxdoc = sb.index.fulltext().getDefaultEmbeddedConnector().getDocumentById(hashstr);
                if (idxdoc != null) {
                    String keywords = (String) idxdoc.getFieldValue(CollectionSchema.keywords.getSolrFieldName());
                    if (keywords != null && !keywords.isEmpty()) {
                        keytxt += keywords;
                    }
                    Collection cols = idxdoc.getFieldValues(CollectionSchema.collection_sxt.getSolrFieldName());
                    if (cols != null && !cols.isEmpty()) {
                        for (Object sx : cols) {
                            coltxt += sx.toString();
                        }
                    }
                }catch (IOException ex) { }
            }
        */
        return prop;
    }

}
