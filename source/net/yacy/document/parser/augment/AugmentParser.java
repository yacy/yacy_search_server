package net.yacy.document.parser.augment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

import net.yacy.yacy;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.rdfa.IRDFaTriple;
import net.yacy.document.parser.rdfa.impl.RDFaParser;
import net.yacy.document.parser.rdfa.impl.RDFaTripleImpl;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;


public class AugmentParser extends RDFaParser {

	public AugmentParser(String name) {
		super(name);
		
		System.out.println("augmented parser was initialized");

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
		
		Document[] htmlDocs = super.parse(url, mimeType, charset, source);
		try {
			source.reset();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String urlHash = String.valueOf(url.hashCode());
		
		DigestURI durl;
		try {
			durl = new DigestURI(MultiProtocolURI.unescape(url.toString()));
			urlHash = ASCII.String(durl.hash());
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        
		Document theDoc = htmlDocs[0];
		
		
		Document superDoc = new Document(url, mimeType, charset, null, null, null, "", "",
				"", null, "", 0, 0, null, null, null, null, false);
		
		// if the magic word appears in the document, perform extra actions.
		
		
//		if (htmlDocs[0].getKeywords().contains("magicword")) {		
//			String all = "";
//			
//			all = "yacylatest";
//			superDoc = new Document(url, mimeType, charset, null, null, null, "", "",
//					"", null, "", 0, 0, all.getBytes(), null, null, null, false);
//		}
			
		Document augmentDoc = parseAndAugment(url, mimeType, charset, source);
		
		
		Document[] retDocs = new Document[htmlDocs.length + 2];
		for (int i = 0; i < htmlDocs.length; i++) {
			retDocs[i] = htmlDocs[i];
		}
		
		retDocs[retDocs.length - 1] = augmentDoc;
		retDocs[retDocs.length - 2] = superDoc;
	 
		return retDocs;
	
	}	
	

	private Document parseAndAugment(MultiProtocolURI url,
			String mimeType, String charset, InputStream source) {

		String all = "";
		
		// add even more information to the document in external routines. 
		
//		all = "augmented";
	
		Document doc = new Document(url, mimeType, charset, null, null, null, "", "",
				"", null, "", 0, 0, all.getBytes(), null, null, null, false);
		return doc;
	}
	

}
