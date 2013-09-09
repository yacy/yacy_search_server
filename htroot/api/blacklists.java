
import java.io.File;
import java.util.List;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.ListManager;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class blacklists {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        final serverObjects prop = new serverObjects();

        final List<String> dirlist = FileUtils.getDirListing(ListManager.listsPath);
        int blacklistCount = 0;

        final String blackListName = (post == null) ? "" : post.get("listname", "");

        if (dirlist != null) {
            for (final String element : dirlist) {
                if ("".equals(blackListName) || element.equals(blackListName)) {
                    prop.putXML("lists_" + blacklistCount + "_name", element);

                    if (ListManager.listSetContains("BlackLists.Shared", element)) {

                        final List<String> list = FileUtils.getListArray(new File(ListManager.listsPath, element));

                        int count=0;
                        for (final String entry : list){

                            if (entry.isEmpty()) {
                                continue;
                            }
                            if (entry.charAt(0) == '#') {
                                continue;
                            }

                            prop.putXML("lists_" + blacklistCount + "_items_" + count + "_item", entry);
                            count++;
                        }
                        prop.put("lists_" + blacklistCount + "_items", count);
                        blacklistCount++;
                    }
                }
            }
        }
        prop.put("lists", blacklistCount);

        // return rewrite properties
        return prop;
    }

}
