// BlacklistTest_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
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

// You must compile this file with
// javac -classpath .:../classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.net.MalformedURLException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.repository.Blacklist;

import de.anomic.data.ListManager;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class BlacklistTest_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        
        // initialize the list manager
        ListManager.switchboard = (Switchboard) env;
        ListManager.listsPath = new File(ListManager.switchboard.getDataPath(),ListManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));

        final serverObjects prop = new serverObjects();
        prop.putHTML("blacklistEngine", Switchboard.urlBlacklist.getEngineInfo());
       
        // do all post operations            
        if(post != null && post.containsKey("testList")) {
            prop.put("testlist", "1");
            String urlstring = post.get("testurl", "");
            if (!urlstring.startsWith("http://") &&
                    !urlstring.startsWith("https://") &&
                    !urlstring.startsWith("ftp://") &&
                    !urlstring.startsWith("smb://") &&
                    !urlstring.startsWith("file://")) urlstring = "http://" + urlstring;
            DigestURI testurl = null;
            try {
                testurl = new DigestURI(urlstring);
            } catch (final MalformedURLException e) { testurl = null; }
            if(testurl != null) {
                prop.putHTML("url",testurl.toString());
                prop.putHTML("testlist_url",testurl.toString());
                if(Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, testurl))
                        prop.put("testlist_listedincrawler", "1");
                if(Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_DHT, testurl))
                        prop.put("testlist_listedindht", "1");
                if(Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_NEWS, testurl))
                        prop.put("testlist_listedinnews", "1");
                if(Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_PROXY, testurl))
                        prop.put("testlist_listedinproxy", "1");
                if(Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_SEARCH, testurl))
                        prop.put("testlist_listedinsearch", "1");
                if(Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_SURFTIPS, testurl))
                        prop.put("testlist_listedinsurftips", "1");
            }
            else {
                prop.putHTML("url",urlstring);
                prop.put("testlist", "2");
            }
        } else {
            prop.putHTML("url", "http://");
        }
        return prop;
    }

}
