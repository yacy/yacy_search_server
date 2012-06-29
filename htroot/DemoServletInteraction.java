import net.yacy.cora.protocol.RequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class DemoServletInteraction {

	public static serverObjects respond(final RequestHeader header,
			final serverObjects post, final serverSwitch env) {
		// return variable that accumulates replacements
		final serverObjects prop = new serverObjects();

		prop.put("temperature", "-10°C");

		// return rewrite properties
		return prop;
	}

}
