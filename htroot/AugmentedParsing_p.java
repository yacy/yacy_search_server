import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class AugmentedParsing_p {

	public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
		// return variable that accumulates replacements
		final serverObjects prop = new serverObjects();

		if (post != null) {

			if (post.containsKey("augmentedparserSettings")) {


				env.setConfig("parserAugmentation",
						"on".equals(post.get("augmentedparserenabled")) ? true : false);

			}

		}

		prop.put("augmentedparserenabled_checked",
				env.getConfigBool("parserAugmentation", false) ? "1" : "0");

		// return rewrite properties
		return prop;
	}

}
