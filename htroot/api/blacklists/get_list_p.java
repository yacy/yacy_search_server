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

    private static final BlacklistType[] BLACKLIST_TYPE_VALUES = BlacklistType.values();

    private static final String KEY_CURRENT_BLACKLIST = "list";
    private static final String ITEMS = "items";
    private static final String POSTFIX_ITEM = "_item";
    private static final String PREFIX_ITEMS = "items_";
    private static final String SHARED = "shared";
    private static final String NAME = "name";
    private static final String TYPES = "types";
    private static final String PREFIX_TYPES = "types_";
    private static final String POSTFIX_VALUE = "_value";
    private static final String POSTFIX_NAME = "_name";
    private static final String POSTFIX_COMMA = "_comma";
    private static final String TYPES_EXT = ".BlackLists";
    private static final String BLACK_LISTS_SHARED = "BlackLists.Shared";

    private static final int lastTypeIndex = BLACKLIST_TYPE_VALUES.length - 1;
    private static final String EMPTY_STRING = "";
    private static final String COMMA_STRING = ",";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
       
        final serverObjects prop = new serverObjects();

        final Collection<String> dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);

        final String blackListName = (post == null) ? "" : post.get(KEY_CURRENT_BLACKLIST, "");

        if (dirlist != null) {
            for (final String element : dirlist) {
                if (element.equals(blackListName)) {
                    
                    prop.putXML(NAME, element);

                    prop.put(SHARED, ListManager.listSetContains(BLACK_LISTS_SHARED, element));
                    
                    int j = 0;
                    for (final BlacklistType type : BLACKLIST_TYPE_VALUES) {
                        prop.putXML(PREFIX_TYPES + j + POSTFIX_NAME, type.toString());
                        prop.put(PREFIX_TYPES + j + POSTFIX_VALUE,
                                ListManager.listSetContains(type + TYPES_EXT, element));
                        
                        if (j < lastTypeIndex) {
                            prop.put(PREFIX_TYPES + j + POSTFIX_COMMA, COMMA_STRING);
                        } else {
                            prop.put(PREFIX_TYPES + j + POSTFIX_COMMA, EMPTY_STRING);
                        }
                        
                        j++;
                    }
                    prop.put(TYPES, BlacklistType.values().length);
                    
                    prop.putXML(NAME, element);

                    final Collection<String> list = FileUtils.getListArray(new File(ListManager.listsPath, element));

                    int count = 0;
                    final int lastItemCount = list.size() - 1;
                    for (final String entry : list){
                        if (entry.isEmpty()) continue;
                        if (entry.charAt(0) == '#') continue;

                        prop.putXML(PREFIX_ITEMS + count + POSTFIX_ITEM, entry);
                        
                        if (count < lastItemCount) {
                            prop.put(PREFIX_ITEMS + count + POSTFIX_COMMA, COMMA_STRING);
                        } else {
                            prop.put(PREFIX_ITEMS + count + POSTFIX_COMMA, EMPTY_STRING);
                        }
                        
                        count++;
                    }
                    prop.put(ITEMS, count);
                }
            }
        }
      
        
        return prop;
    }
    
}
