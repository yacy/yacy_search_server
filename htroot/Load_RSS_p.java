/**
 *  RSSLoader_p
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 20.08.2010 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.DateFormat;

import net.yacy.cora.document.Hit;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;
import de.anomic.http.server.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Load_RSS_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;

        prop.put("showitems", 0);
        prop.put("showload", 0);
        prop.put("url", "");
        
        if (post == null) return prop;

        prop.put("url", post.get("url", ""));
        
        DigestURI url = null;
        try {
            url = post.containsKey("url") ? new DigestURI(post.get("url", ""), null) : null;
        } catch (MalformedURLException e) {
            Log.logException(e);
        }
        
        // if we have an url then try to load the rss
        RSSReader rss = null;
        if (url != null) try {
            prop.put("url", url.toNormalform(true, false));
            Response entry = sb.loader.load(sb.loader.request(url, true, false), CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE);
            byte[] resource = entry == null ? null : entry.getContent();
            rss = resource == null ? null : RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, resource);
        } catch (IOException e) {
            Log.logException(e);
        }

        if (rss != null) {
            prop.put("showitems", 1);
            RSSFeed feed = rss.getFeed();
            RSSMessage channel = feed.getChannel();
            prop.putHTML("showitems_title", channel.getTitle());
            String author = channel.getAuthor();
            if (author == null || author.length() == 0) author = channel.getCopyright();
            prop.putHTML("showitems_author", author == null ? "" : author);
            prop.putHTML("showitems_description", channel.getDescription());
            prop.putHTML("showitems_language", channel.getLanguage());
            prop.putHTML("showitems_date", DateFormat.getDateTimeInstance().format(channel.getPubDate()));
            prop.putHTML("showitems_ttl", channel.getTTL());
            prop.putHTML("showitems_docs", channel.getDocs());
            
            int i = 0;
            for (final Hit item: feed) {
                try {
                    url = new DigestURI(item.getLink(), null);
                    author = item.getAuthor();
                    if (author == null) author = item.getCopyright();
                    prop.put("showitems_item_" + i + "_count", i);
                    prop.putHTML("showitems_item_" + i + "_hash", new String(url.hash()));
                    prop.putHTML("showitems_item_" + i + "_author", author == null ? "" : author);
                    prop.putHTML("showitems_item_" + i + "_title", item.getTitle());
                    prop.putHTML("showitems_item_" + i + "_link", url.toNormalform(false, false));
                    prop.putHTML("showitems_item_" + i + "_description", item.getDescription());
                    prop.putHTML("showitems_item_" + i + "_language", item.getLanguage());
                    prop.putHTML("showitems_item_" + i + "_date", DateFormat.getDateTimeInstance().format(item.getPubDate()));
                    i++;
                } catch (MalformedURLException e) {
                    Log.logException(e);
                    continue;
                }
            }
            prop.put("showitems_item", i);
            prop.put("showitems_num", i);
            if (i > 0) prop.put("showload", 1);
        }
        
        return prop;
    }
    
}
