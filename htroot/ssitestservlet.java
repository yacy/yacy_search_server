// ssitestservlet.java
// --------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.08.2007 on http://yacy.net
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ssitestservlet {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        //plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        int delay = 0;
        final long start = System.currentTimeMillis();

        if (post != null) {
            delay = post.getInt("delay", 1000);
        }

        // make a delay to see how the ssi loads and displays this page
        try {Thread.sleep(delay);} catch (final InterruptedException e) {}

        prop.put("delay", delay);
        prop.put("start", start);
        prop.put("stop", System.currentTimeMillis());

        return prop;
    }

}
