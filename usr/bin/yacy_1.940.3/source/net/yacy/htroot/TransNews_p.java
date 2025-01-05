// TransNews_p.java
//
// This is a part of YaCy, a peer-to-peer based web search engine
// published on http://yacy.net
//
// This file is contributed by Burkhard Buelte
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

package net.yacy.htroot;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.peers.NewsDB;
import net.yacy.peers.NewsPool;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;
import net.yacy.utils.translation.TranslationManager;

public class TransNews_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final String currentlang = sb.getConfig("locale.language", "default");
        prop.put("currentlang", currentlang);

        if ("default".equals(currentlang) || "browser".equals(currentlang)) {
            prop.put("errmsg", 1); // msg: activate diff lng
            prop.put("transsize", 0);
            return prop;
        }
		prop.put("errmsg", 0);

        TranslationManager transMgr = new TranslationManager();
        final File locallangFile = transMgr.getScratchFile(new File(currentlang + ".lng"));
        final Map<String, Map<String, String>> localTrans = transMgr.loadTranslationsLists(locallangFile);
        // calculate size of local translations list
        int size = 0;
        for (final Map<String, String> lst : localTrans.values()) {
            size += lst.size();
        }
        prop.put("transsize", size);


        // read voting
        if ((post != null) && post.containsKey("publishtranslation")) {
            final Iterator<String> filenameit = localTrans.keySet().iterator();
            int msgcounter = 0;
            while (filenameit.hasNext()) {
                final String file = filenameit.next();
                final Map<String, String> tmptrans = localTrans.get(file);
                for (final String sourcetxt : tmptrans.keySet()) {
                    final String targettxt = tmptrans.get(sourcetxt);
                    if (targettxt != null && !targettxt.isEmpty()) {
                        boolean sendit = true;
                        // check if already published (in newsPool)
                        final Iterator<NewsDB.Record> it = sb.peers.newsPool.recordIterator(NewsPool.INCOMING_DB);
                        while (it.hasNext()) {
                            final NewsDB.Record rtmp = it.next();
                            if (rtmp == null) {
                                continue;
                            }
                            if (NewsPool.CATEGORY_TRANSLATION_ADD.equals(rtmp.category())) {
                                final String tmplng = rtmp.attribute("language", null);
                                final String tmpfile = rtmp.attribute("file", null);
                                final String tmpsource = rtmp.attribute("source", null);
                                //String tmptarget = rtmp.attribute("target", null);

                                // if news with file and source exist (maybe from other peer) - skip sending another msg (to avoid confusion)
                                if ((tmplng != null && tmplng.equals(currentlang)) && (tmpfile != null && tmpfile.equals(file))
                                        && (tmpsource != null && tmpsource.equals(sourcetxt))) {
                                    sendit = false;
                                    break;
                                }

                            }
                        }
                        if (sendit) {
                            final HashMap<String, String> map = new HashMap<String, String>();
                            map.put("language", currentlang);
                            map.put("file", file);
                            map.put("source", sourcetxt);
                            map.put("target", targettxt);
                            map.put("#", Integer.toString(msgcounter++));
                            sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_TRANSLATION_ADD, map);
                        }
                    }
                }
            }
        }
        String refid;
        if ((post != null) && ((refid = post.get("voteNegative", null)) != null)) {

            // make new news message with voting
            if (!sb.isRobinsonMode()) {
                final HashMap<String, String> map = new HashMap<String, String>();
                map.put("language", currentlang);
                map.put("file", crypt.simpleDecode(post.get("filename", "")));
                map.put("source", crypt.simpleDecode(post.get("source", "")));
                map.put("target", crypt.simpleDecode(post.get("target", "")));
                map.put("vote", "negative");
                map.put("refid", refid);
                sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_TRANSLATION_VOTE_ADD, map);
                try {
                    sb.peers.newsPool.moveOff(NewsPool.INCOMING_DB, refid);
                } catch (IOException | SpaceExceededException ex) {
                }
            }
        }

        if ((post != null) && ((refid = post.get("votePositive", null)) != null)) {

            final String filename = post.get("filename");

            final File lngfile = new File(sb.getAppPath("locale.source", "locales"), currentlang + ".lng");
            transMgr = new TranslationManager(lngfile); // load full language for check if entry is new (globally)
            if (transMgr.addTranslation(filename, post.get("source"), post.get("target"))) {
                // add to local translation extension
                transMgr.addTranslation(localTrans, filename, post.get("source"), post.get("target"));
                transMgr.saveAsLngFile(currentlang, locallangFile, localTrans); // save local-trans to local-file
                transMgr.translateFile(filename); // ad-hoc translate file with new/added text
            } // TODO: shall we post voting if translation is not new ?

            // make new news message with voting
            final HashMap<String, String> map = new HashMap<String, String>();
            map.put("language", currentlang);
            map.put("file", crypt.simpleDecode(filename));
            map.put("source", crypt.simpleDecode(post.get("source", "")));
            map.put("target", crypt.simpleDecode(post.get("target", "")));
            map.put("vote", "positive");
            map.put("refid", refid);
            sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_TRANSLATION_VOTE_ADD, map);
            try {
                sb.peers.newsPool.moveOff(NewsPool.INCOMING_DB, refid);
            } catch (IOException | SpaceExceededException ex) {
            }

        }

        // create Translation voting list
        final HashMap<String, Integer> negativeHashes = new HashMap<String, Integer>(); // a mapping from an url hash to Integer (count of votes)
        final HashMap<String, Integer> positiveHashes = new HashMap<String, Integer>(); // a mapping from an url hash to Integer (count of votes)
        accumulateVotes(sb, negativeHashes, positiveHashes, NewsPool.INCOMING_DB);
        final ScoreMap<String> ranking = new ConcurrentScoreMap<String>(); // score cluster for url hashes
        final HashMap<String, NewsDB.Record> translation = new HashMap<String, NewsDB.Record>(); // a mapping from an url hash to a kelondroRow.Entry with display properties
        accumulateTranslations(sb, translation, ranking, negativeHashes, positiveHashes, NewsPool.INCOMING_DB);

        // read out translation-news array and create property entries
        final Iterator<String> k = ranking.keys(false);
        int i = 0;
        NewsDB.Record row;
        String filename;
        String source;
        String target;

        while (k.hasNext()) {

            refid = k.next();
            if (refid == null) {
                continue;
            }

            row = translation.get(refid);
            if (row == null) {
                continue;
            }

            final String lang = row.attribute("language", null);
            filename = row.attribute("file", null);
            source = row.attribute("source", null);
            target = row.attribute("target", null);
            if ((lang == null) || (filename == null) || (source == null) || (target == null)) {
                continue;
            }

            if (!lang.equals(currentlang)) continue;

            String existingtarget = null; //transMgr.getTranslation(filename, source);
            final Map<String, String> tmpMap = localTrans.get(filename);
            if (tmpMap != null) {
                existingtarget = tmpMap.get(source);
            }

            final boolean altexist = existingtarget != null && !target.isEmpty() && !existingtarget.isEmpty() && !existingtarget.equals(target);

            prop.put("results_" + i + "_refid", refid);
            prop.put("results_" + i + "_url", filename); // url to local file
            prop.put("results_" + i + "_targetlanguage", lang);
            prop.put("results_" + i + "_filename", filename);
            prop.putHTML("results_" + i + "_source", source);
            prop.putHTML("results_" + i + "_target", target);
            prop.put("results_" + i + "_existing", altexist);
            prop.putHTML("results_" + i + "_existing_target", existingtarget);
            prop.put("results_" + i + "_score", ranking.get(refid));
            prop.put("results_" + i + "_peername", sb.peers.get(row.originator()).getName());
            i++;

            if (i >= 50) {
                break;
            }
        }
        prop.put("results", i);

        return prop;
    }

    private static void accumulateVotes(final Switchboard sb, final HashMap<String, Integer> negativeHashes, final HashMap<String, Integer> positiveHashes, final int dbtype) {
        final int maxCount = Math.min(1000, sb.peers.newsPool.size(dbtype));
        NewsDB.Record newsrecord;
        final Iterator<NewsDB.Record> recordIterator = sb.peers.newsPool.recordIterator(dbtype);
        int j = 0;
        while ((recordIterator.hasNext()) && (j++ < maxCount)) {
            newsrecord = recordIterator.next();
            if (newsrecord == null) {
                continue;
            }

            if (newsrecord.category().equals(NewsPool.CATEGORY_TRANSLATION_VOTE_ADD)) {
                final String refid = newsrecord.attribute("refid", "");
                final String vote = newsrecord.attribute("vote", "");
                final int factor = ((dbtype == NewsPool.OUTGOING_DB) || (dbtype == NewsPool.PUBLISHED_DB)) ? 2 : 1;
                if (vote.equals("negative")) {
                    final Integer i = negativeHashes.get(refid);
                    if (i == null) {
                        negativeHashes.put(refid, Integer.valueOf(factor));
                    } else {
                        negativeHashes.put(refid, Integer.valueOf(i.intValue() + factor));
                    }
                }
                if (vote.equals("positive")) {
                    final Integer i = positiveHashes.get(refid);
                    if (i == null) {
                        positiveHashes.put(refid, Integer.valueOf(factor));
                    } else {
                        positiveHashes.put(refid, Integer.valueOf(i.intValue() + factor));
                    }
                }
            }
        }
    }

    private static void accumulateTranslations(
            final Switchboard sb,
            final HashMap<String, NewsDB.Record> translationmsg, final ScoreMap<String> ranking,
            final HashMap<String, Integer> negativeHashes, final HashMap<String, Integer> positiveHashes, final int dbtype) {
        final int maxCount = Math.min(1000, sb.peers.newsPool.size(dbtype));
        NewsDB.Record newsrecord;
        final Iterator<NewsDB.Record> recordIterator = sb.peers.newsPool.recordIterator(dbtype);
        int j = 0;
        String refid = "";
        String targetlanguage ="";
        String filename="";
        String source="";
        String target="";

        int score = 0;
        Integer vote;

        while ((recordIterator.hasNext()) && (j++ < maxCount)) {
            newsrecord = recordIterator.next();
            if (newsrecord == null) {
                continue;
            }

            if ((newsrecord.category().equals(NewsPool.CATEGORY_TRANSLATION_ADD))
                    && ((sb.peers.get(newsrecord.originator())) != null)) {
                refid = newsrecord.id();
                targetlanguage = newsrecord.attribute("language", "");
                filename = newsrecord.attribute("file", "");
                source = newsrecord.attribute("source", "");
                target = newsrecord.attribute("target", "");
                if (refid.isEmpty() || targetlanguage.isEmpty() || filename.isEmpty() || source.isEmpty() || target.isEmpty()) {
                    continue;
                }
                score = 0;
            }

            // add/subtract votes and write record

            if ((vote = negativeHashes.get(refid)) != null) {
                score -= vote.intValue();
            }
            if ((vote = positiveHashes.get(refid)) != null) {
                score += vote.intValue();
            }
            // consider double-entries
            if (translationmsg.containsKey(refid)) {
                ranking.inc(refid, score);
            } else {
                ranking.set(refid, score);
                translationmsg.put(refid, newsrecord);
            }

        }
    }
}
