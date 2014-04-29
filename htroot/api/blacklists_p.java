
import java.io.File;
import java.util.List;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.ListManager;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class blacklists_p {


    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        final serverObjects prop = new serverObjects();

        final List<String> dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);
        int blacklistCount = 0;

        final String blackListName = (post == null) ? "" : post.get("listname", "");
        final String attrOnly = (post == null) ? "" : post.get("attrOnly", "");

        int count;
        if (dirlist != null) {
            for (final String element : dirlist) {
                if ("".equals(blackListName) || element.equals(blackListName)) {
                    prop.putXML("lists_" + blacklistCount + "_name", element);

                    if (ListManager.listSetContains("BlackLists.Shared", element)) {
                        prop.put("lists_" + blacklistCount + "_shared", "1");
                    } else {
                        prop.put("lists_" + blacklistCount + "_shared", "0");
                    }

                    int j = 0;
                    for (final BlacklistType type : BlacklistType.values()) {
                        prop.putXML("lists_" + blacklistCount + "_types_" + j + "_name", type.toString());
                        prop.put("lists_" + blacklistCount + "_types_" + j + "_value",
                                ListManager.listSetContains(type + ".BlackLists", element) ? 1 : 0);
                        j++;
                    }
                    prop.put("lists_" + blacklistCount + "_types", BlacklistType.values().length);

                    if (!"1".equals(attrOnly) && !"true".equals(attrOnly)) {
                	final List<String> list = FileUtils.getListArray(new File(ListManager.listsPath, element));

                	count=0;
                	for (final String entry : list){
                	    if (entry.isEmpty()) continue;
                	    if (entry.charAt(0) == '#') continue;

                	    prop.putXML("lists_" + blacklistCount + "_items_" + count + "_item", entry);
                	    count++;
                	}
                	prop.put("lists_" + blacklistCount + "_items", count);
                    }
                    blacklistCount++;
                }
            }
        }
        prop.put("lists", blacklistCount);


        // return rewrite properties
        return prop;
    }

}
