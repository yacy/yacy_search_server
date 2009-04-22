import de.anomic.http.httpRequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

//dummy class
public class Help {

    public static servletProperties respond(final httpRequestHeader requestHeader, final serverObjects post, final serverSwitch<?> env) {
        final servletProperties prop = new servletProperties();
        return prop;
    }
}
