// ProxyIndexingMonitor_p.java
// ---------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.Cache;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ProxyIndexingMonitor_p {

//  private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
//  private static String daydate(Date date) {
//      if (date == null) return ""; else return dayFormatter.format(date);
//  }

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

//      int showIndexedCount = 20;
//      boolean se = false;

        String oldProxyCachePath, newProxyCachePath;
        long oldProxyCacheSize, newProxyCacheSize;

        prop.put("info", "0");
        prop.put("info_message", "");

        if (post != null) {

            if (post.containsKey("proxyprofileset")) try {
                // read values and put them in global settings
                final boolean proxyYaCyOnly = post.containsKey("proxyYacyOnly");
                env.setConfig(SwitchboardConstants.PROXY_YACY_ONLY, (proxyYaCyOnly) ? true : false);
                int newProxyPrefetchDepth = post.getInt("proxyPrefetchDepth", 0);
                if (newProxyPrefetchDepth < 0) newProxyPrefetchDepth = 0;
                if (newProxyPrefetchDepth > 20) newProxyPrefetchDepth = 20; // self protection ?
                env.setConfig("proxyPrefetchDepth", Integer.toString(newProxyPrefetchDepth));
                final boolean proxyStoreHTCache = post.containsKey("proxyStoreHTCache");
                env.setConfig("proxyStoreHTCache", (proxyStoreHTCache) ? true : false);
                final boolean proxyIndexingRemote = post.containsKey("proxyIndexingRemote");
                env.setConfig("proxyIndexingRemote", proxyIndexingRemote ? true : false);
                final boolean proxyIndexingLocalText = post.containsKey("proxyIndexingLocalText");
                env.setConfig("proxyIndexingLocalText", proxyIndexingLocalText ? true : false);
                final boolean proxyIndexingLocalMedia = post.containsKey("proxyIndexingLocalMedia");
                env.setConfig("proxyIndexingLocalMedia", proxyIndexingLocalMedia ? true : false);

                // added proxyCache, proxyCacheSize - Borg-0300
                // proxyCache - check and create the directory
                oldProxyCachePath = env.getConfig(SwitchboardConstants.HTCACHE_PATH, SwitchboardConstants.HTCACHE_PATH_DEFAULT);
                newProxyCachePath = post.get("proxyCache", SwitchboardConstants.HTCACHE_PATH_DEFAULT);
                newProxyCachePath = newProxyCachePath.replace('\\', '/');
                if (newProxyCachePath.endsWith("/")) {
                    newProxyCachePath = newProxyCachePath.substring(0, newProxyCachePath.length() - 1);
                }
                env.setConfig(SwitchboardConstants.HTCACHE_PATH, newProxyCachePath);
                final File cache = env.getDataPath(SwitchboardConstants.HTCACHE_PATH, oldProxyCachePath);
                if (!cache.isDirectory() && !cache.isFile()) cache.mkdirs();

                // proxyCacheSize
                oldProxyCacheSize = env.getConfigLong(SwitchboardConstants.PROXY_CACHE_SIZE, 64L);
                newProxyCacheSize = post.getLong(SwitchboardConstants.PROXY_CACHE_SIZE, 64L);
                if (newProxyCacheSize < 4) { newProxyCacheSize = 4; }
                env.setConfig(SwitchboardConstants.PROXY_CACHE_SIZE, newProxyCacheSize);
                Cache.setMaxCacheSize(newProxyCacheSize * 1024L * 1024L);

                // implant these settings also into the crawling profile for the proxy
                if (sb.crawler.defaultProxyProfile == null) {
                    prop.put("info", "1"); //delete DATA/PLASMADB/crawlProfiles0.db
                } else {
                    assert sb.crawler.defaultProxyProfile.handle() != null;
                    sb.crawler.defaultProxyProfile.put("generalDepth", Integer.toString(newProxyPrefetchDepth));
                    sb.crawler.defaultProxyProfile.put("storeHTCache", proxyStoreHTCache);
                    sb.crawler.defaultProxyProfile.put("remoteIndexing", proxyIndexingRemote);
                    sb.crawler.defaultProxyProfile.put("indexText", proxyIndexingLocalText);
                    sb.crawler.defaultProxyProfile.put("indexMedia", proxyIndexingLocalMedia);
                    sb.crawler.putActive(sb.crawler.defaultProxyProfile.handle().getBytes(), sb.crawler.defaultProxyProfile);

                    prop.put("info", "2");//new proxyPrefetchdepth
                    prop.put("info_message", newProxyPrefetchDepth);
                    prop.put("info_caching", proxyStoreHTCache ? "1" : "0");
                    prop.put("info_indexingLocalText", proxyIndexingLocalText ? "1" : "0");
                    prop.put("info_indexingLocalMedia", proxyIndexingLocalMedia ? "1" : "0");
                    prop.put("info_indexingRemote", proxyIndexingRemote ? "1" : "0");

                    // proxyCache - only display on change
                    if (oldProxyCachePath.equals(newProxyCachePath)) {
                        prop.put("info_path", "0");
                        prop.putHTML("info_path_return", oldProxyCachePath);
                    } else {
                        prop.put("info_path", "1");
                        prop.putHTML("info_path_return", newProxyCachePath);
                    }
                    // proxyCacheSize - only display on change
                    if (oldProxyCacheSize == newProxyCacheSize) {
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
                }

            } catch (final Exception e) {
                prop.put("info", "2"); //Error: errmsg
                prop.putHTML("info_error", e.getMessage());
                ConcurrentLog.severe("SERVLET", "ProxyIndexingMonitor.case3", e);
            }
        }

        final boolean yacyonly = env.getConfigBool(SwitchboardConstants.PROXY_YACY_ONLY, false);
        prop.put("proxyYacyOnly", yacyonly ? "1" : "0");
        prop.put("proxyPrefetchDepth", env.getConfigLong("proxyPrefetchDepth", 0));
        prop.put("proxyStoreHTCacheChecked", env.getConfigBool("proxyStoreHTCache", false) ? "1" : "0");
        prop.put("proxyIndexingRemote", env.getConfigBool("proxyIndexingRemote", false) ? "1" : "0");
        prop.put("proxyIndexingLocalText", env.getConfigBool("proxyIndexingLocalText", false) ? "1" : "0");
        prop.put("proxyIndexingLocalMedia", env.getConfigBool("proxyIndexingLocalMedia", false) ? "1" : "0");
        prop.put("proxyCache", env.getConfig(SwitchboardConstants.HTCACHE_PATH, SwitchboardConstants.HTCACHE_PATH_DEFAULT));
        prop.put("proxyCacheSize", env.getConfigLong(SwitchboardConstants.PROXY_CACHE_SIZE, 64));
        // return rewrite properties
        return prop;
    }

}