// ConfigHTCache_p.java
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate: 2010-09-02 21:24:22 +0200 (Do, 02 Sep 2010) $
// $LastChangedRevision: 7092 $
// $LastChangedBy: orbiter $
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.data.Cache;
import net.yacy.data.WorkTables;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ConfigHTCache_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) throws IOException {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        if (post != null && post.containsKey("set")) {
            // proxyCache - check and create the directory
            final String oldProxyCachePath = env.getConfig(SwitchboardConstants.HTCACHE_PATH, SwitchboardConstants.HTCACHE_PATH_DEFAULT);
            String newProxyCachePath = post.get("HTCachePath", SwitchboardConstants.HTCACHE_PATH_DEFAULT);
            newProxyCachePath = newProxyCachePath.replace('\\', '/');
            if (newProxyCachePath.endsWith("/")) {
                newProxyCachePath = newProxyCachePath.substring(0, newProxyCachePath.length() - 1);
            }
            env.setConfig(SwitchboardConstants.HTCACHE_PATH, newProxyCachePath);
            final File cache = env.getDataPath(SwitchboardConstants.HTCACHE_PATH, oldProxyCachePath);
            if (!cache.isDirectory() && !cache.isFile()) {
                cache.mkdirs();
            }

            // proxyCacheSize
            final int newProxyCacheSize = Math.max(post.getInt("maxCacheSize", 64), 0);
            env.setConfig(SwitchboardConstants.PROXY_CACHE_SIZE, newProxyCacheSize);
            Cache.setMaxCacheSize(newProxyCacheSize * 1024L * 1024L);
        }

        if (post != null && post.containsKey("deletecomplete")) {
            if ("on".equals(post.get("deleteCache", ""))) {
                Cache.clear();
            }
            if ("on".equals(post.get("deleteRobots", ""))) {
                sb.robots.clear();
            }
            if ("on".equals(post.get("deleteSearchFl", ""))) {
            	sb.tables.clear(WorkTables.TABLE_SEARCH_FAILURE_NAME);
            }
        }

        prop.put("HTCachePath", env.getConfig(SwitchboardConstants.HTCACHE_PATH, SwitchboardConstants.HTCACHE_PATH_DEFAULT));
        prop.put("actualCacheSize", Cache.getActualCacheSize() / 1024 / 1024);
        prop.put("actualCacheDocCount", Cache.getActualCacheDocCount());
        prop.put("docSizeAverage", Cache.getActualCacheDocCount() == 0 ? 0 : Cache.getActualCacheSize() / Cache.getActualCacheDocCount() / 1024);
        prop.put("maxCacheSize", env.getConfigLong(SwitchboardConstants.PROXY_CACHE_SIZE, 64));
        // return rewrite properties
        return prop;
    }
}
