package de.anomic.crawler.retrieval;


public enum EventOrigin {

    // we must distinguish the following cases: resource-load was initiated by
    // 1) global crawling: the index is extern, not here (not possible here)
    // 2) result of search queries, some indexes are here (not possible here)
    // 3) result of index transfer, some of them are here (not possible here)
    // 4) proxy-load (initiator is "------------")
    // 5) local prefetch/crawling (initiator is own seedHash)
    // 6) local fetching for global crawling (other known or unknown initiator)
    
    UNKNOWN(0),
    REMOTE_RECEIPTS(1),
    QUERIES(2),
    DHT_TRANSFER(3),
    PROXY_LOAD(4),
    LOCAL_CRAWLING(5),
    GLOBAL_CRAWLING(6),
    SURROGATES(7);
    
    protected int code;
    private static final EventOrigin[] list = {
        UNKNOWN, REMOTE_RECEIPTS, QUERIES, DHT_TRANSFER, PROXY_LOAD, LOCAL_CRAWLING, GLOBAL_CRAWLING, SURROGATES};
    private EventOrigin(int code) {
        this.code = code;
    }
    public int getCode() {
        return this.code;
    }
    public static final EventOrigin getEvent(int key) {
        return list[key];
    }
}
