// BlacklistHelperTest.java
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

package net.yacy.repository;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for the {@link BlacklistHelper} class.
 *
 */
public class BlacklistHelperTest {

	/**
	 * Unit testing of the function {@link BlacklistHelper#prepareEntry(String)}
	 */
	@Test
	public void testPrepareEntry() {
		assertEquals("http protocol with path wildcard", "domain.com/path/*", BlacklistHelper.prepareEntry("http://domain.com/path/*"));
		assertEquals("http protocol with path regex wildcard", "domain.com/path/.*", BlacklistHelper.prepareEntry("http://domain.com/path/.*"));
		assertEquals("https protocol with path wildcard", "domain.com/path/*", BlacklistHelper.prepareEntry("https://domain.com/path/*"));
		assertEquals("ftp protocol with path wildcard", "domain.com/path/*", BlacklistHelper.prepareEntry("ftp://domain.com/path/*"));
		assertEquals("wildcard in protocol", "domain.com/path/*", BlacklistHelper.prepareEntry("https?://domain.com/path/*"));
		assertEquals("regex with line beginning mark", "domain.com/path/.*", BlacklistHelper.prepareEntry("^https://domain.com/path/.*"));
		assertEquals("host with regex", "[a-z\\.]*domain.com/path/*", BlacklistHelper.prepareEntry("http://[a-z\\.]*domain.com/path/*"));
		assertEquals("path with regex", "domain.com/path/([^/1-9)+[^/]*/.*", BlacklistHelper.prepareEntry("domain.com/path/([^/1-9)+[^/]*/.*"));
		assertEquals("ip v4 address", "192.168.1.1/*", BlacklistHelper.prepareEntry("192.168.1.1/*"));
		assertEquals("domain only", "domain.com.*/.*", BlacklistHelper.prepareEntry("domain.com.*"));
		assertEquals("word only", ".*.*/.*.*word.*/.*.*", BlacklistHelper.prepareEntry("word"));
	}

}
