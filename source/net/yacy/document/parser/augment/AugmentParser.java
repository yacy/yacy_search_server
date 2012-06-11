package net.yacy.document.parser.augment;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.parser.rdfa.impl.RDFaParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.search.Switchboard;
import de.anomic.data.ymark.YMarkUtil;


public class AugmentParser extends AbstractParser implements Parser {

    RDFaParser rdfaParser;

	public AugmentParser() {
		super("AugmentParser");
		this.rdfaParser = new RDFaParser();

		System.out.println("augmented parser was initialized");

		this.SUPPORTED_EXTENSIONS.add("html");
		this.SUPPORTED_EXTENSIONS.add("php");
		this.SUPPORTED_MIME_TYPES.add("text/html");
		this.SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
		this.SUPPORTED_EXTENSIONS.add("html");
		this.SUPPORTED_EXTENSIONS.add("htm");
	}

	@Override
	public Document[] parse(DigestURI url, String mimeType,
			String charset, InputStream source) throws Failure,
			InterruptedException {

		Document[] htmlDocs = this.rdfaParser.parse(url, mimeType, charset, source);
		try {
			source.reset();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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

	private Document analyze (Document alreadyParsedDocument, DigestURI url,
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


	private Document parseAndAugment(DigestURI url,
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
