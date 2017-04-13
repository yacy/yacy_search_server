/**
 *  URIMetadataNodeTest
 *  part of YaCy
 *  Copyright 2016 by luccioman; https://github.com/luccioman
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.data.meta;

import java.awt.Dimension;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.parser.html.IconEntry;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

/**
 * Unit tests for URIMetadataNode class.
 * 
 * @author luc
 *
 */
public class URIMetadataNodeTest {

	/**
	 * Three standard icons with different sizes, one non-standard with a larger
	 * size
	 */
	@Test
	public final void testGetIcons4Items() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode
				.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
						new String[] { "somehost.org/static/images/icon16.png", "somehost.org/static/images/icon32.png",
								"somehost.org/static/images/icon64.png",
								"somehost.org/static/images/iconApple128.png" });
		List<String> protocols = CollectionConfiguration
				.protocolList2indexedList(Arrays.asList(new String[] { "http", "https", "https", "http" }));
		metadataNode.setField(CollectionSchema.icons_protocol_sxt.getSolrFieldName(), protocols);
		metadataNode.setField(CollectionSchema.icons_rel_sxt.getSolrFieldName(),
				new String[] { "icon", "icon", "icon", "apple-touch-icon" });
		metadataNode.setField(CollectionSchema.icons_sizes_sxt.getSolrFieldName(),
				new String[] { "16x24", "32x32", "58x64", "128x128" });

		Collection<IconEntry> icons = metadataNode.getIcons();
		int nb = 0;
		/* Check results consistency */
		for (IconEntry icon : icons) {
			if ("http://somehost.org/static/images/icon16.png".equals(icon.getUrl().toNormalform(false))) {
				Assert.assertEquals(1, icon.getSizes().size());
				Dimension size = icon.getSizes().iterator().next();
				Assert.assertEquals(16, size.width);
				Assert.assertEquals(24, size.height);
				Assert.assertEquals(1, icon.getRel().size());
				Assert.assertEquals("icon", icon.getRel().iterator().next());
				nb++;
			} else if ("https://somehost.org/static/images/icon32.png".equals(icon.getUrl().toNormalform(false))) {
				Assert.assertEquals(1, icon.getSizes().size());
				Dimension size = icon.getSizes().iterator().next();
				Assert.assertEquals(32, size.width);
				Assert.assertEquals(32, size.height);
				Assert.assertEquals(1, icon.getRel().size());
				Assert.assertEquals("icon", icon.getRel().iterator().next());
				nb++;
			} else if ("https://somehost.org/static/images/icon64.png".equals(icon.getUrl().toNormalform(false))) {
				Assert.assertEquals(1, icon.getSizes().size());
				Dimension size = icon.getSizes().iterator().next();
				Assert.assertEquals(58, size.width);
				Assert.assertEquals(64, size.height);
				Assert.assertEquals(1, icon.getRel().size());
				Assert.assertEquals("icon", icon.getRel().iterator().next());
				nb++;
			} else if ("http://somehost.org/static/images/iconApple128.png".equals(icon.getUrl().toNormalform(false))) {
				Assert.assertEquals(1, icon.getSizes().size());
				Dimension size = icon.getSizes().iterator().next();
				Assert.assertEquals(128, size.width);
				Assert.assertEquals(128, size.height);
				Assert.assertEquals(1, icon.getRel().size());
				Assert.assertEquals("apple-touch-icon", icon.getRel().iterator().next());
				nb++;
			}
		}
		Assert.assertEquals(4, nb);
	}

	/**
	 * Only icons_urlstub_sxt field valued
	 */
	@Test
	public final void testGetIconsOnlyIconsUrlstubSxt() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode
				.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
						new String[] { "somehost.org/static/images/icon16.png", "somehost.org/static/images/icon32.png",
								"somehost.org/static/images/icon64.png",
								"somehost.org/static/images/iconApple124.png" });

		Collection<IconEntry> icons = metadataNode.getIcons();
		Assert.assertEquals(4, icons.size());

	}

	/**
	 * Only one standard icon
	 */
	@Test
	public final void testGetIcons1Item() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
				new String[] { "somehost.org/static/images/icon16.png" });
		List<String> protocols = CollectionConfiguration
				.protocolList2indexedList(Arrays.asList(new String[] { "http" }));
		metadataNode.setField(CollectionSchema.icons_protocol_sxt.getSolrFieldName(), protocols);
		metadataNode.setField(CollectionSchema.icons_rel_sxt.getSolrFieldName(), new String[] { "icon" });
		metadataNode.setField(CollectionSchema.icons_sizes_sxt.getSolrFieldName(), new String[] { "16x16" });

		Collection<IconEntry> icons = metadataNode.getIcons();
		Assert.assertEquals(1, icons.size());
		IconEntry icon = icons.iterator().next();
		Assert.assertEquals(1, icon.getSizes().size());
		Dimension size = icon.getSizes().iterator().next();
		Assert.assertEquals(16.0, size.getWidth(), 0.0);
		Assert.assertEquals(16.0, size.getHeight(), 0.0);
	}

	/**
	 * No Icon
	 */
	@Test
	public final void testGetIconsNoIcon() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));

		Collection<IconEntry> icons = metadataNode.getIcons();
		Assert.assertEquals(0, icons.size());
	}

	/**
	 * Check encoding/decoding consistency
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public final void testEncodeDecode() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode
				.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
						new String[] { "somehost.org/static/images/icon16.png", "somehost.org/static/images/icon32.png",
								"somehost.org/static/images/icon64.png",
								"somehost.org/static/images/iconApple128.png" });
		List<String> protocols = CollectionConfiguration
				.protocolList2indexedList(Arrays.asList(new String[] { "http", "https", "https", "http" }));
		metadataNode.setField(CollectionSchema.icons_protocol_sxt.getSolrFieldName(), protocols);
		metadataNode.setField(CollectionSchema.icons_rel_sxt.getSolrFieldName(),
				new String[] { "icon", "icon", "icon", "apple-touch-icon" });
		metadataNode.setField(CollectionSchema.icons_sizes_sxt.getSolrFieldName(),
				new String[] { "16x24", "32x32", "58x64", "128x128" });

		String encoded = metadataNode.toString();
		URIMetadataNode decoded = URIMetadataNode.importEntry(encoded, "dht");
		Collection<IconEntry> icons = decoded.getIcons();

		/*
		 * Only icon which is the closest to 16x16 pixels is encoded, and sizes
		 * and rel attribute are not encoded
		 */
		Assert.assertEquals(1, icons.size());
		IconEntry icon = icons.iterator().next();

		Assert.assertEquals(0, icon.getSizes().size());

		Assert.assertEquals("http://somehost.org/static/images/icon16.png", icon.getUrl().toNormalform(false));

		Assert.assertEquals(1, icon.getRel().size());
		Assert.assertEquals("icon", icon.getRel().iterator().next());
	}
	
	/**
	 * Check encoding/decoding consistency when document has no indexed icon
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public final void testEncodeDecodeNoIcon() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));

		String encoded = metadataNode.toString();
		URIMetadataNode decoded = URIMetadataNode.importEntry(encoded, "dht");
		Collection<IconEntry> icons = decoded.getIcons();

		Assert.assertEquals(0, icons.size());
	}

}
