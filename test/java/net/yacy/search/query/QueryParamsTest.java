// QueryParamsTest.java
// ---------------------------
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

package net.yacy.search.query;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.document.id.MultiProtocolURL;

/**
 * Unit tests for the {@link QueryParams} class.
 */
public class QueryParamsTest {

	/**
	 * Test URL matching with a single query constraint on top-level domain.
	 * @throws MalformedURLException when a test URL is malformed. Should not happen.
	 */
	@Test
	public void testMatchesURLTLD() throws MalformedURLException {
		final String[] matchingURLs = { "http://example.org", // most basic matching example
				"http://example.org/", // normalized basic example
				"http://www.example.org/", // with www domain prefix
				"http://example.org:8080", // non default port
				"http://example.org?key=value", // empty path and query string
				"http://example.org?key=value#fragment", // empty path, query string and fragment
				"http://example.org:8080?key=value#fragment", // non default port, empty path, query string and fragment
				"http://example.org:8080/?key=value#fragment", // normalized non default port, empty path, query string and fragment
				"http://example.org#fragment", // empty path and fragment
				"ftp://example.org", // another protocol than http
				"http://example.org/index.html", // with file
				"http://example.org/path/index.html", // with path and file
				"http://example.org:8090/path/index.html", // with non default port, path and file
				"http://example.org/index.html?key=value", // with file and query string
				"http://example.org/index.html?key=value#fragment", // with file, query string and url fragment
		};

		final String[] nonMatchingURLs = { "http://example.test", // basic non matching example
				"http://example.test/", // normalized basic example
				"http://org.example.net", // only subdomain matching
				"http://example.org.net", // only secondary-level domain matching
				"http://organization.test", // secondary-level starting like the filter
				"http://test.organic", // top-level domain starting like the filter
				"http://en.organization.test", // subdomain then secondary-level starting like the filter
				"http://example.test/path/file.org", // with file ending like the tld filter
				"http://example.test/?query=example.org", // with query parameter including the tld
				"http://example.test/#fragment.org", // with query parameter including the tld
				"file:///path/file.txt", // empty host name in file URL
				"http://127.0.0.1/index.html", // IPv4 address
				"http://[2001:db8::ff00:42:8329]/index.html" // IPv6 address
		};
		
		final QueryModifier modifier = new QueryModifier(0);
		checkURLs(matchingURLs, nonMatchingURLs, modifier, "org");
	}

	/**
	 * Check matching and non matching URLs against the given query modifier and
	 * eventual top-level domain name.
	 * 
	 * @param matchingURLs
	 *            array of URLs expected to be accepted
	 * @param nonMatchingURLs
	 *            array of URLs expected to be rejected
	 * @param modifier
	 *            the query modifier
	 * @param tld
	 *            the eventual top-level domain to filter on.
	 * @throws MalformedURLException when a test URL string is malformed
	 */
	private void checkURLs(final String[] matchingURLs, final String[] nonMatchingURLs, final QueryModifier modifier, final String tld) throws MalformedURLException {
		for (final String matchingURL : matchingURLs) {
			Assert.assertEquals(matchingURL + " should match", "", QueryParams.matchesURL(modifier, tld, new MultiProtocolURL(matchingURL)));
		}
		for (final String nonMatchingURL : nonMatchingURLs) {
			Assert.assertNotEquals(nonMatchingURL + " should not match", "",
					QueryParams.matchesURL(modifier, tld, new MultiProtocolURL(nonMatchingURL)));
		}
	}

	/**
	 * Test URL matching build with a single query constraint on URL scheme.
	 * @throws MalformedURLException when a test URL is malformed. Should not happen.
	 */
	@Test
	public void testMatchesURLProtocol() throws MalformedURLException {
		final String[] matchingURLs = { "http://example.org/" };

		final String[] nonMatchingURLs = { "https://example.org/", 
				"ftp://www.example.test/", "smb://localhost",
				"mailto:user@example.com", "file:///tmp/path/",
				"https://example.org/index.html?query=http", // with query parameter including the protocol
				"https://example.org/index.html#http" // with fragment string including the protocol
		};
		final QueryModifier modifier = new QueryModifier(0);
		modifier.protocol = "http";
		checkURLs(matchingURLs, nonMatchingURLs, modifier, null);
	}
	
