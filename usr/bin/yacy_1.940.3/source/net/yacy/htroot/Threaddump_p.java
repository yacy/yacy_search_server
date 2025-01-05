// Threaddump_p.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Fieger
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

package net.yacy.htroot;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.ThreadDump;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Threaddump_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        final boolean plain = post != null && post.getBoolean("plain");
        final int sleep = (post == null) ? 0 : post.getInt("sleep", 0); // a sleep before creation of a thread dump can be used for profiling
        if (sleep > 0) try {Thread.sleep(sleep);} catch (final InterruptedException e) {}
        prop.put("dump", "1");

        int multipleCount = 100;
        final boolean multiple = post != null && post.containsKey("multipleThreaddump");
        if (multiple) {
            multipleCount = post.getInt("count", multipleCount);
        }

        final String threaddump = ThreadDump.threaddump(sb, plain, sleep, multiple, multipleCount);
        prop.put("plain_count", multipleCount);
        prop.put("plain_content", threaddump);
        prop.put("plain", (plain) ? 1 : 0);

        return prop;
    }

}
