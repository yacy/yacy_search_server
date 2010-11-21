
import java.io.File;
import java.util.List;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.data.ListManager;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class blacklists {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();

        ListManager.listsPath = new File(ListManager.switchboard.getDataPath(),ListManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));
        final List<String> dirlist = FileUtils.getDirListing(ListManager.listsPath);
        int blacklistCount=0;

        final String blackListName = (post == null) ? "" : post.get("listname", "");
        
        List<String> list;
        int count;
        if (dirlist != null) {
            for (String element : dirlist) {
                if (blackListName.equals("") || element.equals(blackListName)) {
                    prop.putXML("lists_" + blacklistCount + "_name", element);

                    if (ListManager.listSetContains("BlackLists.Shared", element)) {

                        list = FileUtils.getListArray(new File(ListManager.listsPath, element));

                        count=0;
                        for (int j=0;j<list.size();++j){
                            final String nextEntry = list.get(j);

                            if (nextEntry.length() == 0) continue;
                            if (nextEntry.charAt(0) == '#') continue;

                            prop.putXML("lists_" + blacklistCount + "_items_" + count + "_item", nextEntry);
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
