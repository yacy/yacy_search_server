// Supporter.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 13.6.2007 on http://yacy.net
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


import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.peers.NewsDB;
import net.yacy.peers.NewsPool;
import net.yacy.peers.Seed;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;
import net.yacy.utils.nxTools;

public class Supporter {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        prop.put("display", display);

        final boolean showScore = ((post != null) && (post.containsKey("score")));

        // access control
        final boolean publicPage = sb.getConfigBool("publicSurftips", true);
        final boolean authorizedAccess = sb.verifyAuthentication(header);

        if ((publicPage) || (authorizedAccess)) {

            // read voting
            String hash;
            if ((post != null) && ((hash = post.get("voteNegative", null)) != null)) {
                if (!sb.verifyAuthentication(header)) {
                	prop.authenticationRequired();
                    return prop;
                }
                // make new news message with voting
                if (!sb.isRobinsonMode()) {
                    final HashMap<String, String> map = new HashMap<String, String>();
                    map.put("urlhash", hash);
                    map.put("vote", "negative");
                    map.put("refid", post.get("refid", ""));
                    sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map);
                }
            }
            if ((post != null) && ((hash = post.get("votePositive", null)) != null)) {
                if (!sb.verifyAuthentication(header)) {
                	prop.authenticationRequired();
                    return prop;
                }
                // make new news message with voting
                final HashMap<String, String> map = new HashMap<String, String>();
                map.put("urlhash", hash);
                map.put("url", crypt.simpleDecode(post.get("url", "")));
                map.put("title", crypt.simpleDecode(post.get("title", "")));
                map.put("description", crypt.simpleDecode(post.get("description", "")));
                map.put("vote", "positive");
                map.put("refid", post.get("refid", ""));
                map.put("comment", post.get("comment", ""));
                sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map);
            }

            // create Supporter
            final HashMap<String, Integer> negativeHashes = new HashMap<String, Integer>(); // a mapping from an url hash to Integer (count of votes)
            final HashMap<String, Integer> positiveHashes = new HashMap<String, Integer>(); // a mapping from an url hash to Integer (count of votes)
            accumulateVotes(sb, negativeHashes, positiveHashes, NewsPool.INCOMING_DB);
            //accumulateVotes(negativeHashes, positiveHashes, yacyNewsPool.OUTGOING_DB);
            //accumulateVotes(negativeHashes, positiveHashes, yacyNewsPool.PUBLISHED_DB);
            final ScoreMap<String> ranking = new ConcurrentScoreMap<String>(); // score cluster for url hashes
            final Row rowdef = new Row("String url-255, String title-120, String description-120, String refid-" + (GenericFormatter.PATTERN_SHORT_SECOND.length() + 12), NaturalOrder.naturalOrder);
            final HashMap<String, Entry> Supporter = new HashMap<String, Entry>(); // a mapping from an url hash to a kelondroRow.Entry with display properties
            accumulateSupporter(sb, Supporter, ranking, rowdef, negativeHashes, positiveHashes, NewsPool.INCOMING_DB);
            //accumulateSupporter(Supporter, ranking, rowdef, negativeHashes, positiveHashes, yacyNewsPool.OUTGOING_DB);
            //accumulateSupporter(Supporter, ranking, rowdef, negativeHashes, positiveHashes, yacyNewsPool.PUBLISHED_DB);

            // read out surftipp array and create property entries
            final Iterator<String> k = ranking.keys(false);
            int i = 0;
            Row.Entry row;
            String url, urlhash, refid, title, description;
            boolean voted;
            while (k.hasNext()) {
                urlhash = k.next();
                if (urlhash == null) continue;

                row = Supporter.get(urlhash);
                if (row == null) continue;

                url = row.getPrimaryKeyUTF8().trim();
                try {
                    if (Switchboard.urlBlacklist.isListed(BlacklistType.SURFTIPS, new DigestURL(url, urlhash.getBytes()))) continue;
                } catch (final MalformedURLException e) {
                    continue;
                }
                title = row.getColUTF8(1);
                description = row.getColUTF8(2);
                if ((url == null) || (title == null) || (description == null)) continue;
                refid = row.getColUTF8(3);
                voted = (sb.peers.newsPool.getSpecific(NewsPool.OUTGOING_DB, NewsPool.CATEGORY_SURFTIPP_VOTE_ADD, "refid", refid) != null) ||
                        (sb.peers.newsPool.getSpecific(NewsPool.PUBLISHED_DB, NewsPool.CATEGORY_SURFTIPP_VOTE_ADD, "refid", refid) != null);
                prop.put("supporter_results_" + i + "_authorized", authenticated ? "1" : "0");
                prop.put("supporter_results_" + i + "_authorized_recommend", voted ? "0" : "1");

                prop.put("supporter_results_" + i + "_authorized_recommend_urlhash", urlhash);
                prop.put("supporter_results_" + i + "_authorized_recommend_refid", refid);
                prop.put("supporter_results_" + i + "_authorized_recommend_url", crypt.simpleEncode(url, null, 'b'));
                prop.putHTML("supporter_results_" + i + "_authorized_recommend_title", crypt.simpleEncode(title, null, 'b'));
                prop.putHTML("supporter_results_" + i + "_authorized_recommend_description", crypt.simpleEncode(description, null, 'b'));
                prop.put("supporter_results_" + i + "_authorized_recommend_display", display);
                prop.put("supporter_results_" + i + "_authorized_recommend_showScore", showScore ? "1" : "0");

                prop.put("supporter_results_" + i + "_authorized_urlhash", urlhash);
                prop.put("supporter_results_" + i + "_url", url);
                prop.put("supporter_results_" + i + "_urlname", nxTools.shortenURLString(url, 60));
                prop.put("supporter_results_" + i + "_urlhash", urlhash);
                prop.putHTML("supporter_results_" + i + "_title", (showScore) ? ("(" + ranking.get(urlhash) + ") " + title) : title);
                prop.putHTML("supporter_results_" + i + "_description", description);
                i++;

                if (i >= 50) break;
            }
            prop.put("supporter_results", i);
            prop.put("supporter", "1");
        } else {
            prop.put("supporter", "0");
        }

        return prop;
    }

    private static int timeFactor(final Date created) {
        return (int) Math.max(0, 10 - ((System.currentTimeMillis() - created.getTime()) / 24 / 60 / 60 / 1000));
    }

    private static void accumulateVotes(final Switchboard sb, final HashMap<String, Integer> negativeHashes, final HashMap<String, Integer> positiveHashes, final int dbtype) {
        final int maxCount = Math.min(1000, sb.peers.newsPool.size(dbtype));
        NewsDB.Record record;
        final Iterator<NewsDB.Record> recordIterator = sb.peers.newsPool.recordIterator(dbtype);
        int j = 0;
        while ((recordIterator.hasNext()) && (j++ < maxCount)) {
            record = recordIterator.next();
            if (record == null) continue;

            if (record.category().equals(NewsPool.CATEGORY_SURFTIPP_VOTE_ADD)) {
                final String urlhash = record.attribute("urlhash", "");
                final String vote    = record.attribute("vote", "");
                final int factor = ((dbtype == NewsPool.OUTGOING_DB) || (dbtype == NewsPool.PUBLISHED_DB)) ? 2 : 1;
                if (vote.equals("negative")) {
                    final Integer i = negativeHashes.get(urlhash);
                    if (i == null) negativeHashes.put(urlhash, Integer.valueOf(factor));
                    else negativeHashes.put(urlhash, Integer.valueOf(i.intValue() + factor));
                }
                if (vote.equals("positive")) {
                    final Integer i = positiveHashes.get(urlhash);
                    if (i == null) positiveHashes.put(urlhash, Integer.valueOf(factor));
                    else positiveHashes.put(urlhash, Integer.valueOf(i.intValue() + factor));
                }
            }
        }
    }

    private static void accumulateSupporter(
            final Switchboard sb,
            final HashMap<String, Entry> Supporter, final ScoreMap<String> ranking, final Row rowdef,
            final HashMap<String, Integer> negativeHashes, final HashMap<String, Integer> positiveHashes, final int dbtype) {
        final int maxCount = Math.min(1000, sb.peers.newsPool.size(dbtype));
        NewsDB.Record record;
        final Iterator<NewsDB.Record> recordIterator = sb.peers.newsPool.recordIterator(dbtype);
        int j = 0;
        String url = "", urlhash;
        Row.Entry entry;
        int score = 0;
        Integer vote;
        Seed seed;
        while ((recordIterator.hasNext()) && (j++ < maxCount)) {
            record = recordIterator.next();
            if (record == null) continue;

            entry = null;
            if ((record.category().equals(NewsPool.CATEGORY_PROFILE_UPDATE)) &&
                ((seed = sb.peers.getConnected(record.originator())) != null)) {
                url = record.attribute("homepage", "");
                if (url.length() < 12) continue;
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                url.getBytes(),
                                UTF8.getBytes(("Home Page of " + seed.getName())),
                                record.id().getBytes()
                        });
                score = 1 + timeFactor(record.created());
            }

            if ((record.category().equals(NewsPool.CATEGORY_PROFILE_BROADCAST)) &&
                ((seed = sb.peers.getConnected(record.originator())) != null)) {
                url = record.attribute("homepage", "");
                if (url.length() < 12) continue;
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                url.getBytes(),
                                UTF8.getBytes(("Home Page of " + seed.getName())),
                                record.id().getBytes()
                        });
                score = 1 + timeFactor(record.created());
            }

            // add/subtract votes and write record
            if (entry != null) {
                try {
                    urlhash = ASCII.String((new DigestURL(url)).hash());
                } catch (final MalformedURLException e) {
                    urlhash = null;
                }
                if (urlhash == null)
                    try {
                        urlhash = ASCII.String((new DigestURL("http://" + url)).hash());
                    } catch (final MalformedURLException e) {
                        urlhash = null;
                    }
                        if (urlhash==null) {
                            System.out.println("Supporter: bad url '" + url + "' from news record " + record.toString());
                            continue;
                        }
                if ((vote = negativeHashes.get(urlhash)) != null) {
                    score = Math.max(0, score - vote.intValue()); // do not go below zero
                }
                if ((vote = positiveHashes.get(urlhash)) != null) {
                    score += 2 * vote.intValue();
                }
                // consider double-entries
                if (Supporter.containsKey(urlhash)) {
                    ranking.inc(urlhash, score);
                } else {
                    ranking.set(urlhash, score);
                    Supporter.put(urlhash, entry);
                }
            }
        }
    }
}
