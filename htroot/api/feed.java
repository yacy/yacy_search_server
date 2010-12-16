

import java.util.Date;

import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.protocol.RequestHeader;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyChannel;

public class feed {
 
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // insert default values
        final serverObjects prop = new serverObjects();
        prop.put("channel_title", "");
        prop.put("channel_description", "");
        prop.put("channel_pubDate", "");
        prop.put("item", "0");

        if ((post == null) || (env == null)) return prop;
        final boolean authorized = sb.verifyAuthentication(header, false);

        final String channelNames = post.get("set");
        if (channelNames == null) return prop;
        final String[] channels = channelNames.split(","); // several channel names can be given and separated by comma

        int messageCount = 0;
        int messageMaxCount = Math.min(post.getInt("count", 100), 1000);
 
        channelIteration: for (final String channel: channels) {
            // prevent that unauthorized access to this servlet get results from private data
            if ((!authorized) && (yacyChannel.privateChannels.contains(channel))) continue channelIteration; // allow only public channels if not authorized

            if ("TEST".equals(channel)) {
                // for interface testing return at least one single result
                prop.putXML("channel_title", "YaCy News Testchannel");
                prop.putXML("channel_description", "");
                prop.put("channel_pubDate", (new Date()).toString());
                prop.putXML("item_" + messageCount + "_title", channel + ": " + "YaCy Test Entry " + (new Date()).toString());
                prop.putXML("item_" + messageCount + "_description", "abcdefg");
                prop.putXML("item_" + messageCount + "_link", "http://yacy.net");
                prop.put("item_" + messageCount + "_pubDate", (new Date()).toString());
                prop.put("item_" + messageCount + "_guid", System.currentTimeMillis());
                messageCount++;
                messageMaxCount--;
                continue channelIteration;
            }
            
            // read the channel
            final RSSFeed feed = yacyChannel.channels(channel);
            if (feed == null || feed.isEmpty()) continue channelIteration;

            RSSMessage message = feed.getChannel();
            if (message != null) {
                prop.putXML("channel_title", message.getTitle());
                prop.putXML("channel_description", message.getDescription());
                prop.put("channel_pubDate", message.getPubDate());
            }
            while (messageMaxCount > 0 && !feed.isEmpty()) {
                message = feed.pollMessage();
                if (message == null) continue;

                // create RSS entry
                prop.putXML("item_" + messageCount + "_title", channel + ": " + message.getTitle());
                prop.putXML("item_" + messageCount + "_description", message.getDescription());
                prop.putXML("item_" + messageCount + "_link", message.getLink());
                prop.put("item_" + messageCount + "_pubDate", message.getPubDate());
                prop.put("item_" + messageCount + "_guid", message.getGuid());
                messageCount++;
                messageMaxCount--;
            }
            if (messageMaxCount == 0) break channelIteration;
        }
        prop.put("item", messageCount);

        // return rewrite properties
        return prop;
    }
 
}
