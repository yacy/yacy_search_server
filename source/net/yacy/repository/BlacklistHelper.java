package net.yacy.repository;

import net.yacy.cora.document.id.Punycode.PunycodeException;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.ListManager;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.query.SearchEventCache;

public final class BlacklistHelper {

	/** Used for logging. */
    public static final String APP_NAME = "Blacklist";
	
	/** Private constructor to avoid instantiation of static helper class. */
	private BlacklistHelper() {
	}

    /**
     * Adds a new entry to the chosen blacklist.
     * @param blacklistToUse the name of the blacklist the entry is to be added to
     * @param newEntry the entry that is to be added
     * @param header
     * @param supportedBlacklistTypes
     * @return null if no error occurred, else a String to put into LOCATION
     */
	public static String addBlacklistEntry(
	        final String blacklistToUse,
	        final String entry,
	        final RequestHeader header) {
    	String newEntry = entry;

        if (blacklistToUse == null || blacklistToUse.isEmpty()) {
            return "";
        }

        if (newEntry == null || newEntry.isEmpty()) {
            return header.get(HeaderFramework.CONNECTION_PROP_PATH) + "?selectList=&selectedListName=" + blacklistToUse;
        }

        // ignore empty entries
        if(newEntry == null || newEntry.isEmpty()) {
            ConcurrentLog.warn(APP_NAME, "skipped adding an empty entry");
            return "";
        }

        if (newEntry.startsWith("http://") ){
            newEntry = newEntry.substring(7);
        } else if (newEntry.startsWith("https://")) {
            newEntry = newEntry.substring(8);
        }

        if (newEntry.indexOf("*") < 0) {
            // user did not use any wild cards and just submitted a word

            newEntry = ".*" + newEntry + ".*/.*";
            newEntry =  ".*.*/.*" + newEntry + ".*";

        } else {

            int pos = newEntry.indexOf('/',0);
            if (pos < 0) {
                // add default empty path pattern
                newEntry = newEntry + "/.*";
            }
        }

        int pos = newEntry.indexOf('/',0);
        String host = newEntry.substring(0, pos);
        String path = newEntry.substring(pos + 1);
        
        for (final BlacklistType supportedBlacklistType : BlacklistType.values()) {
        	if (ListManager.listSetContains(supportedBlacklistType + ".BlackLists", blacklistToUse)) {
            	try {
                    Switchboard.urlBlacklist.add(supportedBlacklistType, blacklistToUse, host, path);
                } catch (PunycodeException e) {
                    ConcurrentLog.warn(APP_NAME, "Unable to add blacklist entry to blacklist " + supportedBlacklistType, e);
                }
            }
        }
        
        SearchEventCache.cleanupEvents(true);
        return null;
    }
	
    /**
     * Deletes a blacklist entry.
     * @param blacklistToUse the name of the blacklist the entry is to be deleted from
     * @param entry the entry that is to be deleted
     * @param header
     * @param supportedBlacklistTypes
     * @return null if no error occurred, else a String to put into LOCATION
     */
    public static String deleteBlacklistEntry(
            final String blacklistToUse,
            final String entry,
            final RequestHeader header) {
    	String oldEntry = entry;

        if (blacklistToUse == null || blacklistToUse.isEmpty()) {
            return "";
        }

        if (oldEntry == null || oldEntry.isEmpty()) {
            return header.get(HeaderFramework.CONNECTION_PROP_PATH) + "?selectList=&selectedListName=" + blacklistToUse;
        }


        // remove the entry from the running blacklist engine
        int pos = oldEntry.indexOf('/',0);
        String host = oldEntry.substring(0, pos);
        String path = "";
        if (pos > 0) {
            path = oldEntry.substring(pos + 1);
        }
        
        for (final BlacklistType supportedBlacklistType : BlacklistType.values()) {
        	if (ListManager.listSetContains(supportedBlacklistType + ".BlackLists",blacklistToUse)) {
            	Switchboard.urlBlacklist.remove(supportedBlacklistType, blacklistToUse, host, path);
            }
        }
        
        SearchEventCache.cleanupEvents(true);
        return null;
    }
	
}
