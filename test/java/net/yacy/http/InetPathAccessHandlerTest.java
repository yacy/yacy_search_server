// InetPathAccessHandlerTest.java
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

package net.yacy.http;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the {@link InetPathAccessHandler} class.
 */
public class InetPathAccessHandlerTest {

	/**
	 * Check the handler allow the given ip/path pairs.
	 *
	 * @param handler
	 *            the handler to test. Must not be null.
	 * @param ipAndPaths
	 *            array of ip address and path pairs. Must not be null.
	 * @throws UnknownHostException
	 *             when a test address is incorrect.
	 */
	private void assertAllowed(final InetPathAccessHandler handler, final String[][] ipAndPaths)
			throws UnknownHostException {
		for (final String[] ipAndPath : ipAndPaths) {
			final String ip = ipAndPath[0];
			final String path = ipAndPath[1];
			Assert.assertTrue("Should allow " + ip + path, handler.isAllowed(InetAddress.getByName(ip), path));
		}
	}

	/**
	 * Check the handler dos not allow the given ip/path pairs.
	 *
	 * @param handler
	 *            the handler to test. Must not be null.
	 * @param ipAndPaths
	 *            array of ip address and path pairs. Must not be null.
	 * @throws UnknownHostException
	 *             when a test address is incorrect.
	 */
	private void assertRejected(final InetPathAccessHandler handler, final String[][] ipAndPaths)
			throws UnknownHostException {
		for (final String[] ipAndPath : ipAndPaths) {
			final String ip = ipAndPath[0];
			final String path = ipAndPath[1];
			Assert.assertFalse("Should not allow " + ip + path, handler.isAllowed(InetAddress.getByName(ip), path));
		}
	}

