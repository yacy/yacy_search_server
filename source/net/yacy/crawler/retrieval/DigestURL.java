package net.yacy.crawler.retrieval;

public class DigestURL {
    private String url;

    public DigestURL(String url) {
        this.url = url;
    }

    public String getHost() {
        return url.split("/")[2]; // Simplistic host extraction
    }

    public String getUrl() {
        return url;
    }
}
