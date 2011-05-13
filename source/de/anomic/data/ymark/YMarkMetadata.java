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

package de.anomic.data.ymark;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.UTF8;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.repository.LoaderDispatcher;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;
import de.anomic.search.Segments;

public class YMarkMetadata {
	private DigestURI uri;
	Document document;
	Segments indexSegment;
	
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
	
	public YMarkMetadata(final DigestURI uri) {
		this.uri = uri;
		this.document = null;
		this.indexSegment = null;
	}
	
	public YMarkMetadata(final DigestURI uri, final Segments indexSegment) {
		this.uri = uri;
		this.document = null;
		this.indexSegment = indexSegment;
	}
	
	public YMarkMetadata(final Document document) {
		this.document = document;
		try {
			this.uri = new DigestURI(this.document.dc_identifier());
		} catch (MalformedURLException e) {
			this.uri = null;
		}
		this.indexSegment = null;
	}	
	
	public void loadDocument(LoaderDispatcher loader) throws IOException, Failure {
		if(document == null) {
			Response response = null;
			response = loader.load(loader.request(this.uri, true, false), CrawlProfile.CacheStrategy.IFEXIST, Long.MAX_VALUE, true);
			this.document = Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());			
		}
	}
	
	public EnumMap<METADATA, String> getMetadata() {
		final EnumMap<METADATA, String> metadata = new EnumMap<METADATA, String>(METADATA.class);
        final URIMetadataRow urlEntry = this.indexSegment.segment(Segments.Process.PUBLIC).urlMetadata().load(this.uri.hash());
        if (urlEntry != null) {
        	metadata.put(METADATA.SIZE, String.valueOf(urlEntry.size()));
        	metadata.put(METADATA.FRESHDATE, ISO8601Formatter.FORMATTER.format(urlEntry.freshdate()));
        	metadata.put(METADATA.LOADDATE, ISO8601Formatter.FORMATTER.format(urlEntry.loaddate()));
        	metadata.put(METADATA.MODDATE, ISO8601Formatter.FORMATTER.format(urlEntry.moddate()));
        	metadata.put(METADATA.SNIPPET, String.valueOf(urlEntry.snippet()));
        	metadata.put(METADATA.WORDCOUNT, String.valueOf(urlEntry.wordCount()));
        	metadata.put(METADATA.MIMETYPE, String.valueOf(urlEntry.doctype()));
        	metadata.put(METADATA.LANGUAGE, UTF8.String(urlEntry.language()));
        	
        	final URIMetadataRow.Components meta = urlEntry.metadata();
        	if (meta != null) {	        	
	        	metadata.put(METADATA.TITLE, meta.dc_title()); 
	        	metadata.put(METADATA.CREATOR, meta.dc_creator());
	        	metadata.put(METADATA.KEYWORDS, meta.dc_subject());
	        	metadata.put(METADATA.PUBLISHER, meta.dc_publisher());
        	}
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
			metadata.put(METADATA.DESCRIPTION, this.document.dc_description());
			metadata.put(METADATA.MIMETYPE, this.document.dc_format());
			metadata.put(METADATA.LANGUAGE, this.document.dc_language());
			metadata.put(METADATA.CHARSET, this.document.getCharset());
			// metadata.put(METADATA.SIZE, String.valueOf(document.getTextLength()));
		}
		return metadata;
	}
	
	public TreeMap<String,Word> getWordCounts() {
		if (this.document != null) {
            return sortWordCounts(new Condenser(this.document, true, true, LibraryProvider.dymLib).words());
        }
		return new TreeMap<String, Word>();
	}
	
	public static TreeMap<String,Word> sortWordCounts(final Map<String, Word> unsorted_words) {		
        final TreeMap<String, Word> sorted_words = new TreeMap<String, Word>(new YMarkWordCountComparator(unsorted_words));
        sorted_words.putAll(unsorted_words);
        return sorted_words;	
    }
	
}
