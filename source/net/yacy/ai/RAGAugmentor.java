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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LogRedaction;
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

    public static final String SEARCH_DOCUMENT_MAX_LENGTH_CONFIG = "ai.rag.search-document-maxlength";
    public static final int SEARCH_DOCUMENT_MAX_LENGTH_DEFAULT = 30000;

    /**
     * Utility class; not instantiable.
     */
    private RAGAugmentor() {}

    public static int configuredSearchDocumentMaxLength() {
        final Switchboard sb = Switchboard.getSwitchboard();
        if (sb == null) return SEARCH_DOCUMENT_MAX_LENGTH_DEFAULT;
        final long configured = sb.getConfigLong(SEARCH_DOCUMENT_MAX_LENGTH_CONFIG, SEARCH_DOCUMENT_MAX_LENGTH_DEFAULT);
        if (configured <= 0) return SEARCH_DOCUMENT_MAX_LENGTH_DEFAULT;
        return configured > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) configured;
    }

    private static String truncateSearchDocument(final String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        final int maxLength = configuredSearchDocumentMaxLength();
        return markdown.length() <= maxLength ? markdown : markdown.substring(0, maxLength);
    }

    /**
     * Executes local index search.
     *
     * @param query query string
     * @param count max number of results
     * @param includeSnippet include text snippet field in response objects
     * @return JSON array with {@code url,title[,text]} entries
     */
    public static JSONArray searchResults(String query, int count, final boolean includeSnippet) {
        return searchResults(query, count, includeSnippet, null);
    }

    public static JSONArray searchResults(String query, int count, final boolean includeSnippet, final String runId) {
        final QueryParams theQuery = buildTextQueryParams(query, count, QueryParams.Searchdom.LOCAL);
        return searchResults(theQuery, count, includeSnippet, runId);
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
        return searchResultsAsMarkdown(query, count, global, null);
    }

    public static String searchResultsAsMarkdown(String query, int count, boolean global, final String runId) {
        final long searchStart = System.currentTimeMillis();
        JSONArray searchResults = global ? searchResultsGlobal(query, count, true, runId) : searchResults(query, count, true, runId);
        ConcurrentLog.info("RAGProxy", prefix(runId) + "event=rag-search phase=results resultCount=" + searchResults.length() + " global=" + global + " searchMs=" + (System.currentTimeMillis() - searchStart));
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < searchResults.length(); i++) {
            try {
                JSONObject r = searchResults.getJSONObject(i);
                String title = r.optString("title", "");
                String url = r.optString("url", "");
                String text = r.optString("text", "");
                if (title.isEmpty()) title = url;
                if (text.isEmpty()) text = title;
                if (title.length() > 0 && text.length() > 0) {
                    sb.append("## ").append(title).append("\n");
                    sb.append(text).append("\n");
                    if (url.length() > 0) sb.append("Source: ").append(url).append("\n");
                    sb.append("\n\n");
                }
            } catch (JSONException e) {}
        }

        final String markdown = truncateSearchDocument(sb.toString());
        ConcurrentLog.info("RAGProxy", prefix(runId) + "event=rag-search phase=markdown markdownChars=" + markdown.length() + " resultCount=" + searchResults.length());
        return markdown;
    }

    /**
     * Build the same markdown search document envelope used by chat RAG
     * attachment injection and tool-based search retrieval.
     *
     * @param query query string
     * @param count max number of search rows
     * @param global when true, use global YaCy search
     * @return JSON object containing filename, base64 markdown and plain content
     * @throws JSONException when JSON assembly fails
     */
    public static JSONObject searchResultsDocument(final String query, final int count, final boolean global) throws JSONException {
        final String normalizedQuery = query == null ? "" : query.trim();
        final String effectiveQuery = normalizedQuery.isEmpty() ? "search" : normalizedQuery;
        final String markdown = searchResultsAsMarkdown(effectiveQuery, count, global);
        final JSONObject doc = new JSONObject(true);
        doc.put("search-filename", "search_result_" + effectiveQuery.replace(' ', '_') + ".md");
        doc.put("search-text-base64", Base64.getEncoder().encodeToString(markdown.getBytes(StandardCharsets.UTF_8)));
        doc.put("content", markdown);
        return doc;
    }

    /**
     * Determine whether chat search should default to global retrieval on the
     * current peer.
     *
     * @return true when peer-to-peer/global search is available
     */
    public static boolean defaultSearchIsGlobal() {
        final Switchboard sb = Switchboard.getSwitchboard();
        if (sb == null) return false;
        final boolean clustersearch = sb.isRobinsonMode()
                && SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER.equals(sb.getConfig(SwitchboardConstants.CLUSTER_MODE, ""));
        return sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true) || clustersearch;
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
        return searchResultsGlobal(query, count, includeSnippet, null);
    }

    public static JSONArray searchResultsGlobal(String query, int count, final boolean includeSnippet, final String runId) {
        final QueryParams theQuery = buildTextQueryParams(query, count, QueryParams.Searchdom.GLOBAL);
        return searchResults(theQuery, count, includeSnippet, runId);
    }

    /**
     * Execute a shared YaCy search event and extract results in a compact JSON
     * representation. This uses the same execution pipeline as normal web search
     * for both local and global RAG retrieval.
     *
     * @param theQuery fully-built YaCy query params
     * @param count max number of results
     * @param includeSnippet include snippet text when available
     * @return JSON array with normalized result objects
     */
    private static JSONArray searchResults(final QueryParams theQuery, final int count, final boolean includeSnippet) {
        return searchResults(theQuery, count, includeSnippet, null);
    }

    private static JSONArray searchResults(final QueryParams theQuery, final int count, final boolean includeSnippet, final String runId) {
        final long start = System.currentTimeMillis();
        final JSONArray results = new JSONArray();
        if (theQuery == null || count == 0) {
            ConcurrentLog.info("RAGProxy", prefix(runId) + "event=rag-search phase=skip reason=empty-query count=" + count);
            return results;
        }
        final Switchboard sb = Switchboard.getSwitchboard();
        if (sb == null) {
            ConcurrentLog.warn("RAGProxy", prefix(runId) + "event=rag-search phase=fail reason=switchboard-unavailable");
            return results;
        }
        final boolean globalSearch = !theQuery.isLocal();
        ConcurrentLog.info("RAGProxy", prefix(runId) + "event=rag-search phase=start global=" + globalSearch + " count=" + count + " includeSnippet=" + includeSnippet);
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
        if (globalSearch) {
            theSearch.resortCachedResults();
        } else {
            // Local search can wait briefly for feeder completion to stabilize ordering.
            waitForFeedingAndResort(theSearch, timeout);
        }
        final long deadline = System.currentTimeMillis() + timeout;
        int resultIndex = 0;
        while (resultIndex < count) {
            final long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;
            final long attemptTimeout = globalSearch ? Math.min(remaining, 500L) : remaining;
            final URIMetadataNode node = theSearch.oneResult(resultIndex, attemptTimeout);
            if (node == null) {
                if (!globalSearch || theSearch.isFeedingFinished()) break;
                theSearch.resortCachedResults();
                continue;
            }
            if (globalSearch) theSearch.resortCachedResults();
            try {
                final JSONObject result = new JSONObject(true);
                result.put("url", node.urlstring());
                String title = node.title();
                String text = null;
                if (includeSnippet) {
                    TextSnippet snippet = node.textSnippet();
                    if (snippet != null && snippet.exists() && !snippet.getErrorCode().fail()) text = snippet.getLineRaw();
                    if (text == null || text.isEmpty()) text = node.snippet();
                    if (text == null || text.isEmpty()) text = firstFieldString(node.getFieldValue(CollectionSchema.description_txt.getSolrFieldName()));
                    if (text == null || text.isEmpty()) text = firstFieldString(node.getFieldValue(CollectionSchema.text_t.getSolrFieldName()));
                    result.put("text", text == null ? "" : text.trim());
                }
                result.put("title", title);
                results.put(result);
                resultIndex++;
            } catch (JSONException e) {
                ConcurrentLog.warn("RAGProxy", prefix(runId) + "event=rag-search phase=result result=failure errorClass=" + e.getClass().getName() + " reason=" + LogRedaction.redactMessage(e));
            }
        }
        ConcurrentLog.info("RAGProxy", prefix(runId) + "event=rag-search phase=end result=success global=" + globalSearch + " requested=" + count + " returned=" + results.length() + " includeSnippet=" + includeSnippet + " durationMs=" + (System.currentTimeMillis() - start));
        return results;
    }

    /**
     * Build a standard YaCy text query using the shared query parser and ranking
     * configuration. This gives local and global RAG retrieval the same Solr query
     * semantics as the normal search stack.
     *
     * @param query raw query string
     * @param count maximum number of results to retrieve
     * @param searchdom local or global search scope
     * @return shared query parameters ready for Solr/event execution
     */
    private static QueryParams buildTextQueryParams(final String query, final int count, final QueryParams.Searchdom searchdom) {
        final Switchboard sb = Switchboard.getSwitchboard();
        final RankingProfile ranking = sb.getRanking();
        final int timezoneOffset = 0;
        final QueryModifier modifier = new QueryModifier(timezoneOffset);
        String querystring = modifier.parse(query);
        if (querystring.length() == 0) querystring = query == null ? "" : query.trim();
        final QueryGoal qg = new QueryGoal(querystring);
        final QueryParams theQuery = new QueryParams(
                qg,
                modifier,
                0,
                "",
                Classification.ContentDomain.ALL,
                "",
                0,
                new HashSet<Tagging.Metatag>(),
                CacheStrategy.CACHEONLY,
                count,
                0,
                ".*",
                null,
                null,
                searchdom,
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
        theQuery.setStrictContentDom(!Boolean.FALSE.toString().equalsIgnoreCase(
                sb.getConfig(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM,
                        String.valueOf(SwitchboardConstants.SEARCH_STRICT_CONTENT_DOM_DEFAULT))));
        theQuery.setMaxSuggestions(0);
        theQuery.setStandardFacetsMaxCount(sb.getConfigInt(
                SwitchboardConstants.SEARCH_NAVIGATION_MAXCOUNT,
                QueryParams.FACETS_STANDARD_MAXCOUNT_DEFAULT));
        theQuery.setDateFacetMaxCount(sb.getConfigInt(
                SwitchboardConstants.SEARCH_NAVIGATION_DATES_MAXCOUNT,
                QueryParams.FACETS_DATE_MAXCOUNT_DEFAULT));
        theQuery.setSnippetFetchFullText(true);
        theQuery.getQueryGoal().filterOut(Switchboard.blueList);
        return theQuery;
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
    public static String searchWordsForPrompt(final LLM llm, final String model, final String prompt, final String systemPrompt) {
        return searchWordsForPrompt(llm, model, prompt, systemPrompt, null);
    }

    public static String searchWordsForPrompt(final LLM llm, final String model, final String prompt, final String systemPrompt, final String runId) {
        final long start = System.currentTimeMillis();
        final String question = prompt == null ? "" : prompt.trim();
        final String instruction = systemPrompt == null || systemPrompt.trim().isEmpty()
                ? "Compress the user prompt into a short search description. Return only a JSON array of concise, discriminative search terms in lowercase."
                : systemPrompt.trim();
        if (llm == null || model == null || model.isEmpty()) {
            ConcurrentLog.warn("RAGProxy", prefix(runId) + "event=rag-query-generation phase=skip reason=no-model promptChars=" + question.length());
            return null;
        }
        try {
            ConcurrentLog.info("RAGProxy", prefix(runId) + "event=rag-query-generation phase=model-call model=" + LogRedaction.redact(model) + " backend=" + LogRedaction.redact(llm.hoststub) + " promptChars=" + question.length() + " promptWords=" + wordCount(question));
            LLM.Context context = new LLM.Context(instruction);
            context.addPrompt(question);
            Set<String> singlewords = new LinkedHashSet<>();
            String[] a = LLM.stringsFromChat(llm.chat(model, context, LLM.listSchema, 200));
            if (a == null || a.length == 0) {
                ConcurrentLog.warn("RAGProxy", prefix(runId) + "event=rag-query-generation phase=model-return result=empty durationMs=" + (System.currentTimeMillis() - start));
                return null;
            }
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
            ConcurrentLog.info("RAGProxy", prefix(runId) + "event=rag-query-generation phase=end result=success terms=" + singlewords.size() + " queryChars=" + querys.length() + " durationMs=" + (System.currentTimeMillis() - start));
            return querys;
        } catch (IOException | JSONException e) {
            ConcurrentLog.warn("RAGProxy", prefix(runId) + "event=rag-query-generation phase=end result=failure errorClass=" + e.getClass().getName() + " reason=" + LogRedaction.redactMessage(e) + " durationMs=" + (System.currentTimeMillis() - start));
            return null;
        }
    }

    private static String prefix(final String runId) {
        return runId == null || runId.isEmpty() ? "" : "runId=" + runId + " ";
    }

    private static int wordCount(final String text) {
        if (text == null) return 0;
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
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

}
