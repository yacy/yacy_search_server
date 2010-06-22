//ViewFile.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004

//last major change: 12.07.2004

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//you must compile this file with
//javac -classpath .:../Classes Status.java
//if the shell's current path is HTROOT

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;

import net.yacy.document.Document;
import net.yacy.document.ParserException;
import net.yacy.document.parser.rssParser;
import net.yacy.kelondro.data.meta.DigestURI;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;
import de.anomic.http.server.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class RSSLoader_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;
        
        if (post == null) {
            return prop;
        }
        
        DigestURI url = null;
        
        final String urlString = post.get("url", "");
        if (urlString.length() > 0) try {
            url = new DigestURI(urlString, null);
        } catch (final MalformedURLException e) {
            return prop;
        }
        
        
        // if the resource body was not cached we try to load it from web
        Response entry = null;
        try {
            entry = sb.loader.load(sb.loader.request(url, true, false), CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE);
        } catch (final Exception e) {
            return prop;
        }
        if (entry == null) return prop;

        byte[] resource = entry.getContent();

        if (resource == null) {
            return prop;
        }
        
        // now parse the content as rss
        ByteArrayInputStream bais = new ByteArrayInputStream(resource);
        rssParser parser = new rssParser();
        Document doc;
        try {
            doc = parser.parse(url, "text/rss", "UTF-8", bais);
        } catch (ParserException e) {
            return prop;
        } catch (InterruptedException e) {
            return prop;
        }
        
        // get the links out of the rss
        //Map<DigestURI, String> map = doc.getAnchors();
        
        // put the urls into crawler using the proxy profile
        
        
        
        return prop;
    }
    
}
