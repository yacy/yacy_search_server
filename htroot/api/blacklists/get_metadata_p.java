package blacklists;

import java.util.Collection;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.ListManager;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class get_metadata_p {

    private static final String LISTS = "lists";
    private static final String POSTFIX_SHARED = "_shared";
    private static final String POSTFIX_TYPES = "_types";
    private static final String POSTFIX_VALUE = "_value";
    private static final String POSTFIX_NAME = "_name";
    private static final String TYPES_EXT = ".BlackLists";
    private static final String INFIX_TYPES = "_types_";
    private static final String PREFIX_LISTS = "lists_";
    private static final String BLACK_LISTS_SHARED = "BlackLists.Shared";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
       
        final serverObjects prop = new serverObjects();

        final Collection<String> dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);
        int blacklistCount=0;

        if (dirlist != null) {
            for (final String element : dirlist) {
                prop.putXML(PREFIX_LISTS + blacklistCount + POSTFIX_NAME, element);

                prop.put(PREFIX_LISTS + blacklistCount + POSTFIX_SHARED, ListManager.listSetContains(BLACK_LISTS_SHARED, element));
                
                int j = 0;
                for (final BlacklistType type : BlacklistType.values()) {
                    prop.putXML(PREFIX_LISTS + blacklistCount + INFIX_TYPES + j + POSTFIX_NAME, type.toString());
                    prop.put(PREFIX_LISTS + blacklistCount + INFIX_TYPES + j + POSTFIX_VALUE,
                            ListManager.listSetContains(type + TYPES_EXT, element));
                    j++;
                }
                prop.put(PREFIX_LISTS + blacklistCount + POSTFIX_TYPES, BlacklistType.values().length);
                
                blacklistCount++;
            }
        }
        prop.put(LISTS, blacklistCount);
        
        return prop;
    }

}
