// (C) 2016 by luccioman; http://github.com/luccioman
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

package net.yacy.server;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Unit tests for serverObjects
 */
public class serverObjectsTest {

	/**
	 * This method allow manual check of the output obtained with different file
	 * types encoding reserved characters.
	 * 
	 * @throws UnsupportedEncodingException
	 */
	@Test
	public void testPut() throws UnsupportedEncodingException {
		String raw = "query with reserved chars : \"#<>?`{}/:;=@[\\]^|\t&";
		String urlEncoded = URLEncoder.encode(raw, StandardCharsets.UTF_8.name());

		serverObjects prop = new serverObjects();

		prop.put("raw", raw);
		System.out.println("no file type raw : " + prop.get("raw"));

		prop.put("urlEncoded", urlEncoded);
		System.out.println("no file type urlEncoded : " + prop.get("urlEncoded") + "\n");

		prop.putHTML("html raw", raw);
		System.out.println("html raw : " + prop.get("html raw"));

		prop.putHTML("html urlEncoded", urlEncoded);
		System.out.println("html urlEncoded : " + prop.get("html urlEncoded") + "\n");

		prop.putXML("xml raw", raw);
		System.out.println("xml raw : " + prop.get("xml raw"));

		prop.putHTML("xml urlEncoded", urlEncoded);
		System.out.println("xml urlEncoded : " + prop.get("xml urlEncoded") + "\n");

		prop.putJSON("json raw", raw);
		System.out.println("json raw : " + prop.get("json raw"));

		prop.putJSON("json urlEncoded", urlEncoded);
		System.out.println("json urlEncoded : " + prop.get("json urlEncoded") + "\n");
	}

}
