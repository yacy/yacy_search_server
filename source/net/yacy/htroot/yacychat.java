/**
 *  yacychat
 *  Copyright 2025 by Michael Peter Christen
 *  First released 23.11.2025 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.htroot;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class yacychat {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        // system prompt comes from configuration; default is empty
        final Switchboard sb = (Switchboard) env;
        final String systemPrompt = sb.getConfig("ai.system-prompt", net.yacy.http.servlets.RAGProxyServlet.LLM_SYSTEM_PROMPT_DEFAULT);
        // escape for safe embedding in a JS single-quoted string literal
        final String systemPromptJs = systemPrompt.replace("\\", "\\\\").replace("'", "\\'").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
        prop.put("system_prompt", systemPromptJs);
        prop.put("topmenu",sb.getConfigBool("ai.shield.show-chat-link", false) ? (sb.getConfigBool("publicTopmenu", true) ? 1 : 0) : 2);

        String promoteChatPageGreeting = env.getConfig("promoteChatPageGreeting", "");
        if (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) promoteChatPageGreeting = env.getConfig("network.unit.description", "");
        
        prop.put("promoteChatPageGreeting", promoteChatPageGreeting);
        prop.put("topmenu_promoteChatPageGreeting", promoteChatPageGreeting);
        prop.put(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.put("topmenu_" + SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.put(SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
        prop.put("topmenu_" + SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
        prop.put(SwitchboardConstants.GREETING_IMAGE_ALT, sb.getConfig(SwitchboardConstants.GREETING_IMAGE_ALT, ""));
        prop.put("topmenu_" + SwitchboardConstants.GREETING_IMAGE_ALT, sb.getConfig(SwitchboardConstants.GREETING_IMAGE_ALT, ""));

        // determine if P2P mode is active (global search available)
        final boolean indexReceiveGranted =
                sb.getConfigBool(net.yacy.search.SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true) ||
                (sb.isRobinsonMode() && sb.getConfig(net.yacy.search.SwitchboardConstants.CLUSTER_MODE, "").equals(net.yacy.search.SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER));
        prop.put("p2p_mode", indexReceiveGranted ? 1 : 0);

        // return rewrite properties
        return prop;
    }

}
