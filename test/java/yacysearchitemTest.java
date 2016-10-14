
/**
 *  yacysearchitemTest
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

import java.awt.Dimension;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

/**
 * Unit tests for yacysearchitem class.
 * 
 * @author luc
 *
 */
public class yacysearchitemTest {

	/**
	 * Three standard icons with different sizes, one non-standard with a larger
	 * size
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public final void testGetFaviconURL() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode
				.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
						new String[] { "someHost.org/static/images/icon16.png", "somehost.org/static/images/icon32.png",
								"somehost.org/static/images/icon64.png",
								"somehost.org/static/images/iconApple124.png" });
		List<String> protocols = CollectionConfiguration
				.protocolList2indexedList(Arrays.asList(new String[] { "http", "http", "http", "http" }));
		metadataNode.setField(CollectionSchema.icons_protocol_sxt.getSolrFieldName(), protocols);
		metadataNode.setField(CollectionSchema.icons_rel_sxt.getSolrFieldName(),
				new String[] { "icon", "icon", "icon", "apple-touch-icon" });
		metadataNode.setField(CollectionSchema.icons_sizes_sxt.getSolrFieldName(),
				new String[] { "16x16", "32x32", "64x64", "128x128" });

		/* Search for a size present in icons collection */
		DigestURL faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(32, 32));
		Assert.assertNotNull(faviconURL);
		Assert.assertEquals("http://somehost.org/static/images/icon32.png", faviconURL.toNormalform(false));

		/* Search for a size not in icons collection */
		faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(40, 40));
		Assert.assertNotNull(faviconURL);
		Assert.assertEquals("http://somehost.org/static/images/icon32.png", faviconURL.toNormalform(false));

		/*
		 * Search for a size equals to non-standard : standard icon is stil
		 * preffered
		 */
		faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(128, 128));
		Assert.assertNotNull(faviconURL);
		Assert.assertEquals("http://somehost.org/static/images/icon64.png", faviconURL.toNormalform(false));
	}

	/**
	 * Only non-standard icons
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public final void testGetFaviconURLNonStandard() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode
				.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
						new String[] { "somehost.org/static/images/mask32.png",
								"somehost.org/static/images/fluid.64.png",
								"somehost.org/static/images/iconApple124.png" });
		List<String> protocols = CollectionConfiguration
				.protocolList2indexedList(Arrays.asList(new String[] { "http", "http", "http" }));
		metadataNode.setField(CollectionSchema.icons_protocol_sxt.getSolrFieldName(), protocols);
		metadataNode.setField(CollectionSchema.icons_rel_sxt.getSolrFieldName(),
				new String[] { "mask-icon", "fluid-icon", "apple-touch-icon" });
		metadataNode.setField(CollectionSchema.icons_sizes_sxt.getSolrFieldName(),
				new String[] { "32x32", "64x64", "128x128" });

		/* Non standard icon is returned as fallback */
		DigestURL faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(32, 32));
		Assert.assertNotNull(faviconURL);
		Assert.assertEquals("http://somehost.org/static/images/mask32.png", faviconURL.toNormalform(false));
	}

	/**
	 * One standard icon with multiple sizes
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public final void testGetFaviconURLMultiSizes() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
				new String[] { "somehost.org/static/images/favicon.ico" });
		List<String> protocols = CollectionConfiguration
				.protocolList2indexedList(Arrays.asList(new String[] { "http" }));
		metadataNode.setField(CollectionSchema.icons_protocol_sxt.getSolrFieldName(), protocols);
		metadataNode.setField(CollectionSchema.icons_rel_sxt.getSolrFieldName(), new String[] { "icon" });
		metadataNode.setField(CollectionSchema.icons_sizes_sxt.getSolrFieldName(),
				new String[] { "16x16 32x32 64x64", });

		/* Search for a size in sizes set */
		DigestURL faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(32, 32));
		Assert.assertNotNull(faviconURL);
		Assert.assertEquals("http://somehost.org/static/images/favicon.ico", faviconURL.toNormalform(false));

		/* Search for a size not in sizes set */
		faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(40, 40));
		Assert.assertNotNull(faviconURL);
		Assert.assertEquals("http://somehost.org/static/images/favicon.ico", faviconURL.toNormalform(false));
	}

	/**
	 * One standard icon with no size
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public final void testGetFaviconURLNoSize() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
				new String[] { "somehost.org/static/images/favicon.ico" });
		List<String> protocols = CollectionConfiguration
				.protocolList2indexedList(Arrays.asList(new String[] { "http" }));
		metadataNode.setField(CollectionSchema.icons_protocol_sxt.getSolrFieldName(), protocols);
		metadataNode.setField(CollectionSchema.icons_rel_sxt.getSolrFieldName(), new String[] { "icon" });

		DigestURL faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(32, 32));
		Assert.assertNotNull(faviconURL);
		Assert.assertEquals("http://somehost.org/static/images/favicon.ico", faviconURL.toNormalform(false));
	}
	
	/**
	 * One non-standard icon with no size
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public final void testGetFaviconURLNonStandardNoSize() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://somehost.org"));
		metadataNode.setField(CollectionSchema.icons_urlstub_sxt.getSolrFieldName(),
				new String[] { "somehost.org/static/images/favicon.png" });
		List<String> protocols = CollectionConfiguration
				.protocolList2indexedList(Arrays.asList(new String[] { "http" }));
		metadataNode.setField(CollectionSchema.icons_protocol_sxt.getSolrFieldName(), protocols);
		metadataNode.setField(CollectionSchema.icons_rel_sxt.getSolrFieldName(), new String[] { "appel-touch-icon" });

		DigestURL faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(32, 32));
		Assert.assertNotNull(faviconURL);
		Assert.assertEquals("http://somehost.org/static/images/favicon.png", faviconURL.toNormalform(false));
	}

	/**
	 * No icon in document
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public final void testGetFaviconURLNoIcon() throws MalformedURLException {
		URIMetadataNode metadataNode = new URIMetadataNode(new DigestURL("http://someHost.org"));

		/* Default fallback favicon URL should be generated */
		DigestURL faviconURL = yacysearchitem.getFaviconURL(metadataNode, new Dimension(32, 32));
		Assert.assertEquals("http://somehost.org/favicon.ico", faviconURL.toNormalform(false));
	}

}
