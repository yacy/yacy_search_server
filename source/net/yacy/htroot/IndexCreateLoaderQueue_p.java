package net.yacy.htroot;

import java.util.Map;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.retrieval.Request;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexCreateLoaderQueue_p {

    public static serverObjects respond(
            @SuppressWarnings("unused") final RequestHeader header,
            @SuppressWarnings("unused") final serverObjects post,
            final serverSwitch env) {

        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        final Map<DigestURL, Request> map = sb.crawlQueues.activeWorkerEntries();

        if (map == null || map.isEmpty()) {
            prop.put("loader-set", "0");
            prop.put("loader-set_num", 0);
            return prop;
        }

        prop.put("loader-set", "1");

        boolean dark = true;
        int count = 0;

        for (final Request element : map.values()) {
            if (element == null || element.url() == null) continue;

            Seed initiator = sb.peers.getConnected(
                element.initiator() == null ? "" : ASCII.String(element.initiator())
            );

            prop.put("loader-set_list_" + count + "_dark", dark ? "1" : "0");
            prop.putHTML(
                "loader-set_list_" + count + "_initiator",
                initiator == null ? "proxy" : initiator.getName()
            );
            prop.put("loader-set_list_" + count + "_depth", element.depth());
            prop.put("loader-set_list_" + count + "_status", element.getStatus());
            prop.putHTML(
                "loader-set_list_" + count + "_url",
                element.url().toNormalform(true)
            );

            // REQUIRED for terminate button
            prop.put(
                "loader-set_list_" + count + "_loader_abort",
                element.url().toNormalform(true)
            );

            dark = !dark;
            count++;
        }

        prop.put("loader-set_list", count);
        prop.put("loader-set_num", count);

        return prop;
    }
}

