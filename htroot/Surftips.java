// Surftips.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2006 on http://www.anomic.de
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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaURL;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;

public class Surftips {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        
        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        prop.put("display", display);
        
        boolean showScore = ((post != null) && (post.containsKey("score")));
        
        boolean publicPage = sb.getConfigBool("publicSurftips", true);
        boolean authorizedAccess = sb.verifyAuthentication(header, false);
        if ((post != null) && (post.containsKey("publicPage"))) {
            if (!authorizedAccess) {
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            publicPage = post.get("publicPage", "0").equals("1");
            sb.setConfig("publicSurftips", publicPage);
        }
        
        if ((publicPage) || (authorizedAccess)) {
        
            // read voting
            String hash;
            if ((post != null) && ((hash = post.get("voteNegative", null)) != null)) {
                if (!sb.verifyAuthentication(header, false)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                // make new news message with voting
                HashMap map = new HashMap();
                map.put("urlhash", hash);
                map.put("vote", "negative");
                map.put("refid", post.get("refid", ""));
                yacyCore.newsPool.publishMyNews(new yacyNewsRecord(yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map));
            }
            if ((post != null) && ((hash = post.get("votePositive", null)) != null)) {
                if (!sb.verifyAuthentication(header, false)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                // make new news message with voting
                HashMap map = new HashMap();
                map.put("urlhash", hash);
                map.put("url", crypt.simpleDecode(post.get("url", ""), null));
                map.put("title", crypt.simpleDecode(post.get("title", ""), null));
                map.put("description", crypt.simpleDecode(post.get("description", ""), null));
                map.put("vote", "positive");
                map.put("refid", post.get("refid", ""));
                map.put("comment", post.get("comment", ""));
                yacyCore.newsPool.publishMyNews(new yacyNewsRecord(yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map));
            }
        
            // create surftips
            HashMap negativeHashes = new HashMap(); // a mapping from an url hash to Integer (count of votes)
            HashMap positiveHashes = new HashMap(); // a mapping from an url hash to Integer (count of votes)
            accumulateVotes(negativeHashes, positiveHashes, yacyNewsPool.INCOMING_DB);
            //accumulateVotes(negativeHashes, positiveHashes, yacyNewsPool.OUTGOING_DB);
            //accumulateVotes(negativeHashes, positiveHashes, yacyNewsPool.PUBLISHED_DB);
            kelondroMScoreCluster ranking = new kelondroMScoreCluster(); // score cluster for url hashes
            kelondroRow rowdef = new kelondroRow("String url-255, String title-120, String description-120, String refid-" + (yacyCore.universalDateShortPattern.length() + 12), kelondroNaturalOrder.naturalOrder, 0);
            HashMap surftips = new HashMap(); // a mapping from an url hash to a kelondroRow.Entry with display properties
            accumulateSurftips(surftips, ranking, rowdef, negativeHashes, positiveHashes, yacyNewsPool.INCOMING_DB);
            //accumulateSurftips(surftips, ranking, rowdef, negativeHashes, positiveHashes, yacyNewsPool.OUTGOING_DB);
            //accumulateSurftips(surftips, ranking, rowdef, negativeHashes, positiveHashes, yacyNewsPool.PUBLISHED_DB);
        
            // read out surftipp array and create property entries
            Iterator k = ranking.scores(false);
            int i = 0;
            kelondroRow.Entry row;
            String url, urlhash, refid, title, description;
            boolean voted;
            while (k.hasNext()) {
                urlhash = (String) k.next();
                if (urlhash == null) continue;
                
                row = (kelondroRow.Entry) surftips.get(urlhash);
                if (row == null) continue;
                
                url = row.getColString(0, null);
                title = row.getColString(1,"UTF-8");
                description = row.getColString(2,"UTF-8");
                if ((url == null) || (title == null) || (description == null)) continue;
                refid = row.getColString(3, null);
                voted = false;
                try {
                    voted = (yacyCore.newsPool.getSpecific(yacyNewsPool.OUTGOING_DB, yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, "refid", refid) != null) || 
                    		(yacyCore.newsPool.getSpecific(yacyNewsPool.PUBLISHED_DB, yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, "refid", refid) != null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                prop.put("surftips_results_" + i + "_authorized", (authenticated) ? 1 : 0);
                prop.put("surftips_results_" + i + "_authorized_recommend", (voted) ? 0 : 1);

				prop.put("surftips_results_" + i + "_authorized_recommend_urlhash", urlhash);
				prop.put("surftips_results_" + i + "_authorized_recommend_refid", refid);
				prop.putASIS("surftips_results_" + i + "_authorized_recommend_url", crypt.simpleEncode(url, null, 'b'));
				prop.putASIS("surftips_results_" + i + "_authorized_recommend_title", crypt.simpleEncode(title, null, 'b'));
				prop.putASIS("surftips_results_" + i + "_authorized_recommend_description", crypt.simpleEncode(description, null, 'b'));
				prop.put("surftips_results_" + i + "_authorized_recommend_display", display);
				prop.put("surftips_results_" + i + "_authorized_recommend_showScore", (showScore ? 1 : 0));

                prop.put("surftips_results_" + i + "_authorized_urlhash", urlhash);
                prop.put("surftips_results_" + i + "_url", de.anomic.data.htmlTools.replaceXMLEntities(url));
                prop.put("surftips_results_" + i + "_urlname", nxTools.shortenURLString(url, 60));
                prop.put("surftips_results_" + i + "_urlhash", urlhash);
                prop.put("surftips_results_" + i + "_title", (showScore) ? ("(" + ranking.getScore(urlhash) + ") " + title) : title);
                prop.put("surftips_results_" + i + "_description", description);
                i++;
                
                if (i >= 50) break;
            }
            prop.put("surftips_results", i);
            prop.put("surftips", 1);
        } else {
            prop.put("surftips", 0);
        }
        
        return prop;
    }

    private static int timeFactor(Date created) {
        return (int) Math.max(0, 10 - ((System.currentTimeMillis() - created.getTime()) / 24 / 60 / 60 / 1000));
    }
    
    private static void accumulateVotes(HashMap negativeHashes, HashMap positiveHashes, int dbtype) {
        int maxCount = Math.min(1000, yacyCore.newsPool.size(dbtype));
        yacyNewsRecord record;
        
        for (int j = 0; j < maxCount; j++) try {
            record = yacyCore.newsPool.get(dbtype, j);
            if (record == null) continue;
            
            if (record.category().equals(yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD)) {
                String urlhash = record.attribute("urlhash", "");
                String vote    = record.attribute("vote", "");
                int factor = ((dbtype == yacyNewsPool.OUTGOING_DB) || (dbtype == yacyNewsPool.PUBLISHED_DB)) ? 2 : 1;
                if (vote.equals("negative")) {
                    Integer i = (Integer) negativeHashes.get(urlhash);
                    if (i == null) negativeHashes.put(urlhash, new Integer(factor));
                    else negativeHashes.put(urlhash, new Integer(i.intValue() + factor));
                }
                if (vote.equals("positive")) {
                    Integer i = (Integer) positiveHashes.get(urlhash);
                    if (i == null) positiveHashes.put(urlhash, new Integer(factor));
                    else positiveHashes.put(urlhash, new Integer(i.intValue() + factor));
                }
            }
        } catch (IOException e) {e.printStackTrace();}
    }
    
    private static void accumulateSurftips(
            HashMap surftips, kelondroMScoreCluster ranking, kelondroRow rowdef,
            HashMap negativeHashes, HashMap positiveHashes, int dbtype) {
        int maxCount = Math.min(1000, yacyCore.newsPool.size(dbtype));
        yacyNewsRecord record;
        String url = "", urlhash;
        kelondroRow.Entry entry;
        int score = 0;
        Integer vote;
        for (int j = 0; j < maxCount; j++) try {
            record = yacyCore.newsPool.get(dbtype, j);
            if (record == null) continue;
            
            entry = null;
            if (record.category().equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                String intention = record.attribute("intention", "");
                url = record.attribute("startURL", "");
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                ((intention.length() == 0) ? record.attribute("startURL", "") : intention).getBytes(),
                                ("Crawl Start Point").getBytes("UTF-8"),
                                record.id().getBytes()
                        });
                score = 2 + Math.min(10, intention.length() / 4) + timeFactor(record.created());
            }
            
            if (record.category().equals(yacyNewsPool.CATEGORY_PROFILE_UPDATE)) {
                url = record.attribute("homepage", "");
                if ((url == null) || (url.length() < 12)) continue;
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                ("Home Page of " + record.attribute("nickname", "")).getBytes("UTF-8"),
                                ("Profile Update").getBytes("UTF-8"),
                                record.id().getBytes()
                        });
                score = 1 + timeFactor(record.created());
            }
            
            if (record.category().equals(yacyNewsPool.CATEGORY_BOOKMARK_ADD)) {
                url = record.attribute("url", "");
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                (record.attribute("title", "")).getBytes("UTF-8"),
                                ("Bookmark: " + record.attribute("description", "")).getBytes("UTF-8"),
                                record.id().getBytes()
                        });
                score = 8 + timeFactor(record.created());
            }
            
