import net.yacy.cora.protocol.RequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class AugmentedBrowsingFilters_p {

	public static serverObjects respond(final RequestHeader header,
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
				
//				env.setConfig("augmentation.reparse.adduniqueid", "on".equals(post
//						.get("augmentedReparseAdduniqueid")) ? true : false);
//				
//				env.setConfig("augmentation.reparse.addserverinfo", "on".equals(post
//						.get("augmentedReparseAddserver")) ? true : false);
//				
//				env.setConfig("augmentation.reparse.addrdf", "on".equals(post
//						.get("augmentedReparseAddrdf")) ? true : false);

			}

		}

		prop.put("augmentedReflect_checked",
				env.getConfigBool("augmentation.reflect", false) ? "1" : "0");
		
		prop.put("augmentedAddDoctype_checked",
				env.getConfigBool("augmentation.addDoctype", true) ? "1" : "0");
		
		prop.put("augmentedReparse_checked",
				env.getConfigBool("augmentation.reparse", true) ? "1" : "0");
		
//		prop.put("augmentedReparseAdduniqueid_checked",
//				env.getConfigBool("augmentation.reparse.adduniqueid", true) ? "1" : "0");
//		
//		prop.put("augmentedReparseAddserver_checked",
//				env.getConfigBool("augmentation.reparse.addserverinfo", true) ? "1" : "0");
//		
//		prop.put("augmentedReparseAddrdf_checked",
//				env.getConfigBool("augmentation.reparse.addrdf", true) ? "1" : "0");
		


		// return rewrite properties
		return prop;
	}

}
