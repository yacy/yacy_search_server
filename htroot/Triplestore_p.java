import net.yacy.cora.lod.JenaTripleStore;
import net.yacy.cora.protocol.RequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class Triplestore_p {

	public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header,
			final serverObjects post, final serverSwitch env) {
		// return variable that accumulates replacements
		final serverObjects prop = new serverObjects();

		if (post != null) {

			if (post.containsKey("tsSettings")) {

				env.setConfig("triplestore.persistent",
						"on".equals(post.get("tspersistentenabled")) ? true : false);

//				env.setConfig("interaction.feedback.accept",
//						"on".equals(post.get("acceptfeedbackenabled")) ? true : false);

			}


		}

		prop.put("tspersistentenabled_checked",
				env.getConfigBool("triplestore.persistent", false) ? "1" : "0");

//		prop.put("acceptfeedbackenabled_checked",
//				env.getConfigBool("interaction.feedback.accept", false) ? "1" : "0");
		prop.put("size", JenaTripleStore.size());

		// return rewrite properties
		return prop;
	}

}
