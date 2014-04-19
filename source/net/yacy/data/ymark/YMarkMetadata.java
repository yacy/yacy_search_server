// YMarkMetadata.java
// (C) 2011 by Stefan Förster, sof@gmx.de, Norderstedt, Germany
// first published 2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2011-03-09 13:50:39 +0100 (Mi, 09 Mrz 2011) $
// $LastChangedRevision: 7574 $
// $LastChangedBy: apfelmaennchen $
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

package net.yacy.data.ymark;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.EnumMap;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.index.Segment;

public class YMarkMetadata {
	private DigestURL uri;
	Document document;
	Segment indexSegment;

	public enum METADATA {
		TITLE,
		DESCRIPTION,
		FAVICON,
		KEYWORDS,
		LANGUAGE,
		CREATOR,
		PUBLISHER,
		CHARSET,
		MIMETYPE,
		SIZE,
		WORDCOUNT,
		IN_URLDB,
		FRESHDATE,
		LOADDATE,
		MODDATE,
		SNIPPET,
		AUTOTAG
	}

	public YMarkMetadata(final DigestURL uri) {
		this.uri = uri;
		this.document = null;
		this.indexSegment = null;
	}

	public YMarkMetadata(final DigestURL uri, final Segment indexSegment) {
		this.uri = uri;
		this.document = null;
		this.indexSegment = indexSegment;
	}

	public YMarkMetadata(final byte[] urlHash, final Segment indexSegment) {
		this.document = null;
		this.indexSegment = indexSegment;
		this.uri = this.indexSegment.fulltext().getURL(ASCII.String(urlHash));
	}

	public YMarkMetadata(final Document document) {
		this.document = document;
		try {
			this.uri = new DigestURL(this.document.dc_identifier());
		} catch (final MalformedURLException e) {
			this.uri = null;
		}
		this.indexSegment = null;
	}

	public Document loadDocument(final LoaderDispatcher loader, ClientIdentification.Agent agent) throws IOException, Failure {
		if(this.document == null) {
			Response response = null;
			response = loader.load(loader.request(this.uri, true, false), CacheStrategy.IFEXIST, Integer.MAX_VALUE, null, agent);
			Document[] docs = response.parse();
			this.document = Document.mergeDocuments(response.url(), response.getMimeType(), docs);
		}
		return this.document;
	}

	public EnumMap<METADATA, String> getMetadata() {
		final EnumMap<METADATA, String> metadata = new EnumMap<METADATA, String>(METADATA.class);
        final URIMetadataNode urlEntry = this.indexSegment.fulltext().getMetadata(this.uri.hash());
        if (urlEntry != null) {
        	metadata.put(METADATA.SIZE, String.valueOf(urlEntry.size()));
        	metadata.put(METADATA.FRESHDATE, ISO8601Formatter.FORMATTER.format(urlEntry.freshdate()));
        	metadata.put(METADATA.LOADDATE, ISO8601Formatter.FORMATTER.format(urlEntry.loaddate()));
        	metadata.put(METADATA.MODDATE, ISO8601Formatter.FORMATTER.format(urlEntry.moddate()));
        	metadata.put(METADATA.SNIPPET, String.valueOf(urlEntry.snippet()));
        	metadata.put(METADATA.WORDCOUNT, String.valueOf(urlEntry.wordCount()));
        	metadata.put(METADATA.MIMETYPE, String.valueOf(urlEntry.doctype()));
        	metadata.put(METADATA.LANGUAGE, urlEntry.language());
        	metadata.put(METADATA.TITLE, urlEntry.dc_title());
        	metadata.put(METADATA.CREATOR, urlEntry.dc_creator());
	        metadata.put(METADATA.KEYWORDS, urlEntry.dc_subject());
	        metadata.put(METADATA.PUBLISHER, urlEntry.dc_publisher());
        }
        return metadata;
	}

	public EnumMap<METADATA, String> loadMetadata() {
		final EnumMap<METADATA, String> metadata = new EnumMap<METADATA, String>(METADATA.class);
        if(this.document != null) {
			metadata.put(METADATA.TITLE, this.document.dc_title());
			metadata.put(METADATA.CREATOR, this.document.dc_creator());
			metadata.put(METADATA.KEYWORDS, this.document.dc_subject(' '));
			metadata.put(METADATA.PUBLISHER, this.document.dc_publisher());
			metadata.put(METADATA.DESCRIPTION, this.document.dc_description().length > 0 ? this.document.dc_description()[0] : "");
			metadata.put(METADATA.MIMETYPE, this.document.dc_format());
			metadata.put(METADATA.LANGUAGE, this.document.dc_language());
			metadata.put(METADATA.CHARSET, this.document.getCharset());
			// metadata.put(METADATA.SIZE, String.valueOf(document.getTextLength()));
		}
		return metadata;
	}
}
