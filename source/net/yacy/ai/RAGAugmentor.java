/**
 *  RAGAugmentor
 *  Copyright 2026 by Michael Peter Christen
 *  First released 06.02.2026 at https://yacy.net
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

package net.yacy.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.ranking.RankingProfile;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.snippet.TextSnippet;

/**
 * Utility methods that enrich prompts/responses with search-derived context for
 * Retrieval-Augmented Generation (RAG).
 * <p>
 * This class provides:
 * <ul>
 *   <li>local Solr-backed search result extraction</li>
 *   <li>global YaCy network search extraction</li>
 *   <li>markdown condensation of search results</li>
 *   <li>token intersection helpers for query boosting</li>
 *   <li>snippet scoring/selection utilities</li>
 * </ul>
 */
public final class RAGAugmentor {

    /**
     * Utility class; not instantiable.
     */
    private RAGAugmentor() {}

    /**
     * Executes local index search with default boost terms.
     *
     * @param query query string
     * @param count max number of results
     * @param includeSnippet include text snippet field in response objects
     * @return JSON array with {@code url,title[,text]} entries
     */
    public static JSONArray searchResults(String query, int count, final boolean includeSnippet) {
        return searchResults(query, count, includeSnippet, new LinkedHashSet<>());
    }

    /**
     * Executes local Solr search with optional dynamic boost terms.
     *
     * @param query query string
     * @param count max number of results
     * @param includeSnippet include text snippets from indexed text field
     * @param boostTerms optional overlap terms used to bias ranking
     * @return JSON array with normalized search result objects
     */
    public static JSONArray searchResults(String query, int count, final boolean includeSnippet, final Set<String> boostTerms) {
        final JSONArray results = new JSONArray();
        if (query == null || query.length() == 0 || count == 0) return results;
        Switchboard sb = Switchboard.getSwitchboard();
        EmbeddedSolrConnector connector = sb.index.fulltext().getDefaultEmbeddedConnector();
        final SolrQuery params = new SolrQuery();
        // Base query and parser setup.
        params.setQuery(query);
        params.set("defType", "edismax");
        // Static field boosts favor title/headings over body text.
        params.set("qf",
            CollectionSchema.title.getSolrFieldName() + "^3 " +
            CollectionSchema.text_t.getSolrFieldName() + "^1 " +
            CollectionSchema.sku.getSolrFieldName() + "^0.5 " +
            CollectionSchema.h1_txt.getSolrFieldName() + "^2");
        params.set("pf",
            CollectionSchema.title.getSolrFieldName() + "^5 " +
            CollectionSchema.text_t.getSolrFieldName() + "^2");
        final List<String> bqParts = new ArrayList<>();
        if (boostTerms != null && !boostTerms.isEmpty()) {
            // Apply weak boosts to overlap terms, keeping lexical query dominant.
            for (String term : boostTerms) {
                if (term == null || term.isEmpty()) continue;
                bqParts.add(CollectionSchema.title.getSolrFieldName() + ":" + term + "^0.5");
                bqParts.add(CollectionSchema.h1_txt.getSolrFieldName() + ":" + term + "^0.4");
                bqParts.add(CollectionSchema.text_t.getSolrFieldName() + ":" + term + "^0.2");
            }
        }
        // Slightly prefer archive/container file extensions in this use case.
        bqParts.add("(" + CollectionSchema.url_file_ext_s.getSolrFieldName() + ":(zip rar 7z tar gz bz2 xz tgz))^0.1");
        params.set("bq", String.join(" ", bqParts));
        params.setRows(count);
        params.setStart(0);
        params.setFacet(false);
        params.clearSorts();
        // Fetch only fields needed for tool / markdown rendering.
        params.setFields(
            CollectionSchema.sku.getSolrFieldName(), CollectionSchema.title.getSolrFieldName(), CollectionSchema.text_t.getSolrFieldName(),
            CollectionSchema.description_txt.getSolrFieldName(), CollectionSchema.keywords.getSolrFieldName(), CollectionSchema.synonyms_sxt.getSolrFieldName(),
            CollectionSchema.h1_txt.getSolrFieldName(), CollectionSchema.h2_txt.getSolrFieldName(), CollectionSchema.h3_txt.getSolrFieldName(),
            CollectionSchema.h4_txt.getSolrFieldName(), CollectionSchema.h5_txt.getSolrFieldName(), CollectionSchema.h6_txt.getSolrFieldName()
        );
        params.setIncludeScore(true);
        params.set("df", CollectionSchema.text_t.getSolrFieldName());

        try {
            final SolrDocumentList sdl = connector.getDocumentListByParams(params);
            Iterator<SolrDocument> i = sdl.iterator();
            while (i.hasNext()) {
                try {
                    SolrDocument doc = i.next();
                    final JSONObject result = new JSONObject(true);
                    String url = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                    result.put("url", url == null ? "" : url.trim());
                    String title = getOneString(doc, CollectionSchema.title);
                    result.put("title", title == null ? "" : title.trim());
                    if (includeSnippet) {
                        // Use indexed text body as quick snippet source.
                        String text = (String) doc.getFieldValue(CollectionSchema.text_t.getSolrFieldName());
                        result.put("text", limitSnippet(text == null ? "" : text.trim(), 2000));
                    }
                    results.put(result);
                } catch (JSONException e) {
                }
            }
            return results;
        } catch (SolrException | IOException e) {
            return results;
        }
    }

