// SingleDocumentMatcherTest.java
// ---------------------------
// Copyright 2018 by luccioman; https://github.com/luccioman
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

package net.yacy.search.index;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SyntaxError;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

/**
 * Unit tests for the {@link SingleDocumentMatcher} class.
 */
public class SingleDocumentMatcherTest {

	/** Embedded Solr test instance */
	private static EmbeddedInstance EMBEDDED_INSTANCE;

	/** The configuration of the main Solr collection */
	private static CollectionConfiguration COLLECTION_CONFIG;

	/**
	 * Inits the embedded Solr index used for these tests.
	 */
	@BeforeClass
	public static void initSolr() {
		final File solr_config = new File("defaults/solr");
		final File storage = new File("test/DATA/INDEX/webportal/SEGMENTS/text/solr/");
		storage.mkdirs();
		System.out.println("setup EmeddedSolrConnector using config dir: " + solr_config.getAbsolutePath());
		try {
			SingleDocumentMatcherTest.EMBEDDED_INSTANCE = new EmbeddedInstance(solr_config, storage,
					CollectionSchema.CORE_NAME, new String[] { CollectionSchema.CORE_NAME, WebgraphSchema.CORE_NAME });
		} catch (final IOException ex) {
			Assert.fail("IOException on embedded Solr initialization");
		}

		final File config = new File("defaults/solr.collection.schema");
		try {
			SingleDocumentMatcherTest.COLLECTION_CONFIG = new CollectionConfiguration(config, true);
		} catch (final IOException e) {
			Assert.fail("IOException on collection configuration initialization");
		}
	}

	/**
	 * Closes the embedded Solr index.
	 */
	@AfterClass
	public static void finalizeTesting() {
		SingleDocumentMatcherTest.EMBEDDED_INSTANCE.close();
	}

	/**
	 * @throws Exception
	 *             when an unexpected exception occurred
	 */
	@Test
	public void testMatches() throws Exception {
		final CollectionConfiguration collectionConfig = SingleDocumentMatcherTest.COLLECTION_CONFIG;
		final SolrCore solrCore = SingleDocumentMatcherTest.EMBEDDED_INSTANCE.getDefaultCore();

		final SolrInputDocument solrDoc = new SolrInputDocument();
		final DigestURL docUrl = new DigestURL("http://example.com/");
		/* Using fields active in the defaults/solr.collection.schema */
		collectionConfig.add(solrDoc, CollectionSchema.id, ASCII.String(docUrl.hash()));
		collectionConfig.add(solrDoc, CollectionSchema.sku, docUrl.toNormalform(true));
		collectionConfig.add(solrDoc, CollectionSchema.http_unique_b, true);
		collectionConfig.add(solrDoc, CollectionSchema.title, Arrays.asList(new String[] { "Lorem ipsum" }));
		collectionConfig.add(solrDoc, CollectionSchema.host_s, "example.com");
		collectionConfig.add(solrDoc, CollectionSchema.last_modified, new Date());
		collectionConfig.add(solrDoc, CollectionSchema.text_t,
				"Lorem ipsum dolor sit amet, consectetur adipisicing elit,  sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
		collectionConfig.add(solrDoc, CollectionSchema.size_i, 126);

		/* query on the default field */
		Assert.assertFalse(SingleDocumentMatcher.matches(solrDoc, "absent", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "adipisicing", solrCore));

		/* query on a multi valued text field */
		Assert.assertFalse(SingleDocumentMatcher.matches(solrDoc, "title:test", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "title:ipsum", solrCore));

		/* query on a string field */
		Assert.assertFalse(SingleDocumentMatcher.matches(solrDoc, "host_s:example.org", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "host_s:example.com", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "host_s:example.*", solrCore));
		
		/* query on a boolean field */
		Assert.assertFalse(SingleDocumentMatcher.matches(solrDoc, "http_unique_b:false", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "http_unique_b:true", solrCore));

		final Calendar yesterdayCal = new GregorianCalendar();
		yesterdayCal.add(Calendar.DAY_OF_MONTH, -1);
		final String yesterday = ISO8601Formatter.FORMATTER.format(yesterdayCal.getTime());

		final Calendar tomorrowCal = new GregorianCalendar();
		tomorrowCal.add(Calendar.DAY_OF_MONTH, 1);
		final String tomorrow = ISO8601Formatter.FORMATTER.format(tomorrowCal.getTime());

		/* range query on a date field */
		Assert.assertFalse(SingleDocumentMatcher.matches(solrDoc, "last_modified:[" + tomorrow + " TO * ]", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc,
				"last_modified:[" + yesterday + " TO " + tomorrow + "]", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "last_modified:[" + yesterday + " TO * ]", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "last_modified:[ * TO " + tomorrow + "]", solrCore));

		/* range query on an integer field */ 
		Assert.assertFalse(SingleDocumentMatcher.matches(solrDoc, "size_i:[ 0 TO 50 ]", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "size_i:[ 0 TO * ]", solrCore));
		Assert.assertTrue(SingleDocumentMatcher.matches(solrDoc, "size_i:[ * TO 200 ]", solrCore));
	}

	/**
	 * @throws Exception
	 *             when an unexpected exception occurred
	 */
	@Test
	public void testMatchesSyntaxError() throws Exception {
		final CollectionConfiguration collectionConfig = SingleDocumentMatcherTest.COLLECTION_CONFIG;
		final SolrCore solrCore = SingleDocumentMatcherTest.EMBEDDED_INSTANCE.getDefaultCore();

		final SolrInputDocument solrDoc = new SolrInputDocument();
		collectionConfig.add(solrDoc, CollectionSchema.id, ASCII.String(new DigestURL("http://example.com").hash()));
		collectionConfig.add(solrDoc, CollectionSchema.title, Arrays.asList(new String[] { "Lorem ipsum" }));
		collectionConfig.add(solrDoc, CollectionSchema.host_s, "example.com");
		collectionConfig.add(solrDoc, CollectionSchema.last_modified, new Date());
		collectionConfig.add(solrDoc, CollectionSchema.text_t,
				"Lorem ipsum dolor sit amet, consectetur adipisicing elit,  sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
		collectionConfig.add(solrDoc, CollectionSchema.size_i, 126);

		try {
			SingleDocumentMatcher.matches(solrDoc, ":", solrCore);
			Assert.fail("Should have raised a syntax error");
		} catch (final SyntaxError e) {
			return;
		}
	}

}