            if (record.category().equals(yacyNewsPool.CATEGORY_SURFTIPP_ADD)) {
                url = record.attribute("url", "");
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                (record.attribute("title", "")).getBytes("UTF-8"),
                                ("Surf Tipp: " + record.attribute("description", "")).getBytes("UTF-8"),
                                record.id().getBytes()
                        });
                score = 5 + timeFactor(record.created());
            }
            
            if (record.category().equals(yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD)) {
                if (!(record.attribute("vote", "negative").equals("positive"))) continue;
                url = record.attribute("url", "");
                entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                record.attribute("title", "").getBytes("UTF-8"),
                                record.attribute("description", "").getBytes("UTF-8"),
                                record.attribute("refid", "").getBytes()
                        });
                score = 5 + timeFactor(record.created());
            }
            
            if (record.category().equals(yacyNewsPool.CATEGORY_WIKI_UPDATE)) {
                yacySeed seed = yacyCore.seedDB.getConnected(record.originator());
                if (seed == null) seed = yacyCore.seedDB.getDisconnected(record.originator());
                if (seed != null) {
                    url = "http://" + seed.getPublicAddress() + "/Wiki.html?page=" + record.attribute("page", "");
                    entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                (record.attribute("author", "Anonymous") + ": " + record.attribute("page", "")).getBytes("UTF-8"),
                                ("Wiki Update: " + record.attribute("description", "")).getBytes("UTF-8"),
                                record.id().getBytes()
                        });
                    score = 4 + timeFactor(record.created());
                }
            }
            
            if (record.category().equals(yacyNewsPool.CATEGORY_BLOG_ADD)) {
                yacySeed seed = yacyCore.seedDB.getConnected(record.originator());
                if (seed == null) seed = yacyCore.seedDB.getDisconnected(record.originator());
                if (seed != null) {
                    url = "http://" + seed.getPublicAddress() + "/Blog.html?page=" + record.attribute("page", "");
                    entry = rowdef.newEntry(new byte[][]{
                                url.getBytes(),
                                (record.attribute("author", "Anonymous") + ": " + record.attribute("page", "")).getBytes("UTF-8"),
                                ("Blog Entry: " + record.attribute("subject", "")).getBytes("UTF-8"),
                                record.id().getBytes()
                        });
                    score = 4 + timeFactor(record.created());
                }
            }

            // add/subtract votes and write record
            if (entry != null) {
                urlhash = plasmaURL.urlHash(url);
                if (urlhash == null) {
                    System.out.println("Surftips: bad url '" + url + "' from news record " + record.toString());
                    continue;
                }
                if ((vote = (Integer) negativeHashes.get(urlhash)) != null) {
                    score = Math.max(0, score - vote.intValue()); // do not go below zero
                }
                if ((vote = (Integer) positiveHashes.get(urlhash)) != null) {
                    score += 2 * vote.intValue();
                }
                // consider double-entries
                if (surftips.containsKey(urlhash)) {
                    ranking.addScore(urlhash, score);
                } else {
                    ranking.setScore(urlhash, score);
                    surftips.put(urlhash, entry);
                }
            }
            
        } catch (IOException e) {e.printStackTrace();}
        
    }
    
    
}
