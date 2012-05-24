// yacysearch.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// You must compile this file with
// javac -classpath .:../classes yacysearch.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.Classification;
import net.yacy.cora.document.Classification.ContentDomain;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.document.Autotagging.Metatag;
import net.yacy.document.Autotagging.Vocabulary;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser;
import net.yacy.document.geolocalization.Location;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.ISO639;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.SetTools;
import net.yacy.peers.EventChannel;
import net.yacy.peers.NewsPool;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.search.index.Segments;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.ranking.RankingProfile;
import de.anomic.data.DidYouMean;
import de.anomic.data.UserDB;
import de.anomic.data.ymark.YMarkTables;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class yacysearch {

    public static serverObjects respond(
        final RequestHeader header,
        final serverObjects post,
        final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        sb.localSearchLastAccess = System.currentTimeMillis();

        final boolean searchAllowed =
            sb.getConfigBool("publicSearchpage", true) || sb.verifyAuthentication(header);

        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        if ( !authenticated ) {
            final UserDB.Entry user = sb.userDB.getUser(header);
            authenticated = (user != null && user.hasRight(UserDB.AccessRight.EXTENDED_SEARCH_RIGHT));
        }
        final boolean localhostAccess = sb.accessFromLocalhost(header);
        final String promoteSearchPageGreeting =
            (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) ? env.getConfig(
                "network.unit.description",
                "") : env.getConfig(SwitchboardConstants.GREETING, "");
        final String client = header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP); // the search client who initiated the search

        // get query
        final String originalquerystring =
            (post == null) ? "" : post.get("query", post.get("search", "")).trim();
        String querystring = originalquerystring.replace('+', ' ').replace('*', ' ').trim();
        CacheStrategy snippetFetchStrategy =
            (post == null) ? null : CacheStrategy.parse(post.get("verify", "cacheonly"));
        final servletProperties prop = new servletProperties();
        prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);

        //get focus option
        prop.put("focus", ((post == null) ? true : post.get("focus", "1").equals("1")) ? 1 : 0);

        // produce vocabulary navigation sidebars
        Collection<Vocabulary> vocabularies = LibraryProvider.autotagging.getVocabularies();
        int j = 0;
        for (Vocabulary v: vocabularies) {
            prop.put("sidebarVocabulary_" + j + "_vocabulary", v.getName());
            j++;
        }
        prop.put("sidebarVocabulary", j);

        // get segment
        Segment indexSegment = null;
        if ( post != null && post.containsKey("segment") ) {
            final String segmentName = post.get("segment");
            if ( sb.indexSegments.segmentExist(segmentName) ) {
                indexSegment = sb.indexSegments.segment(segmentName);
            }
        } else {
            // take default segment
            indexSegment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }

        final String EXT = header.get("EXT", "");
        final boolean rss = EXT.equals("rss");
        final boolean json = EXT.equals("json");
        prop.put("promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.put(
            "promoteSearchPageGreeting.homepage",
            sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.put(
            "promoteSearchPageGreeting.smallImage",
            sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
        if ( post == null || indexSegment == null || env == null || !searchAllowed ) {
            // we create empty entries for template strings
            prop.put("searchagain", "0");
            prop.put("former", "");
            prop.put("count", "10");
            prop.put("offset", "0");
            prop.put("resource", "global");
            prop.put("urlmaskfilter", (post == null) ? ".*" : post.get("urlmaskfilter", ".*"));
            prop.put("prefermaskfilter", (post == null) ? "" : post.get("prefermaskfilter", ""));
            prop.put("tenant", (post == null) ? "" : post.get("tenant", ""));
            prop.put("indexof", "off");
            prop.put("constraint", "");
            prop.put("cat", "href");
            prop.put("depth", "0");
            prop.put(
                "search.verify",
                (post == null) ? sb.getConfig("search.verify", "iffresh") : post.get("verify", "iffresh"));
            prop.put(
                "search.navigation",
                (post == null) ? sb.getConfig("search.navigation", "all") : post.get("nav", "all"));
            prop.put("contentdom", "text");
            prop.put("contentdomCheckText", "1");
            prop.put("contentdomCheckAudio", "0");
            prop.put("contentdomCheckVideo", "0");
            prop.put("contentdomCheckImage", "0");
            prop.put("contentdomCheckApp", "0");
            prop.put("excluded", "0");
            prop.put("results", "");
            prop.put("resultTable", "0");
            prop.put("num-results", searchAllowed ? "0" : "4");
            prop.put("num-results_totalcount", 0);
            prop.put("num-results_offset", 0);
            prop.put("num-results_itemsPerPage", 10);
            prop.put("geoinfo", "0");
            prop.put("rss_queryenc", "");
            prop.put("meanCount", 5);
            return prop;
        }

        // check for JSONP
        if ( post.containsKey("callback") ) {
            final String jsonp = post.get("callback") + "([";
            prop.put("jsonp-start", jsonp);
            prop.put("jsonp-end", "])");
        } else {
            prop.put("jsonp-start", "");
            prop.put("jsonp-end", "");
        }

        // Adding CORS Access header for yacysearch.rss output
        if ( rss ) {
            final ResponseHeader outgoingHeader = new ResponseHeader();
            outgoingHeader.put(HeaderFramework.CORS_ALLOW_ORIGIN, "*");
            prop.setOutgoingHeader(outgoingHeader);
        }

        // collect search attributes

        int itemsPerPage =
            Math.min(
                (authenticated)
                    ? (snippetFetchStrategy != null && snippetFetchStrategy.isAllowedToFetchOnline()
                        ? 100
                        : 5000) : (snippetFetchStrategy != null
                        && snippetFetchStrategy.isAllowedToFetchOnline() ? 20 : 1000),
                post.getInt("maximumRecords", post.getInt("count", 10))); // SRU syntax with old property as alternative
        int startRecord = post.getInt("startRecord", post.getInt("offset", 0));

        boolean global = post.get("resource", "local").equals("global") && sb.peers.sizeConnected() > 0;
        final boolean indexof = (post != null && post.get("indexof", "").equals("on"));

        final String originalUrlMask;
        if ( post.containsKey("urlmaskfilter") ) {
            originalUrlMask = post.get("urlmaskfilter", ".*");
        } else {
            originalUrlMask = ".*";
        }

        String prefermask = (post == null) ? "" : post.get("prefermaskfilter", "");
        if ( !prefermask.isEmpty() && prefermask.indexOf(".*", 0) < 0 ) {
            prefermask = ".*" + prefermask + ".*";
        }

        Bitfield constraint =
            (post != null && post.containsKey("constraint") && !post.get("constraint", "").isEmpty())
                ? new Bitfield(4, post.get("constraint", "______"))
                : null;
        if ( indexof ) {
            constraint = new Bitfield(4);
            constraint.set(Condenser.flag_cat_indexof, true);
        }

        // SEARCH
        final boolean clustersearch = sb.isRobinsonMode() && sb.getConfig(SwitchboardConstants.CLUSTER_MODE, "").equals(SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER);
        final boolean indexReceiveGranted =
            sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true)
                || sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_AUTODISABLED, true)
                || clustersearch;
        global = global && indexReceiveGranted; // if the user does not want indexes from remote peers, it cannot be a global searchnn
        final boolean intranetMode = sb.isIntranetMode() || sb.isAllIPMode();

        // increase search statistic counter
        if ( !global ) {
            // we count only searches on the local peer here, because global searches
            // are counted on the target peer to preserve privacy of the searcher
            if ( authenticated ) {
                // local or authenticated search requests are counted separately
                // because they are not part of a public available peer statistic
                sb.searchQueriesRobinsonFromLocal++;
            } else {
                // robinson-searches from non-authenticated requests are public
                // and may be part of the public available statistic
                sb.searchQueriesRobinsonFromRemote++;
            }
        }

        // find search domain
        final Classification.ContentDomain contentdom =
            ContentDomain.contentdomParser(post == null ? "all" : post.get("contentdom", "all"));

        // patch until better search profiles are available
        if (contentdom == ContentDomain.IMAGE && (itemsPerPage == 10 || itemsPerPage == 100)) {
            itemsPerPage = 64;
        } else if ( contentdom != ContentDomain.IMAGE && itemsPerPage > 50 && itemsPerPage < 100 ) {
            itemsPerPage = 10;
        }

        // check the search tracker
        TreeSet<Long> trackerHandles = sb.localSearchTracker.get(client);
        if ( trackerHandles == null ) {
            trackerHandles = new TreeSet<Long>();
        }
        boolean block = false;
        if ( Domains.matchesList(client, sb.networkBlacklist) ) {
            global = false;
            if ( snippetFetchStrategy != null ) {
                snippetFetchStrategy = null;
            }
            block = true;
            Log.logWarning("LOCAL_SEARCH", "ACCESS CONTROL: BLACKLISTED CLIENT FROM "
                + client
                + " gets no permission to search");
        } else if ( Domains.matchesList(client, sb.networkWhitelist) ) {
            Log.logInfo("LOCAL_SEARCH", "ACCESS CONTROL: WHITELISTED CLIENT FROM "
                + client
                + " gets no search restrictions");
        } else if ( !authenticated && !localhostAccess && !intranetMode ) {
            // in case that we do a global search or we want to fetch snippets, we check for DoS cases
            synchronized ( trackerHandles ) {
                final int accInThreeSeconds =
                    trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 3000)).size();
                final int accInOneMinute =
                    trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 60000)).size();
                final int accInTenMinutes =
                    trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 600000)).size();
                // protections against too strong YaCy network load, reduces remote search
                if ( global ) {
                    if ( accInTenMinutes >= 60 || accInOneMinute >= 6 || accInThreeSeconds >= 1 ) {
                        global = false;
                        Log.logWarning("LOCAL_SEARCH", "ACCESS CONTROL: CLIENT FROM "
                            + client
                            + ": "
                            + accInThreeSeconds
                            + "/3s, "
                            + accInOneMinute
                            + "/60s, "
                            + accInTenMinutes
                            + "/600s, "
                            + " requests, disallowed global search");
                    }
                }
                // protection against too many remote server snippet loads (protects traffic on server)
                if ( snippetFetchStrategy != null && snippetFetchStrategy.isAllowedToFetchOnline() ) {
                    if ( accInTenMinutes >= 20 || accInOneMinute >= 4 || accInThreeSeconds >= 1 ) {
                        snippetFetchStrategy = CacheStrategy.CACHEONLY;
                        Log.logWarning("LOCAL_SEARCH", "ACCESS CONTROL: CLIENT FROM "
                            + client
                            + ": "
                            + accInThreeSeconds
                            + "/3s, "
                            + accInOneMinute
                            + "/60s, "
                            + accInTenMinutes
                            + "/600s, "
                            + " requests, disallowed remote snippet loading");
                    }
                }
                // general load protection
                if ( accInTenMinutes >= 3000 || accInOneMinute >= 600 || accInThreeSeconds >= 60 ) {
                    block = true;
                    Log.logWarning("LOCAL_SEARCH", "ACCESS CONTROL: CLIENT FROM "
                        + client
                        + ": "
                        + accInThreeSeconds
                        + "/3s, "
                        + accInOneMinute
                        + "/60s, "
                        + accInTenMinutes
                        + "/600s, "
                        + " requests, disallowed search");
                }
            }
        }

        if ( !block && (post == null || post.get("cat", "href").equals("href")) ) {
            String urlmask = null;

            // check available memory and clean up if necessary
            if ( !MemoryControl.request(8000000L, false) ) {
                indexSegment.urlMetadata().clearCache();
                SearchEventCache.cleanupEvents(true);
            }

            final RankingProfile ranking = sb.getRanking();
            final StringBuilder modifier = new StringBuilder(20);

            if ( querystring.indexOf("/near", 0) >= 0 ) {
                querystring = querystring.replace("/near", "");
                ranking.coeff_worddistance = RankingProfile.COEFF_MAX;
                modifier.append("/near ");
            }
            if ( querystring.indexOf("/date", 0) >= 0 ) {
                querystring = querystring.replace("/date", "");
                ranking.coeff_date = RankingProfile.COEFF_MAX;
                modifier.append("/date ");
            }
            if ( querystring.indexOf("/http", 0) >= 0 ) {
                querystring = querystring.replace("/http", "");
                urlmask = "https?://.*";
                modifier.append("/http ");
            }
            if ( querystring.indexOf("/https", 0) >= 0 ) {
                querystring = querystring.replace("/https", "");
                urlmask = "https?://.*";
                modifier.append("/https ");
            }
            if ( querystring.indexOf("/ftp", 0) >= 0 ) {
                querystring = querystring.replace("/ftp", "");
                urlmask = "ftp://.*";
                modifier.append("/ftp ");
            }
            if ( querystring.indexOf("/smb", 0) >= 0 ) {
                querystring = querystring.replace("/smb", "");
                urlmask = "smb://.*";
                modifier.append("/smb ");
            }

            if ( querystring.indexOf("/file", 0) >= 0 ) {
                querystring = querystring.replace("/file", "");
                urlmask = "file://.*";
                modifier.append("/file ");
            }

            if ( querystring.indexOf("/location", 0) >= 0 ) {
                querystring = querystring.replace("/location", "");
                if ( constraint == null ) {
                    constraint = new Bitfield(4);
                }
                constraint.set(Condenser.flag_cat_haslocation, true);
                modifier.append("/location ");
            }

            final int lrp = querystring.indexOf("/language/", 0);
            String language = "";
            if ( lrp >= 0 ) {
                if ( querystring.length() >= (lrp + 12) ) {
                    language = querystring.substring(lrp + 10, lrp + 12);
                }
                querystring = querystring.replace("/language/" + language, "");
                language = language.toLowerCase();
                modifier.append("/language/").append(language).append(' ');
            }

            final int inurl = querystring.indexOf("inurl:", 0);
            if ( inurl >= 0 ) {
                int ftb = querystring.indexOf(' ', inurl);
                if ( ftb == -1 ) {
                    ftb = querystring.length();
                }
                final String urlstr = querystring.substring(inurl + 6, ftb);
                querystring = querystring.replace("inurl:" + urlstr, "");
                if ( !urlstr.isEmpty() ) {
                    urlmask = urlmask == null ? ".*" + urlstr + ".*" : urlmask + urlstr + ".*";
                }
                modifier.append("inurl:").append(urlstr).append(' ');
            }

            final int filetype = querystring.indexOf("filetype:", 0);
            if ( filetype >= 0 ) {
                int ftb = querystring.indexOf(' ', filetype);
                if ( ftb == -1 ) {
                    ftb = querystring.length();
                }
                String ft = querystring.substring(filetype + 9, ftb);
                querystring = querystring.replace("filetype:" + ft, "");
                while ( !ft.isEmpty() && ft.charAt(0) == '.' ) {
                    ft = ft.substring(1);
                }
                if ( !ft.isEmpty() ) {
                    if ( urlmask == null ) {
                        urlmask = ".*\\." + ft;
                    } else {
                        urlmask = urlmask + ".*\\." + ft;
                    }
                }
                modifier.append("filetype:").append(ft).append(' ');
            }

            int voc = 0;
            Collection<Metatag> metatags = new ArrayList<Metatag>(1);
            while ((voc = querystring.indexOf("/vocabulary/", 0)) >= 0) {
                String vocabulary = "";
                int ve = querystring.indexOf(' ', voc + 12);
                if (ve < 0) {
                    vocabulary = querystring.substring(voc);
                    querystring = querystring.substring(0, voc).trim();
                } else {
                    vocabulary = querystring.substring(voc, ve);
                    querystring = querystring.substring(0, voc) + querystring.substring(ve);
                }
                modifier.append(vocabulary).append(' ');
                vocabulary = vocabulary.substring(12);
                int p = vocabulary.indexOf('/');
                if (p > 0) {
                    String k = vocabulary.substring(0, p);
                    String v = vocabulary.substring(p + 1);
                    metatags.add(LibraryProvider.autotagging.metatag(LibraryProvider.autotagging.prefixChar + k + ":" + v));
                }
            }

            String tenant = null;
            if ( post.containsKey("tenant") ) {
                tenant = post.get("tenant");
                if ( tenant != null && tenant.isEmpty() ) {
                    tenant = null;
                }
                if ( tenant != null ) {
                    if ( urlmask == null ) {
                        urlmask = ".*" + tenant + ".*";
                    } else {
                        urlmask = ".*" + tenant + urlmask;
                    }
                }
            }

            final int site = querystring.indexOf("site:", 0);
            String sitehash = null;
            String sitehost = null;
            if ( site >= 0 ) {
                int ftb = querystring.indexOf(' ', site);
                if ( ftb == -1 ) {
                    ftb = querystring.length();
                }
                sitehost = querystring.substring(site + 5, ftb);
                querystring = querystring.replace("site:" + sitehost, "");
                while ( sitehost.length() > 0 && sitehost.charAt(0) == '.' ) {
                    sitehost = sitehost.substring(1);
                }
                while ( sitehost.endsWith(".") ) {
                    sitehost = sitehost.substring(0, sitehost.length() - 1);
                }
                sitehash = DigestURI.hosthash(sitehost);
                modifier.append("site:").append(sitehost).append(' ');
            }

            final int heuristicBlekko = querystring.indexOf("/heuristic/blekko", 0);
            if ( heuristicBlekko >= 0 ) {
                querystring = querystring.replace("/heuristic/blekko", "");
                modifier.append("/heuristic/blekko ");
            }

            final int authori = querystring.indexOf("author:", 0);
            String authorhash = null;
            if ( authori >= 0 ) {
                // check if the author was given with single quotes or without
                final boolean quotes = (querystring.charAt(authori + 7) == '(');
                String author;
                if ( quotes ) {
                    int ftb = querystring.indexOf(')', authori + 8);
                    if ( ftb == -1 ) {
                        ftb = querystring.length() + 1;
                    }
                    author = querystring.substring(authori + 8, ftb);
                    querystring = querystring.replace("author:(" + author + ")", "");
                    modifier.append("author:(").append(author).append(") ");
                } else {
                    int ftb = querystring.indexOf(' ', authori);
                    if ( ftb == -1 ) {
                        ftb = querystring.length();
                    }
                    author = querystring.substring(authori + 7, ftb);
                    querystring = querystring.replace("author:" + author, "");
                    modifier.append("author:").append(author).append(' ');
                }
                authorhash = ASCII.String(Word.word2hash(author));
            }

            final int tld = querystring.indexOf("tld:", 0);
            if ( tld >= 0 ) {
                int ftb = querystring.indexOf(' ', tld);
                if ( ftb == -1 ) {
                    ftb = querystring.length();
                }
                String domain = querystring.substring(tld + 4, ftb);
                querystring = querystring.replace("tld:" + domain, "");
                modifier.append("tld:").append(domain).append(' ');
                while ( domain.length() > 0 && domain.charAt(0) == '.' ) {
                    domain = domain.substring(1);
                }
                if ( domain.indexOf('.', 0) < 0 ) {
                    domain = "\\." + domain;
                } // is tld
                if ( domain.length() > 0 ) {
                    urlmask = "[a-zA-Z]*://[^/]*" + domain + "/.*" + ((urlmask != null) ? urlmask : "");
                }
            }
            if ( urlmask == null || urlmask.isEmpty() ) {
                urlmask = originalUrlMask;
            } //if no urlmask was given

            // read the language from the language-restrict option 'lr'
            // if no one is given, use the user agent or the system language as default
            language = (post == null) ? language : post.get("lr", language);
            if ( language.startsWith("lang_") ) {
                language = language.substring(5);
            }
            if ( !ISO639.exists(language) ) {
                // find out language of the user by reading of the user-agent string
                String agent = header.get(HeaderFramework.ACCEPT_LANGUAGE);
                if ( agent == null ) {
                    agent = System.getProperty("user.language");
                }
                language = (agent == null) ? "en" : ISO639.userAgentLanguageDetection(agent);
                if ( language == null ) {
                    language = "en";
                }
            }

            // navigation
            final String navigation =
                (post == null) ? sb.getConfig("search.navigation", "all") : post.get("nav", "");

            // the query
            final Collection<String>[] query = QueryParams.cleanQuery(querystring.trim()); // converts also umlaute

            final int maxDistance = (querystring.indexOf('"', 0) >= 0) ? query.length - 1 : Integer.MAX_VALUE;

            // filter out stopwords
            final SortedSet<String> filtered = SetTools.joinConstructiveByTest(query[0], Switchboard.stopwords);
            if ( !filtered.isEmpty() ) {
                SetTools.excludeDestructiveByTestSmallInLarge(query[0], Switchboard.stopwords);
            }

            // if a minus-button was hit, remove a special reference first
            if ( post != null && post.containsKey("deleteref") ) {
                try {
                    if ( !sb.verifyAuthentication(header) ) {
                    	prop.authenticationRequired();
                        return prop;
                    }

                    // delete the index entry locally
                    final String delHash = post.get("deleteref", ""); // urlhash
                    indexSegment.termIndex().remove(Word.words2hashesHandles(query[0]), delHash.getBytes());

                    // make new news message with negative voting
                    if ( !sb.isRobinsonMode() ) {
                        final Map<String, String> map = new HashMap<String, String>();
                        map.put("urlhash", delHash);
                        map.put("vote", "negative");
                        map.put("refid", "");
                        sb.peers.newsPool.publishMyNews(
                            sb.peers.mySeed(),
                            NewsPool.CATEGORY_SURFTIPP_VOTE_ADD,
                            map);
                    }

                    // delete the search history since this still shows the entry
                    SearchEventCache.delete(delHash);
                } catch ( final IOException e ) {
                    Log.logException(e);
                }
            }

            // if a plus-button was hit, create new voting message
            if ( post != null && post.containsKey("recommendref") ) {
                if ( !sb.verifyAuthentication(header) ) {
                	prop.authenticationRequired();
                    return prop;
                }
                final String recommendHash = post.get("recommendref", ""); // urlhash
                final URIMetadataRow urlentry = indexSegment.urlMetadata().load(UTF8.getBytes(recommendHash));
                if ( urlentry != null ) {
                    Document[] documents = null;
                    try {
                        documents =
                            sb.loader.loadDocuments(
                                sb.loader.request(urlentry.url(), true, false),
                                CacheStrategy.IFEXIST,
                                5000,
                                Integer.MAX_VALUE);
                    } catch ( final IOException e ) {
                    } catch ( final Parser.Failure e ) {
                    }
                    if ( documents != null ) {
                        // create a news message
                        final Map<String, String> map = new HashMap<String, String>();
                        map.put("url", urlentry.url().toNormalform(false, true).replace(',', '|'));
                        map.put("title", urlentry.dc_title().replace(',', ' '));
                        map.put("description", documents[0].dc_title().replace(',', ' '));
                        map.put("author", documents[0].dc_creator());
                        map.put("tags", documents[0].dc_subject(' '));
                        sb.peers.newsPool.publishMyNews(
                            sb.peers.mySeed(),
                            NewsPool.CATEGORY_SURFTIPP_ADD,
                            map);
                        documents[0].close();
                    }
                }
            }

            // if a bookmarks-button was hit, create new bookmark entry
            if ( post != null && post.containsKey("bookmarkref") ) {
                if ( !sb.verifyAuthentication(header) ) {
                	prop.authenticationRequired();
                    return prop;
                }
                final String bookmarkHash = post.get("bookmarkref", ""); // urlhash
                final URIMetadataRow urlentry = indexSegment.urlMetadata().load(UTF8.getBytes(bookmarkHash));
                if ( urlentry != null ) {
                    try {
                        sb.tables.bookmarks.createBookmark(
                            sb.loader,
                            urlentry.url(),
                            YMarkTables.USER_ADMIN,
                            true,
                            "searchresult",
                            "/search");
                    } catch ( final Throwable e ) {
                    }
                }
            }

            // do the search
            final HandleSet queryHashes = Word.words2hashesHandles(query[0]);
            final Pattern snippetPattern = QueryParams.stringSearchPattern(originalquerystring);

            // check filters
            try {
                Pattern.compile(urlmask);
            } catch ( final PatternSyntaxException ex ) {
                Log.logWarning("SEARCH", "Illegal URL mask, not a valid regex: " + urlmask);
                prop.put("urlmaskerror", 1);
                prop.putHTML("urlmaskerror_urlmask", urlmask);
                urlmask = ".*";
            }

            try {
                Pattern.compile(prefermask);
            } catch ( final PatternSyntaxException ex ) {
                Log.logWarning("SEARCH", "Illegal prefer mask, not a valid regex: " + prefermask);
                prop.put("prefermaskerror", 1);
                prop.putHTML("prefermaskerror_prefermask", prefermask);
                prefermask = "";
            }

            final QueryParams theQuery =
                new QueryParams(
                    originalquerystring,
                    queryHashes,
                    Word.words2hashesHandles(query[1]),
                    Word.words2hashesHandles(query[2]),
                    snippetPattern,
                    tenant,
                    modifier.toString().trim(),
                    maxDistance,
                    prefermask,
                    contentdom,
                    language,
                    metatags,
                    navigation,
                    snippetFetchStrategy,
                    itemsPerPage,
                    startRecord,
                    urlmask,
                    clustersearch && global ? QueryParams.Searchdom.CLUSTER : (global && indexReceiveGranted
                        ? QueryParams.Searchdom.GLOBAL
                        : QueryParams.Searchdom.LOCAL),
                    20,
                    constraint,
                    true,
                    sitehash,
                    DigestURI.hosthashess(sb.getConfig("search.excludehosth", "")),
                    authorhash,
                    DigestURI.TLD_any_zone_filter,
                    client,
                    authenticated,
                    indexSegment,
                    ranking,
                    header.get(RequestHeader.USER_AGENT, ""),
                    sb.getConfigBool(SwitchboardConstants.SEARCH_VERIFY_DELETE, false)
                        && sb.getConfigBool(SwitchboardConstants.NETWORK_SEARCHVERIFY, false)
                        && sb.peers.mySeed().getFlagAcceptRemoteIndex());
            EventTracker.delete(EventTracker.EClass.SEARCH);
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(
                theQuery.id(true),
                SearchEvent.Type.INITIALIZATION,
                "",
                0,
                0), false);

            // tell all threads to do nothing for a specific time
            sb.intermissionAllThreads(3000);

            // filter out words that appear in bluelist
            theQuery.filterOut(Switchboard.blueList);

            // log
            Log.logInfo(
                "LOCAL_SEARCH",
                "INIT WORD SEARCH: "
                    + theQuery.queryString
                    + ":"
                    + QueryParams.hashSet2hashString(theQuery.queryHashes)
                    + " - "
                    + theQuery.neededResults()
                    + " links to be computed, "
                    + theQuery.itemsPerPage()
                    + " lines to be displayed");
            EventChannel.channels(EventChannel.LOCALSEARCH).addMessage(
                new RSSMessage("Local Search Request", theQuery.queryString, ""));
            final long timestamp = System.currentTimeMillis();

            // create a new search event
            if ( SearchEventCache.getEvent(theQuery.id(false)) == null ) {
                theQuery.setOffset(0); // in case that this is a new search, always start without a offset
                startRecord = 0;
            }
            final SearchEvent theSearch =
                SearchEventCache.getEvent(
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
                        sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXTIME_DEFAULT, 3000)),
                    (int) sb.getConfigLong(SwitchboardConstants.DHT_BURST_ROBINSON, 0),
                    (int) sb.getConfigLong(SwitchboardConstants.DHT_BURST_MULTIWORD, 0));

            if ( startRecord == 0 ) {
                if ( sitehost != null && sb.getConfigBool("heuristic.site", false) && authenticated ) {
                    sb.heuristicSite(theSearch, sitehost);
                }
                if ( (heuristicBlekko >= 0 || sb.getConfigBool("heuristic.blekko", false)) && authenticated ) {
                    sb.heuristicRSS("http://blekko.com/ws/$+/rss", theSearch, "blekko");
                }
            }

            // log
            Log.logInfo("LOCAL_SEARCH", "EXIT WORD SEARCH: "
                + theQuery.queryString
                + " - "
                + "local-unfiltered("
                + theSearch.getRankingResult().getLocalIndexCount()
                + "), "
                + "local_miss("
                + theSearch.getRankingResult().getMissCount()
                + "), "
                + "local_sortout("
                + theSearch.getRankingResult().getSortOutCount()
                + "), "
                + "remote("
                + theSearch.getRankingResult().getRemoteResourceSize()
                + ") links found, "
                + (System.currentTimeMillis() - timestamp)
                + " ms");

            // prepare search statistics
            theQuery.resultcount =
                theSearch.getRankingResult().getLocalIndexCount()
                    - theSearch.getRankingResult().getMissCount()
                    - theSearch.getRankingResult().getSortOutCount()
                    + theSearch.getRankingResult().getRemoteIndexCount();
            theQuery.searchtime = System.currentTimeMillis() - timestamp;
            theQuery.urlretrievaltime = theSearch.result().getURLRetrievalTime();
            theQuery.snippetcomputationtime = theSearch.result().getSnippetComputationTime();
            AccessTracker.add(AccessTracker.Location.local, theQuery);

            // check suggestions
            final int meanMax = (post != null) ? post.getInt("meanCount", 0) : 0;

            prop.put("meanCount", meanMax);
            if ( meanMax > 0 && !json && !rss ) {
                final DidYouMean didYouMean =
                    new DidYouMean(indexSegment.termIndex(), new StringBuilder(querystring));
                final Iterator<StringBuilder> meanIt = didYouMean.getSuggestions(100, 5).iterator();
                int meanCount = 0;
                String suggestion;
                try {
                    meanCollect: while ( meanCount < meanMax && meanIt.hasNext() ) {
                        try {
                            suggestion = meanIt.next().toString();
                            prop.put("didYouMean_suggestions_" + meanCount + "_word", suggestion);
                            prop.put(
                                "didYouMean_suggestions_" + meanCount + "_url",
                                QueryParams.navurl(
                                    "html",
                                    0,
                                    theQuery,
                                    suggestion,
                                    originalUrlMask.toString(),
                                    theQuery.navigators).toString());
                            prop.put("didYouMean_suggestions_" + meanCount + "_sep", "|");
                            meanCount++;
                        } catch (ConcurrentModificationException e) {break meanCollect;}
                    }
                } catch (ConcurrentModificationException e) {}
                prop.put("didYouMean_suggestions_" + (meanCount - 1) + "_sep", "");
                prop.put("didYouMean", meanCount > 0 ? 1 : 0);
                prop.put("didYouMean_suggestions", meanCount);
            } else {
                prop.put("didYouMean", 0);
            }

            // find geographic info
            final SortedSet<Location> coordinates = LibraryProvider.geoLoc.find(originalquerystring, false);
            if ( coordinates == null || coordinates.isEmpty() || startRecord > 0 ) {
                prop.put("geoinfo", "0");
            } else {
                int i = 0;
                for ( final Location c : coordinates ) {
                    prop.put("geoinfo_loc_" + i + "_lon", Math.round(c.lon() * 10000.0f) / 10000.0f);
                    prop.put("geoinfo_loc_" + i + "_lat", Math.round(c.lat() * 10000.0f) / 10000.0f);
                    prop.put("geoinfo_loc_" + i + "_name", c.getName());
                    i++;
                    if ( i >= 10 ) {
                        break;
                    }
                }
                prop.put("geoinfo_loc", i);
                prop.put("geoinfo", "1");
            }

            // update the search tracker
            try {
                synchronized ( trackerHandles ) {
                    trackerHandles.add(theQuery.time);
                    while ( trackerHandles.size() > 600 ) {
                        if ( !trackerHandles.remove(trackerHandles.first()) ) {
                            break;
                        }
                    }
                }
                sb.localSearchTracker.put(client, trackerHandles);
                if ( sb.localSearchTracker.size() > 100 ) {
                    sb.localSearchTracker.remove(sb.localSearchTracker.keys().nextElement());
                }
                if ( MemoryControl.shortStatus() ) {
                    sb.localSearchTracker.clear();
                }
            } catch ( final Exception e ) {
                Log.logException(e);
            }

            final int indexcount =
                theSearch.getRankingResult().getLocalIndexCount()
                    - theSearch.getRankingResult().getMissCount()
                    - theSearch.getRankingResult().getSortOutCount()
                    + theSearch.getRankingResult().getRemoteIndexCount();
            prop.put("num-results_offset", startRecord == 0 ? 0 : startRecord + 1);
            prop.put("num-results_itemscount", Formatter.number(
                startRecord + theSearch.getQuery().itemsPerPage > indexcount ? startRecord
                    + indexcount
                    % theSearch.getQuery().itemsPerPage : startRecord + theSearch.getQuery().itemsPerPage,
                true));
            prop.put("num-results_itemsPerPage", itemsPerPage);
            prop.put("num-results_totalcount", Formatter.number(indexcount, true));
            prop.put("num-results_globalresults", global && (indexReceiveGranted || clustersearch)
                ? "1"
                : "0");
            prop.put(
                "num-results_globalresults_localResourceSize",
                Formatter.number(theSearch.getRankingResult().getLocalIndexCount(), true));
            prop.put(
                "num-results_globalresults_localMissCount",
                Formatter.number(theSearch.getRankingResult().getMissCount(), true));
            prop.put(
                "num-results_globalresults_remoteResourceSize",
                Formatter.number(theSearch.getRankingResult().getRemoteResourceSize(), true));
            prop.put(
                "num-results_globalresults_remoteIndexCount",
                Formatter.number(theSearch.getRankingResult().getRemoteIndexCount(), true));
            prop.put(
                "num-results_globalresults_remotePeerCount",
                Formatter.number(theSearch.getRankingResult().getRemotePeerCount(), true));

            // compose page navigation
            final StringBuilder resnav = new StringBuilder(200);
            final int thispage = startRecord / theQuery.itemsPerPage();
            if ( thispage == 0 ) {
                resnav
                    .append("<img src=\"env/grafics/navdl.gif\" alt=\"arrowleft\" width=\"16\" height=\"16\" />&nbsp;");
            } else {
                resnav.append("<a id=\"prevpage\" href=\"");
                resnav.append(QueryParams.navurl(
                    "html",
                    thispage - 1,
                    theQuery,
                    null,
                    originalUrlMask,
                    navigation).toString());
                resnav
                    .append("\"><img src=\"env/grafics/navdl.gif\" alt=\"arrowleft\" width=\"16\" height=\"16\" /></a>&nbsp;");
            }
            final int numberofpages = Math.min(10, 1 + ((indexcount - 1) / theQuery.itemsPerPage()));

            for ( int i = 0; i < numberofpages; i++ ) {
                if ( i == thispage ) {
                    resnav.append("<img src=\"env/grafics/navs");
                    resnav.append(i + 1);
                    resnav.append(".gif\" alt=\"page");
                    resnav.append(i + 1);
                    resnav.append("\" width=\"16\" height=\"16\" />&nbsp;");
                } else {
                    resnav.append("<a href=\"");
                    resnav.append(QueryParams
                        .navurl("html", i, theQuery, null, originalUrlMask, navigation)
                        .toString());
                    resnav.append("\"><img src=\"env/grafics/navd");
                    resnav.append(i + 1);
                    resnav.append(".gif\" alt=\"page");
                    resnav.append(i + 1);
                    resnav.append("\" width=\"16\" height=\"16\" /></a>&nbsp;");
                }
            }
            if ( thispage >= numberofpages ) {
                resnav
                    .append("<img src=\"env/grafics/navdr.gif\" alt=\"arrowright\" width=\"16\" height=\"16\" />");
            } else {
                resnav.append("<a id=\"nextpage\" href=\"");
                resnav.append(QueryParams.navurl(
                    "html",
                    thispage + 1,
                    theQuery,
                    null,
                    originalUrlMask,
                    navigation).toString());
                resnav
                    .append("\"><img src=\"env/grafics/navdr.gif\" alt=\"arrowright\" width=\"16\" height=\"16\" /></a>");
            }
            final String resnavs = resnav.toString();
            prop.put("num-results_resnav", resnavs);
            prop.put("pageNavBottom", (indexcount - startRecord > 6) ? 1 : 0); // if there are more results than may fit on the page we add a navigation at the bottom
            prop.put("pageNavBottom_resnav", resnavs);

            // generate the search result lines; the content will be produced by another servlet
            for ( int i = 0; i < theQuery.itemsPerPage(); i++ ) {
                prop.put("results_" + i + "_item", startRecord + i);
                prop.put("results_" + i + "_eventID", theQuery.id(false));
            }
            prop.put("results", theQuery.itemsPerPage());
            prop
                .put(
                    "resultTable",
                    (contentdom == ContentDomain.APP || contentdom == ContentDomain.AUDIO || contentdom == ContentDomain.VIDEO)
                        ? 1
                        : 0);
            prop.put("eventID", theQuery.id(false)); // for bottomline

            // process result of search
            if ( !filtered.isEmpty() ) {
                prop.put("excluded", "1");
                prop.putHTML("excluded_stopwords", filtered.toString());
            } else {
                prop.put("excluded", "0");
            }

            if ( prop == null || prop.isEmpty() ) {
                if ( post.get("query", post.get("search", "")).length() < 2 ) {
                    prop.put("num-results", "2"); // no results - at least 2 chars
                } else {
                    prop.put("num-results", "1"); // no results
                }
            } else {
                prop.put("num-results", "3");
            }

            prop.put("cat", "href");
            prop.put("depth", "0");

            // adding some additional properties needed for the rss feed
            String hostName = header.get("Host", "localhost");
            if ( hostName.indexOf(':', 0) == -1 ) {
                hostName += ":" + serverCore.getPortNr(env.getConfig("port", "8090"));
            }
            prop.put("searchBaseURL", "http://" + hostName + "/yacysearch.html");
            prop.put("rssYacyImageURL", "http://" + hostName + "/env/grafics/yacy.gif");
            prop.put("thisaddress", hostName);
        }

        prop.put("searchagain", global ? "1" : "0");
        prop.putHTML("former", originalquerystring);
        prop.put("count", itemsPerPage);
        prop.put("offset", startRecord);
        prop.put("resource", global ? "global" : "local");
        prop.putHTML("urlmaskfilter", originalUrlMask);
        prop.putHTML("prefermaskfilter", prefermask);
        prop.put("indexof", (indexof) ? "on" : "off");
        prop.put("constraint", (constraint == null) ? "" : constraint.exportB64());
        prop.put("search.verify", snippetFetchStrategy == null
            ? sb.getConfig("search.verify", "iffresh")
            : snippetFetchStrategy.toName());
        prop.put(
            "search.navigation",
            (post == null) ? sb.getConfig("search.navigation", "all") : post.get("nav", "all"));
        prop.put("contentdom", (post == null ? "text" : post.get("contentdom", "text")));
        prop.put(
            "searchdomswitches",
            sb.getConfigBool("search.text", true)
                || sb.getConfigBool("search.audio", true)
                || sb.getConfigBool("search.video", true)
                || sb.getConfigBool("search.image", true)
                || sb.getConfigBool("search.app", true) ? 1 : 0);
        prop.put("searchdomswitches_searchtext", sb.getConfigBool("search.text", true) ? 1 : 0);
        prop.put("searchdomswitches_searchaudio", sb.getConfigBool("search.audio", true) ? 1 : 0);
        prop.put("searchdomswitches_searchvideo", sb.getConfigBool("search.video", true) ? 1 : 0);
        prop.put("searchdomswitches_searchimage", sb.getConfigBool("search.image", true) ? 1 : 0);
        prop.put("searchdomswitches_searchapp", sb.getConfigBool("search.app", true) ? 1 : 0);
        prop.put("searchdomswitches_searchtext_check", (contentdom == ContentDomain.TEXT || contentdom == ContentDomain.ALL) ? "1" : "0");
        prop.put("searchdomswitches_searchaudio_check", (contentdom == ContentDomain.AUDIO) ? "1" : "0");
        prop.put("searchdomswitches_searchvideo_check", (contentdom == ContentDomain.VIDEO) ? "1" : "0");
        prop.put("searchdomswitches_searchimage_check", (contentdom == ContentDomain.IMAGE) ? "1" : "0");
        prop.put("searchdomswitches_searchapp_check", (contentdom == ContentDomain.APP) ? "1" : "0");

        // copy properties for "more options" link
        prop.put("searchdomswitches_count", prop.get("count"));
        prop.put("searchdomswitches_urlmaskfilter", prop.get("urlmaskfilter"));
        prop.put("searchdomswitches_prefermaskfilter", prop.get("prefermaskfilter"));
        prop.put("searchdomswitches_cat", prop.get("cat"));
        prop.put("searchdomswitches_constraint", prop.get("constraint"));
        prop.put("searchdomswitches_contentdom", prop.get("contentdom"));
        prop.put("searchdomswitches_former", prop.get("former"));
        prop.put("searchdomswitches_meanCount", prop.get("meanCount"));

        // for RSS: don't HTML encode some elements
        prop.putXML("rss_query", originalquerystring);
        prop.putXML("rss_queryenc", originalquerystring.replace(' ', '+'));

        sb.localSearchLastAccess = System.currentTimeMillis();

        // hostname and port (assume locahost if nothing helps)
        final InetAddress hostIP = Domains.myPublicLocalIP();
        prop.put("myhost", hostIP != null ? hostIP.getHostAddress() : "localhost");
        prop.put("myport", serverCore.getPortNr(sb.getConfig("port", "8090")));

        // return rewrite properties
        return prop;
    }
}
