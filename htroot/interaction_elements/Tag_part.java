package interaction_elements;


import java.util.Collection;

import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.LibraryProvider;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Tag_part {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();

        prop.put("hash", post.get("hash", ""));
        prop.put("url", post.get("url", ""));
        prop.put("action", post.get("action", ""));

        String vocabularies = "";

        Collection<Tagging> vocs = LibraryProvider.autotagging.getVocabularies();

        for (Tagging v: vocs) {
            vocabularies += v.getName()+",";
        }

        vocabularies += "manual";

        prop.put("vocabularies", vocabularies);

        return prop;
    }
}
