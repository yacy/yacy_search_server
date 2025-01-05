/**
 *
 */
package net.yacy.document.parser.rdfa.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.rdfa.IRDFaTriple;

/**
 * @author fgandon
 *
 */
public class RDFaParser extends AbstractParser implements Parser {

    private final htmlParser hp;

	public RDFaParser() {
		super("RDFa Parser");
		this.hp = new htmlParser();

		this.SUPPORTED_EXTENSIONS.add("html");
                this.SUPPORTED_EXTENSIONS.add("htm");
                this.SUPPORTED_EXTENSIONS.add("xhtml");                
		this.SUPPORTED_EXTENSIONS.add("php");
		this.SUPPORTED_MIME_TYPES.add("text/html");
		this.SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
	}

	@Override
    public Document[] parse(
            final DigestURL url,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source) throws Failure,
			InterruptedException {

		if(!source.markSupported()) {
			throw new Failure("RDFaParser needs an input stream with mark/reset operations supported.", url);
		}
		final int maxBytes = 10 * 1024;
		source.mark(maxBytes);
		
		Document[] htmlDocs = this.hp.parse(url, mimeType, charset, scraper, timezoneOffset, source);
		
		boolean resetDone;
		try {
			source.reset();
			resetDone = true;
		} catch (final IOException e1) {
			ConcurrentLog.warn("RDFA PARSER",
					"Could not reset stream to beginning : only HTML has been parsed. Document may be larger than limit (" + maxBytes + " bytes.)");
			resetDone = false;
		}

		Document[] retDocs;
		if (resetDone) {
			Document rdfaDoc = parseRDFa(url, mimeType, charset, source);
			retDocs = new Document[htmlDocs.length + 1];
			for (int i = 0; i < htmlDocs.length; i++) {
				retDocs[i] = htmlDocs[i];
			}
			retDocs[retDocs.length - 1] = rdfaDoc;
		} else {
			retDocs = htmlDocs;
		}
		return retDocs;
	}

	private static Document parseRDFa(DigestURL url, String mimeType,
			String charset, InputStream source) {
		RDFaTripleImpl triple;
		IRDFaTriple[] allTriples = null;
		try {
			triple = new RDFaTripleImpl(new InputStreamReader(source), url
					.toString());
			allTriples = triple.parse();

		} catch (final Exception e) {
			ConcurrentLog.warn("RDFA PARSER", "Triple extraction failed");
		}

		Document doc = new Document(url, mimeType, charset, null, null, null, singleList(""), "",
				"", null, new ArrayList<String>(0), 0, 0, null, null, null, null, false, new Date());

		try {
			if (allTriples.length > 0)
				doc = convertAllTriplesToDocument(url, mimeType, charset,
						allTriples);

		} catch (final Exception e) {
			ConcurrentLog.warn("RDFA PARSER",
					"Conversion triple to document failed");
		}
		return doc;
	}

	private static Document convertAllTriplesToDocument(DigestURL url,
			String mimeType, String charset, IRDFaTriple[] allTriples) {

		//Set<String> languages = new HashSet<String>(2);
		Set<String> keywords = new HashSet<String>(allTriples.length);
		//Set<String> sections = new HashSet<String>(5);
		String all = "";

		for (IRDFaTriple irdFaTriple : allTriples) {
			// addNotEmptyValuesToSet(keywords, irdFaTriple.getLanguage());
			// addNotEmptyValuesToSet(keywords,
			// irdFaTriple.getSubjectNodeURI());
			// addNotEmptyValuesToSet(keywords, irdFaTriple.getSubjectURI());
			// addNotEmptyValuesToSet(keywords, irdFaTriple.getPropertyURI());
			// addNotEmptyValuesToSet(keywords, irdFaTriple.getObjectNodeURI());
			// addNotEmptyValuesToSet(keywords, irdFaTriple.getObjectURI());
			// addNotEmptyValuesToSet(keywords, irdFaTriple.getValue());
			addNotEmptyValuesToSet(keywords, irdFaTriple.getPropertyURI() + "Z"
					+ irdFaTriple.getValue());
		}
		for (String string : keywords) {
			string = string.replace(":", "X");
			string = string.replace("_", "Y");
			string = string.replace(" ", "Y");
			string = string.replace(".", "Y");
			string = string.replace(",", "Y");
			all += string + ",";
		}

		Document doc = new Document(url, mimeType, charset, null, null, null, singleList(""), "",
				"", null, new ArrayList<String>(0), 0, 0, all, null, null, null, false, new Date());
		return doc;
	}

	private static void addNotEmptyValuesToSet(Set<String> set, String value) {
		if (value != null) {
			set.add(value);
		}
	}

	public static void main(String[] args) {
		try {
			URL aURL = null;
			if (args.length < 1) {
				System.out.println("Usage: one and only one argument giving a file path or a URL.");
			} else {
				File aFile = new File(args[0]);
				if (aFile.exists()) {
					try {
						aURL = aFile.getAbsoluteFile().toURI().toURL();
					} catch (final MalformedURLException e) {
						System.err.println("Could not convert file path to URL.");
					}
				} else {
					try {
						aURL = new URI(args[0]).toURL();
					} catch (final MalformedURLException | URISyntaxException e) {
						System.err.println("URL is malformed.");
					}

				}

				if (aURL != null) {
					RDFaParser aParser = new RDFaParser();
					try {
						aParser.parse(new DigestURL(args[0]), "", "", new VocabularyScraper(), 0, aURL.openStream());
					} catch (final FileNotFoundException e) {
						e.printStackTrace();
					} catch (final IOException e) {
						e.printStackTrace();
					} catch (final Failure e) {
						e.printStackTrace();
					} catch (final InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("File or URL not recognized.");
				}
				
			}
		} finally {
			ConcurrentLog.shutdown();
		}

    }
}