    /**
     * Renders search results as compact markdown context using local-search mode.
     *
     * @param query query string
     * @param count max number of search rows
     * @param global when true, use global YaCy search
     * @return markdown context block
     */
    public static String searchResultsAsMarkdown(String query, int count, boolean global) {
        return searchResultsAsMarkdown(query, count, global, new LinkedHashSet<>());
    }

    /**
     * Renders search results as compact markdown and applies snippet ranking to
     * reduce noise.
     *
     * @param query query string
     * @param count max number of search rows
     * @param global when true, use global YaCy search; otherwise local Solr
     * @param boostTerms optional local-search boost terms
     * @return markdown formatted context used in downstream prompt augmentation
     */
    public static String searchResultsAsMarkdown(String query, int count, boolean global, final Set<String> boostTerms) {
        final long searchStart = System.currentTimeMillis();
        JSONArray searchResults = global ? searchResultsGlobal(query, count, true) : searchResults(query, count, true, boostTerms);
        ConcurrentLog.info("RAGProxy", "searchResults=" + searchResults.length() + " global=" + global + " searchMs=" + (System.currentTimeMillis() - searchStart));
        StringBuilder sb = new StringBuilder();

        // Convert raw rows into scoreable snippet candidates.
        List<Snippet> results = new ArrayList<>();
        for (int i = 0; i < searchResults.length(); i++) {
            try {
                JSONObject r = searchResults.getJSONObject(i);
                String title = r.optString("title", "");
                String url = r.optString("url", "");
                String text = r.optString("text", "");
                if (title.isEmpty()) title = url;
                if (text.isEmpty()) text = title;
                if (title.length() > 0 && text.length() > 0) {
                    Snippet snippet = new Snippet(query, text, url, title, 256);
                    if (snippet.getText().length() > 0) results.add(snippet);
                }
            } catch (JSONException e) {}
        }

        // Lower score is better with the current tf-idf based chunk scorer.
        results.sort(Comparator.comparingDouble(Snippet::getScore));
        // Keep top half to avoid overloading the model context window.
        int limit = results.size() / 2;
        if (results.size() > 0 && limit == 0) limit = 1;
        for (int i = 0; i < limit; i++) {
            Snippet snippet = results.get(i);
            sb.append("## ").append(snippet.getTitle()).append("\n");
            sb.append(snippet.text).append("\n");
            if (snippet.getURL().length() > 0) sb.append("Source: ").append(snippet.getURL()).append("\n");
            sb.append("\n\n");
        }

        ConcurrentLog.info("RAGProxy", "markdownChars=" + sb.length() + " snippetCount=" + results.size());
        return sb.toString();
    }

