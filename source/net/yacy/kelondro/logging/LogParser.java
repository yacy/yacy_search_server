//LogParser.java
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Matthias Soehnholz
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.logging;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class LogParser {

    /** the version of the LogParser - <strong>Double</strong>*/
    private static final String PARSER_VERSION           = "version";

    /** the amount of URLs received during DHT - <strong>Integer</strong> */
    private static final String URLS_RECEIVED            = "urlSum";

    /** the amount of URLs requested during DHT - <strong>Integer</strong> */
    private static final String URLS_REQUESTED           = "urlReqSum";

    /** the amount of URLs blocked during DHT because they match the peer's blacklist - <strong>Integer</strong> */
    private static final String URLS_BLOCKED             = "blockedURLSum";

    /** the amount of words received during DHT - <strong>Integer</strong> */
    private static final String WORDS_RECEIVED           = "wordsSum";

    /** the amount of RWIs received during DHT - <strong>Integer</strong> */
    private static final String RWIS_RECEIVED            = "rwiSum";

    /** the amount of RWIs blocked during DHT because their entries match the peer's blacklist - <strong>Integer</strong> */
    private static final String RWIS_BLOCKED             = "blockedRWISum";

    /** total time receiving RWIs during DHT in milli seconds - <strong>Long</strong> */
    private static final String RWIS_RECEIVED_TIME       = "rwiTimeSum";

    /** total time receiving URLs during DHT in milli seconds - <strong>Long</strong> */
    private static final String URLS_RECEIVED_TIME       = "urlTimeSum";

    /** the traffic sent during DHT in bytes - <strong>Long</strong> */
    private static final String DHT_TRAFFIC_SENT         = "DHTSendTraffic";

    /** the amount of URLs requested by other peers and sent by this one - <strong>Integer</strong> */
    private static final String DHT_URLS_SENT            = "DHTSendURLs";

    /** the amount of rejected DHT transfers from other peers (i.e. because this peer was busy) - <strong>Integer</strong> */
    private static final String DHT_REJECTED             = "RWIRejectCount";

    /** the peer-names from whose DHT transfers were rejected - <strong>HasSet</strong> */
    private static final String DHT_REJECTED_PEERS_NAME  = "DHTRejectPeerNames";

    /** the peer-hashes from whose DHT transfers were rejected - <strong>HasSet</strong> */
    private static final String DHT_REJECTED_PEERS_HASH  = "DHTRejectPeerHashs";

    /** the peer-names this peer sent DHT chunks to - <strong>HasSet</strong> */
    private static final String DHT_SENT_PEERS_NAME      = "DHTPeerNames";

    /** the peer-hashes this peer sent DHT chunks to - <strong>HasSet</strong> */
    private static final String DHT_SENT_PEERS_HASH      = "DHTPeerHashs";

    /** total amount of selected peers for index distribution - <strong>Integer</strong> */
    private static final String DHT_SELECTED             = "DHTSelectionTargetCount";

    /** total amount of words selected for index distribution - <strong>Integer</strong> */
    private static final String DHT_WORDS_SELECTED       = "DHTSelectionWordsCount";

    /** total time selecting words for index distribution - <strong>Integer</strong> */
    private static final String DHT_WORDS_SELECTED_TIME  = "DHTSelectionWordsTimeCount";

    /** the minimal DHT distance during peer-selection for index distribution - <strong>Long</strong> */
    private static final String DHT_DISTANCE_MIN         = "minDHTDist";

    /** the maximal DHT distance during peer-selection for index distribution - <strong>Long</strong> */
    private static final String DHT_DISTANCE_MAX         = "maxDHTDist";

    /** the average DHT distance during peer-selection for index distribution - <strong>Long</strong> */
    private static final String DHT_DISTANCE_AVERAGE     = "avgDHTDist";

    /** how many times remote peers were too busy to accept the index transfer - <strong>Integer</strong> */
    private static final String PEERS_BUSY               = "busyPeerCount";

    /** how many times not enough peers for index distribution were found - <strong>Integer</strong> */
    private static final String PEERS_TOO_LESS           = "notEnoughDHTPeers";

    /** how many times the index distribution failed (i.e. due to time-out or other reasons) - <strong>Integer</strong> */
    private static final String DHT_SENT_FAILED          = "failedIndexDistributionCount";

    /** how many times the error "<code>tried to create left child-node twice</code>" occured - <strong>Integer</strong> */
    private static final String ERROR_CHILD_TWICE_LEFT   = "leftChildTwiceCount";

    /** how many times the error "<code>tried to create right child-node twice</code>" occured - <strong>Integer</strong> */
    private static final String ERROR_CHILD_TWICE_RIGHT  = "rightChildTwiceCount";

    /** how many ranking distributions were executed successfully - <strong>Integer</strong> */
    private static final String RANKING_DIST             = "rankingDistributionCount";

    /** total time the ranking distributions took - <strong>Integer</strong> */
    private static final String RANKING_DIST_TIME        = "rankingDistributionTime";

    /** how many ranking distributions failed - <strong>Integer</strong> */
    private static final String RANKING_DIST_FAILED      = "rankingDistributionFailCount";

    /** how many times the error "<code>Malformed URL</code>" occured - <strong>Integer</strong> */
    private static final String ERROR_MALFORMED_URL      = "malformedURLCount";

    /** the amount of indexed sites - <strong>Integer</strong> */
    private static final String INDEXED_SITES            = "indexedSites";

    /** total amount of indexed words - <strong>Integer</strong> */
    private static final String INDEXED_WORDS            = "indexedWords";

    /** total size of all indexed sites - <strong>Integer</strong> */
    private static final String INDEXED_SITES_SIZE       = "indexedSiteSizeSum";

    /** total amount of indexed anchors - <strong>Integer</strong> */
    private static final String INDEXED_ANCHORS          = "indexedAnchors";

//    /** total time needed for stacking the site of an indexing - <strong>Integer</strong> */
//    public static final String INDEXED_STACK_TIME       = "indexedStackingTime";
//
//    /** total time needed for parsing during indexing - <strong>Integer</strong> */
//    public static final String INDEXED_PARSE_TIME       = "indexedParsingTime";
//
//    /** total time needed for the actual indexing during indexing - <strong>Integer</strong> */
//    public static final String INDEXED_INDEX_TIME       = "indexedIndexingTime";
//
//    /** total time needed for storing the results of an indexing - <strong>Integer</strong> */
//    public static final String INDEXED_STORE_TIME       = "indexedStorageTime";

    /** total time needed for storing the results of a link indexing - <strong>Integer</strong> */
    private static final String INDEXED_LINKSTORE_TIME       = "indexedLinkStorageTime";

    /** total time needed for storing the results of a word indexing - <strong>Integer</strong> */
    private static final String INDEXED_INDEXSTORE_TIME       = "indexedIndexStorageTime";

    /** accumulated time needed to parse the log entries up to now (in ms)*/
    private static final String TOTAL_PARSER_TIME        = "totalParserTime";

    /** times the parser was called, respectively amount of independent log-lines */
    private static final String TOTAL_PARSER_RUNS        = "totalParserRuns";


    private static final float parserVersion = 0.1f;
    private static final String parserType = "PLASMA";

    //RegExp for LogLevel I
    private static final Pattern i1 = Pattern.compile("Received (\\d*) URLs from peer [\\w-_]{12}:[\\w-_]*/[\\w.-]* in (\\d*) ms, blocked (\\d*) URLs");
    private static final Pattern i2 = Pattern.compile("Received (\\d*) Entries (\\d*) Words \\[[\\w-_]{12} .. [\\w-_]{12}\\]/[\\w.-]* from [\\w-_]{12}:[\\w-_]*/[\\w.-]*, processed in (\\d*) milliseconds, requesting (\\d*)/(\\d*) URLs, blocked (\\d*) RWIs");
    private static final Pattern i2_2 = Pattern.compile("Received (\\d*) Entries (\\d*) Words \\[[\\w-_]{12} .. [\\w-_]{12}\\]/[\\w.-]* from [\\w-_]{12}:[\\w-_]*, processed in (\\d*) milliseconds, requesting (\\d*)/(\\d*) URLs, blocked (\\d*) RWIs");
    private static final Pattern i3 = Pattern.compile("Index transfer of (\\d*) words \\[[\\w-_]{12} .. [\\w-_]{12}\\] to peer ([\\w-_]*):([\\w-_]{12}) in (\\d*) seconds successful \\((\\d*) words/s, (\\d*) Bytes\\)");
    private static final Pattern i4 = Pattern.compile("Index transfer of (\\d*) entries (\\d*) words \\[[\\w-_]{12} .. [\\w-_]{12}\\] and (\\d*) URLs to peer ([\\w-_]*):([\\w-_]{12}) in (\\d*) seconds successful \\((\\d*) words/s, (\\d*) Bytes\\)");
    private static final Pattern i5 = Pattern.compile("Selected DHT target peer ([\\w-_]*):([\\w-_]{12}), distance2first = ([\\d]*), distance2last = ([\\d]*)");
    private static final Pattern i6 = Pattern.compile("Rejecting RWIs from peer ([\\w-_]{12}):([\\w-_]*)/([\\w.]*). ([\\w. ]*)");
    private static final Pattern i7 = Pattern.compile("DHT distribution: transfer to peer [\\w-]* finished.");
    private static final Pattern i8 = Pattern.compile("Index selection of (\\d*) words \\[[\\w-_]{12} .. [\\w-_]{12}\\] in (\\d*) seconds");
    private static final Pattern i9 = Pattern.compile("RankingDistribution - transmitted file [\\w\\s-:.\\\\]* to [\\w.]*:\\d* successfully in (\\d)* seconds");
    private static final Pattern i10 = Pattern.compile("RankingDistribution - error transmitting file");
    private static final Pattern i11 = Pattern.compile("Peer [\\w-_]*:[\\w-_]{12} is busy\\. Waiting \\d* ms\\.");
    //private static Pattern i12 = Pattern.compile("\\*Indexed \\d* words in URL [\\w:.&/%-~$\u00A7@=]* \\[[\\w-_]{12}\\]");
    private static final Pattern i13 = Pattern.compile("WROTE HEADER for |LOCALCRAWL\\[\\d*, \\d*, \\d*, \\d*\\]|REJECTED WRONG STATUS TYPE");
    //RegExp for LogLevel W
    private static final Pattern w1 = Pattern.compile("found not enough \\(\\d*\\) peers for distribution");
    private static final Pattern w2 = Pattern.compile("Transfer to peer ([\\w-_]*):([\\w-_]{12}) failed:'(\\w*)'");
    //RegExp for LogLevel E
    private static final Pattern e1 = Pattern.compile("INTERNAL ERROR AT plasmaCrawlLURL:store:de.anomic.kelondro.kelondroException: tried to create (\\w*) node twice in db");
    private static final Pattern e2 = Pattern.compile("INTERNAL ERROR [\\w./: ]* java.net.MalformedURLException");

    private Matcher m;
    //RegExp for advancedParser
    //private Pattern adv1 = Pattern.compile("\\*Indexed (\\d*) words in URL [\\w:.&?/%-=]* \\[[\\w-_]{12}\\]\\n\\tDescription: ([\\w- ]*)\\n\\tMimeType: ([\\w-_/]*) \\| Size: (\\d*) bytes \\| Anchors: (\\d*)\\n\\tStackingTime: (\\d*) ms \\| ParsingTime: (\\d*) ms \\| IndexingTime: (\\d*) ms \\| StorageTime: (\\d*) ms");
    private final static Pattern adv1 = Pattern.compile(
            "\\*Indexed (\\d+) words in URL [\\w:.&/%-~;$\u00A7@=]* \\[[\\w_-]{12}\\]\\r?\\n?" +
            "\\tDescription: +([\\w-\\.,:!='\"|/+@\\(\\) \\t]*)\\r?\\n?" +
            "\\tMimeType: ([\\w_~/-]*) \\| Charset: ([\\w-]*) \\| Size: (\\d+) bytes \\| Anchors: (\\d+)\\r?\\n?" +
    		"\\tLinkStorageTime: (\\d+) ms \\| indexStorageTime: (\\d+) ms");
            //"\\tStackingTime:[ ]*(\\d+) ms \\| ParsingTime:[ ]*(\\d+) ms \\| IndexingTime: (\\d+) ms \\| StorageTime: (\\d+) ms");

    private int urlSum=0;
    private int urlReqSum=0;
    private int blockedURLSum=0;
    private int wordsSum=0;
    private int rwiSum=0;
    private int blockedRWISum=0;
    private long urlTimeSum=0;
    private long rwiTimeSum=0;
    private long DHTSendTraffic=0;
    private int DHTSendURLs=0;
    private int RWIRejectCount=0;
    private final Set<String> RWIRejectPeerNames = new HashSet<String>();
    private final Set<String> RWIRejectPeerHashs = new HashSet<String>();
    private final Set<String> DHTPeerNames = new HashSet<String>();
    private final Set<String> DHTPeerHashs = new HashSet<String>();
    private int DHTSelectionTargetCount = 1;
    private int DHTSelectionWordsCount = 0;
    private int DHTSelectionWordsTimeCount = 0;
    private long minDHTDist = Long.MAX_VALUE;
    private long maxDHTDist = 0;
    private long avgDHTDist = 0;
    private int busyPeerCount = 0;
    private int notEnoughDHTPeers = 0;
    private int failedIndexDistributionCount = 0;
    private int leftChildTwiceCount = 0;
    private int rightChildTwiceCount = 0;
    private int rankingDistributionCount = 0;
    private int rankingDistributionTime = 0;
    private int rankingDistributionFailCount = 0;
    private int malformedURLCount = 0;
    private int indexedSites = 0;
    private int indexedWordSum = 0;
    private int indexedSiteSizeSum = 0;
    private int indexedAnchorsCount = 0;
    private int indexedLinkStorageTime = 0;
    private int indexedIndexStorageTime = 0;
//    private int indexedStackingTime = 0;
//    private int indexedParsingTime = 0;
//    private int indexedIndexingTime = 0;
//    private int indexedStorageTime = 0;
    private long totalParserTime = 0;
    private int totalParserRuns = 0;

    public final int parse(final String logLevel, final String logLine) {
        final long start = System.currentTimeMillis();
        if ("INFO".equals(logLevel)){
            this.m = i1.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 3) {
                this.urlSum += Integer.parseInt(this.m.group(1));
                this.urlTimeSum += Integer.parseInt(this.m.group(2));
                this.blockedURLSum += Integer.parseInt(this.m.group(3));
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i2.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 6) {
                this.rwiSum += Integer.parseInt(this.m.group(1));
                this.wordsSum += Integer.parseInt(this.m.group(2));
                this.rwiTimeSum += Integer.parseInt(this.m.group(3));
                this.urlReqSum += Integer.parseInt(this.m.group(4));
                this.blockedRWISum += Integer.parseInt(this.m.group(6));
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i2_2.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 6) {
                this.rwiSum += Integer.parseInt(this.m.group(1));
                this.wordsSum += Integer.parseInt(this.m.group(2));
                this.rwiTimeSum += Integer.parseInt(this.m.group(3));
                this.urlReqSum += Integer.parseInt(this.m.group(4));
                this.blockedRWISum += Integer.parseInt(this.m.group(6));
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i3.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 6) {
                this.DHTSendTraffic += Integer.parseInt(this.m.group(6));
                this.DHTPeerNames.add(this.m.group(2));
                this.DHTPeerHashs.add(this.m.group(3));
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i4.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 8) {
                this.DHTSendTraffic += Integer.parseInt(this.m.group(8));
                this.DHTSendURLs += Integer.parseInt(this.m.group(3));
                this.DHTPeerNames.add(this.m.group(4));
                this.DHTPeerHashs.add(this.m.group(5));
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i5.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 4) {
                this.minDHTDist = Math.min(this.minDHTDist, Math.min(Long.parseLong(this.m.group(3)), Long.parseLong(this.m.group(4))));
                this.maxDHTDist = Math.max(this.maxDHTDist, Math.max(Long.parseLong(this.m.group(3)), Long.parseLong(this.m.group(4))));
                this.avgDHTDist += Long.parseLong(this.m.group(3));
                this.DHTSelectionTargetCount++;
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i6.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 2) {
                this.RWIRejectPeerNames.add(this.m.group(2));
                this.RWIRejectPeerHashs.add(this.m.group(1));
                this.RWIRejectCount++;
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i7.matcher (logLine);

            if (this.m.find ()) {
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i8.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 2) {
                this.DHTSelectionWordsCount += Float.parseFloat(this.m.group(1));
                this.DHTSelectionWordsTimeCount += Float.parseFloat(this.m.group(2));
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i9.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 1) {
                this.rankingDistributionCount++;
                this.rankingDistributionTime += Integer.parseInt(this.m.group(1));
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i10.matcher (logLine);

            if (this.m.find ()) {
                this.rankingDistributionFailCount++;
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = i11.matcher (logLine);

            if (this.m.find ()) {
                this.busyPeerCount++;
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
//            m = i12.matcher (logLine);
//
//            if (m.find ()) {
//                return 3;
//            }
            this.m = i13.matcher (logLine);

            if (this.m.find ()) {
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = adv1.matcher (logLine);

            if (this.m.find() && this.m.groupCount() >= 8) {
                this.indexedSites++;
                this.indexedWordSum += Integer.parseInt(this.m.group(1));
                this.indexedSiteSizeSum += Integer.parseInt(this.m.group(5));
                this.indexedAnchorsCount += Integer.parseInt(this.m.group(6));
                this.indexedLinkStorageTime += Integer.parseInt(this.m.group(7));
                this.indexedIndexStorageTime += Integer.parseInt(this.m.group(8));
//                indexedStackingTime += Integer.parseInt(m.group(7));
//                indexedParsingTime += Integer.parseInt(m.group(8));
//                indexedIndexingTime += Integer.parseInt(m.group(9));
//                indexedStorageTime += Integer.parseInt(m.group(10));
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }

        } else if ("WARNING".equals(logLevel)){
            this.m = w1.matcher (logLine);

            if (this.m.find ()) {
                this.notEnoughDHTPeers++;
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = w2.matcher (logLine);

            if (this.m.find ()) {
                this.failedIndexDistributionCount++;
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
        } else if ("SEVERE".equals(logLevel)){
            this.m = e1.matcher (logLine);

            if (this.m.find () && this.m.groupCount() >= 1) {
                if ("leftchild".equals(this.m.group(1))) this.leftChildTwiceCount++;
                else if ("rightchild".equals(this.m.group(1))) this.rightChildTwiceCount++;
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
            this.m = e2.matcher (logLine);

            if (this.m.find ()) {
                this.malformedURLCount++;
                this.totalParserTime += (System.currentTimeMillis() - start);
                this.totalParserRuns++;
                return 0;
            }
        }
        this.totalParserTime += (System.currentTimeMillis() - start);
        this.totalParserRuns++;
        return -1;
    }

    public final Map<String, Object> getResults() {
        final Map<String, Object> results = new HashMap<String, Object>();
        results.put(PARSER_VERSION          , Float.valueOf(parserVersion));
        results.put(URLS_RECEIVED           , Integer.valueOf(this.urlSum));
        results.put(URLS_REQUESTED          , Integer.valueOf(this.urlReqSum));
        results.put(URLS_BLOCKED            , Integer.valueOf(this.blockedURLSum));
        results.put(WORDS_RECEIVED          , Integer.valueOf(this.wordsSum));
        results.put(RWIS_RECEIVED           , Integer.valueOf(this.rwiSum));
        results.put(RWIS_BLOCKED            , Integer.valueOf(this.blockedRWISum));
        results.put(URLS_RECEIVED_TIME      , Long.valueOf(this.urlTimeSum));
        results.put(RWIS_RECEIVED_TIME      , Long.valueOf(this.rwiTimeSum));
        results.put(DHT_TRAFFIC_SENT        , Long.valueOf(this.DHTSendTraffic));
        results.put(DHT_URLS_SENT           , Integer.valueOf(this.DHTSendURLs));
        results.put(DHT_REJECTED            , Integer.valueOf(this.RWIRejectCount));
        results.put(DHT_REJECTED_PEERS_NAME , this.RWIRejectPeerNames);
        results.put(DHT_REJECTED_PEERS_HASH , this.RWIRejectPeerHashs);
        results.put(DHT_SENT_PEERS_NAME     , this.DHTPeerNames);
        results.put(DHT_SENT_PEERS_HASH     , this.DHTPeerHashs);
        results.put(DHT_SELECTED            , Integer.valueOf(this.DHTSelectionTargetCount));
        results.put(DHT_WORDS_SELECTED      , Integer.valueOf(this.DHTSelectionWordsCount));
        results.put(DHT_WORDS_SELECTED_TIME , Integer.valueOf(this.DHTSelectionWordsTimeCount));
        results.put(DHT_DISTANCE_MIN        , Long.valueOf(this.minDHTDist));
        results.put(DHT_DISTANCE_MAX        , Long.valueOf(this.maxDHTDist));
        results.put(DHT_DISTANCE_AVERAGE    , Long.valueOf(this.avgDHTDist / this.DHTSelectionTargetCount / Long.MAX_VALUE)); //FIXME: broken avg
        results.put(PEERS_BUSY              , Integer.valueOf(this.busyPeerCount));
        results.put(PEERS_TOO_LESS          , Integer.valueOf(this.notEnoughDHTPeers));
        results.put(DHT_SENT_FAILED         , Integer.valueOf(this.failedIndexDistributionCount));
        results.put(ERROR_CHILD_TWICE_LEFT  , Integer.valueOf(this.leftChildTwiceCount));
        results.put(ERROR_CHILD_TWICE_RIGHT , Integer.valueOf(this.rightChildTwiceCount));
        results.put(RANKING_DIST            , Integer.valueOf(this.rankingDistributionCount));
        results.put(RANKING_DIST_TIME       , Integer.valueOf(this.rankingDistributionTime));
        results.put(RANKING_DIST_FAILED     , Integer.valueOf(this.rankingDistributionFailCount));
        results.put(ERROR_MALFORMED_URL     , Integer.valueOf(this.malformedURLCount));
        results.put(INDEXED_SITES           , Integer.valueOf(this.indexedSites));
        results.put(INDEXED_WORDS           , Integer.valueOf(this.indexedWordSum));
        results.put(INDEXED_SITES_SIZE      , Integer.valueOf(this.indexedSiteSizeSum));
        results.put(INDEXED_ANCHORS         , Integer.valueOf(this.indexedAnchorsCount));
//        results.put(INDEXED_STACK_TIME      , new Integer(indexedStackingTime));
//        results.put(INDEXED_PARSE_TIME      , new Integer(indexedParsingTime));
//        results.put(INDEXED_INDEX_TIME      , new Integer(indexedIndexingTime));
//        results.put(INDEXED_STORE_TIME      , new Integer(indexedStorageTime));
        results.put(INDEXED_LINKSTORE_TIME , Integer.valueOf(this.indexedLinkStorageTime));
        results.put(INDEXED_INDEXSTORE_TIME, Integer.valueOf(this.indexedIndexStorageTime));
        results.put(TOTAL_PARSER_TIME      , Long.valueOf(this.totalParserTime));
        results.put(TOTAL_PARSER_RUNS      , Integer.valueOf(this.totalParserRuns));
        return results;
    }

    public final static String getParserType() {
        return parserType;
    }

    public final static double getParserVersion() {
        return parserVersion;
    }

    public final void printResults() {
        if(this.rankingDistributionCount == 0) this.rankingDistributionCount = 1;
        if(this.DHTSelectionWordsTimeCount == 0) this.DHTSelectionWordsTimeCount = 1;
        if(this.indexedSites != 0) this.indexedSites++;
        System.out.println("INDEXER: Indexed " + this.indexedSites + " sites in " + (this.indexedLinkStorageTime + this.indexedIndexStorageTime) + " milliseconds.");
        System.out.println("INDEXER: Indexed " + this.indexedWordSum + " words on " + this.indexedSites + " sites. (avg. words per site: " + (this.indexedWordSum / this.indexedSites) + ").");
        System.out.println("INDEXER: Total Size of indexed sites: " + this.indexedSiteSizeSum + " bytes (avg. size per site: " + (this.indexedSiteSizeSum / this.indexedSites) + " bytes).");
        System.out.println("INDEXER: Total Number of Anchors found: " + this.indexedAnchorsCount + "(avg. Anchors per site: " + (this.indexedAnchorsCount / this.indexedSites) + ").");
        System.out.println("INDEXER: Total LinkStorageTime: " + this.indexedLinkStorageTime + " milliseconds (avg. StorageTime: " + (this.indexedLinkStorageTime / this.indexedSites) + " milliseconds).");
        System.out.println("INDEXER: Total indexStorageTime: " + this.indexedIndexStorageTime + " milliseconds (avg. StorageTime: " + (this.indexedIndexStorageTime / this.indexedSites) + " milliseconds).");
//        System.out.println("INDEXER: Total StackingTime: " + indexedStackingTime + " milliseconds (avg. StackingTime: " + (indexedStackingTime / indexedSites) + " milliseconds).");
//        System.out.println("INDEXER: Total ParsingTime: " + indexedParsingTime + " milliseconds (avg. ParsingTime: " + (indexedParsingTime / indexedSites) + " milliseconds).");
//        System.out.println("INDEXER: Total IndexingTime: " + indexedIndexingTime + " milliseconds (avg. IndexingTime: " + (indexedIndexingTime / indexedSites) + " milliseconds).");
//        System.out.println("INDEXER: Total StorageTime: " + indexedStorageTime + " milliseconds (avg. StorageTime: " + (indexedStorageTime / indexedSites) + " milliseconds).");
        if(this.urlSum != 0) this.urlSum++;
        System.out.println("DHT: Recieved " + this.urlSum + " Urls in " + this.urlTimeSum + " ms. Blocked " + this.blockedURLSum + " URLs.");
        System.out.println("DHT: " + this.urlTimeSum / this.urlSum + " milliseconds per URL.");
        if(this.rwiSum != 0) this.rwiSum++;
        System.out.println("DHT: Recieved " + this.rwiSum + " RWIs from " + this.wordsSum + " Words in " + this.rwiTimeSum + " ms. " + this.urlReqSum + " requested URLs.");
        System.out.println("DHT: Blocked " + this.blockedRWISum + " RWIs before requesting URLs, because URL-Hash was blacklisted.");
        System.out.println("DHT: " + this.rwiTimeSum / this.rwiSum + " milliseconds per RWI.");
        System.out.println("DHT: Rejected " + this.RWIRejectCount + " Indextransfers from " + this.RWIRejectPeerNames.size() + " PeerNames with " + this.RWIRejectPeerHashs.size() + " PeerHashs.");
        System.out.println("DHT: " + this.DHTSendTraffic/(1024*1024l) + " MegaBytes (" + this.DHTSendTraffic + " Bytes) of DHT-Transfertraffic.");
        System.out.println("DHT: Sended " + this.DHTSendURLs + " URLs via DHT.");
        System.out.println("DHT: DHT Transfers send to " + this.DHTPeerNames.size() + " Peernames with " + this.DHTPeerHashs.size() + " Peerhashs.");
        System.out.println("DHT: Totally selected " + this.DHTSelectionWordsCount + " words in " + this.DHTSelectionWordsTimeCount + " seconds (" + (float)this.DHTSelectionWordsCount/this.DHTSelectionWordsTimeCount + " words/s)");
        System.out.println("DHT: Selected " + this.DHTSelectionTargetCount + " possible DHT Targets (min. Distance: " + this.minDHTDist + " max. Distance: " + this.maxDHTDist + " avg. Distance: " + (this.avgDHTDist/this.DHTSelectionTargetCount));
        System.out.println("DHT: " + this.busyPeerCount + " times a targetpeer was too busy to accept a transfer.");
        System.out.println("DHT: " + this.notEnoughDHTPeers + " times there were not enought targetpeers for the selected DHTChunk");
        System.out.println("DHT: IndexDistribution failed " + this.failedIndexDistributionCount + " times.");
        System.out.println("RANKING: Transmitted " + this.rankingDistributionCount + " Rankingfiles in " + this.rankingDistributionTime + " seconds (" + this.rankingDistributionTime/this.rankingDistributionCount + " seconds/file)");
        System.out.println("RANKING: RankingDistribution failed " + this.rankingDistributionFailCount + " times.");
        if (this.leftChildTwiceCount != 0)
            System.out.println("ERRORS: tried " + this.leftChildTwiceCount + " times to create leftchild node twice in db");
        if (this.rightChildTwiceCount != 0)
            System.out.println("ERRORS: tried " + this.rightChildTwiceCount + " times to create rightchild node twice in db");
        if (this.malformedURLCount != 0)
            System.out.println("ERRORS: " + this.malformedURLCount + " MalformedURLExceptions accord.");
    }

}
