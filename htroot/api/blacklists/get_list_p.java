package blacklists;

import java.io.File;
import java.util.Collection;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.ListManager;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class get_list_p {

    private static final String ITEMS = "items";
    private static final String POSTFIX_ITEM = "_item";
    private static final String PREFIX_ITEMS = "items_";
    private static final String SHARED = "shared";
    private static final String NAME = "name";
    private static final String TYPES = "types";
    private static final String PREFIX_TYPES = "types_";
    private static final String POSTFIX_VALUE = "_value";
    private static final String POSTFIX_NAME = "_name";
    private static final String TYPES_EXT = ".BlackLists";
    private static final String BLACK_LISTS_SHARED = "BlackLists.Shared";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
       
        final serverObjects prop = new serverObjects();

        final Collection<String> dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);

        final String blackListName = (post == null) ? "" : post.get("name", "");

        int count;
        if (dirlist != null) {
            for (final String element : dirlist) {
                if (element.equals(blackListName)) {
                    
                    prop.putXML(NAME, element);

                    prop.put(SHARED, ListManager.listSetContains(BLACK_LISTS_SHARED, element));
                    
                    int j = 0;
                    for (final BlacklistType type : BlacklistType.values()) {
                        prop.putXML(PREFIX_TYPES + j + POSTFIX_NAME, type.toString());
                        prop.put(PREFIX_TYPES + j + POSTFIX_VALUE,
                                ListManager.listSetContains(type + TYPES_EXT, element));
                        j++;
                    }
                    prop.put(TYPES, BlacklistType.values().length);
                    
                    prop.putXML(NAME, element);

                    final Collection<String> list = FileUtils.getListArray(new File(ListManager.listsPath, element));

                    count = 0;
                    for (final String entry : list){
                        if (entry.isEmpty()) continue;
                        if (entry.charAt(0) == '#') continue;

                        prop.putXML(PREFIX_ITEMS + count + POSTFIX_ITEM, entry);
                        count++;
                    }
                    prop.put(ITEMS, count);
                }
            }
        }
      
        
        return prop;
    }
    
}
