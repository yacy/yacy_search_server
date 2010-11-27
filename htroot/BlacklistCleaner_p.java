// BlacklistCleaner_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Franz Brausze
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

// You must compile this file with
// javac -classpath .:../classes BlacklistCleaner_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.data.ListManager;
import de.anomic.search.SearchEventCache;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import java.util.Set;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistError;

public class BlacklistCleaner_p {
    
    private static final String RESULTS = "results_";
    private static final String DISABLED = "disabled_";
    private static final String BLACKLISTS = "blacklists_";
    private static final String ENTRIES = "entries_";
        
    private final static String BLACKLIST_FILENAME_FILTER = "^.*\\.black$";
    
    public static final Class<?>[] supportedBLEngines = {
        Blacklist.class
    };
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        
        // initialize the list manager
        ListManager.switchboard = (Switchboard) env;
        ListManager.listsPath = new File(env.getDataPath(), env.getConfig("listManager.listsPath", "DATA/LISTS"));
        String blacklistToUse = null;

        // get the list of supported blacklist types
        final String supportedBlacklistTypesStr = Blacklist.BLACKLIST_TYPES_STRING;
        final String[] supportedBlacklistTypes = supportedBlacklistTypesStr.split(","); 

        prop.put(DISABLED+"checked", "1");

        if (post != null) {

            final boolean allowRegex = post.get("allowRegex", "off").equalsIgnoreCase("on") ? true: false;
            prop.put(DISABLED+"checked", (allowRegex) ? "1" : "0");

            if (post.containsKey("listNames")) {
                blacklistToUse = post.get("listNames");
                if (blacklistToUse.length() == 0 || !ListManager.listSetContains("listManager.listsPath", blacklistToUse)) {
                    prop.put("results", "2");

                }
            }

            putBlacklists(prop, FileUtils.getDirListing(ListManager.listsPath, BLACKLIST_FILENAME_FILTER), blacklistToUse);

            if (blacklistToUse != null) {
                prop.put("results", "1");

                if (post.containsKey("delete")) {
                    prop.put(RESULTS + "modified", "1");
                    prop.put(RESULTS + "modified_delCount", removeEntries(blacklistToUse, supportedBlacklistTypes, getKeysByPrefix(post, "select", true)));
                } else if (post.containsKey("alter")) {
                    prop.put(RESULTS + "modified", "2");
                    prop.put(RESULTS + "modified_alterCount", alterEntries(blacklistToUse, supportedBlacklistTypes, getKeysByPrefix(post, "select", false), getValuesByPrefix(post, "entry", false)));
                }

                // list illegal entries
                final Map<String, BlacklistError> illegalEntries = getIllegalEntries(blacklistToUse, Switchboard.urlBlacklist, allowRegex);
                prop.put(RESULTS + "blList", blacklistToUse);
                prop.put(RESULTS + "entries", illegalEntries.size());
                prop.putHTML(RESULTS + "blEngine", Switchboard.urlBlacklist.getEngineInfo());
                prop.put(RESULTS + "disabled", (illegalEntries.isEmpty()) ? "1" : "0");
                if (!illegalEntries.isEmpty()) {
                    prop.put(RESULTS + DISABLED + "entries", illegalEntries.size());
                    int i = 0;
                    String key;
                    for (final Entry<String, BlacklistError> entry : illegalEntries.entrySet()) {
                        key = entry.getKey();
                        prop.put(RESULTS + DISABLED + ENTRIES + i + "_error", entry.getValue().getLong());
                        prop.putHTML(RESULTS + DISABLED + ENTRIES + i + "_entry", key);
                        i++;
                    }
                }
            }
        } else {
            prop.put("results", "0");
            putBlacklists(prop, FileUtils.getDirListing(ListManager.listsPath, BLACKLIST_FILENAME_FILTER), blacklistToUse);
        }
        
