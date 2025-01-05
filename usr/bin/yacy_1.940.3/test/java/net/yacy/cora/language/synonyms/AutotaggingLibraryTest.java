// AutotaggingLibraryTest.java
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

package net.yacy.cora.language.synonyms;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.Tagging.SOTuple;

/**
 * Unit tests for the {@link AutotaggingLibrary} class.
 * @author luccioman
 *
 */
public class AutotaggingLibraryTest {

	/**
	 * Test tags search from term URL
	 * @throws IOException when an unexpected error occurred
	 */
	@Test
	public void testGetTagsFromTermURL() throws IOException {
		final ConcurrentHashMap<String, Tagging> vocabularies = new ConcurrentHashMap<String, Tagging>();
		final AutotaggingLibrary lib = new AutotaggingLibrary(vocabularies);
		
		Map<String, SOTuple> table = new LinkedHashMap<String, Tagging.SOTuple>();
		/* Sample types extracted from https://www.w3.org/TR/activitystreams-vocabulary/#activity-types */
		table.put("Accept", new Tagging.SOTuple(Tagging.normalizeTerm("Accept"), null));
		table.put("Add", new Tagging.SOTuple(Tagging.normalizeTerm("Add"), null));
		Tagging voc = new Tagging("activitystream", null, "https://www.w3.org/ns/activitystreams#", table);
		voc.setMatchFromLinkedData(true);
		vocabularies.put("activitystream", voc);
		
		table = new LinkedHashMap<String, Tagging.SOTuple>();
		/* Sample classes extracted from http://dublincore.org/documents/dcmi-terms/#H2 */
		table.put("Agent", new Tagging.SOTuple(Tagging.normalizeTerm("Agent"), null));
		table.put("MediaType", new Tagging.SOTuple(Tagging.normalizeTerm("MediaType"), null));
		voc = new Tagging("DublinCore", null, "http://purl.org/dc/terms/", table);
		voc.setMatchFromLinkedData(true);
		vocabularies.put("DublinCore", voc);
		
		table = new LinkedHashMap<String, Tagging.SOTuple>();
		/* Sample types extracted from http://schema.org/docs/full.html */
		table.put("Article", new Tagging.SOTuple(Tagging.normalizeTerm("Article"), null));
		table.put("Blog", new Tagging.SOTuple(Tagging.normalizeTerm("Blog"), null));
		voc = new Tagging("Schema.org", null, "http://schema.org/", table);
		voc.setMatchFromLinkedData(true);
		vocabularies.put("Schema.org", voc);
		
		
		/* Term URL with fragment, path and file parts */
		Set<Tagging.Metatag> tags = lib.getTagsFromTermURL(new DigestURL("https://www.w3.org/ns/activitystreams#Accept"));
		Assert.assertEquals(1, tags.size());
		Tagging.Metatag tag = tags.iterator().next();
		Assert.assertEquals("activitystream", tag.getVocabularyName());
		Assert.assertEquals("Accept", tag.getObject());
		
		/* Alternate accepted term URL form with http protocol */
		tags = lib.getTagsFromTermURL(new DigestURL("http://www.w3.org/ns/activitystreams#Accept"));
		Assert.assertEquals(1, tags.size());
		tag = tags.iterator().next();
		Assert.assertEquals("activitystream", tag.getVocabularyName());
		Assert.assertEquals("Accept", tag.getObject());
		
		/* Term URL with path and file parts */
		tags = lib.getTagsFromTermURL(new DigestURL("http://purl.org/dc/terms/MediaType"));
		Assert.assertEquals(1, tags.size());
		tag = tags.iterator().next();
		Assert.assertEquals("DublinCore", tag.getVocabularyName());
		Assert.assertEquals("MediaType", tag.getObject());
		
		/* Term URL with file part only */
		tags = lib.getTagsFromTermURL(new DigestURL("http://schema.org/Article"));
		Assert.assertEquals(1, tags.size());
		tag = tags.iterator().next();
		Assert.assertEquals("Schema.org", tag.getVocabularyName());
		Assert.assertEquals("Article", tag.getObject());
		
		/* Missing terms */
		tags = lib.getTagsFromTermURL(new DigestURL("https://www.w3.org/ns/activitystreams#MissingTerm"));
		Assert.assertEquals(0, tags.size());
		
		tags = lib.getTagsFromTermURL(new DigestURL("https://www.w3.org/ns/activitystreams#Accepting"));
		Assert.assertEquals(0, tags.size());
		
		/* Wrong namespace */
		tags = lib.getTagsFromTermURL(new DigestURL("https://example.org/namespace#Accept"));
		Assert.assertEquals(0, tags.size());
	}

}
