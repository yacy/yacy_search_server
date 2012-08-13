// IndexControlRWIs_p.java
// -----------------------
// (C) 2004-2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.lod.JenaTripleStore;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadata;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.peers.dht.PeerSelection;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.RWIProcess;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.ranking.BlockRank;
import net.yacy.search.ranking.ReferenceOrder;
import de.anomic.crawler.Cache;
import de.anomic.crawler.ResultURLs;
import de.anomic.data.ListManager;
import de.anomic.data.WorkTables;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexControlRWIs_p {

    private final static String errmsg = "not possible to compute word from hash";

    public static serverObjects respond(
        @SuppressWarnings("unused") final RequestHeader header,
        final serverObjects post,
        final serverSwitch env) throws IOException {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // set default values
        prop.putHTML("keystring", "");
        prop.put("keyhash", "");
        prop.put("result", "");
        prop.put("cleanup", post == null || post.containsKey("maxReferencesLimit") ? 1 : 0);
        prop.put("cleanup_solr", sb.index.connectedSolr() ? 1 : 0);

        // switch off all optional forms/lists
        prop.put("searchresult", 0);
        prop.put("keyhashsimilar", 0);
        prop.put("genUrlList", 0);

        // clean up all search events
        SearchEventCache.cleanupEvents(true);

        Segment segment = sb.index;

        if ( post != null ) {
            final String keystring = post.get("keystring", "").trim();
            byte[] keyhash = post.get("keyhash", "").trim().getBytes();
            if (keystring.length() > 0 && !keystring.contains(errmsg)) {
                keyhash = Word.word2hash(keystring);
            }
            prop.putHTML("keystring", keystring);
            prop.putHTML("keyhash", ASCII.String(keyhash));

            // read values from checkboxes
            final String[] urls = post.getAll("urlhx.*");
            HandleSet urlb =
                new RowHandleSet(
                    URIMetadataRow.rowdef.primaryKeyLength,
                    URIMetadataRow.rowdef.objectOrder,
                    urls.length);
            if ( urls != null ) {
                for ( final String s : urls ) {
                    try {
                        urlb.put(s.getBytes());
                    } catch ( final SpaceExceededException e ) {
                        Log.logException(e);
                    }
                }
            }
            final boolean delurl = post.containsKey("delurl");
            final boolean delurlref = post.containsKey("delurlref");

            if ( post.containsKey("keystringsearch") ) {
                prop.put("keyhash", keyhash);
                final RWIProcess ranking = genSearchresult(prop, sb, segment, keyhash, null);
                if ( ranking.filteredCount() == 0 ) {
                    prop.put("searchresult", 1);
                    prop.putHTML("searchresult_word", keystring);
                }
            }

            if ( post.containsKey("keyhashsearch") ) {
                if ( keystring.isEmpty() || !ByteBuffer.equals(Word.word2hash(keystring), keyhash) ) {
                    prop.put("keystring", "&lt;" + errmsg + "&gt;");
                }
                final RWIProcess ranking = genSearchresult(prop, sb, segment, keyhash, null);
                if ( ranking.filteredCount() == 0 ) {
                    prop.put("searchresult", 2);
                    prop.putHTML("searchresult_wordhash", ASCII.String(keyhash));
                }
            }

            // delete everything
            if ( post.containsKey("deletecomplete") ) {
                if ( post.get("deleteIndex", "").equals("on") ) {
                    segment.clear();
                }
                if ( post.get("deleteRemoteSolr", "").equals("on") && sb.index.connectedSolr()) {
                    try {
                        sb.index.getSolr().clear();
                    } catch ( final Exception e ) {
                        Log.logException(e);
                    }
                }
                if ( post.get("deleteCrawlQueues", "").equals("on") ) {
                    sb.crawlQueues.clear();
                    sb.crawlStacker.clear();
                    ResultURLs.clearStacks();
                }
                if ( post.get("deleteTriplestore", "").equals("on") ) {
                    JenaTripleStore.clear();
                }
                if ( post.get("deleteCache", "").equals("on") ) {
                    Cache.clear();
                }
                if ( post.get("deleteRobots", "").equals("on") ) {
                    sb.robots.clear();
                }
                if ( post.get("deleteSearchFl", "").equals("on") ) {
                    sb.tables.clear(WorkTables.TABLE_SEARCH_FAILURE_NAME);
                }
                post.remove("deletecomplete");
            }

            // set reference limitation
            if ( post.containsKey("maxReferencesLimit") ) {
                if ( post.get("maxReferencesRadio", "").equals("on") ) {
                    ReferenceContainer.maxReferences = post.getInt("maxReferences", 0);
                } else {
                    ReferenceContainer.maxReferences = 0;
                }
                sb.setConfig("index.maxReferences", ReferenceContainer.maxReferences);
            }

            // delete word
            if ( post.containsKey("keyhashdeleteall") ) {
                try {
                    if ( delurl || delurlref ) {
                        // generate urlx: an array of url hashes to be deleted
                        ReferenceContainer<WordReference> index = null;
                        index = segment.termIndex().get(keyhash, null);
                        final Iterator<WordReference> en = index.entries();
                        urlb =
                            new RowHandleSet(
                                URIMetadataRow.rowdef.primaryKeyLength,
                                URIMetadataRow.rowdef.objectOrder,
                                index.size());
                        while ( en.hasNext() ) {
                            try {
                                urlb.put(en.next().urlhash());
                            } catch ( final SpaceExceededException e ) {
                                Log.logException(e);
                            }
                        }
                        index = null;
                    }
                    if ( delurlref ) {
                        segment.removeAllUrlReferences(urlb, sb.loader, CacheStrategy.IFEXIST);
                    }
                    // delete the word first because that is much faster than the deletion of the urls from the url database
                    segment.termIndex().delete(keyhash);
                    // now delete all urls if demanded
                    if ( delurl || delurlref ) {
                        for ( final byte[] b : urlb ) {
                            sb.urlRemove(segment, b);
                        }
                    }
                    post.remove("keyhashdeleteall");
                    post.put("urllist", "generated");
                } catch ( final IOException e ) {
                    Log.logException(e);
                }
            }

            // delete selected URLs
            if ( post.containsKey("keyhashdelete") ) {
                try {
                    if ( delurlref ) {
                        segment.removeAllUrlReferences(urlb, sb.loader, CacheStrategy.IFEXIST);
                    }
                    if ( delurl || delurlref ) {
                        for ( final byte[] b : urlb ) {
                            sb.urlRemove(segment, b);
                        }
                    }
                    final HandleSet urlHashes =
                        new RowHandleSet(
                            URIMetadataRow.rowdef.primaryKeyLength,
                            URIMetadataRow.rowdef.objectOrder,
                            0);
                    for ( final byte[] b : urlb ) {
                        try {
                            urlHashes.put(b);
                        } catch ( final SpaceExceededException e ) {
                            Log.logException(e);
                        }
                    }
                    segment.termIndex().remove(keyhash, urlHashes);
                    // this shall lead to a presentation of the list; so handle that the remaining program
                    // thinks that it was called for a list presentation
                    post.remove("keyhashdelete");
                    post.put("urllist", "generated");
                } catch ( final IOException e ) {
                    Log.logException(e);
                }
            }

            if ( post.containsKey("urllist") ) {
                if ( keystring.isEmpty() || !ByteBuffer.equals(Word.word2hash(keystring), keyhash) ) {
                    prop.put("keystring", "&lt;" + errmsg + "&gt;");
                }
                final Bitfield flags = compileFlags(post);
                final int count = (post.get("lines", "all").equals("all")) ? -1 : post.getInt("lines", -1);
                final RWIProcess ranking = genSearchresult(prop, sb, segment, keyhash, flags);
                genURLList(prop, keyhash, keystring, ranking, flags, count);
            }

            // transfer to other peer
            if ( post.containsKey("keyhashtransfer") ) {
                try {
                    if ( keystring.isEmpty() || !ByteBuffer.equals(Word.word2hash(keystring), keyhash) ) {
                        prop.put("keystring", "&lt;" + errmsg + "&gt;");
                    }

                    // find host & peer
                    String host = post.get("host", ""); // get host from input field
                    Seed seed = null;
                    if ( host.length() != 0 ) {
                        if ( host.length() == 12 ) {
                            // the host string is !likely! a peer hash (or peer name with 12 chars)
                            seed = sb.peers.getConnected(host); // check for seed.hash
                            if (seed == null) seed = sb.peers.lookupByName(host); // check for peer name
                        } else {
                            // the host string can be a host name
                            seed = sb.peers.lookupByName(host);
                        }
                    } else {
                        host = post.get("hostHash", ""); // if input field is empty, get from select box
                        seed = sb.peers.getConnected(host);
                    }

                    if (seed != null) { // if no seed found skip transfer
                        // prepare index
                        ReferenceContainer<WordReference> index;
                        final long starttime = System.currentTimeMillis();
                        index = segment.termIndex().get(keyhash, null);
                        // built urlCache
                        final Iterator<WordReference> urlIter = index.entries();
                        final TreeMap<byte[], URIMetadata> knownURLs =
                                new TreeMap<byte[], URIMetadata>(Base64Order.enhancedCoder);
                        final HandleSet unknownURLEntries =
                                new RowHandleSet(
                                WordReferenceRow.urlEntryRow.primaryKeyLength,
                                WordReferenceRow.urlEntryRow.objectOrder,
                                index.size());
                        Reference iEntry;
                        URIMetadata lurl;
                        while (urlIter.hasNext()) {
                            iEntry = urlIter.next();
                            lurl = segment.urlMetadata().load(iEntry.urlhash());
                            if (lurl == null) {
                                try {
                                    unknownURLEntries.put(iEntry.urlhash());
                                } catch (final SpaceExceededException e) {
                                    Log.logException(e);
                                }
                                urlIter.remove();
                            } else {
                                knownURLs.put(iEntry.urlhash(), lurl);
                            }
                        }

                        // make an indexContainerCache
                        final ReferenceContainerCache<WordReference> icc =
                                new ReferenceContainerCache<WordReference>(
                                Segment.wordReferenceFactory,
                                Segment.wordOrder,
                                Word.commonHashLength);
                        try {
                            icc.add(index);
                        } catch (final SpaceExceededException e) {
                            Log.logException(e);
                        }

                        // transport to other peer
                        final boolean gzipBody = sb.getConfigBool("indexControl.gzipBody", false);
                        final int timeout = (int) sb.getConfigLong("indexControl.timeout", 60000);
                        final String error = Protocol.transferIndex(seed, icc, knownURLs, gzipBody, timeout);
                        prop.put("result", (error == null) ? ("Successfully transferred "
                                + knownURLs.size()
                                + " words in "
                                + ((System.currentTimeMillis() - starttime) / 1000)
                                + " seconds, "
                                + unknownURLEntries.size() + " URL not found") : "error: " + error);
                        index = null;
                    } else {
                        prop.put("result", "Peer " + host + " not found");
                    }
                } catch ( final IOException e ) {
                    Log.logException(e);
                }
            }

            // generate list
            if ( post.containsKey("keyhashsimilar") ) {
                try {
                    final Iterator<ReferenceContainer<WordReference>> containerIt =
                        segment.termIndex().referenceContainer(keyhash, true, false, 256, false).iterator();
                    ReferenceContainer<WordReference> container;

                    int i = 0, rows = 0, cols = 0;
                    prop.put("keyhashsimilar", "1");
                    while ( containerIt.hasNext() && i < 256 ) {
                        container = containerIt.next();
                        prop.put(
                            "keyhashsimilar_rows_" + rows + "_cols_" + cols + "_wordHash",
                            container.getTermHash());
                        cols++;
                        if ( cols == 8 ) {
                            prop.put("keyhashsimilar_rows_" + rows + "_cols", cols);
                            cols = 0;
                            rows++;
                        }
                        i++;
                    }
                    prop.put("keyhashsimilar_rows_" + rows + "_cols", cols);
                    prop.put("keyhashsimilar_rows", rows + 1);
                    prop.put("result", "");
                } catch ( final IOException e ) {
                    Log.logException(e);
                }
            }

            if ( post.containsKey("blacklist") ) {
                final String blacklist = post.get("blacklist", "");
                final HandleSet urlHashes =
                    new RowHandleSet(
                        URIMetadataRow.rowdef.primaryKeyLength,
                        URIMetadataRow.rowdef.objectOrder,
                        urlb.size());
                if ( post.containsKey("blacklisturls") ) {
                    PrintWriter pw;
                    try {
                        final String[] supportedBlacklistTypes =
                            env.getConfig("BlackLists.types", "").split(",");
                        pw =
                            new PrintWriter(new FileWriter(new File(ListManager.listsPath, blacklist), true));
                        DigestURI url;
                        for ( final byte[] b : urlb ) {
                            try {
                                urlHashes.put(b);
                            } catch ( final SpaceExceededException e ) {
                                Log.logException(e);
                            }
                            final URIMetadata e = segment.urlMetadata().load(b);
                            segment.urlMetadata().remove(b);
                            if ( e != null ) {
                                url = e.url();
                                pw.println(url.getHost() + "/" + url.getFile());
                                for ( final String supportedBlacklistType : supportedBlacklistTypes ) {
                                    if ( ListManager.listSetContains(
                                        supportedBlacklistType + ".BlackLists",
                                        blacklist) ) {
                                        Switchboard.urlBlacklist.add(
                                            BlacklistType.valueOf(supportedBlacklistType),
                                            url.getHost(),
                                            url.getFile());
                                    }
                                }
                                SearchEventCache.cleanupEvents(true);
                            }
                        }
                        pw.close();
                    } catch ( final IOException e ) {
                    }
                }

                if ( post.containsKey("blacklistdomains") ) {
                    PrintWriter pw;
                    try {
                        pw =
                            new PrintWriter(new FileWriter(new File(ListManager.listsPath, blacklist), true));
                        DigestURI url;
                        for ( final byte[] b : urlb ) {
                            try {
                                urlHashes.put(b);
                            } catch ( final SpaceExceededException e ) {
                                Log.logException(e);
                            }
                            final URIMetadata e = segment.urlMetadata().load(b);
                            segment.urlMetadata().remove(b);
                            if ( e != null ) {
                                url = e.url();
                                pw.println(url.getHost() + "/.*");
                                for ( final BlacklistType supportedBlacklistType : BlacklistType.values() ) {
                                    if ( ListManager.listSetContains(
                                        supportedBlacklistType + ".BlackLists",
                                        blacklist) ) {
                                        Switchboard.urlBlacklist.add(
                                            supportedBlacklistType,
                                            url.getHost(),
                                            ".*");
                                    }
                                }
                            }
                        }
                        pw.close();
                    } catch ( final IOException e ) {
                    }
                }
                try {
                    segment.termIndex().remove(keyhash, urlHashes);
                } catch ( final IOException e ) {
                    Log.logException(e);
                }
            }

            if ( prop.getInt("searchresult", 0) == 3 ) {
                listHosts(prop, keyhash, sb);
            }
        }

        // insert constants
        prop.putNum("wcount", segment.termIndex().sizesMax());
        prop.put("cleanup_maxReferencesRadioChecked", ReferenceContainer.maxReferences > 0 ? 1 : 0);
        prop.put("cleanup_maxReferences", ReferenceContainer.maxReferences > 0
            ? ReferenceContainer.maxReferences
            : 100000);

        // return rewrite properties
        return prop;
    }

    public static void genURLList(
        final serverObjects prop,
        final byte[] keyhash,
        final String keystring,
        final RWIProcess ranked,
        final Bitfield flags,
        final int maxlines) {
        // search for a word hash and generate a list of url links
        final String keyhashs = ASCII.String(keyhash);
        prop.put("genUrlList_keyHash", keyhashs);

        if ( ranked.filteredCount() == 0 ) {
            prop.put("genUrlList", 1);
            prop.put("genUrlList_count", 0);
            prop.put("searchresult", 2);
        } else {
            prop.put("genUrlList", 2);
            prop.put("searchresult", 3);
            prop.put("genUrlList_flags", (flags == null) ? "" : flags.exportB64());
            prop.put("genUrlList_lines", maxlines);
            int i = 0;
            DigestURI url;
            URIMetadata entry;
            String us;
            long rn = -1;
            while ( !ranked.isEmpty() && (entry = ranked.takeURL(false, 1000)) != null ) {
                url = entry.url();
                if ( url == null ) {
                    continue;
                }
                us = url.toNormalform(false, false);
                if ( rn == -1 ) {
                    rn = entry.ranking();
                }
                prop.put("genUrlList_urlList_" + i + "_urlExists", "1");
                prop.put("genUrlList_urlList_" + i + "_urlExists_urlhxCount", i);
                prop.putHTML("genUrlList_urlList_" + i + "_urlExists_urlhxValue", entry.word().urlhash());
                prop.putHTML("genUrlList_urlList_" + i + "_urlExists_keyString", keystring);
                prop.put("genUrlList_urlList_" + i + "_urlExists_keyHash", keyhashs);
                prop.putHTML("genUrlList_urlList_" + i + "_urlExists_urlString", us);
                prop.put(
                    "genUrlList_urlList_" + i + "_urlExists_urlStringShort",
                    (us.length() > 40) ? (us.substring(0, 20) + "<br>" + us.substring(20, 40) + "...") : ((us
                        .length() > 30) ? (us.substring(0, 20) + "<br>" + us.substring(20)) : us));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_ranking", (entry.ranking() - rn));
                prop.putNum(
                    "genUrlList_urlList_" + i + "_urlExists_domlength",
                    DigestURI.domLengthEstimation(entry.hash()));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_ybr", BlockRank.ranking(entry.hash()));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_tf", 1000.0 * entry
                    .word()
                    .termFrequency());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_authority", (ranked.getOrder() == null)
                    ? -1
                    : ranked.getOrder().authority(ASCII.String(entry.hash(), 6, 6)));
                prop.put(
                    "genUrlList_urlList_" + i + "_urlExists_date",
                    GenericFormatter.SHORT_DAY_FORMATTER.format(new Date(entry.word().lastModified())));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_wordsintitle", entry
                    .word()
                    .wordsintitle());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_wordsintext", entry.word().wordsintext());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_phrasesintext", entry
                    .word()
                    .phrasesintext());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_llocal", entry.word().llocal());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_lother", entry.word().lother());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_hitcount", entry.word().hitcount());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_worddistance", 0);
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_ybr", BlockRank.ranking(entry.hash()));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_pos", entry.word().minposition());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_phrase", entry.word().posofphrase());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_posinphrase", entry.word().posinphrase());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_urlcomps", entry.word().urlcomps());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_urllength", entry.word().urllength());
                prop
                    .put(
                        "genUrlList_urlList_" + i + "_urlExists_props",
                        ((entry.word().flags().get(Condenser.flag_cat_indexof))
                            ? "appears on index page, "
                            : "")
                            + ((entry.word().flags().get(Condenser.flag_cat_hasimage))
                                ? "contains images, "
                                : "")
                            + ((entry.word().flags().get(Condenser.flag_cat_hasaudio))
                                ? "contains audio, "
                                : "")
                            + ((entry.word().flags().get(Condenser.flag_cat_hasvideo))
                                ? "contains video, "
                                : "")
                            + ((entry.word().flags().get(Condenser.flag_cat_hasapp))
                                ? "contains applications, "
                                : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_identifier))
                                ? "appears in url, "
                                : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_title))
                                ? "appears in title, "
                                : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_creator))
                                ? "appears in author, "
                                : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_subject))
                                ? "appears in subject, "
                                : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_description))
                                ? "appears in description, "
                                : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_emphasized))
                                ? "appears emphasized, "
                                : "")
                            + ((DigestURI.probablyRootURL(entry.word().urlhash())) ? "probably root url" : ""));
                if ( Switchboard.urlBlacklist.isListed(BlacklistType.DHT, url) ) {
                    prop.put("genUrlList_urlList_" + i + "_urlExists_urlhxChecked", "1");
                }
                i++;
                if ( (maxlines >= 0) && (i >= maxlines) ) {
                    break;
                }
            }
            final Iterator<byte[]> iter = ranked.miss(); // iterates url hash strings
            byte[] b;
            while ( iter.hasNext() ) {
                b = iter.next();
                prop.put("genUrlList_urlList_" + i + "_urlExists", "0");
                prop.put("genUrlList_urlList_" + i + "_urlExists_urlhxCount", i);
                prop.putHTML("genUrlList_urlList_" + i + "_urlExists_urlhxValue", b);
                i++;
            }
            prop.put("genUrlList_urlList", i);
            prop.putHTML("genUrlList_keyString", keystring);
            prop.put("genUrlList_count", i);
            putBlacklists(prop, FileUtils.getDirListing(ListManager.listsPath));
        }
    }

    public static void putBlacklists(final serverObjects prop, final List<String> lists) {
        prop.put("genUrlList_blacklists", lists.size());
        int i = 0;
        for ( final String list : lists ) {
            prop.put("genUrlList_blacklists_" + i++ + "_name", list);
        }
    }

    public static Bitfield compileFlags(final serverObjects post) {
        final Bitfield b = new Bitfield(4);
        if ( post.get("allurl", "").equals("on") ) {
            return null;
        }
        if ( post.get("flags") != null ) {
            if ( post.get("flags", "").isEmpty() ) {
                return null;
            }
            return new Bitfield(4, post.get("flags"));
        }
        if ( post.get("description", "").equals("on") ) {
            b.set(WordReferenceRow.flag_app_dc_description, true);
        }
        if ( post.get("title", "").equals("on") ) {
            b.set(WordReferenceRow.flag_app_dc_title, true);
        }
        if ( post.get("creator", "").equals("on") ) {
            b.set(WordReferenceRow.flag_app_dc_creator, true);
        }
        if ( post.get("subject", "").equals("on") ) {
            b.set(WordReferenceRow.flag_app_dc_subject, true);
        }
        if ( post.get("url", "").equals("on") ) {
            b.set(WordReferenceRow.flag_app_dc_identifier, true);
        }
        if ( post.get("emphasized", "").equals("on") ) {
            b.set(WordReferenceRow.flag_app_emphasized, true);
        }
        if ( post.get("image", "").equals("on") ) {
            b.set(Condenser.flag_cat_hasimage, true);
        }
        if ( post.get("audio", "").equals("on") ) {
            b.set(Condenser.flag_cat_hasaudio, true);
        }
        if ( post.get("video", "").equals("on") ) {
            b.set(Condenser.flag_cat_hasvideo, true);
        }
        if ( post.get("app", "").equals("on") ) {
            b.set(Condenser.flag_cat_hasapp, true);
        }
        if ( post.get("indexof", "").equals("on") ) {
            b.set(Condenser.flag_cat_indexof, true);
        }
        return b;
    }

    public static void listHosts(final serverObjects prop, final byte[] startHash, final Switchboard sb) {
        // list known hosts
        Seed seed;
        int hc = 0;
        prop.put("searchresult_keyhash", startHash);
        final Iterator<Seed> e =
            PeerSelection.getAcceptRemoteIndexSeeds(sb.peers, startHash, sb.peers.sizeConnected(), true);
        while ( e.hasNext() ) {
            seed = e.next();
            if ( seed != null ) {
                prop.put("searchresult_hosts_" + hc + "_hosthash", seed.hash);
                prop.putHTML(
                    "searchresult_hosts_" + hc + "_hostname",
                    seed.hash + " " + seed.get(Seed.NAME, "nameless"));
                hc++;
            }
        }
        prop.put("searchresult_hosts", hc);
    }

    public static RWIProcess genSearchresult(
        final serverObjects prop,
        final Switchboard sb,
        final Segment segment,
        final byte[] keyhash,
        final Bitfield filter) {
        final QueryParams query =
            new QueryParams(ASCII.String(keyhash), -1, filter, segment, sb.getRanking(), "IndexControlRWIs_p");
        final ReferenceOrder order = new ReferenceOrder(query.ranking, UTF8.getBytes(query.targetlang));
        final RWIProcess ranked = new RWIProcess(query, order, false);
        ranked.run();

        if ( ranked.filteredCount() == 0 ) {
            prop.put("searchresult", 2);
            prop.put("searchresult_wordhash", keyhash);
        } else {
            prop.put("searchresult", 3);
            prop.put("searchresult_allurl", ranked.filteredCount());
            prop
                .put("searchresult_description", ranked.flagCount()[WordReferenceRow.flag_app_dc_description]);
            prop.put("searchresult_title", ranked.flagCount()[WordReferenceRow.flag_app_dc_title]);
            prop.put("searchresult_creator", ranked.flagCount()[WordReferenceRow.flag_app_dc_creator]);
            prop.put("searchresult_subject", ranked.flagCount()[WordReferenceRow.flag_app_dc_subject]);
            prop.put("searchresult_url", ranked.flagCount()[WordReferenceRow.flag_app_dc_identifier]);
            prop.put("searchresult_emphasized", ranked.flagCount()[WordReferenceRow.flag_app_emphasized]);
            prop.put("searchresult_image", ranked.flagCount()[Condenser.flag_cat_hasimage]);
            prop.put("searchresult_audio", ranked.flagCount()[Condenser.flag_cat_hasaudio]);
            prop.put("searchresult_video", ranked.flagCount()[Condenser.flag_cat_hasvideo]);
            prop.put("searchresult_app", ranked.flagCount()[Condenser.flag_cat_hasapp]);
            prop.put("searchresult_indexof", ranked.flagCount()[Condenser.flag_cat_indexof]);
        }
        return ranked;
    }
}
