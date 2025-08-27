/**
 *  HFClient
 *  Copyright 2025 by Michael Peter Christen
 *  First released 19.08.2025 at https://yacy.net
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

package net.yacy.kelondro.util;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;


public class HFClient {

    private static final String BASE = "https://huggingface.co";

    public enum RepoType {
        MODEL("models"),
        DATASET("datasets"),
        SPACE("spaces");

        private final String pluralSegment;
        RepoType(String s) { this.pluralSegment = s; }
        public String segment() { return this.pluralSegment; }
    }


    private static String textOrNull(JsonNode n) {
        return (n != null && !n.isNull()) ? n.asText() : null;
    }


    public static List<String> listYaCyPacks() throws IOException, InterruptedException {
        List<String> repos = listRepositories(null, RepoType.DATASET, 20, "YaCy-Pack");
        return repos;
    }

    private static List<String> listRepositories(String author, RepoType type, int limit, String search) throws IOException, InterruptedException {
        if (limit <= 0) limit = 100;
        StringBuilder url = new StringBuilder(BASE).append("/api/").append(type.segment()).append("?limit=").append(limit);
        if (author != null && !author.isBlank()) url.append("&author=").append(URLEncoder.encode(author, java.nio.charset.StandardCharsets.UTF_8));
        if (search != null && !search.isBlank()) url.append("&search=").append(URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8));

        HTTPClient hc = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        byte[] body = hc.GETbytes(url.toString(), null, null, false);
        hc.close();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode arr = mapper.readTree(body);
        List<String> ids = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                // The field is "modelId" for models, "id" for datasets/spaces; normalize both.
                String id = textOrNull(n.get("modelId"));
                if (id == null) id = textOrNull(n.get("id"));
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }

    public static ArrayList<String> listFiles(String repoId, boolean only_jsonl) throws IOException, InterruptedException {
        ArrayList<String>  files = listFiles(repoId, RepoType.DATASET);
        if (!only_jsonl) return files;
        for (int i = files.size() - 1; i >= 0; i--) {
            String file = files.get(i);
            if (!file.contains(".jsonl")) {
                files.remove(i); // remove non-jsonl files
            }
        }
        return files;
    }

    public static ArrayList<String> listFiles(String repoId, RepoType type) throws IOException, InterruptedException {
        String url = BASE + "/api/" + type.segment() + "/" + repoId;
        HTTPClient hc = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        byte[] body = hc.GETbytes(url, null, null, false);
        hc.close();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(body);
        JsonNode filesArray = root.get("siblings");
        if (filesArray == null) filesArray = root.get("files");

        ArrayList<String> out = new ArrayList<>();
        if (filesArray != null && filesArray.isArray()) {
            for (JsonNode f : filesArray) {
                // models/datasets commonly expose "rfilename"
                String path = textOrNull(f.get("rfilename"));
                if (path == null) {
                    // fallback to "path" if present
                    path = textOrNull(f.get("path"));
                }
                if (path != null) out.add(path);
            }
        }

        return out;
    }

    public static Map<String, List<String>> getAllPacks(boolean only_jsonl) throws IOException, InterruptedException {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> repos = listYaCyPacks();
        for (String repoId : repos) {
            List<String> files = listFiles(repoId, only_jsonl);
            if (!files.isEmpty()) {
                map.put(repoId, files);
            }
        }
        return map;
    }

    public static byte[] downloadFile(String repoId, String filePath) throws IOException, InterruptedException {
        return downloadFile(repoId, RepoType.DATASET, "main", filePath);
    }

    private static byte[] downloadFile(String repoId, RepoType type, String revision, String filePath) throws IOException, InterruptedException {

        Objects.requireNonNull(repoId);
        Objects.requireNonNull(filePath);
        if (revision == null || revision.isBlank()) revision = "main";

        // Raw file URL pattern:
        // https://huggingface.co/{models|datasets|spaces}/{repoId}/resolve/{revision}/{filePath}
        String url = BASE + "/" + RepoType.DATASET.segment() + "/" + repoId + "/resolve/" + revision + "/" + filePath + "?download=true";
        HTTPClient hc = new HTTPClient(ClientIdentification.browserAgent);
        hc.setRedirecting(true);
        byte[] body = hc.GETbytes(url, null, null, false);
        hc.close();
        return body;
    }

    // test
    public static void main(String[] args) throws Exception {
        List<String> repos = listYaCyPacks();
        System.out.println("Repos: " + repos);
        String test_repo_id = repos.isEmpty() ? null : repos.get(0);
        ArrayList<String> all_files = test_repo_id == null ? new ArrayList<>(0) : listFiles(test_repo_id, false);
        ArrayList<String> jsonl_files = test_repo_id == null ? new ArrayList<>(0) : listFiles(test_repo_id, true);
        System.out.println("all   files in " + test_repo_id + ": " + all_files);
        System.out.println("jsonl files in " + test_repo_id + ": " + jsonl_files);
        if (!jsonl_files.isEmpty()) {
            byte[] data = downloadFile(test_repo_id, RepoType.DATASET, "main", jsonl_files.get(0));
            System.out.println("Downloaded " + data.length + " bytes from " + jsonl_files.get(0) + " in " + test_repo_id);
        }

        Map<String, List<String>> map = getAllPacks(true);
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String repo = entry.getKey();
            List<String> files = entry.getValue();
            System.out.println("Repo: " + repo + ", Files: " + files);
        }

    }
}