    /**
     * Executes a global/distributed YaCy search event and maps results into a
     * compact JSON format.
     *
     * @param query query string
     * @param count max number of results
     * @param includeSnippet include snippet text when available
     * @return JSON array with normalized result objects
     */
    public static JSONArray searchResultsGlobal(String query, int count, final boolean includeSnippet) {
        final JSONArray results = new JSONArray();
        if (query == null || query.length() == 0 || count == 0) return results;
        final Switchboard sb = Switchboard.getSwitchboard();
        final RankingProfile ranking = sb.getRanking();
        final int timezoneOffset = 0;
        final QueryModifier modifier = new QueryModifier(timezoneOffset);
        // Parse modifiers and normalize effective query string.
        String querystring = modifier.parse(query);
        if (querystring.length() == 0) querystring = query == null ? "" : query.trim();
        if (querystring.length() == 0) return results;
        final QueryGoal qg = new QueryGoal(querystring);
        // Construct a standard text-domain global query.
        final QueryParams theQuery = new QueryParams(
                qg,
                modifier,
                0,
                "",
                Classification.ContentDomain.TEXT,
                "",
                timezoneOffset,
                new HashSet<Tagging.Metatag>(),
                CacheStrategy.IFFRESH,
                count,
                0,
                ".*",
                null,
                null,
                QueryParams.Searchdom.GLOBAL,
                null,
                true,
                DigestURL.hosthashess(sb.getConfig("search.excludehosth", "")),
                MultiProtocolURL.TLD_any_zone_filter,
                null,
                false,
                sb.index,
                ranking,
                ClientIdentification.yacyIntranetCrawlerAgent.userAgent(),
                0.0d,
                0.0d,
                0.0d,
                sb.getConfigSet("search.navigation"));
        final SearchEvent theSearch = SearchEventCache.getEvent(
                theQuery,
                sb.peers,
                sb.tables,
                (sb.isRobinsonMode()) ? sb.clusterhashes : null,
                false,
                sb.loader,
                (int) sb.getConfigLong(
                        SwitchboardConstants.REMOTESEARCH_MAXCOUNT_USER,
                        sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXCOUNT_DEFAULT, 10)),
                sb.getConfigLong(
                        SwitchboardConstants.REMOTESEARCH_MAXTIME_USER,
                        sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXTIME_DEFAULT, 3000)));
        final long timeout = sb.getConfigLong(
                SwitchboardConstants.REMOTESEARCH_MAXTIME_USER,
                sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXTIME_DEFAULT, 3000));
        // Wait until remote feeds are done (or timeout), then stabilize ordering.
        waitForFeedingAndResort(theSearch, timeout);
        for (int i = 0; i < count; i++) {
            URIMetadataNode node = theSearch.oneResult(i, timeout);
            if (node == null) break;
            try {
                final JSONObject result = new JSONObject(true);
                result.put("url", node.urlstring());
                result.put("title", node.title());
                if (includeSnippet) {
                    // Prefer direct snippet; fall back to textSnippet, description, then text field.
                    String text = node.snippet();
                    if (text == null || text.isEmpty()) {
                        TextSnippet snippet = node.textSnippet();
                        if (snippet != null && snippet.exists() && !snippet.getErrorCode().fail()) text = snippet.getLineRaw();
                    }
                    if (text == null || text.isEmpty()) text = firstFieldString(node.getFieldValue(CollectionSchema.description_txt.getSolrFieldName()));
                    if (text == null || text.isEmpty()) text = firstFieldString(node.getFieldValue(CollectionSchema.text_t.getSolrFieldName()));
                    result.put("text", limitSnippet(text == null ? "" : text.trim(), 2000));
                }
                results.put(result);
            } catch (JSONException e) {
            }
        }
        return results;
    }

    /**
     * Uses an LLM list schema prompt to compute likely discriminative search
     * terms for a user prompt.
     *
     * @param llm configured LLM backend
     * @param model target model name
     * @param prompt user prompt to analyze
     * @return space-separated lowercase term list or {@code null} on failure
     */
    public static String searchWordsForPrompt(LLM llm, String model, String prompt) {
        final String question = prompt == null ? "" : prompt;
        if (llm == null || model == null || model.isEmpty()) return null;
        try {
            LLM.Context context = new LLM.Context("\n\nYou may receive additional expert knowledge in the user prompt after a 'Additional Information' headline to enhance your knowledge. Use it only if applicable.");
            context.addPrompt(question);
            Set<String> singlewords = new LinkedHashSet<>();
            String[] a = LLM.stringsFromChat(llm.chat(model, context, LLM.listSchema, 200));
            if (a == null || a.length == 0) return null;
            for (String s: a) {
                if (s == null) continue;
                // Flatten model output into unique lowercased tokens.
                for (String t: s.split(" ")) if (!t.isEmpty()) singlewords.add(t.toLowerCase());
            }
            if (singlewords.isEmpty()) return null;
            StringBuilder query = new StringBuilder();
            for (String s: singlewords) query.append(s).append(' ');
            String querys = query.toString().trim();
            if (querys.length() == 0) return null;
            return querys;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Computes token overlap between original prompt and computed query terms.
     *
     * @param originalPrompt raw user prompt
     * @param computedQuery generated query terms
     * @param maxTerms max terms to keep; {@code <=0} means unlimited
     * @return cleaned ordered overlap set
     */
    public static Set<String> intersectTokens(String originalPrompt, String computedQuery, int maxTerms) {
        Set<String> promptTerms = querySet(originalPrompt == null ? "" : originalPrompt);
        Set<String> queryTerms = querySet(computedQuery == null ? "" : computedQuery);
        Set<String> intersection = new LinkedHashSet<>();
        for (String term : promptTerms) {
            if (!queryTerms.contains(term)) continue;
            final String cleaned = cleanToken(term);
            if (cleaned.isEmpty()) continue;
            intersection.add(cleaned);
            if (maxTerms > 0 && intersection.size() >= maxTerms) break;
        }
        return intersection;
    }

    /**
     * Splits text into sentence-aware chunks around a target max length.
     *
     * @param text source text
     * @param len approximate chunk length
     * @return ordered chunks
     */
    public static List<String> slicer(String text, int len) {
        List<String> result = new ArrayList<>();
        if (text == null || len <= 0) return result;

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + len, text.length());
            // Extend to sentence boundary when possible.
            while (end < text.length()) {
                char ch = text.charAt(end - 1);
                if ((ch == '.' || ch == '?' || ch == '!') && Character.isWhitespace(text.charAt(end))) break;
                end++;
            }
            result.add(text.substring(start, end));
            start = end;
        }
        return result;
    }

    /**
     * Converts a query string into a normalized token set.
     *
     * @param query raw query text
     * @return lowercase token set
     */
    private static Set<String> querySet(String query) {
        return Arrays.stream(query.trim().toLowerCase().split("\\s+"))
                .map(String::toLowerCase)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Removes non-alphanumeric characters and enforces minimal token length.
     *
     * @param term source token
     * @return cleaned lowercase token or empty string
     */
    private static String cleanToken(String term) {
        if (term == null) return "";
        String cleaned = term.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.length() < 2) return "";
        return cleaned.toLowerCase();
    }

    /**
     * Truncates snippet text to a maximum character count.
     *
     * @param text input text
     * @param maxChars limit
     * @return truncated or original text
     */
    private static String limitSnippet(String text, int maxChars) {
        if (text == null) return "";
        if (maxChars <= 0 || text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }

    /**
     * Returns first non-null string from a field value that may be scalar or
     * collection.
     *
     * @param value field value
     * @return first string representation or empty string
     */
    private static String firstFieldString(Object value) {
        if (value == null) return "";
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) if (item != null) return item.toString();
            return "";
        }
        return value.toString();
    }

    /**
     * Waits for global search feeding completion and then resorts cached results.
     *
     * @param search active search event
     * @param timeoutMs max wait time
     */
    private static void waitForFeedingAndResort(SearchEvent search, long timeoutMs) {
        if (search == null || timeoutMs <= 0) return;
        final long end = System.currentTimeMillis() + timeoutMs;
        while (!search.isFeedingFinished() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        search.resortCachedResults();
    }

    /**
     * Reads a single string value from a (possibly multivalued) Solr field.
     *
     * @param doc source Solr document
     * @param field schema field descriptor
     * @return first string value or empty string
     */
    private static String getOneString(SolrDocument doc, CollectionSchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general;
        Object r = doc.getFieldValue(field.getSolrFieldName());
        if (r == null) return "";
        if (r instanceof ArrayList) {
            return (String) ((ArrayList<?>) r).get(0);
        }
        return r.toString();
    }

    /**
     * Represents one candidate snippet around a search result and stores its
     * relevance score relative to the query.
     */
    private static class Snippet {
        private String text, url, title;
        private double score;

        /**
         * Scores text chunks and keeps the best chunk plus direct neighbors to
         * preserve context continuity.
         *
         * @param query query text
         * @param text source document/snippet text
         * @param url source URL
         * @param title source title
         * @param maxChunkLength target chunk size
         */
        public Snippet(String query, String text, String url, String title, int maxChunkLength) {
            this.url = url;
            this.title = title;
            this.score = 0.0;

            if (text == null || text.isEmpty() || maxChunkLength <= 0 || query == null) {
                this.text = "";
                return;
            }

            List<String> chunks = slicer(text, maxChunkLength);
            if (chunks.isEmpty()) {
                this.text = "";
                return;
            }
            List<String> chunksLowerCase = new ArrayList<>(chunks.size());
            // Cache lowercase chunks so token comparisons are case-insensitive.
            for (String chunk: chunks) chunksLowerCase.add(chunk.toLowerCase());

            Set<String> queryWordSet = querySet(query);
            if (queryWordSet.isEmpty()) {
                this.text = "";
                return;
            }

            int totalChunks = chunksLowerCase.size();
            Map<String, Double> idf = new HashMap<>();
            for (String word: queryWordSet) {
                int docFreq = 0;
                for (String chunk: chunksLowerCase) {
                    if (chunk.contains(word)) docFreq++;
                }
                // Smoothed IDF to avoid divide-by-zero and extreme values.
                idf.put(word, Math.log((double) totalChunks / (docFreq + 1)) + 1);
            }

            Map<Integer, Double> chunkScores = new HashMap<>();
            for (int i = 0; i < chunksLowerCase.size(); i++) {
                String chunk = chunksLowerCase.get(i);
                double score = 0.0;
                Map<String, Integer> tf = new HashMap<>();

                String[] wordsInChunk = chunk.split("\\s+");
                for (String w : wordsInChunk) {
                    String cleanWord = w.replaceAll("[.,!?;:]", "");
                    if (cleanWord.length() > 0 && queryWordSet.contains(cleanWord)) {
                        tf.put(cleanWord, tf.getOrDefault(cleanWord, 0) + 1);
                    }
                }

                for (String word: queryWordSet) {
                    int tfValue = tf.getOrDefault(word, 0);
                    double tfIdf = (double) tfValue * idf.getOrDefault(word, 1.0);
                    score += tfIdf;
                }
                chunkScores.put(i, score);
            }

            int topChunkIndex = -1;
            for (Map.Entry<Integer, Double> entry: chunkScores.entrySet()) {
                // Keep best-scoring chunk index.
                if (entry.getValue() > this.score) {
                    this.score = entry.getValue();
                    topChunkIndex = entry.getKey();
                }
            }

            if (topChunkIndex < 0) {
                this.text = "";
                this.score = 0.0;
                return;
            }

            List<String> snippetChunks = new ArrayList<>();
            // Include neighboring chunks to reduce abrupt starts/ends.
            if (topChunkIndex > 0) snippetChunks.add(chunks.get(topChunkIndex - 1));
            snippetChunks.add(chunks.get(topChunkIndex));
            if (topChunkIndex < chunks.size() - 1) snippetChunks.add(chunks.get(topChunkIndex + 1));
            this.text = String.join(" ", snippetChunks);
        }

        public double getScore() { return this.score; }
        public String getText() { return this.text; }
        public String getURL() { return this.url; }
        public String getTitle() { return this.title; }
    }
}
