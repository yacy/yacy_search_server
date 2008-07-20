// ProxyIndexingMonitor_p.java 
// ---------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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
// javac -classpath .:../classes ProxyIndexingMonitor_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;

public class ProxyIndexingMonitor_p {

//  private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
//  private static String daydate(Date date) {
//      if (date == null) return ""; else return dayFormatter.format(date);
//  }

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        // return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();

//      int showIndexedCount = 20;
//      boolean se = false;

        String oldProxyCachePath, newProxyCachePath;
        String oldProxyCacheSize, newProxyCacheSize;

        prop.put("info", "0");
        prop.put("info_message", "");

        if (post != null) {

            if (post.containsKey("proxyprofileset")) try {
                // read values and put them in global settings
                int newProxyPrefetchDepth = post.getInt("proxyPrefetchDepth", 0);
                if (newProxyPrefetchDepth < 0) newProxyPrefetchDepth = 0; 
                if (newProxyPrefetchDepth > 20) newProxyPrefetchDepth = 20; // self protection ?
                env.setConfig("proxyPrefetchDepth", Integer.toString(newProxyPrefetchDepth));
                boolean proxyStoreHTCache = post.containsKey("proxyStoreHTCache");
                env.setConfig("proxyStoreHTCache", (proxyStoreHTCache) ? "true" : "false");
                boolean proxyIndexingRemote = post.containsKey("proxyIndexingRemote");
                env.setConfig("proxyIndexingRemote", proxyIndexingRemote ? "true" : "false");
                boolean proxyIndexingLocalText = post.containsKey("proxyIndexingLocalText");
                env.setConfig("proxyIndexingLocalText", proxyIndexingLocalText ? "true" : "false");
                boolean proxyIndexingLocalMedia = post.containsKey("proxyIndexingLocalMedia");
                env.setConfig("proxyIndexingLocalMedia", proxyIndexingLocalMedia ? "true" : "false");
                
                // added proxyCache, proxyCacheSize - Borg-0300
                // proxyCache - check and create the directory
                oldProxyCachePath = env.getConfig(plasmaSwitchboard.HTCACHE_PATH, plasmaSwitchboard.HTCACHE_PATH_DEFAULT);
                newProxyCachePath = post.get("proxyCache", plasmaSwitchboard.HTCACHE_PATH_DEFAULT);
                newProxyCachePath = newProxyCachePath.replace('\\', '/');
                if (newProxyCachePath.endsWith("/")) {
                    newProxyCachePath = newProxyCachePath.substring(0, newProxyCachePath.length() - 1);
                }
                env.setConfig(plasmaSwitchboard.HTCACHE_PATH, newProxyCachePath);
                final File cache = env.getConfigPath(plasmaSwitchboard.HTCACHE_PATH, oldProxyCachePath);
                if (!cache.isDirectory() && !cache.isFile()) cache.mkdirs();

                // proxyCacheSize 
                oldProxyCacheSize = getStringLong(env.getConfig("proxyCacheSize", "64"));
                newProxyCacheSize = getStringLong(post.get("proxyCacheSize", "64"));
                if (getLong(newProxyCacheSize) < 4) { newProxyCacheSize = "4"; }
                env.setConfig("proxyCacheSize", newProxyCacheSize);
                sb.setCacheSize(Long.parseLong(newProxyCacheSize));                

                // implant these settings also into the crawling profile for the proxy
                if (sb.webIndex.defaultProxyProfile == null) {
                    prop.put("info", "1"); //delete DATA/PLASMADB/crawlProfiles0.db
                } else {
                    try {
                        sb.webIndex.profilesActiveCrawls.changeEntry(sb.webIndex.defaultProxyProfile, "generalDepth", Integer.toString(newProxyPrefetchDepth));
                        sb.webIndex.profilesActiveCrawls.changeEntry(sb.webIndex.defaultProxyProfile, "storeHTCache", (proxyStoreHTCache) ? "true": "false");
                        sb.webIndex.profilesActiveCrawls.changeEntry(sb.webIndex.defaultProxyProfile, "remoteIndexing",proxyIndexingRemote ? "true":"false");
                        sb.webIndex.profilesActiveCrawls.changeEntry(sb.webIndex.defaultProxyProfile, "indexText",proxyIndexingLocalText ? "true":"false");
                        sb.webIndex.profilesActiveCrawls.changeEntry(sb.webIndex.defaultProxyProfile, "indexMedia",proxyIndexingLocalMedia ? "true":"false");
                        
                        prop.put("info", "2");//new proxyPrefetchdepth
                        prop.put("info_message", newProxyPrefetchDepth);
                        prop.put("info_caching", proxyStoreHTCache ? "1" : "0");
                        prop.put("info_indexingLocalText", proxyIndexingLocalText ? "1" : "0");
                        prop.put("info_indexingLocalMedia", proxyIndexingLocalMedia ? "1" : "0");
                        prop.put("info_indexingRemote", proxyIndexingRemote ? "1" : "0");

                        // proxyCache - only display on change
                        if (oldProxyCachePath.equals(newProxyCachePath)) {
                            prop.put("info_path", "0");
                            prop.put("info_path_return", oldProxyCachePath);
                        } else {
                            prop.put("info_path", "1");
                            prop.put("info_path_return", newProxyCachePath);
                        }
                        // proxyCacheSize - only display on change
                        if (oldProxyCacheSize.equals(newProxyCacheSize)) {
                            prop.put("info_size", "0");
                            prop.put("info_size_return", oldProxyCacheSize);
                        } else {
                            prop.put("info_size", "1");
                            prop.put("info_size_return", newProxyCacheSize);
                        }
                        // proxyCache, proxyCacheSize we need a restart
                        prop.put("info_restart", "0");
                        prop.put("info_restart_return", "0");
                        if (!oldProxyCachePath.equals(newProxyCachePath)) prop.put("info_restart", "1");

                    } catch (IOException e) {
                        prop.put("info", "3"); //Error: errmsg
                        prop.putHTML("info_error", e.getMessage());
                    }
                }

            } catch (Exception e) {
                prop.put("info", "2"); //Error: errmsg
                prop.putHTML("info_error", e.getMessage());
                serverLog.logSevere("SERVLET", "ProxyIndexingMonitor.case3", e);
            }
        }

        prop.put("proxyPrefetchDepth", env.getConfig("proxyPrefetchDepth", "0"));
        prop.put("proxyStoreHTCacheChecked", env.getConfig("proxyStoreHTCache", "").equals("true") ? "1" : "0");
        prop.put("proxyIndexingRemote", env.getConfig("proxyIndexingRemote", "").equals("true") ? "1" : "0");
        prop.put("proxyIndexingLocalText", env.getConfig("proxyIndexingLocalText", "").equals("true") ? "1" : "0");
        prop.put("proxyIndexingLocalMedia", env.getConfig("proxyIndexingLocalMedia", "").equals("true") ? "1" : "0");
        prop.put("proxyCache", env.getConfig(plasmaSwitchboard.HTCACHE_PATH, plasmaSwitchboard.HTCACHE_PATH_DEFAULT));
        prop.put("proxyCacheSize", env.getConfig("proxyCacheSize", "64"));
        // return rewrite properties
        return prop;
    }

    public static long getLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getStringLong(String value) {
        try {
            return Long.toString(Long.parseLong(value));
        } catch (Exception e) {
            return "0";
        }
    }

}