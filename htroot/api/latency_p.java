// latency_p.java
// ------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 19.03.2009 on http://yacy.net
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

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.data.Latency;
import net.yacy.crawler.data.Latency.Host;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class latency_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        //final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final Iterator<Map.Entry<String, Host>> i = Latency.iterator();
        Map.Entry<String, Host> e;
        int c = 0;
        Latency.Host host;
        ClientIdentification.Agent agent = post == null ? ClientIdentification.yacyInternetCrawlerAgent : ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
        while (i.hasNext()) {
            e = i.next();
            host = e.getValue();
            prop.putXML("domains_" + c + "_hosthash", e.getKey());
            prop.putXML("domains_" + c + "_host", host.host());
            prop.putXML("domains_" + c + "_lastaccess", GenericFormatter.SHORT_SECOND_FORMATTER.format(new Date(host.lastacc())));
            prop.put("domains_" + c + "_count", host.count());
            prop.put("domains_" + c + "_average", host.average());
            prop.put("domains_" + c + "_robots", host.robotsDelay());
            prop.put("domains_" + c + "_flux", host.flux(agent.minimumDelta));
            c++;
        }
        prop.put("domains", c);

        // return rewrite properties
        return prop;
    }

}
