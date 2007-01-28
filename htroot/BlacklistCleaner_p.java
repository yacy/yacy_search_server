// Blacklist_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Franz Brauße
//
// $LastChangedDate: 2007-01-27 14:07:54 +0000 (Sa, 27 Jan 2007) $
// $LastChangedRevision: 3217 $
// $LastChangedBy: karlchenofhell $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../classes BlacklistCleaner_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.urlPattern.defaultURLPattern;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class BlacklistCleaner_p {
    
    private static final String RESULTS = "results_";
    private static final String DISABLED = "disabled_";
    private static final String BLACKLISTS = "blacklists_";
    private static final String ENTRIES = "entries_";
    
    private static final int ERR_TWO_WILDCARDS_IN_HOST = 0;
    private static final int ERR_SUBDOMAIN_XOR_WILDCARD = 1;
    private static final int ERR_PATH_REGEX = 2;
    private static final int ERR_WILDCARD_BEGIN_OR_END = 3;
    private static final int ERR_HOST_WRONG_CHARS = 4;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        
        // initialize the list manager
        listManager.switchboard = (plasmaSwitchboard) env;
        listManager.listsPath = new File(env.getRootPath(), env.getConfig("listManager.listsPath", "DATA/LISTS"));
        String blacklistToUse = null;
        
        // getting the list of supported blacklist types
        String supportedBlacklistTypesStr = env.getConfig("BlackLists.types", "");
        String[] supportedBlacklistTypes = supportedBlacklistTypesStr.split(","); 
        
        if (post == null) {
            prop.put("results", 0);
            putBlacklists(prop, listManager.getDirListing(listManager.listsPath), blacklistToUse);
            return prop;
        }
        
        if (post.containsKey("listNames")) {
            blacklistToUse = (String)post.get("listNames"); 
            if (blacklistToUse.length() == 0 || !listManager.ListInListslist("listManager.listsPath", blacklistToUse))
                prop.put("results", 2);
        }
        
        putBlacklists(prop, listManager.getDirListing(listManager.listsPath), blacklistToUse);
        
        if (blacklistToUse != null) {
            prop.put("results", 1);
            
            if (post.containsKey("delete")) {
                prop.put(RESULTS + "modified", 1);
                prop.put(RESULTS + "modified_delCount", removeEntries(blacklistToUse, supportedBlacklistTypes, getByPrefix(post, "select")));
            } else if (post.containsKey("alter")) {
                prop.put(RESULTS + "modified", 2);
                prop.put(RESULTS + "modified_alterCount", alterEntries(blacklistToUse, supportedBlacklistTypes, getByPrefix(post, "select"), getByPrefix(post, "entry")));
            }
            
            // list illegal entries
            HashMap ies = getIllegalEntries(blacklistToUse, supportedBlacklistTypes, plasmaSwitchboard.urlBlacklist);
            prop.put(RESULTS + "entries", ies.size());
            prop.putSafeXML(RESULTS + "blEngine", plasmaSwitchboard.urlBlacklist.getEngineInfo());
            prop.put(RESULTS + "disabled", (ies.size() == 0) ? 1 : 0);
            if (ies.size() > 0) {
                prop.put(RESULTS + DISABLED + "entries", ies.size());
                Iterator it = ies.keySet().iterator();
                int i = 0;
                String s;
                while (it.hasNext()) {
                    s = (String)it.next();
                    prop.put(RESULTS + DISABLED + ENTRIES + i + "_error", ((Integer)ies.get(s)).longValue());
                    prop.putSafeXML(RESULTS + DISABLED + ENTRIES + i + "_entry", s);
                    i++;
                }
            }
        }
        
        return prop;
    }
    
    private static void putBlacklists(serverObjects prop, String[] lists, String selected) {
        if (lists.length > 0) {
            prop.put("disabled", 0);
            prop.put(DISABLED + "blacklists", lists.length);
            for (int i=0; i<lists.length; i++) {
                prop.put(DISABLED + BLACKLISTS + i + "_name", lists[i]);
                prop.put(DISABLED + BLACKLISTS + i + "_selected", (lists[i].equals(selected)) ? 1 : 0);
            }
        } else {
            prop.put("disabled", 1);
        }
    }
    
    private static String[] getByPrefix(serverObjects post, String prefix) {
        ArrayList r = new ArrayList();
        Iterator it = post.keySet().iterator();
        Object o;
        while (it.hasNext())
            if (((String)(o = it.next())).indexOf(prefix) == 0)
                r.add(((String)o).substring(prefix.length()));
        
        return (String[])r.toArray(new String[r.size()]);
    }
    
    private static HashMap /* entry, error-code */ getIllegalEntries(String blacklistToUse, String[] supportedBlacklistTypes, plasmaURLPattern blEngine) {
        HashMap r = new HashMap();
        
        ArrayList list = listManager.getListArray(new File(listManager.listsPath, blacklistToUse));
        Iterator it = list.iterator();
        String s, host, path;
        
        if (blEngine instanceof defaultURLPattern) {
            while (it.hasNext()) {
                s = (String)it.next();
            
                if (s.indexOf("/") == -1) {
                    host = s;
                    path = ".*";
                } else {
                    host = s.substring(0, s.indexOf("/"));
                    path = s.substring(s.indexOf("/") + 1);
                }
                
                int i = host.indexOf("*");
                
                // check whether host begins illegally
                if (!host.matches("([A-Za-z0-9_-]+|\\*)(\\.([A-Za-z0-9_-]+|\\*))*")) {
                    if (i == 0 && host.length() > 1 && host.charAt(1) != '.') {
                        r.put(s, new Integer(ERR_SUBDOMAIN_XOR_WILDCARD));
                        continue;
                    } else {
                        r.put(s, new Integer(ERR_HOST_WRONG_CHARS));
                        continue;
                    }
                }
                
                // in host-part only full sub-domains may be wildcards
                if (host.length() > 0 && i > -1) {
                    if (!(i == 0 || i == host.length() - 1)) {
                        r.put(s, new Integer(ERR_WILDCARD_BEGIN_OR_END));
                        continue;
                    }
                    
                    if (i == host.length() - 1 && host.length() > 1 && host.charAt(i - 1) != '.') {
                        r.put(s, new Integer(ERR_SUBDOMAIN_XOR_WILDCARD));
                        continue;
                    }
                }
                
                // check for double-occurences of "*" in host
                if (host.indexOf("*", i + 1) > -1) {
                    r.put(s, new Integer(ERR_TWO_WILDCARDS_IN_HOST));
                    continue;
                }
                
                // check for errors on regex-compiling path
                try {
                    Pattern.compile(path);
                } catch (PatternSyntaxException e) {
                    r.put(s, new Integer(ERR_PATH_REGEX));
                    continue;
                }
            }
        }

        return r;
    }
    
    private static int removeEntries(String blacklistToUse, String[] supportedBlacklistTypes, String[] entries) {
        // load blacklist data from file
        ArrayList list = listManager.getListArray(new File(listManager.listsPath, blacklistToUse));
        
        // delete the old entry from file
        String s;
        for (int i=0; i<entries.length; i++) {
            s = entries[i];
            if (list != null) list.remove(s);
            
            // remove the entry from the running blacklist engine
            for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                if (listManager.ListInListslist(supportedBlacklistTypes[blTypes] + ".BlackLists", blacklistToUse)) {
                    plasmaSwitchboard.urlBlacklist.remove(supportedBlacklistTypes[blTypes],
                            (s.indexOf("/") == -1) ? s : s.substring(0, s.indexOf("/")));
                }                
            }    
        }
        if (list != null) listManager.writeList(new File(listManager.listsPath, blacklistToUse), (String[])list.toArray(new String[list.size()]));
        
        return entries.length;
    }
    
    private static int alterEntries(String blacklistToUse, String[] supportedBlacklistTypes, String[] entries, String[] newEntries) {
        // load blacklist data from file
        ArrayList list = listManager.getListArray(new File(listManager.listsPath, blacklistToUse));
        
        // delete the old entry from file
        String s, t, host, path;
        for (int i=0; i<entries.length; i++) {
            s = entries[i];
            t = newEntries[i];
            if (t.indexOf("/") == -1) {
                host = t;
                path = "/.*";
            } else {
                host = t.substring(0, t.indexOf("/"));
                path = t.substring(t.indexOf("/"));
            }
            
            if (list != null && list.contains(s)) {
                list.remove(s);
                list.add(t);
            }
            
            // remove the entry from the running blacklist engine
            for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                if (listManager.ListInListslist(supportedBlacklistTypes[blTypes] + ".BlackLists", blacklistToUse)) {
                    plasmaSwitchboard.urlBlacklist.remove(supportedBlacklistTypes[blTypes],
                            (s.indexOf("/") == -1) ? s : s.substring(0, s.indexOf("/")));
                    plasmaSwitchboard.urlBlacklist.add(supportedBlacklistTypes[blTypes], host, path);
                }                
            }    
        }
        if (list != null) listManager.writeList(new File(listManager.listsPath, blacklistToUse), (String[])list.toArray(new String[list.size()]));
        return entries.length;
    }
}
