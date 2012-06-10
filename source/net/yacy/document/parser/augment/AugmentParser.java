package net.yacy.document.parser.augment;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.Document;
import net.yacy.document.parser.rdfa.impl.RDFaParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.search.Switchboard;
import de.anomic.data.ymark.YMarkUtil;


public class AugmentParser extends RDFaParser {

	public AugmentParser(String name) {
		super(name);

		System.out.println("augmented parser was initialized");

		this.SUPPORTED_EXTENSIONS.remove("htm");
		this.SUPPORTED_EXTENSIONS.remove("html");
		this.SUPPORTED_EXTENSIONS.remove("shtml");
		this.SUPPORTED_EXTENSIONS.remove("xhtml");
		this.SUPPORTED_EXTENSIONS.remove("php");
		this.SUPPORTED_EXTENSIONS.remove("php3");
		this.SUPPORTED_EXTENSIONS.remove("php4");
		this.SUPPORTED_EXTENSIONS.remove("php5");
		this.SUPPORTED_EXTENSIONS.remove("cfm");
		this.SUPPORTED_EXTENSIONS.remove("asp");
		this.SUPPORTED_EXTENSIONS.remove("aspx");
		this.SUPPORTED_EXTENSIONS.remove("tex");
		this.SUPPORTED_EXTENSIONS.remove("txt");
		this.SUPPORTED_EXTENSIONS.remove("jsp");
		this.SUPPORTED_EXTENSIONS.remove("mf");
		this.SUPPORTED_EXTENSIONS.remove("pl");
		this.SUPPORTED_EXTENSIONS.remove("py");
		this.SUPPORTED_MIME_TYPES.remove("text/html");
		this.SUPPORTED_MIME_TYPES.remove("text/xhtml+xml");
		this.SUPPORTED_MIME_TYPES.remove("application/xhtml+xml");
		this.SUPPORTED_MIME_TYPES.remove("application/x-httpd-php");
		this.SUPPORTED_MIME_TYPES.remove("application/x-tex");
		this.SUPPORTED_MIME_TYPES.remove("text/plain");
		this.SUPPORTED_MIME_TYPES.remove("text/sgml");
		this.SUPPORTED_MIME_TYPES.remove("text/csv");

		this.SUPPORTED_EXTENSIONS.add("html");
		this.SUPPORTED_EXTENSIONS.add("php");
		this.SUPPORTED_MIME_TYPES.add("text/html");
		this.SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
		this.SUPPORTED_EXTENSIONS.add("html");
		this.SUPPORTED_EXTENSIONS.add("htm");
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

		Document alreadyParsedDocument = htmlDocs[0];

		Document superDoc = analyze(alreadyParsedDocument, url, mimeType, charset, source);



		Document augmentDoc = parseAndAugment(url, mimeType, charset, source);


		Document[] retDocs = new Document[htmlDocs.length + 2];
		for (int i = 0; i < htmlDocs.length; i++) {
			retDocs[i] = htmlDocs[i];
		}

		retDocs[retDocs.length - 1] = augmentDoc;
		retDocs[retDocs.length - 2] = superDoc;

		return retDocs;

	}

	private Document analyze (Document alreadyParsedDocument, MultiProtocolURI url,
			String mimeType, String charset, InputStream source) {

		Document newDoc = new Document(url, mimeType, charset, null, null, null, "", "",
				"", null, "", 0, 0, null, null, null, null, false);

		// if the magic word appears in the document, perform extra actions.


		if (alreadyParsedDocument.getKeywords().contains("magicword")) {
			String all = "";

			all = "yacylatest";
			newDoc = new Document(url, mimeType, charset, null, null, null, "", "",
					"", null, "", 0, 0, all.getBytes(), null, null, null, false);
		}

		return newDoc;
	}


	private Document parseAndAugment(MultiProtocolURI url,
			String mimeType, String charset, InputStream source) {

		String all = "";

		Document newDoc = new Document(url, mimeType, charset, null, null, null, "", "",
				"", null, "", 0, 0, all.getBytes(), null, null, null, false);


		Iterator<net.yacy.kelondro.blob.Tables.Row> it;
		try {
			it = Switchboard.getSwitchboard().tables.iterator("aggregatedtags");

			it = Switchboard.getSwitchboard().tables.orderBy(it, -1, "timestamp_creation").iterator();

			while (it.hasNext()) {
				net.yacy.kelondro.blob.Tables.Row r = it.next();

				if (r.get("url", "").equals (url.toNormalform(false, false))) {

					Set<String> tags = new HashSet<String>();

					for (String s : YMarkUtil.keysStringToSet(r.get("scitag", ""))) {

						tags.add(s);

					}


					newDoc.addTags(tags);

				}
			}


		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		return newDoc;
	}


}
