// SolrjResponseWriter.java
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

package net.yacy.cora.federate.solr.responsewriter;

import java.io.IOException;
import java.io.Writer;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.request.SolrQueryRequest;

/**
 * Interface for Solr response writers capable of rendering a Solr response
 * obtained with the Solrj client.
 */
public interface SolrjResponseWriter {

	/**
	 * Append to the writer a representation of the given Solr response. This is the
	 * responsibility of the caller to close the writer.
	 * 
	 * @param writer
	 *            an open writer
	 * @param request
	 *            the initial Solr request
	 * @param coreName
	 *            the requested Solr core name
	 * @param rsp
	 *            the Solr response
	 * @throws IOException
	 *             when a write error occurred
	 */
	public void write(final Writer writer, final SolrQueryRequest request, final String coreName,
			final QueryResponse rsp) throws IOException;

}
