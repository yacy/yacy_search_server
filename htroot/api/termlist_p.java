// rwilist_p
// ------------
// (C) 2011 by Michael Peter Christen; mc@yacy.net
// first published 25.08.2011 on http://yacy.net
//
// $LastChangedDate: 2011-01-03 21:52:54 +0100 (Mo, 03 Jan 2011) $
// $LastChangedRevision: 7420 $
// $LastChangedBy: orbiter $
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

import java.util.Iterator;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.ranking.Rating;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class termlist_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        Segment segment = null;
        final boolean html = post != null && post.containsKey("html");
        final long mincount = post == null ? 10000 : post.getLong("mincount", 10000);
        if (post != null && post.containsKey("segment") && sb.verifyAuthentication(header, false)) {
            segment = sb.indexSegments.segment(post.get("segment"));
        }
        if (segment == null) segment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        final Iterator<Rating<byte[]>> i = segment.termIndex().referenceCountIterator(null, false);
        Rating<byte[]> e;
        int c = 0;
        byte[] termhash, maxterm = null;
        long count, maxcount = 0;
        while (i.hasNext()) {
            e = i.next();
            count = e.getScore();
            if (count > maxcount) {
                maxcount = count;
                maxterm = e.getObject();
            }
            if (count < mincount) continue;
            termhash = e.getObject();
            prop.put("terms_" + c + "_termhash", ASCII.String(termhash));
            prop.put("terms_" + c + "_count", count);
            c++;
        }
        prop.put("terms", c);
        prop.put("maxterm", maxterm == null ? "" : ASCII.String(maxterm));
        prop.put("maxcount", maxcount);

        // return rewrite properties
        return prop;
    }

}
