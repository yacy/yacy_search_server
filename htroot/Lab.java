import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Lab {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        //XXX: Should we use Constants like DEFAULT_PAGE, PAGE_WITHOUT_MENU and so on,
        //or is it nice enough to set the real path in the servlets?
        prop.put("SUPERTEMPLATE", "/env/page.html");
        return prop;
    }
}
