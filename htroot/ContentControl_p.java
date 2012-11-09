import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class ContentControl_p {

	public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header,
			final serverObjects post, final serverSwitch env) {
		
		final serverObjects prop = new serverObjects();

		if (post != null) {

			if (post.containsKey("contentcontrolExtraSettings")) {

				env.setConfig("contentcontrol.smwimport.baseurl",
						post.get("ccsmwimporturl"));

				env.setConfig("contentcontrol.smwimport.enabled",
						"on".equals(post.get("ccsmwimport")) ? true : false);
				
				env.setConfig("contentcontrol.smwimport.purgelistoninit",
						"on".equals(post.get("ccsmwpurge")) ? true : false);
				
				env.setConfig("contentcontrol.smwimport.targetlist",
						post.get("ccsmwimportlist"));


			}

			if (post.containsKey("contentcontrolSettings")) {

				env.setConfig("contentcontrol.enabled",
						"on".equals(post.get("contentcontrolenabled")) ? true : false);
				
			
				env.setConfig("contentcontrol.bookmarklist",
						post.get("contentcontrolbml"));

			}

		}
		

		prop.putHTML("ccsmwimportlist",
				env.getConfig("contentcontrol.smwimport.targetlist", "contentcontrol"));
		
		prop.put("ccsmwpurge_checked", env.getConfigBool(
				"contentcontrol.smwimport.purgelistoninit", false) ? "1" : "0");
		
		prop.putHTML("ccsmwimporturl",
				env.getConfig("contentcontrol.smwimport.baseurl", ""));

		prop.put("ccsmwimport_checked", env.getConfigBool(
				"contentcontrol.smwimport.enabled", false) ? "1" : "0");
		

		prop.put("contentcontrolenabled_checked",
				env.getConfigBool("contentcontrol.enabled", false) ? "1" : "0");
		
		prop.putHTML("contentcontrolbml",
				env.getConfig("contentcontrol.bookmarklist", ""));

		// return rewrite properties
		return prop;
	}

}
