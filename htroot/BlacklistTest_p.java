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

import java.net.MalformedURLException;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class BlacklistTest_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        prop.putHTML("blacklistEngine", Blacklist.getEngineInfo());

        // do all post operations
        if(post != null && post.containsKey("testList")) {
            prop.put("testlist", "1");
            String urlstring = post.get("testurl", "");
            if (!urlstring.startsWith("http://") &&
                    !urlstring.startsWith("https://") &&
                    !urlstring.startsWith("ftp://") &&
                    !urlstring.startsWith("smb://") &&
                    !urlstring.startsWith("file://")) urlstring = "http://" + urlstring;
            DigestURL testurl = null;
            try {
                testurl = new DigestURL(urlstring);
            } catch (final MalformedURLException e) {
            	testurl = null;
            }
            if(testurl != null) {
                prop.putHTML("url",testurl.toString());
                prop.putHTML("testlist_url",testurl.toString());
                boolean isblocked = false;

                if (Switchboard.urlBlacklist.isListed(BlacklistType.CRAWLER, testurl)) {
                    prop.put("testlist_listedincrawler", "1");
                    isblocked = true;
                }
                if (Switchboard.urlBlacklist.isListed(BlacklistType.DHT, testurl)) {
                    prop.put("testlist_listedindht", "1");
                    isblocked = true;
                }
                if (Switchboard.urlBlacklist.isListed(BlacklistType.NEWS, testurl)) {
                    prop.put("testlist_listedinnews", "1");
                    isblocked = true;
                }
                if (Switchboard.urlBlacklist.isListed(BlacklistType.PROXY, testurl)) {
                    prop.put("testlist_listedinproxy", "1");
                    isblocked = true;
                }
                if (Switchboard.urlBlacklist.isListed(BlacklistType.SEARCH, testurl)) {
                    prop.put("testlist_listedinsearch", "1");
                    isblocked = true;
                }
                if (Switchboard.urlBlacklist.isListed(BlacklistType.SURFTIPS, testurl)) {
                    prop.put("testlist_listedinsurftips", "1");
                    isblocked = true;
                }

                if (!isblocked) {
                    prop.put("testlist_isnotblocked", "1");
                }
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
