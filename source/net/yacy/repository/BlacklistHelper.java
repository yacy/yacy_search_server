package net.yacy.repository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yacy.cora.document.id.Punycode.PunycodeException;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.ListManager;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.query.SearchEventCache;

public final class BlacklistHelper {

	/** Used for logging. */
    public static final String APP_NAME = "Blacklist";
    
    /** Pattern to identify the eventual URL scheme (protocol) part. 
     * Examples that will be recognized : "http://", "https://", "ftp://", "^https?://", "anyprotocol://" */
    private static final Pattern URL_SCHEME_PATTERN = Pattern.compile("(^\\^?[a-z\\?]+://).+");
    
	/** Private constructor to avoid instantiation of static helper class. */
	private BlacklistHelper() {
	}
	
	/**
	 * @param entry a blacklist entry. Must not be null.
	 * @return the entry eventually modified to be ready to use by the Blacklist engine
	 */
	protected static String prepareEntry(final String entry) {
		String newEntry = entry;
    	/* Remove the eventual unnecessary Regex line beginning char '^' and URL scheme (protocol) part */
    	Matcher schemeMatcher = URL_SCHEME_PATTERN.matcher(newEntry);
    	if(schemeMatcher.matches()) {
    		newEntry = newEntry.substring(schemeMatcher.end(1));
    	}

        if (newEntry.indexOf("*") < 0 && newEntry.indexOf("?") < 0 && newEntry.indexOf("+") < 0) {
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
        return newEntry;
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

    	String location = null;
        if (blacklistToUse == null || blacklistToUse.isEmpty()) {
        	location = header.getPathInfo();
        } else if (newEntry == null || newEntry.isEmpty()) {
        	location = header.getPathInfo() + "?selectList=&selectedListName=" + blacklistToUse;
        }
        
    	if(location != null) {
    		if(location.startsWith("/")) {
    	    	/* Remove the starting "/" to redirect to a relative location for easier reverse proxy integration */
    			location = location.substring(1, location.length() -1 );
    		}
            return location;
    	}
    	
    	newEntry = prepareEntry(newEntry);

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

    	String location = null;
        if (blacklistToUse == null || blacklistToUse.isEmpty()) {
        	location = header.getPathInfo();
        } else if (oldEntry == null || oldEntry.isEmpty()) {
            location =  header.getPathInfo() + "?selectList=&selectedListName=" + blacklistToUse;
        }
        
    	if(location != null) {
    		if(location.startsWith("/")) {
    	    	/* Remove the starting "/" to redirect to a relative location for easier reverse proxy integration */
    			location = location.substring(1, location.length() -1 );
    		}
            return location;
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
