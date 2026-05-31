package net.yacy.htroot;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class yacychat {

 public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
    // return variable that accumulates replacements
    final Switchboard sb = (Switchboard) env;

    final serverObjects prop = new serverObjects();
    String body = post == null ? "" : post.get("BODY", "");
    JSONObject bodyj = new JSONObject();
    if (body.length() > 0) {
        try {
            bodyj = new JSONObject(new JSONTokener(body));
        } catch (JSONException e) {
            // silently catch this
        }
    }

    // system prompt comes from configuration; default is empty
    final String systemPrompt = sb.getConfig("ai.system-prompt", net.yacy.http.servlets.RAGProxyServlet.LLM_SYSTEM_PROMPT_DEFAULT);
    // escape for safe embedding in a JS single-quoted string literal
    final String systemPromptJs = systemPrompt
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n");
    prop.put("system_prompt", systemPromptJs);
    prop.put("topmenu", sb.getConfigBool("ai.shield.show-chat-link", false) ? (sb.getConfigBool("publicTopmenu", true) ? 1 : 0) : 2);

    String promoteChatPageGreeting = env.getConfig("promoteChatPageGreeting", "");
    if (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) {
        promoteChatPageGreeting = env.getConfig("network.unit.description", "");
    }
    prop.put("promoteChatPageGreeting", promoteChatPageGreeting);
    prop.put("topmenu_promoteChatPageGreeting", promoteChatPageGreeting);
    prop.put(SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
    prop.put("topmenu_" + SwitchboardConstants.GREETING_HOMEPAGE, sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
    prop.put(SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
    prop.put("topmenu_" + SwitchboardConstants.GREETING_LARGE_IMAGE, sb.getConfig(SwitchboardConstants.GREETING_LARGE_IMAGE, ""));
    prop.put(SwitchboardConstants.GREETING_IMAGE_ALT, sb.getConfig(SwitchboardConstants.GREETING_IMAGE_ALT, ""));
    prop.put("topmenu_" + SwitchboardConstants.GREETING_IMAGE_ALT, sb.getConfig(SwitchboardConstants.GREETING_IMAGE_ALT, ""));

    // determine if P2P mode is active (global search available)
    final boolean indexReceiveGranted = sb.getConfigBool(net.yacy.search.SwitchboardConstants.INDEX_RECEIVE_ALLOW_SEARCH, true) || (sb.isRobinsonMode() && sb.getConfig(net.yacy.search.SwitchboardConstants.CLUSTER_MODE, "").equals(net.yacy.search.SwitchboardConstants.CLUSTER_MODE_PUBLIC_CLUSTER));
    final boolean p2pmode = indexReceiveGranted;
    prop.put("p2p_mode", p2pmode ? 1 : 0);

    // return rewrite properties
    return prop;
 }

}
