/**
 *  citation
 *  Copyright 2013 by Michael Peter Christen
 *  First released 12.6.2013 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */


import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.OrderedScoreMap;
import net.yacy.document.SentenceReader;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class citation {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final Segment segment = sb.index;
        final SolrConnector connector = segment.fulltext().getDefaultConnector();

        // avoid UNRESOLVED PATTERN
        prop.put("url", "");
        prop.put("citations", 0);
        prop.put("sentences", 0);

        DigestURL uri = null;
        String url = "";
        String hash = "";
        int ch = 10;
        if (post != null) {
            if (post.containsKey("url")) {
                url = post.get("url");
                if (!url.startsWith("http://") &&
                           !url.startsWith("https://") &&
                           !url.startsWith("ftp://") &&
                           !url.startsWith("smb://") &&
                          !url.startsWith("file://")) {
                    url = "http://" + url;
                }
            }
            if (post.containsKey("hash")) {
                hash = post.get("hash");
            }
            if (post.containsKey("ch")) {
                ch = post.getInt("ch", ch);
            }
        }
        
        if (url.length() > 0) {
            try {
                uri = new DigestURL(url, null);
                hash = ASCII.String(uri.hash());
            } catch (final MalformedURLException e) {}
        }
        if (uri == null && hash.length() > 0) {
            uri = sb.getURL(ASCII.getBytes(hash));
            if (uri == null) {
                connector.commit(true); // try again, that url can be fresh
                uri = sb.getURL(ASCII.getBytes(hash));
            }
        }
        if (uri == null) return prop; // no proper url addressed
        url = uri.toNormalform(true);
        prop.put("url", url);
        
        // get the document from the index
        SolrDocument doc;
        try {
            doc = segment.fulltext().getDefaultConnector().getDocumentById(hash, CollectionSchema.title.getSolrFieldName(), CollectionSchema.text_t.getSolrFieldName());
        } catch (final IOException e1) {
            return prop;
        }
        @SuppressWarnings("unchecked")
        ArrayList<String> title = (ArrayList<String>) doc.getFieldValue(CollectionSchema.title.getSolrFieldName());
        String text = (String) doc.getFieldValue(CollectionSchema.text_t.getSolrFieldName());

        ArrayList<String> sentences = new ArrayList<String>();
        if (title != null) for (String s: title) if (s.length() > 0) sentences.add(s);
        SentenceReader sr = new SentenceReader(text);
        StringBuilder line;
        while (sr.hasNext()) {
            line = sr.next();
            if (line.length() > 0) sentences.add(line.toString());
        }
        
        // for each line make a statistic about the number of occurrences somewhere else
        OrderedScoreMap<String> scores = new OrderedScoreMap<String>(null); // accumulates scores for citating urls
        LinkedHashMap<String, Set<DigestURL>> sentenceOcc = new LinkedHashMap<String, Set<DigestURL>>();
        for (String sentence: sentences) {
            if (sentence == null || sentence.length() < 40) {
                // do not count the very short sentences
                sentenceOcc.put(sentence, null);
                continue;
            }
            try {
                sentence = sentence.replace('"', '\'');
                SolrDocumentList doclist = connector.getDocumentListByQuery("text_t:\"" + sentence + "\"", CollectionSchema.url_chars_i.getSolrFieldName() + " asc", 0, 100, CollectionSchema.sku.getSolrFieldName());
                int count = (int) doclist.getNumFound();
                if (count > 0) {
                    Set<DigestURL> list = new TreeSet<DigestURL>();
                    for (SolrDocument d: doclist) {
                        String u = (String) d.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                        if (u == null || u.equals(url)) continue;
                        scores.inc(u);
                        try {list.add(new DigestURL(u, null));} catch (final MalformedURLException e) {}
                    }
                    sentenceOcc.put(sentence, list);
                }
            } catch (final Throwable ee) {
                
            }
        }
        sentences.clear(); // we do not need this again
        
        // iterate the sentences
        int i = 0;
        for (Map.Entry<String, Set<DigestURL>> se: sentenceOcc.entrySet()) {
            prop.put("sentences_" + i + "_dt", i);
            StringBuilder dd = new StringBuilder(se.getKey());
            Set<DigestURL> app = se.getValue();
            if (app != null && app.size() > 0) {
                dd.append("<br/>appears in:");
                for (DigestURL u: app) {
                    if (u != null) {
                        dd.append(" <a href=\"").append(u.toNormalform(false)).append("\">").append(u.getHost()).append("</a>");
                    }
                }
            }
            prop.put("sentences_" + i + "_dd", dd.toString());
            i++;
        }
        prop.put("sentences", i);
        
        // iterate the citations in order of number of citations
        i = 0;
        for (String u: scores.keyList(false)) {
            try {
                DigestURL uu = new DigestURL(u, null);
                prop.put("citations_" + i + "_dt", "<a href=\"" + u + "\">" + u + "</a>");
                StringBuilder dd = new StringBuilder();
                dd.append("makes ").append(Integer.toString(scores.get(u))).append(" citations: of ").append(url);
                for (Map.Entry<String, Set<DigestURL>> se: sentenceOcc.entrySet()) {
                    Set<DigestURL> occurls = se.getValue();
                    if (occurls != null && occurls.contains(uu)) dd.append("<br/><a href=\"/solr/select?q=text_t:%22").append(se.getKey().replace('"', '\'')).append("%22&rows=100&grep=&wt=grephtml\">").append(se.getKey()).append("</a>");
                }
                prop.put("citations_" + i + "_dd", dd.toString());
                i++;
            } catch (final MalformedURLException e) {}
        }
        prop.put("citations", i);
        
        // find similar documents from different hosts
        i = 0;
        for (String u: scores.keyList(false)) {
            if (scores.get(u) < ch) continue;
            try {
                DigestURL uu = new DigestURL(u, null);
                if (uu.getOrganization().equals(uri.getOrganization())) continue;
                prop.put("similar_links_" + i + "_url", u);
                i++;
            } catch (final MalformedURLException e) {}
        }
        prop.put("similar_links", i);
        prop.put("similar", i > 0 ? 1 : 0);
        
        // return rewrite properties
        return prop;
    }

}
