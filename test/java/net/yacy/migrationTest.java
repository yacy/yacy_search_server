// migrationTest.java
// Copyright 2017 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
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

package net.yacy;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the {@link migration} class.
 */
public class migrationTest {

	/**
	 * Testing the conversion of IP addresses patterns
	 */
	@Test
	public void testMigrateIPAddressPatterns() {
		final String patternSeparator = ",";
		final String[] nonDeprecatedPatterns = { "*", // match all (default)
				"10.10.1.2,2001:db8::ff00:42:8329", // single IPv4 and IPv6 addresses
				"10.10.1.2|/foo/bar,2001:db8::ff00:42:8329|/foo/bar", // single IPv4 and IPv6 addresses with path
				"192.168.1.1-192.168.1.10,2001:db8::ff00:42:8330-2001:db8::ff00:42:83ff", // IPv4 and IPv6 addresses
																							// ranges
				"192.168.1.1-192.168.1.10|/path,2001:db8::ff00:42:8330-2001:db8::ff00:42:83ff|/path", // IPv4 and IPv6 addresses ranges with path
				"127.0.0.1/8,192.168.1.0/24,2001:db8::aaaa:0:0/96,::1/128", // IPv4 and IPv6 addresses ranges defined using CIDR notation
				"127.0.0.1/8|*.html,192.168.1.0/24|/foo/bar,2001:db8::aaaa:0:0/96|/foo/bar,::1/128|*.html", // IPv4 and IPv6 addresses ranges defined using CIDR notation with path
				"192.168.3.0-255", // legacy IPv4 addresses range format
				"192.168.3.0-255|/foo/bar,192.168.1.0-255|*.html", // legacy IPv4 addresses range format with path
		};
		final StringBuilder migrated = new StringBuilder();
		for (final String patterns : nonDeprecatedPatterns) {
			migrated.setLength(0);
			Assert.assertFalse("Should not be detected as deprecated : " + patterns,
					migration.migrateIPAddressPatterns(patternSeparator, patterns, migrated));
			Assert.assertEquals(patterns, migrated.toString());
		}
		
		final Map<String, String> deprecatedToMigrated = new HashMap<>();
		/* old IPv4  wildcard notation */
		deprecatedToMigrated.put("127.", "127.0.0.0-127.255.255.255");
		
		/* old IPv4  wildcard notation */
		deprecatedToMigrated.put("192.168.", "192.168.0.0-192.168.255.255");
		
		/* old IPv4  wildcard notation */
		deprecatedToMigrated.put("192.168.1.", "192.168.1.0-192.168.1.255");
		
		/* IPV4 address and old style path pattern */
		deprecatedToMigrated.put("192.168.1.1/foo/bar,127.0.0.1/*.txt", "192.168.1.1|/foo/bar,127.0.0.1|*.txt");
		
		/* old IPv4 wildcard notation and old style path pattern */
		deprecatedToMigrated.put("192.168./foo/bar,127./*.txt", "192.168.0.0-192.168.255.255|/foo/bar,127.0.0.0-127.255.255.255|*.txt");
		
		/* old IPv4 wildcard notation and new style path pattern */
		deprecatedToMigrated.put("192.168.|/foo/bar,127.|*.txt", "192.168.0.0-192.168.255.255|/foo/bar,127.0.0.0-127.255.255.255|*.txt");
		
		/* mixed deprecated and non deprecated patterns */
		deprecatedToMigrated.put("10.10.1.2,2001:db8::ff00:42:8329|/foo/bar,192.168.|/foo/bar,192.168.1.0/24,127.|*.txt", 
				"10.10.1.2,2001:db8::ff00:42:8329|/foo/bar,192.168.0.0-192.168.255.255|/foo/bar,192.168.1.0/24,127.0.0.0-127.255.255.255|*.txt");
		
		for (final Entry<String, String> entry : deprecatedToMigrated.entrySet()) {
			migrated.setLength(0);
			Assert.assertTrue("Should be detected as deprecated : " + entry.getKey(),
					migration.migrateIPAddressPatterns(patternSeparator, entry.getKey(), migrated));
			Assert.assertEquals(entry.getValue(), migrated.toString());
		}
	}

}
