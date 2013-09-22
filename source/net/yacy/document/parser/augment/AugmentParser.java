package net.yacy.document.parser.augment;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.ymark.YMarkUtil;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.parser.rdfa.impl.RDFaParser;
import net.yacy.search.Switchboard;


public class AugmentParser extends AbstractParser implements Parser {

    RDFaParser rdfaParser;

    public AugmentParser() {
        super("AugmentParser");
        this.rdfaParser = new RDFaParser();

        ConcurrentLog.info("AugmentedParser", "augmented parser was initialized");

        this.SUPPORTED_EXTENSIONS.add("html");
        this.SUPPORTED_EXTENSIONS.add("htm");
        this.SUPPORTED_EXTENSIONS.add("xhtml");        
        this.SUPPORTED_EXTENSIONS.add("php");
        this.SUPPORTED_MIME_TYPES.add("text/html");
        this.SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
    }

    @Override
    public Document[] parse(AnchorURL url, String mimeType, String charset, InputStream source) throws Parser.Failure, InterruptedException {

        Document[] htmlDocs = this.rdfaParser.parse(url, mimeType, charset, source);

        for (final Document doc : htmlDocs) {
            /* analyze(doc, url, mimeType, charset);  // enrich document text */
            parseAndAugment(doc, url, mimeType, charset); // enrich document with additional tags
        }
        return htmlDocs;
    }

/*  TODO: not implemented yet
 *
    private void analyze(Document origDoc, DigestURI url,
            String mimeType, String charset) {
        // if the magic word appears in the document, perform extra actions.
        if (origDoc.getKeywords().contains("magicword")) {
            String all = "";
            all = "yacylatest";
            // TODO: append content of string all to origDoc.text, maybe use Document.mergeDocuments() to do so
        }
    }
*/
    private void parseAndAugment(Document origDoc, DigestURL url, @SuppressWarnings("unused") String mimeType, @SuppressWarnings("unused") String charset) {

        Iterator<net.yacy.kelondro.blob.Tables.Row> it;
        try {
            it = Switchboard.getSwitchboard().tables.iterator("aggregatedtags");
            it = Switchboard.getSwitchboard().tables.orderBy(it, -1, "timestamp_creation").iterator();
            while (it.hasNext()) {
                net.yacy.kelondro.blob.Tables.Row r = it.next();
                if (r.get("url", "").equals(url.toNormalform(false))) {
                    Set<String> tags = new HashSet<String>();
                    for (String s : YMarkUtil.keysStringToSet(r.get("scitag", ""))) {
                        tags.add(s);
                    }
                    origDoc.addTags(tags);
                }
            }

        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }


}
