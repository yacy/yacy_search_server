package interaction_elements;


import java.util.Collection;

import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.LibraryProvider;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Tag_part {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

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
