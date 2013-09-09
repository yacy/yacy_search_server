import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class DemoServletRDF {

	public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header,
			final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
		// return variable that accumulates replacements
		final serverObjects prop = new serverObjects();

//		prop.put("temperature", "-10°C");

		if (post != null) {

		if (post.containsKey("submit")) {
			prop.put("temperature", post.get("textthing"));

			String filename= post.get("textthing");

//			prop.put("imglink", filename+".jpg");

			int counter = 0;

			while (counter < 10) {

				prop.put("localimg_"+counter+"_path","/"+filename);

				prop.put("localimg_"+counter+"_checked", "2");
				counter++;
			}

			prop.put("localimg", counter);



//			prop.put("temperature",yacy.homedir+"/DATA/HTDOCS/"+filename);
		}

		}

		// return rewrite properties
		return prop;
	}

}
