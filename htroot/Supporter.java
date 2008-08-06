// Supporter.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 13.6.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.index.indexReferenceBlacklist;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRow.Entry;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class Supporter {

    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        prop.put("display", display);
        
        final boolean showScore = ((post != null) && (post.containsKey("score")));
        
        // access control
        final boolean publicPage = sb.getConfigBool("publicSurftips", true);
        final boolean authorizedAccess = sb.verifyAuthentication(header, false);
        
        if ((publicPage) || (authorizedAccess)) {
        
            // read voting
            String hash;
            if ((post != null) && ((hash = post.get("voteNegative", null)) != null)) {
                if (!sb.verifyAuthentication(header, false)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                // make new news message with voting
                final HashMap<String, String> map = new HashMap<String, String>();
                map.put("urlhash", hash);
                map.put("vote", "negative");
                map.put("refid", post.get("refid", ""));
                sb.webIndex.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.webIndex.seedDB.mySeed(), yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map));
            }
            if ((post != null) && ((hash = post.get("votePositive", null)) != null)) {
                if (!sb.verifyAuthentication(header, false)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                // make new news message with voting
                final HashMap<String, String> map = new HashMap<String, String>();
                map.put("urlhash", hash);
                map.put("url", crypt.simpleDecode(post.get("url", ""), null));
                map.put("title", crypt.simpleDecode(post.get("title", ""), null));
                map.put("description", crypt.simpleDecode(post.get("description", ""), null));
                map.put("vote", "positive");
                map.put("refid", post.get("refid", ""));
                map.put("comment", post.get("comment", ""));
                sb.webIndex.newsPool.publishMyNews(new yacyNewsRecord(sb.webIndex.seedDB.mySeed(), yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map));
            }
        
            // create Supporter
            final HashMap<String, Integer> negativeHashes = new HashMap<String, Integer>(); // a mapping from an url hash to Integer (count of votes)
            final HashMap<String, Integer> positiveHashes = new HashMap<String, Integer>(); // a mapping from an url hash to Integer (count of votes)
            accumulateVotes(sb, negativeHashes, positiveHashes, yacyNewsPool.INCOMING_DB);
            //accumulateVotes(negativeHashes, positiveHashes, yacyNewsPool.OUTGOING_DB);
            //accumulateVotes(negativeHashes, positiveHashes, yacyNewsPool.PUBLISHED_DB);
            final kelondroMScoreCluster<String> ranking = new kelondroMScoreCluster<String>(); // score cluster for url hashes
            final kelondroRow rowdef = new kelondroRow("String url-255, String title-120, String description-120, String refid-" + (serverDate.PATTERN_SHORT_SECOND.length() + 12), kelondroNaturalOrder.naturalOrder, 0);
            final HashMap<String, Entry> Supporter = new HashMap<String, Entry>(); // a mapping from an url hash to a kelondroRow.Entry with display properties
            accumulateSupporter(sb, Supporter, ranking, rowdef, negativeHashes, positiveHashes, yacyNewsPool.INCOMING_DB);
            //accumulateSupporter(Supporter, ranking, rowdef, negativeHashes, positiveHashes, yacyNewsPool.OUTGOING_DB);
            //accumulateSupporter(Supporter, ranking, rowdef, negativeHashes, positiveHashes, yacyNewsPool.PUBLISHED_DB);
        
            // read out surftipp array and create property entries
            final Iterator<String> k = ranking.scores(false);
            int i = 0;
            kelondroRow.Entry row;
            String url, urlhash, refid, title, description;
            boolean voted;
            while (k.hasNext()) {
                urlhash = k.next();
                if (urlhash == null) continue;
                
                row = Supporter.get(urlhash);
                if (row == null) continue;
                
                url = row.getColString(0, null);
                try {
                    if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_SURFTIPS ,new yacyURL(url, urlhash))) continue;
                } catch(final MalformedURLException e) {continue;}
                title = row.getColString(1,"UTF-8");
                description = row.getColString(2,"UTF-8");
                if ((url == null) || (title == null) || (description == null)) continue;
                refid = row.getColString(3, null);
                voted = (sb.webIndex.newsPool.getSpecific(yacyNewsPool.OUTGOING_DB, yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, "refid", refid) != null) || 
                        (sb.webIndex.newsPool.getSpecific(yacyNewsPool.PUBLISHED_DB, yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, "refid", refid) != null);
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
                prop.putHTML("supporter_results_" + i + "_title", (showScore) ? ("(" + ranking.getScore(urlhash) + ") " + title) : title);
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
    
    private static void accumulateVotes(final plasmaSwitchboard sb, final HashMap<String, Integer> negativeHashes, final HashMap<String, Integer> positiveHashes, final int dbtype) {
        final int maxCount = Math.min(1000, sb.webIndex.newsPool.size(dbtype));
        yacyNewsRecord record;
        final Iterator<yacyNewsRecord> recordIterator = sb.webIndex.newsPool.recordIterator(dbtype, true);
        int j = 0;
        while ((recordIterator.hasNext()) && (j++ < maxCount)) {
            record = recordIterator.next();
            if (record == null) continue;
            
            if (record.category().equals(yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD)) {
                final String urlhash = record.attribute("urlhash", "");
                final String vote    = record.attribute("vote", "");
                final int factor = ((dbtype == yacyNewsPool.OUTGOING_DB) || (dbtype == yacyNewsPool.PUBLISHED_DB)) ? 2 : 1;
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
            final plasmaSwitchboard sb,
            final HashMap<String, Entry> Supporter, final kelondroMScoreCluster<String> ranking, final kelondroRow rowdef,
            final HashMap<String, Integer> negativeHashes, final HashMap<String, Integer> positiveHashes, final int dbtype) {
        final int maxCount = Math.min(1000, sb.webIndex.newsPool.size(dbtype));
        yacyNewsRecord record;
        final Iterator<yacyNewsRecord> recordIterator = sb.webIndex.newsPool.recordIterator(dbtype, true);
        int j = 0;
        String url = "", urlhash;
        kelondroRow.Entry entry;
        int score = 0;
        Integer vote;
        yacySeed seed;
        while ((recordIterator.hasNext()) && (j++ < maxCount)) {
            record = recordIterator.next();
            if (record == null) continue;
            
            entry = null;
            if ((record.category().equals(yacyNewsPool.CATEGORY_PROFILE_UPDATE)) &&
                ((seed = sb.webIndex.seedDB.getConnected(record.originator())) != null)) try {
                url = record.attribute("homepage", "");
                if (url.length() < 12) continue;
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                url.getBytes(),
                                ("Home Page of " + seed.getName()).getBytes("UTF-8"),
                                record.id().getBytes()
                        });
                score = 1 + timeFactor(record.created());
            } catch (final IOException e) {}

            if ((record.category().equals(yacyNewsPool.CATEGORY_PROFILE_BROADCAST)) &&
                ((seed = sb.webIndex.seedDB.getConnected(record.originator())) != null)) try {
                url = record.attribute("homepage", "");
                if (url.length() < 12) continue;
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                url.getBytes(),
                                ("Home Page of " + seed.getName()).getBytes("UTF-8"),
                                record.id().getBytes()
                        });
                score = 1 + timeFactor(record.created());
            } catch (final IOException e) {}

            // add/subtract votes and write record
            if (entry != null) {
                try {
                    urlhash = (new yacyURL(url, null)).hash();
                } catch (final MalformedURLException e) {
                    urlhash = null;
                }
                if (urlhash == null)
                    try {
                        urlhash = (new yacyURL("http://" + url, null)).hash();
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
                    ranking.addScore(urlhash, score);
                } else {
                    ranking.setScore(urlhash, score);
                    Supporter.put(urlhash, entry);
                }
            }
        }
    }
}