        return prop;
    }

    /**
     * Adds a list of blacklist to the server objects properties which are used to
     * display the blacklist in the HTML page belonging to this servlet.
     * @param prop Server objects properties object.
     * @param lists List of blacklists.
     * @param selected Element in list of blacklists which will be preselected in HTML.
     */
    private static void putBlacklists(final serverObjects prop, final List<String> lists, final String selected) {
        boolean supported = false;
        for (int i=0; i < supportedBLEngines.length && !supported; i++) {
            supported |= (Switchboard.urlBlacklist.getClass() == supportedBLEngines[i]);
        }
        
        if (supported) {
            if (!lists.isEmpty()) {
                prop.put("disabled", "0");
                prop.put(DISABLED + "blacklists", lists.size());
                int count = 0;
                for (final String list : lists) {
                    prop.putHTML(DISABLED + BLACKLISTS + count + "_name", list);
                    prop.put(DISABLED + BLACKLISTS + count + "_selected", (list.equals(selected)) ? "1" : "0");
                    count++;
                }
            } else {
                prop.put("disabled", "2");
            }
        } else {
            prop.put("disabled", "1");
            for (int i = 0; i < supportedBLEngines.length; i++) {
                prop.putHTML(DISABLED + "engines_" + i + "_name", supportedBLEngines[i].getName());
            }
            prop.put(DISABLED + "engines", supportedBLEngines.length);
        }
    }

    /**
     * Retrieves all keys with a certain prefix from the data which has been sent and returns them as an array. This
     * method is only a wrapper for {@link getByPrefix(de.anomic.server.serverObjects, java.lang.String, boolean, boolean)}
     * which has been created to make it easier to understand the code.
     * @param post All POST values.
     * @param prefix Prefix by which the input is filtered.
     * @param filterDoubles Set true if only unique results shall be returned, else false.
     * @return Keys which have been posted.
     */
    private static String[] getKeysByPrefix(final serverObjects post, final String prefix, final boolean filterDoubles) {
        return getByPrefix(post, prefix, true, filterDoubles);
    }

    /**
     * Retrieves all values with a certain prefix from the data which has been sent and returns them as an array. This
     * method is only a wrapper for {@link getByPrefix(de.anomic.server.serverObjects, java.lang.String, boolean, boolean)}.
     * @param post All POST values.
     * @param prefix Prefix by which the input is filtered.
     * @param filterDoubles Set true if only unique results shall be returned, else false.
     * @return Values which have been posted.
     */
    private static String[] getValuesByPrefix(final serverObjects post, final String prefix, final boolean filterDoubles) {
        return getByPrefix(post, prefix, false, filterDoubles);
    }

    /**
     * Method which does all the work for {@link getKeysByPrefix(de.anomic.server.serverObjects, java.lang.String prefix, boolean)}
     * and {@link getValuesByPrefix(de.anomic.server.serverObjects, java.lang.String prefix, boolean)} which
     * have been crested to make it easier to understand the code.
     * @param post
     * @param prefix
     * @param useKeys
     * @param useHashSet
     * @return
     */
    private static String[] getByPrefix(final serverObjects post, final String prefix, final boolean useKeys, final boolean useHashSet) {
        Collection<String> r;
        if (useHashSet) {
            r = new HashSet<String>();
        } else {
            r = new ArrayList<String>();
        }

        if (useKeys) {
            for (final String entry : post.keySet()) {
                if (entry.indexOf(prefix) == 0) {
                    r.add(entry.substring(prefix.length()));
                }
            }
        } else {
            for (final Map.Entry<String, String> entry : post.entrySet()) {
                if (entry.getKey().indexOf(prefix) == 0) {
                    r.add(entry.getValue());
                }
            }
        }
        
        return r.toArray(new String[r.size()]);
    }

    /**
     * Finds illegal entries in black list.
     * @param blacklistToUse The blacklist to be checked.
     * @param blEngine The blacklist engine which is used to check
     * @param allowRegex Set to true to allow regular expressions in host part of blacklist entry.
     * @return A map which contains all entries whoch have been identified as being
     * illegal by the blacklistEngine with the entry as key and an error code as
     * value.
     */
    private static Map<String, BlacklistError> getIllegalEntries(final String blacklistToUse, final Blacklist blEngine, final boolean allowRegex) {
        final Map<String, BlacklistError> illegalEntries = new HashMap<String, BlacklistError>();
        final Set<String> legalEntries = new HashSet<String>();
        
        final List<String> list = FileUtils.getListArray(new File(ListManager.listsPath, blacklistToUse));
        final Map<String, String> properties= new HashMap<String, String>();
        properties.put("allowRegex", String.valueOf(allowRegex));

        BlacklistError err = BlacklistError.NO_ERROR;

        for (String element : list) {
            element = element.trim();
            
            // check for double-occurance
            if (legalEntries.contains(element)) {
                illegalEntries.put(element, BlacklistError.DOUBLE_OCCURANCE);
                continue;
            }
            legalEntries.add(element);

            err = blEngine.checkError(element, properties);

            if (err.getInt() > 0) {
                illegalEntries.put(element, err);
            }
        }

        return illegalEntries;
    }

    /**
     * Removes existing entries from a blacklist.
     * @param blacklistToUse The blacklist which contains the
     * @param supportedBlacklistTypes Types of blacklists which the entry is to changed in.
     * @param entries Array of entries to be deleted.
     * @return Length of the list of entries to be removed.
     */
    private static int removeEntries(final String blacklistToUse, final String[] supportedBlacklistTypes, final String[] entries) {
        // load blacklist data from file
        final List<String> list = FileUtils.getListArray(new File(ListManager.listsPath, blacklistToUse));
        
        boolean listChanged = false;
        
        // delete the old entry from file
        for (final String entry : entries) {
            String s = entry;
            
            if (list != null){
                
                // get rid of escape characters which make it impossible to
                // properly use contains()
                if (s.contains("\\\\")) {
                    s = s.replaceAll(Pattern.quote("\\\\"), Matcher.quoteReplacement("\\"));
                }
           
                if (list.contains(s)) {
                    listChanged = list.remove(s);
                }
            }
            
            // remove the entry from the running blacklist engine
            for (final String supportedBlacklistType : supportedBlacklistTypes) {
                if (ListManager.listSetContains(supportedBlacklistType + ".BlackLists", blacklistToUse)) {
                    final String host = (s.indexOf('/') == -1) ? s : s.substring(0, s.indexOf('/'));
                    final String path = (s.indexOf('/') == -1) ? ".*" : s.substring(s.indexOf('/') + 1);
                    try {
                    Switchboard.urlBlacklist.remove(supportedBlacklistType, host, path);
                    } catch (final RuntimeException e) {
                        Log.logSevere("BLACKLIST-CLEANER", e.getMessage() + ": " + host + "/" + path);
                    }
                }                
            }
            SearchEventCache.cleanupEvents(true);
        }
        if (listChanged){
            FileUtils.writeList(new File(ListManager.listsPath, blacklistToUse), list.toArray(new String[list.size()]));
        }
        return entries.length;
    }

    /**
     * Changes existing entry in a blacklist.
     * @param blacklistToUse The blacklist which contains the entry.
     * @param supportedBlacklistTypes Types of blacklists which the entry is to changed in.
     * @param oldEntry Entry to be changed.
     * @param newEntry Changed entry.
     * @return The length of the new entry.
     */
    private static int alterEntries(
            final String blacklistToUse,
            final String[] supportedBlacklistTypes,
            final String[] oldEntry,
            final String[] newEntry) {
        removeEntries(blacklistToUse, supportedBlacklistTypes, oldEntry);
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new FileWriter(new File(ListManager.listsPath, blacklistToUse), true));
            String host, path;
            for (final String n : newEntry) {
                int pos = n.indexOf('/');
                if (pos < 0) {
                    host = n;
                    path = ".*";
                } else {
                    host = n.substring(0, pos);
                    path = n.substring(pos + 1);
                }
                pw.println(host + "/" + path);
                for (final String s : supportedBlacklistTypes) {
                    if (ListManager.listSetContains(s + ".BlackLists",blacklistToUse)) {
                        Switchboard.urlBlacklist.add(
                                s,
                                host,
                                path);
                    }
                }
                SearchEventCache.cleanupEvents(true);
            }
            pw.close();
        } catch (final IOException e) {
            Log.logSevere("BLACKLIST-CLEANER", "error on writing altered entries to blacklist", e);
        }
        return newEntry.length;
    }
}
