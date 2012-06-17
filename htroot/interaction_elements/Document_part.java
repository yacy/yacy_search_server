package interaction_elements;


import java.util.Collection;

import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.LibraryProvider;
import net.yacy.search.Switchboard;
import de.anomic.data.UserDB;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Document_part {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
    	
    	final Switchboard sb = (Switchboard) env;
    	
        final serverObjects prop = new serverObjects();
        
        prop.put("hash", post.get("hash", ""));
        prop.put("url", post.get("url", ""));
        
        return prop;
    }
}