	/**
	 * Test URL matching with a single query constraint on host name.
	 * @throws MalformedURLException when a test URL is malformed. Should not happen.
	 */
	@Test
	public void testMatchesURLHostName() throws MalformedURLException {
		final String[] matchingURLs = { "http://example.org", // most basic matching example
				"http://example.org/", // normalized basic example
				"http://www.example.org/", // with www domain prefix
				"http://example.org:8080", // non default port
				"http://example.org?key=value", // empty path and query string
				"http://example.org?key=value#fragment", // empty path, query string and fragment
				"http://example.org:8080?key=value#fragment", // non default port, empty path, query string and fragment
				"http://example.org:8080/?key=value#fragment", // normalized non default port, empty path, query string and fragment
				"http://example.org#fragment", // empty path and fragment
				"ftp://example.org", // another protocol than http
				"http://example.org/index.html", // with file
				"http://example.org/path/index.html", // with path and file
				"http://example.org:8090/path/index.html", // with non default port, path and file
				"http://example.org/index.html?key=value", // with file and query string
				"http://example.org/index.html?key=value#fragment", // with file, query string and url fragment
		};

		final String[] nonMatchingURLs = { "http://domain.test", // basic non matching example
				"http://domain.test/", // normalized basic example
				"http://fr.example.org", // domain prefix different from www
				"http://example.net", // only secondary-level domain matching
				"http://test.org", // only top-level domain matching
				"http://example.organic", // domain starting like the one of the filter
				"http://unexample.org", // domain ending like the one of the filter
				"http://example.net/index.html?query=example.org", // with query including the filtered domain
				"http://example.net/index.html#example.org", // with fragment string including the filtered domain
				"file:///path/file.txt", // empty host name in file URL
				"http://127.0.0.1/index.html", // IPv4 address
				"http://[2001:db8::ff00:42:8329]/index.html" // IPv6 address
		};
		final QueryModifier modifier = new QueryModifier(0);
		modifier.sitehost = "example.org";
		checkURLs(matchingURLs, nonMatchingURLs, modifier, null);
	}
	
	/**
	 * Test URL matching with a single query constraint on file extension.
	 * @throws MalformedURLException when a test URL is malformed. Should not happen.
	 */
	@Test
	public void testMatchesURLFileExt() throws MalformedURLException {
		final String[] matchingURLs = { "http://example.org/image.html", // most basic matching example
				"http://example.org/image.html#anchor", // with url fragment
				"http://example.org/image.html?key=value#anchor", // with query string and url fragment
		};

		final String[] nonMatchingURLs = { "http://example.org/file.txt", // basic non matching example
				"http://example.org/file.xhtml", // extension ending like the expected one
				"http://example.org/html/example.txt", // extension found in path
				"http://example.org/resource?key=html", // extension found as query parameter value
				"http://example.org/resource#html", // extension found as url fragment
		};
		final QueryModifier modifier = new QueryModifier(0);
		modifier.filetype = "html";
		checkURLs(matchingURLs, nonMatchingURLs, modifier, null);
	}
	
