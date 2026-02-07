/**
 *  WebFetchTool
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

package net.yacy.ai.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.TagValency;

public class WebFetchTool implements ToolHandler {

    private static final String NAME = "webfetch";
    private static final int TOOL_WEBFETCH_MAX_BYTES = 2_000_000;
    private static final int TOOL_WEBFETCH_MAX_CHARS = 12_000;

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Fetch a URL and return text content. Parse HTML/documents into markdown-style text when possible.");
        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);
        JSONObject url = new JSONObject(true);
        url.put("type", "string");
        url.put("description", "Absolute http or https URL to fetch.");
        props.put("url", url);
        params.put("properties", props);
        params.put("required", new JSONArray().put("url"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    
    public int maxCallsPerTurn() {
        return 3;
    }

    
    public String execute(String arguments) {
        String urlRaw;
        try {
            JSONObject obj = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
            urlRaw = obj.optString("url", "").trim();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON. don't try again");
        }
        if (urlRaw == null || urlRaw.isEmpty()) return ToolHandler.errorJson("Missing url. don't try again");

        final DigestURL url;
        try {
            url = new DigestURL(urlRaw);
        } catch (Exception e) {
            return ToolHandler.errorJson("Invalid URL. don't try again");
        }
        final String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            return ToolHandler.errorJson("Only http/https URLs are allowed. don't try again");
        }

        try (HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 30000)) {
            byte[] content = client.GETbytes(url, null, null, TOOL_WEBFETCH_MAX_BYTES, false);
            int status = client.getStatusCode();
            if (status < 200 || status >= 300) return ToolHandler.errorJson("HTTP status " + status + ". don't try again");
            if (content == null || content.length == 0) return ToolHandler.errorJson("Empty response body. don't try again");

            String contentType = "application/octet-stream";
            Header ct = client.getHttpResponse() == null ? null : client.getHttpResponse().getFirstHeader(HeaderFramework.CONTENT_TYPE);
            if (ct != null && ct.getValue() != null && !ct.getValue().isEmpty()) contentType = ct.getValue();
            int semicolon = contentType.indexOf(';');
            if (semicolon > 0) contentType = contentType.substring(0, semicolon).trim().toLowerCase();

            final String ext = MultiProtocolURL.getFileExtension(url.getFileName());
            final ContentDomain mimeDomain = Classification.getContentDomainFromMime(contentType);
            if (mimeDomain == ContentDomain.IMAGE || mimeDomain == ContentDomain.AUDIO || mimeDomain == ContentDomain.VIDEO
                    || Classification.isMediaExtension(ext)) {
                return ToolHandler.errorJson("Media content is not supported. don't try again");
            }

            String text;
            if (contentType.startsWith("text/plain") || contentType.startsWith("text/markdown") || "txt".equals(ext) || "md".equals(ext)) {
                text = truncate(new String(content, StandardCharsets.UTF_8), TOOL_WEBFETCH_MAX_CHARS);
            } else {
                String supportError = TextParser.supports(url, contentType);
                if (supportError == null) {
                    Document[] docs = TextParser.parseSource(url, contentType, "UTF-8", TagValency.EVAL,
                            new HashSet<String>(), new VocabularyScraper(), 0, 0, content, new Date());
                    Document merged = Document.mergeDocuments(url, contentType, docs);
                    text = truncate(documentToMarkdown(merged), TOOL_WEBFETCH_MAX_CHARS);
                } else if (contentType.startsWith("text/")) {
                    text = truncate(new String(content, StandardCharsets.UTF_8), TOOL_WEBFETCH_MAX_CHARS);
                } else {
                    return ToolHandler.errorJson("Unsupported content type " + contentType + ". don't try again");
                }
            }

            JSONObject result = new JSONObject(true);
            result.put("url", url.toNormalform(true));
            result.put("content_type", contentType);
            result.put("content", text == null ? "" : text);
            return result.toString();
        } catch (IOException e) {
            return ToolHandler.errorJson("Fetch error: " + e.getMessage() + ". don't try again");
        } catch (Parser.Failure e) {
            return ToolHandler.errorJson("Parse error: " + e.getMessage() + ". don't try again");
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build tool response. don't try again");
        }
    }

    private static String documentToMarkdown(Document doc) {
        if (doc == null) return "";
        StringBuilder sb = new StringBuilder();
        String title = doc.dc_title();
        if (title != null && !title.isEmpty()) {
            sb.append("# ").append(title).append("\n\n");
        }
        String text = doc.getTextString();
        if (text != null && !text.isEmpty()) sb.append(text);
        return sb.toString();
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (maxChars <= 0 || text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }
}
