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

    private static final BlacklistType[] BLACKLIST_TYPE_VALUES = BlacklistType.values();

    private static final String LISTS = "lists";
    private static final String POSTFIX_SHARED = "_shared";
    private static final String POSTFIX_TYPES = "_types";
    private static final String POSTFIX_VALUE = "_value";
    private static final String POSTFIX_NAME = "_name";
    private static final String POSTFIX_COMMA = "_comma";
    private static final String TYPES_EXT = ".BlackLists";
    private static final String INFIX_TYPES = "_types_";
    private static final String PREFIX_LISTS = "lists_";
    private static final String BLACK_LISTS_SHARED = "BlackLists.Shared";

    private static final int lastTypeIndex = BLACKLIST_TYPE_VALUES.length - 1;
    private static final String EMPTY_STRING = "";
    private static final String COMMA_STRING = ",";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        final serverObjects prop = new serverObjects();

        final Collection<String> dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);
        final int lastBlacklistCount = dirlist.size() - 1;

        int blacklistCount = 0;
        if (dirlist != null) {
            for (final String element : dirlist) {
                prop.putXML(PREFIX_LISTS + blacklistCount + POSTFIX_NAME, element);

                prop.put(PREFIX_LISTS + blacklistCount + POSTFIX_SHARED, ListManager.listSetContains(BLACK_LISTS_SHARED, element));

                int j = 0;
                for (final BlacklistType type : BLACKLIST_TYPE_VALUES) {
                    prop.putXML(PREFIX_LISTS + blacklistCount + INFIX_TYPES + j + POSTFIX_NAME, type.toString());
                    prop.put(PREFIX_LISTS + blacklistCount + INFIX_TYPES + j + POSTFIX_VALUE,
                            ListManager.listSetContains(type + TYPES_EXT, element));

                    if (j < lastTypeIndex) {
                        prop.put(PREFIX_LISTS + blacklistCount + INFIX_TYPES + j + POSTFIX_COMMA, COMMA_STRING);
                    } else {
                        prop.put(PREFIX_LISTS + blacklistCount + INFIX_TYPES + j + POSTFIX_COMMA, EMPTY_STRING);
                    }
                    
                    j++;
                }
                prop.put(PREFIX_LISTS + blacklistCount + POSTFIX_TYPES, BLACKLIST_TYPE_VALUES.length);

                if (blacklistCount < lastBlacklistCount) {
                    prop.put(PREFIX_LISTS + blacklistCount + POSTFIX_COMMA, COMMA_STRING);
                } else {
                    prop.put(PREFIX_LISTS + blacklistCount + POSTFIX_COMMA, EMPTY_STRING);
                }

                blacklistCount++;
            }
        }
        prop.put(LISTS, blacklistCount);

        return prop;
    }

}
