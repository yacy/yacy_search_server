/**
 * 
 */
package net.yacy.document.parser.rdfa.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.Document;
import net.yacy.document.Parser.Failure;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.rdfa.IRDFaTriple;
import net.yacy.kelondro.logging.Log;

/**
 * @author fgandon
 * 
 */
public class RDFaParser extends htmlParser {

	public RDFaParser(String name) {
		super(name);
		SUPPORTED_EXTENSIONS.remove("htm");
		SUPPORTED_EXTENSIONS.remove("html");
		SUPPORTED_EXTENSIONS.remove("shtml");
		SUPPORTED_EXTENSIONS.remove("xhtml");
		SUPPORTED_EXTENSIONS.remove("php");
		SUPPORTED_EXTENSIONS.remove("php3");
		SUPPORTED_EXTENSIONS.remove("php4");
		SUPPORTED_EXTENSIONS.remove("php5");
		SUPPORTED_EXTENSIONS.remove("cfm");
		SUPPORTED_EXTENSIONS.remove("asp");
		SUPPORTED_EXTENSIONS.remove("aspx");
		SUPPORTED_EXTENSIONS.remove("tex");
		SUPPORTED_EXTENSIONS.remove("txt");
		SUPPORTED_EXTENSIONS.remove("jsp");
		SUPPORTED_EXTENSIONS.remove("mf");
		SUPPORTED_EXTENSIONS.remove("pl");
		SUPPORTED_EXTENSIONS.remove("py");
		SUPPORTED_MIME_TYPES.remove("text/html");
		SUPPORTED_MIME_TYPES.remove("text/xhtml+xml");
		SUPPORTED_MIME_TYPES.remove("application/xhtml+xml");
		SUPPORTED_MIME_TYPES.remove("application/x-httpd-php");
		SUPPORTED_MIME_TYPES.remove("application/x-tex");
		SUPPORTED_MIME_TYPES.remove("text/plain");
		SUPPORTED_MIME_TYPES.remove("text/sgml");
		SUPPORTED_MIME_TYPES.remove("text/csv");

		SUPPORTED_EXTENSIONS.add("html");
		SUPPORTED_EXTENSIONS.add("php");
		SUPPORTED_MIME_TYPES.add("text/html");
		SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
		SUPPORTED_EXTENSIONS.add("html");
		SUPPORTED_EXTENSIONS.add("htm");
	}

	@Override
	public Document[] parse(MultiProtocolURI url, String mimeType,
			String charset, InputStream source) throws Failure,
			InterruptedException {

		Document[] htmlDocs = parseHtml(url, mimeType, charset, source);
		
		// TODO: current hardcoded restriction: apply rdfa parser only on selected sources.

		if (url.toString().contains(".yacy") || url.toString().contains("experiments")) {
		// if (true == false) {
			Document rdfaDoc = parseRDFa(url, mimeType, charset, source);
			Document[] retDocs = new Document[htmlDocs.length + 1];
			for (int i = 0; i < htmlDocs.length; i++) {
				retDocs[i] = htmlDocs[i];
			}
			retDocs[retDocs.length - 1] = rdfaDoc;
			return retDocs;
		} else {
			return htmlDocs;
		}

	}

	private Document parseRDFa(MultiProtocolURI url, String mimeType,
			String charset, InputStream source) {
		RDFaTripleImpl triple;
		IRDFaTriple[] allTriples = null;
		try {
			triple = new RDFaTripleImpl(new InputStreamReader(source), url
					.toString());
			allTriples = triple.parse();

		} catch (Exception e) {
			Log.logWarning("RDFA PARSER", "Triple extraction failed");
		}

		Document doc = new Document(url, mimeType, charset, null, null, null, "", "",
				"", null, "", 0, 0, null, null, null, null, false);

		try {
			if (allTriples.length > 0)
				doc = convertAllTriplesToDocument(url, mimeType, charset,
						allTriples);

		} catch (Exception e) {
			Log.logWarning("RDFA PARSER",
					"Conversion triple to document failed");
		}
		return doc;

	}

	private Document[] parseHtml(MultiProtocolURI url, String mimeType,
			String charset, InputStream source) throws Failure,
			InterruptedException {

		Document[] htmlDocs = null;
		try {
			htmlDocs = super.parse(url, mimeType, charset, source);
			source.reset();

		} catch (IOException e1) {
			Log.logWarning("RDFA PARSER", "Super call failed");
		}
		return htmlDocs;

	}

	private Document convertAllTriplesToDocument(MultiProtocolURI url,
			String mimeType, String charset, IRDFaTriple[] allTriples) {

		Set<String> languages = new HashSet<String>(2);
		Set<String> keywords = new HashSet<String>(allTriples.length);
		Set<String> sections = new HashSet<String>(5);
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

		Document doc = new Document(url, mimeType, charset, null, null, null, "", "",
				"", null, "", 0, 0, all.getBytes(), null, null, null, false);
		return doc;
	}

	private void addNotEmptyValuesToSet(Set<String> set, String value) {
		if (value != null) {
			set.add(value);
		}
	}

}
