package net.yacy.cora.services.federated.yacy;

public enum CacheStrategy {

    NOCACHE(0),    // never use the cache, all content from fresh internet source
    IFFRESH(1),    // use the cache if the cache exists and is fresh using the proxy-fresh rules
    IFEXIST(2),    // use the cache if the cache exist. Do no check freshness. Otherwise use online source.
    CACHEONLY(3);  // never go online, use all content from cache. If no cache entry exist, consider content nevertheless as available
    // the fifth case may be that the CacheStrategy object is assigned NULL. That means that no snippet creation is wanted.

    public int code;

    private CacheStrategy(final int code) {
        this.code = code;
    }

    public String toString() {
        return Integer.toString(this.code);
    }

    public static CacheStrategy decode(final int code) {
        for (final CacheStrategy strategy: CacheStrategy.values()) if (strategy.code == code) return strategy;
        return NOCACHE;
    }

    public static CacheStrategy parse(final String name) {
        if (name == null) return null;
        if (name.equals("nocache")) return NOCACHE;
        if (name.equals("iffresh")) return IFFRESH;
        if (name.equals("ifexist")) return IFEXIST;
        if (name.equals("cacheonly")) return CACHEONLY;
        if (name.equals("true")) return IFEXIST;
        if (name.equals("false")) return null; // if this cache strategy is assigned as query attribute, null means "do not create a snippet"
        return null;
    }

    public String toName() {
        return name().toLowerCase();
    }

    public boolean isAllowedToFetchOnline() {
        return this.code < 3;
    }

    public boolean mustBeOffline() {
        return this.code == 3;
    }
}