	/**
	 * Test inclusion with a single white listed IPv4 address.
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeSingleIPv4() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("10.10.1.2");

		final String[][] allowed = { { "10.10.1.2", "/" }, // matching address, root path
				{ "10.10.1.2", "/foo/bar" }, // matching address, non root path
				{ "10.10.1.2", null } // matching address, no path information provided
		};
		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.10.1.3", "/" }, // non matching address, root path
				{ null, null } // no address nor path information provided
		};
		this.assertRejected(handler, rejected);
	}

	/**
	 * Test inclusion with a single white listed IPv6 address.
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeSingleIPv6() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("2001:db8::ff00:42:8329");

		final String[][] allowed = { { "2001:db8::ff00:42:8329", "/" }, // matching address, root path
				{ "2001:0db8:0000:0000:0000:ff00:0042:8329", "/" }, // matching address in long representation, root
																	// path
				{ "2001:db8::ff00:42:8329", "/foo/bar" }, // matching address, non root path
				{ "2001:db8::ff00:42:8329", null } // matching address, no path information provided
		};
		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "2001:db8::ff00:42:8539", "/" }, // non matching address, root path
				{ null, null } // no address nor path information provided
		};
		this.assertRejected(handler, rejected);
	}

	/**
	 * Test inclusion with a single white listed IPV4 address and path.
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeSingleAddressAndPath() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("10.10.1.2|/foo/bar");

		final String[][] allowed = { { "10.10.1.2", "/foo/bar" } // matching address, matching path
		};
		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.10.1.3", "/" }, // non matching address, non matching path
				{ "10.10.1.3", "/foo/bar" }, // non matching address, even if matching path
				{ "10.10.1.2", "/" }, // matching address, but non matching root path
				{ "10.10.1.2", "/foo" }, // matching address, but non matching parent path
				{ "10.10.1.2", "/foo/" }, // matching address, but non matching parent path
				{ "10.10.1.2", "/foo/wrong" }, // matching address, but non matching sub path
				{ "10.10.1.2", "/foo/bar/file.txt" } // matching address, but non matching sub path with file
		};
		this.assertRejected(handler, rejected);
	}

	/**
	 * Test inclusion with a single white listed IPV4 address and wildcard path.
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeSingleAddressAndWildcardPath() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("10.10.1.2|/foo/*");

		final String[][] allowed = { { "10.10.1.2", "/foo/bar" }, // matching address, matching sub path
				{ "10.10.1.2", "/foo/bar/sub" }, // matching address, matching sub path
				{ "10.10.1.2", "/foo/file.txt" }, // matching address, matching sub path with file
				{ "10.10.1.2", "/foo" }, // matching address, matching path
		};
		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.10.1.3", "/" }, // non matching address, non matching path
				{ "10.10.1.3", "/foo/bar" }, // non matching address, event if matching path
				{ "10.10.1.2", "/" }, // matching address, but non matching root path
				{ "10.10.1.2", null }, // matching address, but no path information provided
				{ null, "/foo/bar" } // no address provided, event if matching path
		};
		this.assertRejected(handler, rejected);
	}

	/**
	 * Test inclusion with a single white listed IPV4 address and wildcard path
	 * suffix.
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeSingleAddressAndWildcardSuffix() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("10.10.1.2|*.html");

		final String[][] allowed = { { "10.10.1.2", "/index.html" }, // matching address, matching file path
				{ "10.10.1.2", "/foo/bar/index.html" }, // matching address, matching file with parent path
		};
		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.10.1.3", "/" }, // non matching address, non matching path
				{ "10.10.1.3", "/index.html" }, // non matching address, event if matching file path
				{ "10.10.1.2", "/" }, // matching address, but non matching root path
				{ "10.10.1.2", "/index.txt" }, // matching address, but non matching file path
				{ "10.10.1.2", null }, // matching address, but no path information provided
				{ null, "/index.html" } // no address provided, event if matching path
		};
		this.assertRejected(handler, rejected);
	}

	/**
	 * Test inclusion with ranges of white listed addresses.
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeRanges() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("10.10.1.1-255"); // legacy IPv4 range format used by IPAddressMap
		handler.include("192.168.128.0-192.168.128.255"); // inclusive range of IPv4 addresses
		handler.include("2001:db8::ff00:42:8329-2001:db8::ff00:42:ffff"); // inclusive range of IPv6 addresses
		handler.include("192.168.1.0/24"); // CIDR notation on IPv4
		handler.include("2001:db8::aaaa:0:0/96"); // CIDR notation on IPv6

		final String[][] allowed = { { "10.10.1.1", "/" }, // matching legacy IPv4 range
				{ "10.10.1.255", "/" }, // matching legacy IPv4 range
				{ "192.168.128.0", "/" }, // matching second range of IPv4 addresses
				{ "192.168.128.255", "/" }, // matching second range of IPv4 addresses
				{ "2001:db8::ff00:42:8329", "/" }, // matching IPv6 range
				{ "2001:db8::ff00:42:99ff", "/" }, // matching IPv6 range
				{ "192.168.1.0", "/" }, // matching IPv4 CIDR notation range
				{ "192.168.1.255", "/" }, // matching IPv4 CIDR notation range
				{ "2001:db8::aaaa:1:1", "/" }, // matching IPv6 CIDR notation range
				{ "2001:db8::aaaa:ffff:ffff", "/" } // matching IPv6 CIDR notation range
		};
		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.9.1.1", "/" }, { "10.10.2.1", "/" }, { "192.168.127.1", "/" },
				{ "2001:db8::ff00:43:1234", "/" }, { "192.168.2.1", "/" }, { "2001:db8::aabb:ffff:ffff", "/" } };
		this.assertRejected(handler, rejected);
	}

	/**
	 * Test inclusion with ranges of white listed addresses associated with wildcard
	 * paths.
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeRangesAndWildcardPaths() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("10.10.1.1-255|/foo/*"); // legacy IPv4 range format used by IPAddressMap
		handler.include("192.168.128.0-192.168.128.255|/path/*"); // inclusive range of IPv4 adresses
		handler.include("2001:db8::ff00:42:8329-2001:db8::ff00:42:ffff|/root/*"); // inclusive range of IPv6 adresses
		handler.include("192.168.1.0/24|/www/*"); // CIDR notation

		final String[][] allowed = { { "10.10.1.1", "/foo/bar" }, // matching legacy IPv4 range and path
				{ "10.10.1.255", "/foo/bar" }, // matching legacy IPv4 range and path
				{ "192.168.128.0", "/path/index.html" }, // matching second range of IPv4 addresses and path
				{ "192.168.128.255", "/path/file.txt" }, // matching second range of IPv4 addresses and path
				{ "2001:db8::ff00:42:8329", "/root/index.txt" }, // matching IPv6 range and path
				{ "2001:db8::ff00:42:99ff", "/root/image.jpg" }, // matching IPv6 range and path
				{ "192.168.1.0", "/www/resource" }, // matching IPv4 CIDR notation range and path
				{ "192.168.1.255", "/www/home" } }; // matching IPv4 CIDR notation range and path
		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.9.1.1", "/" }, { "10.9.1.1", "/foo/bar" }, { "10.10.2.1", "/" },
				{ "10.10.2.1", "/foo/bar" }, { "192.168.127.1", "/" }, { "192.168.127.1", "/path/index.html" },
				{ "2001:db8::ff00:43:1234", "/" }, { "2001:db8::ff00:43:1234", "/root/index.txt" },
				{ "192.168.2.1", "/" }, { "192.168.2.1", "/www/content" } };
		this.assertRejected(handler, rejected);
	}

	/**
	 * Test inclusion with multiple patterns using the same path
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeMultiplePatternsOnSamePath() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("10.10.1.1|/foo/bar"); // a single address pattern
		handler.include("192.168.128.0-192.168.128.255|/foo/bar"); // inclusive range of IPv4 adresses

		final String[][] allowed = { { "10.10.1.1", "/foo/bar" }, // matching single address pattern
				{ "192.168.128.0", "/foo/bar" }, { "192.168.128.255", "/foo/bar" } // matching range pattern
		};
		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.10.1.1", "/" }, // matching single address pattern bu root path
				{ "127.0.0.1", "/" }, // non matching address
		};
		this.assertRejected(handler, rejected);
	}

	/**
	 * Test exclusion with a single white listed IPV4 address and path.
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testExcludeSingleAddressAndPath() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.exclude("10.10.1.2|/foo/bar");

		final String[][] allowed = { { "10.10.1.3", "/" }, // non matching address, non matching path
				{ "10.10.1.3", "/foo/bar" }, // non matching address, even if matching path
				{ "10.10.1.2", "/" }, // matching address, but non matching root path
				{ "10.10.1.2", "/foo" }, // matching address, but non matching parent path
				{ "10.10.1.2", "/foo/" }, // matching address, but non matching parent path
				{ "10.10.1.2", "/foo/wrong" }, // matching address, but non matching sub path
				{ "10.10.1.2", "/foo/bar/file.txt" } // matching address, but non matching sub path with file
		};

		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.10.1.2", "/foo/bar" } // matching address, matching path
		};

		this.assertRejected(handler, rejected);
	}
	
	/**
	 * Test inclusion and exclusion rules applied on the same address
	 * 
	 * @throws UnknownHostException
	 *             when a test address is incorrect. Should not happen.
	 */
	@Test
	public void testIncludeExcludeOnSameAddress() throws UnknownHostException {
		final InetPathAccessHandler handler = new InetPathAccessHandler();
		handler.include("10.10.1.1-10.10.1.255"); // include a range of addresses without path restrictions
		handler.exclude("10.10.1.2|/foo/bar"); // exclude a specific address and path

		final String[][] allowed = { { "10.10.1.3", "/" }, // matching included addresses range
				{ "10.10.1.2", "/" }, // matching excluded address, but non matching root path
				{ "10.10.1.2", "/foo" }, // matching excluded address, but non matching parent path
				{ "10.10.1.2", "/foo/wrong" }, // matching excluded address, but non matching sub path
				{ "10.10.1.2", "/foo/bar/file.txt" } // matching excluded address, but non matching sub path with file
		};

		this.assertAllowed(handler, allowed);

		final String[][] rejected = { { "10.10.1.2", "/foo/bar" } // matching excluded address and path
		};

		this.assertRejected(handler, rejected);
	}
}
