// SingleDocumentMatcher.java
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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.Query;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.LuceneQParserPlugin;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.update.DocumentBuilder;

import net.yacy.search.schema.CollectionSchema;

/**
 * Provide utility functions to check if a single indexable Document matches a
 * given Solr query.
 */
public abstract class SingleDocumentMatcher {
	
	/**
	 * @param query a Solr query string to parse
	 * @param targetCore an open Solr index core that is the target of the query
	 * @return a lucene Query instance parsed from the given Solr query string on the provided Solr core.
	 * @throws SyntaxError when the query syntax is not valid
	 * @throws SolrException when a query required element is missing, or when a problem occurred when accessing the target core
	 */
	public static Query toLuceneQuery(final String query, final SolrCore targetCore) throws SyntaxError, SolrException {
		if (query == null || targetCore == null) {
			throw new IllegalArgumentException("All parameters must be non null");
		}
		
		final SolrQuery solrQuery = new SolrQuery(query);
		solrQuery.setParam(CommonParams.DF, CollectionSchema.text_t.getSolrFieldName());

		final SolrQueryRequestBase solrRequest = new SolrQueryRequestBase(targetCore, solrQuery) {
		};

		@SuppressWarnings("resource")
        final LuceneQParserPlugin luceneParserPlugin = new LuceneQParserPlugin();
		final QParser solrParser = luceneParserPlugin.createParser(query, null, solrRequest.getParams(), solrRequest);
		return solrParser.parse();
	}
	
	/**
	 * Check a given Solr document against a Solr query, without requesting a Solr
	 * index, but using instead in-memory Lucene utility. This lets checking if a
	 * single document matches some criterias, before adding it to a Solr index.
	 * 
	 * @param solrDoc
	 *            the Solr document to check
	 * @param query
	 *            a standard Solr query string
	 * @param core
	 *            the Solr index core holding the Solr schema of the document
	 * @return true when the document matches the given Solr query
	 * @throws SyntaxError
	 *             when the query String syntax is not valid
	 * @throws SolrException when a query required element is missing, or when a problem occurred when accessing the target core
	 * @throws IllegalArgumentException
	 *             when a parameter is null.
	 * @see <a href=
	 *      "http://lucene.apache.org/solr/guide/6_6/the-standard-query-parser.html">The
	 *      Solr Standard Query Parser</a>
	 */
	public static boolean matches(final SolrInputDocument solrDoc, final String query, final SolrCore core)
			throws SyntaxError, IllegalArgumentException {
		if (solrDoc == null || query == null || core == null) {
			throw new IllegalArgumentException("All parameters must be non null");
		}
		final IndexSchema schema = core.getLatestSchema();
		if (schema == null) {
			throw new IllegalArgumentException("All parameters must be non null");
		}

		final org.apache.lucene.document.Document luceneDoc = DocumentBuilder.toDocument(solrDoc, schema);

		final Analyzer indexAnalyzer = schema.getIndexAnalyzer();

		/*
		 * Using the Lucene RAMDirectory could be an alternative, but it is slower with
		 * a larger memory footprint
		 */
		final MemoryIndex index = MemoryIndex.fromDocument(luceneDoc, indexAnalyzer);

		final Query luceneQuery = toLuceneQuery(query, core);

		final float score = index.search(luceneQuery);

		return score > 0.0f;
	}

}
