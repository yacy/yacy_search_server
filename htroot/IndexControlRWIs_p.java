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

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.document.id.Punycode.PunycodeException;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.ListManager;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.DHTSelection;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexControlRWIs_p {
    
    private static final String APP_NAME = "IndexControlRWIs_p";

    private final static String errmsg = "not possible to compute word from hash";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // set default values
        prop.putHTML("keystring", "");
        prop.put("keyhash", "");
        prop.put("result", "");
        prop.put("limitations", post == null || post.containsKey("maxReferencesLimit") ? 1 : 0);

        // switch off all optional forms/lists
        prop.put("searchresult", 0);
        prop.put("keyhashsimilar", 0);
        prop.put("genUrlList", 0);

        // clean up all search events
        SearchEventCache.cleanupEvents(true);

        Segment segment = sb.index;

        if ( post != null ) {
            ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
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
                    Word.commonHashLength,
                    Word.commonHashOrder,
                    urls.length);
            if ( urls != null ) {
                for ( final String s : urls ) {
                    try {
                        urlb.put(s.getBytes());
                    } catch (final SpaceExceededException e ) {
                        ConcurrentLog.logException(e);
                    }
                }
            }
            final boolean delurl = post.containsKey("delurl");
            final boolean delurlref = post.containsKey("delurlref");

            if ( post.containsKey("keystringsearch") ) {
                prop.put("keyhash", keyhash);
                final SearchEvent theSearch = genSearchresult(prop, sb, keyhash, null);
                if (theSearch.local_rwi_available.get() == 0) {
                    prop.put("searchresult", 1);
                    prop.putHTML("searchresult_word", keystring);
                }
            }

            if ( post.containsKey("keyhashsearch") ) {
                if ( keystring.isEmpty() || !ByteBuffer.equals(Word.word2hash(keystring), keyhash) ) {
                    prop.put("keystring", "&lt;" + errmsg + "&gt;");
                }
                final SearchEvent theSearch = genSearchresult(prop, sb, keyhash, null);
                if (theSearch.local_rwi_available.get() == 0) {
                    prop.put("searchresult", 2);
                    prop.putHTML("searchresult_wordhash", ASCII.String(keyhash));
                }
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
                                Word.commonHashLength,
                                Word.commonHashOrder,
                                index.size());
                        while ( en.hasNext() ) {
                            try {
                                urlb.put(en.next().urlhash());
                            } catch (final SpaceExceededException e ) {
                                ConcurrentLog.logException(e);
                            }
                        }
                        index = null;
                    }
                    if ( delurlref ) {
                        segment.removeAllUrlReferences(urlb, sb.loader, agent, CacheStrategy.IFEXIST);
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
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
                }
            }

            // delete selected URLs
            if ( post.containsKey("keyhashdelete") ) {
                try {
                    if ( delurlref ) {
                        segment.removeAllUrlReferences(urlb, sb.loader, agent, CacheStrategy.IFEXIST);
                    }
                    if ( delurl || delurlref ) {
                        for ( final byte[] b : urlb ) {
                            sb.urlRemove(segment, b);
                        }
                    }
                    final HandleSet urlHashes =
                        new RowHandleSet(
                            Word.commonHashLength,
                            Word.commonHashOrder,
                            0);
                    for ( final byte[] b : urlb ) {
                        try {
                            urlHashes.put(b);
                        } catch (final SpaceExceededException e ) {
                            ConcurrentLog.logException(e);
                        }
                    }
                    segment.termIndex().remove(keyhash, urlHashes);
                    // this shall lead to a presentation of the list; so handle that the remaining program
                    // thinks that it was called for a list presentation
                    post.remove("keyhashdelete");
                    post.put("urllist", "generated");
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
                }
            }

            if ( post.containsKey("urllist") ) {
                if ( keystring.isEmpty() || !ByteBuffer.equals(Word.word2hash(keystring), keyhash) ) {
                    prop.put("keystring", "&lt;" + errmsg + "&gt;");
                }
                final Bitfield flags = compileFlags(post);
                final int count = (post.get("lines", "all").equals("all")) ? -1 : post.getInt("lines", -1);
                final SearchEvent theSearch = genSearchresult(prop, sb, keyhash, flags);
                genURLList(prop, keyhash, keystring, theSearch, flags, count);
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
                        final HandleSet knownURLs =
                        		new RowHandleSet(
                                        WordReferenceRow.urlEntryRow.primaryKeyLength,
                                        WordReferenceRow.urlEntryRow.objectOrder,
                                        index.size());
                        final HandleSet unknownURLEntries =
                                new RowHandleSet(
                                WordReferenceRow.urlEntryRow.primaryKeyLength,
                                WordReferenceRow.urlEntryRow.objectOrder,
                                index.size());
                        Reference iEntry;
                        while (urlIter.hasNext()) {
                            iEntry = urlIter.next();
                            if (segment.fulltext().getLoadTime(ASCII.String(iEntry.urlhash())) >= 0) {
                                try {
                                    unknownURLEntries.put(iEntry.urlhash());
                                } catch (final SpaceExceededException e) {
                                    ConcurrentLog.logException(e);
                                }
                                urlIter.remove();
                            } else {
                                try {
									knownURLs.put(iEntry.urlhash());
								} catch (final SpaceExceededException e) {
									ConcurrentLog.logException(e);
								}
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
                            ConcurrentLog.logException(e);
                        }

                        // transport to other peer
                        final boolean gzipBody = sb.getConfigBool("indexControl.gzipBody", false);
                        final int timeout = (int) sb.getConfigLong("indexControl.timeout", 60000);
                        final String error = Protocol.transferIndex(seed, icc, knownURLs, segment, gzipBody, timeout);
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
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
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
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
                }
            }

            if ( post.containsKey("blacklist") ) {
                final String blacklist = post.get("blacklist", "");
                final HandleSet urlHashes =
                    new RowHandleSet(
                        Word.commonHashLength,
                        Word.commonHashOrder,
                        urlb.size());
                if ( post.containsKey("blacklisturls") ) {
                    final String[] supportedBlacklistTypes =
					    env.getConfig("BlackLists.types", "").split(",");
					DigestURL url;
					for ( final byte[] b : urlb ) {
					    try {
					        urlHashes.put(b);
					    } catch (final SpaceExceededException e ) {
					        ConcurrentLog.logException(e);
					    }
					    url = segment.fulltext().getURL(ASCII.String(b));
					    segment.fulltext().remove(b);
					    if ( url != null ) {
					        for ( final String supportedBlacklistType : supportedBlacklistTypes ) {
					            if ( ListManager.listSetContains(
					                supportedBlacklistType + ".BlackLists",
					                blacklist) ) {
					                try {
                                        Switchboard.urlBlacklist.add(
                                            BlacklistType.valueOf(supportedBlacklistType),
                                            blacklist,
                                            url.getHost(),
                                            url.getFile());
                                    } catch (PunycodeException e) {
                                        ConcurrentLog.warn(APP_NAME,
                                                        "Unable to add blacklist entry to blacklist "
                                                                        + supportedBlacklistType, e);
                                    }
					            }
					        }
					        SearchEventCache.cleanupEvents(true);
					    }
					}
                }

                if ( post.containsKey("blacklistdomains") ) {
                    DigestURL url;
					for ( final byte[] b : urlb ) {
					    try {
					        urlHashes.put(b);
					    } catch (final SpaceExceededException e ) {
					        ConcurrentLog.logException(e);
					    }
					    url = segment.fulltext().getURL(ASCII.String(b));
					    segment.fulltext().remove(b);
					    if ( url != null ) {
					        for ( final BlacklistType supportedBlacklistType : BlacklistType.values() ) {
					            if ( ListManager.listSetContains(
					                supportedBlacklistType + ".BlackLists",
					                blacklist) ) {
					                try {
                                        Switchboard.urlBlacklist.add(
                                            supportedBlacklistType,
                                            blacklist,
                                            url.getHost(),
                                            ".*");
                                    } catch (PunycodeException e) {
                                        ConcurrentLog.warn(APP_NAME,
                                                        "Unable to add blacklist entry to blacklist "
                                                                        + supportedBlacklistType, e);
                                    }
					            }
					        }
					    }
					}
                }
                try {
                    segment.termIndex().remove(keyhash, urlHashes);
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
                }
            }

            if ( prop.getInt("searchresult", 0) == 3 ) {
                listHosts(prop, keyhash, sb);
            }
        }

        // insert constants
        prop.putNum("wcount", segment.RWICount());
        prop.put("limitations_maxReferencesRadioChecked", ReferenceContainer.maxReferences > 0 ? 1 : 0);
        prop.put("limitations_maxReferences", ReferenceContainer.maxReferences > 0 ? ReferenceContainer.maxReferences : 100000);

        // return rewrite properties
        return prop;
    }

    public static void genURLList(
        final serverObjects prop,
        final byte[] keyhash,
        final String keystring,
        final SearchEvent theSearch,
        final Bitfield flags,
        final int maxlines) {
        // search for a word hash and generate a list of url links
        final String keyhashs = ASCII.String(keyhash);
        prop.put("genUrlList_keyHash", keyhashs);

        if (theSearch.local_rwi_stored.get() == 0) {
            prop.put("genUrlList", 1);
            prop.put("genUrlList_count", 0);
            prop.put("searchresult", 2);
        } else {
            prop.put("genUrlList", 2);
            prop.put("searchresult", 3);
            prop.put("genUrlList_flags", (flags == null) ? "" : flags.exportB64());
            prop.put("genUrlList_lines", maxlines);
            int i = 0;
            DigestURL url;
            URIMetadataNode entry;
            String us;
            long rn = -1;
            while (!theSearch.rwiIsEmpty() && (entry = theSearch.pullOneFilteredFromRWI(false)) != null) {
                url = entry.url();
                if ( url == null ) {
                    continue;
                }
                us = url.toNormalform(true);
                if ( rn == -1 ) {
                    rn = entry.ranking();
                }
                prop.put("genUrlList_urlList_" + i + "_urlExists", "1");
                prop.put("genUrlList_urlList_" + i + "_urlExists_urlhxCount", i);
                prop.putHTML("genUrlList_urlList_" + i + "_urlExists_urlhxValue", entry.word().urlhash());
                prop.putHTML("genUrlList_urlList_" + i + "_urlExists_keyString", keystring);
                prop.put("genUrlList_urlList_" + i + "_urlExists_keyHash", keyhashs);
                prop.putHTML("genUrlList_urlList_" + i + "_urlExists_urlString", us);
                prop.put("genUrlList_urlList_" + i + "_urlExists_urlStringShort",
                    (us.length() > 40) ? (us.substring(0, 20) + "<br>" + us.substring(20, 40) + "...") : ((us.length() > 30) ? (us.substring(0, 20) + "<br>" + us.substring(20)) : us));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_ranking", (entry.ranking() - rn));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_domlength", DigestURL.domLengthEstimation(entry.hash()));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_tf", 1000.0 * entry.word().termFrequency());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_authority", (theSearch.getOrder() == null) ? -1 : theSearch.getOrder().authority(ASCII.String(entry.hash(), 6, 6)));
                prop.put("genUrlList_urlList_" + i + "_urlExists_date", GenericFormatter.SHORT_DAY_FORMATTER.format(new Date(entry.word().lastModified())));
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_wordsintitle", entry.word().wordsintitle());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_wordsintext", entry.word().wordsintext());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_phrasesintext", entry.word().phrasesintext());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_llocal", entry.word().llocal());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_lother", entry.word().lother());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_hitcount", entry.word().hitcount());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_worddistance", 0);
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_pos", entry.word().minposition());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_phrase", entry.word().posofphrase());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_posinphrase", entry.word().posinphrase());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_urlcomps", entry.word().urlcomps());
                prop.putNum("genUrlList_urlList_" + i + "_urlExists_urllength", entry.word().urllength());
                prop.put(
                        "genUrlList_urlList_" + i + "_urlExists_props",
                        ((entry.word().flags().get(Condenser.flag_cat_indexof)) ? "appears on index page, " : "")
                            + ((entry.word().flags().get(Condenser.flag_cat_hasimage)) ? "contains images, " : "")
                            + ((entry.word().flags().get(Condenser.flag_cat_hasaudio)) ? "contains audio, " : "")
                            + ((entry.word().flags().get(Condenser.flag_cat_hasvideo)) ? "contains video, " : "")
                            + ((entry.word().flags().get(Condenser.flag_cat_hasapp)) ? "contains applications, " : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_identifier)) ? "appears in url, " : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_title)) ? "appears in title, " : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_creator)) ? "appears in author, " : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_subject)) ? "appears in subject, " : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_dc_description)) ? "appears in description, " : "")
                            + ((entry.word().flags().get(WordReferenceRow.flag_app_emphasized)) ? "appears emphasized, " : ""));
                if ( Switchboard.urlBlacklist.isListed(BlacklistType.DHT, url) ) {
                    prop.put("genUrlList_urlList_" + i + "_urlExists_urlhxChecked", "1");
                }
                i++;
                if ( (maxlines >= 0) && (i >= maxlines) ) {
                    break;
                }
            }
            prop.put("genUrlList_urlList", i);
            prop.putHTML("genUrlList_keyString", keystring);
            prop.put("genUrlList_count", i);
            putBlacklists(prop, FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER));
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
            DHTSelection.getAcceptRemoteIndexSeeds(sb.peers, startHash, sb.peers.sizeConnected(), true);
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

    public static SearchEvent genSearchresult(
        final serverObjects prop,
        final Switchboard sb,
        final byte[] keyhash,
        final Bitfield filter) {
        
        final HandleSet queryhashes = QueryParams.hashes2Set(ASCII.String(keyhash));        
        final QueryGoal qg = new QueryGoal(queryhashes, null);
        final QueryParams query = new QueryParams(
                qg,
                new QueryModifier(),
                Integer.MAX_VALUE,
                "",
                ContentDomain.ALL,
                "", //lang
                null,
                CacheStrategy.IFFRESH,
                1000, 0, //count, offset             
                ".*", //urlmask
                null,
                null,
                QueryParams.Searchdom.LOCAL,
                filter,
                false,
                null,
                MultiProtocolURL.TLD_any_zone_filter,
                "", 
                false,
                sb.index,
                sb.getRanking(),
                "",//userAgent
                false,
                false,
                0.0d, 0.0d, 0.0d,
                new String[0]);       
        final SearchEvent theSearch = SearchEventCache.getEvent(query, sb.peers, sb.tables, null, false, sb.loader, Integer.MAX_VALUE, Long.MAX_VALUE);       
        if (theSearch.rwiProcess != null && theSearch.rwiProcess.isAlive()) try {theSearch.rwiProcess.join();} catch (final InterruptedException e) {}
        if (theSearch.local_rwi_available.get() == 0) {
            prop.put("searchresult", 2);
            prop.put("searchresult_wordhash", keyhash);
        } else {
            prop.put("searchresult", 3);
            prop.put("searchresult_allurl", theSearch.local_rwi_available.get());
            prop
                .put("searchresult_description", theSearch.flagCount()[WordReferenceRow.flag_app_dc_description]);
            prop.put("searchresult_title", theSearch.flagCount()[WordReferenceRow.flag_app_dc_title]);
            prop.put("searchresult_creator", theSearch.flagCount()[WordReferenceRow.flag_app_dc_creator]);
            prop.put("searchresult_subject", theSearch.flagCount()[WordReferenceRow.flag_app_dc_subject]);
            prop.put("searchresult_url", theSearch.flagCount()[WordReferenceRow.flag_app_dc_identifier]);
            prop.put("searchresult_emphasized", theSearch.flagCount()[WordReferenceRow.flag_app_emphasized]);
            prop.put("searchresult_image", theSearch.flagCount()[Condenser.flag_cat_hasimage]);
            prop.put("searchresult_audio", theSearch.flagCount()[Condenser.flag_cat_hasaudio]);
            prop.put("searchresult_video", theSearch.flagCount()[Condenser.flag_cat_hasvideo]);
            prop.put("searchresult_app", theSearch.flagCount()[Condenser.flag_cat_hasapp]);
            prop.put("searchresult_indexof", theSearch.flagCount()[Condenser.flag_cat_indexof]);
        }
        return theSearch;
    }
}
