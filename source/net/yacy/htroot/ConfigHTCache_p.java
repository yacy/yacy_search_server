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

package net.yacy.htroot;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.zip.Deflater;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.data.Cache;
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

            /* Compression level*/
            /* Ensure a value within the range supported by the Deflater class */
			final int newCompressionLevel = Math.max(Deflater.NO_COMPRESSION, Math.min(Deflater.BEST_COMPRESSION,
					post.getInt("compressionLevel", SwitchboardConstants.HTCACHE_COMPRESSION_LEVEL_DEFAULT)));
			env.setConfig(SwitchboardConstants.HTCACHE_COMPRESSION_LEVEL, newCompressionLevel);
			Cache.setCompressionLevel(newCompressionLevel);

            /* Synchronization lock timeout */
			final long newLockTimeout = Math.max(10, Math.min(60000,
					post.getLong("lockTimeout", SwitchboardConstants.HTCACHE_SYNC_LOCK_TIMEOUT_DEFAULT)));
			env.setConfig(SwitchboardConstants.HTCACHE_SYNC_LOCK_TIMEOUT, newLockTimeout);
			Cache.setLockTimeout(newLockTimeout);
        }

        if (post != null && post.containsKey("deletecomplete")) {
            if ("on".equals(post.get("deleteCache", ""))) {
                Cache.clear();
            }
            if ("on".equals(post.get("deleteRobots", ""))) {
                sb.robots.clear();
            }
        }

        prop.put("HTCachePath", env.getConfig(SwitchboardConstants.HTCACHE_PATH, SwitchboardConstants.HTCACHE_PATH_DEFAULT));

        /* Compression levels */
		final int configuredCompressionLevel = env.getConfigInt(SwitchboardConstants.HTCACHE_COMPRESSION_LEVEL,
				SwitchboardConstants.HTCACHE_COMPRESSION_LEVEL_DEFAULT);
		int levelsCount = 0;
        for(int level = Deflater.NO_COMPRESSION; level <= Deflater.BEST_COMPRESSION; level++) {
        	if(level == configuredCompressionLevel) {
        		prop.put("compressionLevels_" + levelsCount + "_selected", "1");
        	} else {
        		prop.put("compressionLevels_" + levelsCount + "_selected", "0");
        	}
        	prop.put("compressionLevels_" + levelsCount + "_value", level);
        	prop.put("compressionLevels_" + levelsCount + "_name", level);
        	if(level == Deflater.NO_COMPRESSION) {
        		prop.put("compressionLevels_" + levelsCount + "_name", "0 - No compression");
        	} else if(level == Deflater.BEST_SPEED) {
        		prop.put("compressionLevels_" + levelsCount + "_name", Deflater.BEST_SPEED + " - Best speed");
        	} else if(level == Deflater.BEST_COMPRESSION) {
        		prop.put("compressionLevels_" + levelsCount + "_name", Deflater.BEST_COMPRESSION + " - Best compression");
        	}
        	levelsCount++;
        }
        prop.put("compressionLevels", levelsCount);

		prop.put("lockTimeout", env.getConfigLong(SwitchboardConstants.HTCACHE_SYNC_LOCK_TIMEOUT,
				SwitchboardConstants.HTCACHE_SYNC_LOCK_TIMEOUT_DEFAULT));
        prop.put("actualCacheSize", Cache.getActualCacheSize() / 1024 / 1024);
        prop.put("actualCacheDocCount", Cache.getActualCacheDocCount());
        prop.put("docSizeAverage", Cache.getActualCacheDocCount() == 0 ? 0 : Cache.getActualCacheSize() / Cache.getActualCacheDocCount() / 1024);
        prop.put("maxCacheSize", env.getConfigLong(SwitchboardConstants.PROXY_CACHE_SIZE, 64));
        /* Statistics */
        final long hits = Cache.getHits();
        final long totalRequests = Cache.getTotalRequests();
        prop.put("hits", hits);
        prop.put("requests", totalRequests);
        prop.put("hitRate", NumberFormat.getPercentInstance().format(Cache.getHitRate()));

        // return rewrite properties
        return prop;
    }
}
