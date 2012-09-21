import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class AugmentedBrowsingFilters_p {

	public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header,
			final serverObjects post, final serverSwitch env) {
		// return variable that accumulates replacements
		final serverObjects prop = new serverObjects();

		if (post != null) {


			if (post.containsKey("augmentationFiltersSettings")) {

				env.setConfig("augmentation.reflect", "on".equals(post
						.get("augmentedReflect")) ? true : false);

				env.setConfig("augmentation.addDoctype", "on".equals(post
						.get("augmentedAddDoctype")) ? true : false);

				env.setConfig("augmentation.reparse", "on".equals(post
						.get("augmentedReparse")) ? true : false);

				env.setConfig("interaction.overlayinteraction.enabled", "on".equals(post
						.get("overlayInteraction")) ? true : false);

			}

		}

		prop.put("augmentedReflect_checked",
				env.getConfigBool("augmentation.reflect", false) ? "1" : "0");

		prop.put("augmentedAddDoctype_checked",
				env.getConfigBool("augmentation.addDoctype", true) ? "1" : "0");

		prop.put("augmentedReparse_checked",
				env.getConfigBool("augmentation.reparse", true) ? "1" : "0");

		prop.put("overlayInteraction_checked",
				env.getConfigBool("interaction.overlayinteraction.enabled", true) ? "1" : "0");


		// return rewrite properties
		return prop;
	}

}
