
package net.yacy.htroot.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.http.servlets.YaCyDefaultServlet;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ynetSearch {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final boolean isAdmin=sb.verifyAuthentication(header);
        final serverObjects prop = new serverObjects();

        if(post != null){
            if(!isAdmin){
            // force authentication if desired
                if(post.containsKey("login")){
                    prop.authenticationRequired();
                }
                return prop;
            }
            InputStream is = null;
            try {
                String searchaddress = post.get("url");
                if (!searchaddress.startsWith("http://") && !searchaddress.startsWith("https://")) {
                    // a relative path .. this addresses the local peer
                    searchaddress = YaCyDefaultServlet.getContext(header, sb) + ((searchaddress.length() > 0 && searchaddress.charAt(0) == '/') ? "" : "/") + searchaddress;
                }
                post.remove("url");
                post.remove("login");
                final Iterator <Map.Entry<String, String>> it = post.entrySet().iterator();
                String s = searchaddress;
                Map.Entry<String, String> k;
                while(it.hasNext()) {
                    k = it.next();
                    s = s + "&" + k.getKey() + "=" + k.getValue();
                }
                // final String s = searchaddress+"&query="+post.get("search")+"&maximumRecords="+post.get("maximumRecords")+"&startRecord="+post.get("startRecord");
                final URL url = new URI(s).toURL();
                is = url.openStream();
                Scanner scanner = new Scanner(is);
                final String httpout = scanner.useDelimiter( "\\Z" ).next();
                scanner.close();
                prop.put("http", httpout);
            } catch (final Exception e ) {
                prop.put("url", "error!");
            } finally {
                if ( is != null )
                    try { is.close(); } catch (final IOException e ) { }
            }
        }
        return prop;
    }
}