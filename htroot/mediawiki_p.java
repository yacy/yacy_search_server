// mediawiki.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2007 on http://yacy.net
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
import java.io.IOException;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.importer.MediawikiImporter;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class mediawiki_p {
    
    //http://localhost:8090/mediawiki_p.html?dump=wikipedia.de.xml&title=Kartoffel
    public static serverObjects respond(final RequestHeader header, serverObjects post, final serverSwitch env) throws IOException {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        prop.put("title", "");
        prop.put("page", "");
        
        if (post == null) {
            return post;
        }

        String dump = post.get("dump", null);
        String title = post.get("title", null);
        if (dump == null || title == null) return post;
        
        
        File dumpFile = new File(sb.getDataPath(), "DATA/HTCACHE/mediawiki/" + dump);
        if (!dumpFile.exists()) return post;
        MediawikiImporter.checkIndex(dumpFile);
        MediawikiImporter.wikisourcerecord w = MediawikiImporter.find(title.replaceAll(" ", "_"), MediawikiImporter.idxFromWikimediaXML(dumpFile));
        if (w == null) {
            return post;
        }
        String page = UTF8.String(MediawikiImporter.read(dumpFile, w.start, (int) (w.end - w.start)));
        int p = page.indexOf("<text");
        if (p < 0) return prop;
        p = page.indexOf('>', p);
        if (p < 0) return prop;
        p++;
        int q = page.lastIndexOf("</text>");
        if (q < 0) return prop;
        page = page.substring(p, q);
        
        prop.putHTML("title", title);
        prop.putWiki(sb.peers.mySeed().getClusterAddress(), "page", page);
            
        return prop;
    }
}