	/**
	 * Test URL matching with combined protocol and host name query modifiers.
	 * @throws MalformedURLException when a test URL is malformed. Should not happen.
	 */
	@Test
	public void testBuildURLFilterProtocolAndHostName() throws MalformedURLException {
		final String[] matchingURLs = { "http://example.org", // most basic matching example
				"http://example.org/", // normalized basic example
				"http://www.example.org/", // with www domain prefix
				"http://example.org:8080", // non default port
				"http://example.org?key=value", // empty path and query string
				"http://example.org?key=value#fragment", // empty path, query string and fragment
				"http://example.org:8080?key=value#fragment", // non default port, empty path, query string and fragment
				"http://example.org:8080/?key=value#fragment", // normalized non default port, empty path, query string and fragment
				"http://example.org#fragment", // empty path and fragment
				"http://example.org/index.html", // with file
				"http://example.org/path/index.html", // with path and file
				"http://example.org:8090/path/index.html", // with non default port, path and file
				"http://example.org/index.html?key=value", // with file and query string
				"http://example.org/index.html?key=value#fragment", // with file, query string and url fragment
		};

		final String[] nonMatchingURLs = { "ftp://domain.test", // basic non matching example
				"ftp://domain.test/", // normalized basic example
				"ftp://example.org/", // only domain matching
				"http://fr.example.org", // domain prefix different from www
				"http://example.net", // only secondary-level domain matching
				"http://test.org", // only top-level domain matching
				"http://example.organic", // domain starting like the one of the filter
				"http://unexample.org", // domain ending like the one of the filter
				"http://example.net/index.html?query=example.org", // with query including the filtered domain
				"http://example.net/index.html#example.org", // with fragment string including the filtered domain
				"http://127.0.0.1/index.html", // IPv4 address
				"http://[2001:db8::ff00:42:8329]/index.html" // IPv6 address
		};
		final QueryModifier modifier = new QueryModifier(0);
		modifier.sitehost = "example.org";
		modifier.protocol = "http";
		checkURLs(matchingURLs, nonMatchingURLs, modifier, null);
	}
	
	/**
	 * Test URL filter build with no constraints at all
	 */
	@Test
	public void testBuilURLFilterEmpty() {
		final QueryModifier modifier = new QueryModifier(0);
		final String filter = QueryParams.buildApproximateURLFilter(modifier, null);
		
		Assert.assertEquals(QueryParams.catchall_pattern.toString(), filter);
	}
	
	/**
	 * Test removal of old modifier(s) when building a search navigation URL.
	 */
	@Test
	public void testRemoveOldModifiersFromNavUrl() {
		final String baseURL = "yacysearch.html?query=test+search+terms";
		
		final String newModifier = "keywords:new";

		final Map<String, String> modifiers2Expected = new HashMap<>();
		/* No existing modifiers */
		modifiers2Expected.put(baseURL, baseURL);
		
		/* No existing modifiers */
		modifiers2Expected.put(baseURL + "+keywords:old", baseURL);
		
		/* One modifier matching the new modifier's name, but with a different value */
		modifiers2Expected.put(baseURL + "+keywords:old", baseURL);
		
		/* One modifier matching the new modifier's name, with the same value */
		modifiers2Expected.put(baseURL + "+keywords:new", baseURL);
		
		/* Two modifiers matching the new modifier's name */
		modifiers2Expected.put(baseURL + "+keywords:old keywords:new", baseURL);
		
		/* One modifier with a different name than the new one */
		modifiers2Expected.put(baseURL + "+site:example.org", baseURL + "+site:example.org");
		
		/* Two modifiers, only one matching the new modifier's name */
		modifiers2Expected.put(baseURL + "+site:example.org keywords:old", baseURL + "+site:example.org");
		
		/* Three modifiers, the one not matching the new modifier's name in the middle of the others */
		modifiers2Expected.put(baseURL + "+keywords:old site:example.org keywords:other", baseURL + "+site:example.org");
		
		/* Three modifiers, only one matching the new modifier's name. The others having two different naming styles. */
		modifiers2Expected.put(baseURL + "+keywords:old /language/en site:example.org keywords:other", baseURL + "+/language/en site:example.org");
		
		for(final Entry<String, String> entry : modifiers2Expected.entrySet()) {
			StringBuilder sb = new StringBuilder(entry.getKey());		
			QueryParams.removeOldModifiersFromNavUrl(sb, newModifier);
			Assert.assertEquals(entry.getValue(), sb.toString());
		}
	}
}
