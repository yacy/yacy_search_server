
import java.io.File;
import java.util.List;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist;

import de.anomic.data.ListManager;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class blacklists_p {
    
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        
        ListManager.listsPath = new File(ListManager.switchboard.getDataPath(),ListManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));
        final List<String> dirlist = FileUtils.getDirListing(ListManager.listsPath);
        int blacklistCount=0;

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

                    final String[] types = Blacklist.BLACKLIST_TYPES_STRING.split(",");
                    int j = 0;
                    for (final String type : types) {
                        prop.putXML("lists_" + blacklistCount + "_types_" + j + "_name", type);
                        prop.put("lists_" + blacklistCount + "_types_" + j + "_value",
                                ListManager.listSetContains(type + ".BlackLists", element) ? 1 : 0);
                        j++;
                    }
                    prop.put("lists_" + blacklistCount + "_types", types.length);

                    if (!"1".equals(attrOnly) && !"true".equals(attrOnly)) {
                	final List<String> list = FileUtils.getListArray(new File(ListManager.listsPath, element));

                	count=0;
                	for (final String entry : list){
                	    if (entry.length() == 0) continue;
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
