/**
 *  JsonListImporter
 *  Copyright 23.10.2022 by Michael Peter Christen, @orbiterlab
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.importer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.GZIPInputStream;

import org.apache.solr.common.SolrInputDocument;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.language.synonyms.SynonymLibrary;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Tokenizer;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.content.SurrogateReader;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;

public class JsonListImporter extends Thread implements Importer {

    private static ConcurrentLog log = new ConcurrentLog("JsonListImporter");
    public static JsonListImporter job = null;

    private InputStream source;
    private final String name;

    private final File inputFile;
    private final long sourceSize;
    private long lineCount, startTime, consumed;
    private boolean abort;
    private final boolean deletewhendone;

    public JsonListImporter(final File inputFile, final boolean gz, final boolean deletewhendone) throws IOException {
       super("JsonListImporter - from file " + inputFile.getName());
       this.lineCount = 0;
       this.consumed = 0;
       this.inputFile = inputFile;
       this.name = inputFile.getName();
       this.sourceSize = inputFile.length();
       this.abort = false;
       this.deletewhendone = deletewhendone;
       this.source = new FileInputStream(inputFile);
       if (this.name.endsWith(".gz") || gz) this.source = new GZIPInputStream(this.source);
    }

    @Override
    public void run() {
        try {
            processSurrogateJson();
        } catch (final IOException e) {
            log.warn(e);
        }
    }

    public void processSurrogateJson() throws IOException {
        this.startTime = System.currentTimeMillis();
        job = this;

        // start indexer threads which mostly care about tokenization and facet + synonym enrichment
        final int concurrency = Runtime.getRuntime().availableProcessors();
        final BlockingQueue<SolrInputDocument> sidQueue = new ArrayBlockingQueue<>(concurrency * 2);
        final Thread[] indexer = new Thread[concurrency];
        for (int t = 0; t < indexer.length; t++) {
            indexer[t] = new Thread("Switchboard.processSurrogateJson-" + t) {
                @Override
                public void run() {
                    final VocabularyScraper scraper = new VocabularyScraper();
                    SolrInputDocument sid;
                    try {
                        while ((sid = sidQueue.take()) != SurrogateReader.POISON_DOCUMENT ) {
                            // enrich the surrogate
                            final String id = (String) sid.getFieldValue(CollectionSchema.id.getSolrFieldName());
                            final String text = (String) sid.getFieldValue(CollectionSchema.text_t.getSolrFieldName());
                            DigestURL rootURL;
                            if (text != null && text.length() > 0 && id != null ) try {
                                if (SynonymLibrary.size() > 0 || !LibraryProvider.autotagging.isEmpty()) {
                                    rootURL = new DigestURL((String) sid.getFieldValue(CollectionSchema.sku.getSolrFieldName()), ASCII.getBytes(id));
                                    // run the tokenizer on the text to get vocabularies and synonyms
                                    final Tokenizer tokenizer = new Tokenizer(rootURL, text, LibraryProvider.dymLib, true, scraper);
                                    final Map<String, Set<String>> facets = Document.computeGenericFacets(tokenizer.tags());
                                    // overwrite the given vocabularies and synonyms with new computed ones
                                    Switchboard.getSwitchboard().index.fulltext().getDefaultConfiguration().enrich(sid, tokenizer.synonyms(), facets);
                                }
                                Switchboard.getSwitchboard().index.putDocument(sid);
                            } catch (final MalformedURLException e) {}
                        }
                    } catch (final InterruptedException e) {
                    }
                }
            };
            indexer[t].start();
        }

        final InputStream bis = new BufferedInputStream(this.source);
        BufferedReader br = new BufferedReader(new InputStreamReader(bis, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) {
            if (this.abort) break;
            final JSONTokener jt = new JSONTokener(line);
            JSONObject json = null;
            try {
                json = new JSONObject(jt);
            } catch (final JSONException e1) {
                throw new IOException(e1.getMessage());
            }
            if ((json.opt("index") != null && json.length() == 1) || json.length() == 0) continue;
            final SolrInputDocument surrogate = new SolrInputDocument();

            // set default values which act as constraints for a proper search
            CollectionSchema.httpstatus_i.add(surrogate, 200);

            // get fields for json object
            jsonreader: for (final String key: json.keySet()) {
                final Object o = json.opt(key);
                if (o == null) continue;
                if (o instanceof JSONArray) {
                    // transform this into a list
                    final JSONArray a = (JSONArray) o;
                    // patch altered yacy grid schema (yacy grid does not split url lists into protocol and urlstub)
                    if (key.equals("inboundlinks_sxt")) {
                        // compute inboundlinks_urlstub_sxt and inboundlinks_protocol_sxt
                        final List<Object> urlstub = new ArrayList<>();
                        final List<Object> protocol = new ArrayList<>();
                        for (int i = 0; i < a.length(); i++) {
                            final AnchorURL b = new AnchorURL((String) a.opt(i));
                            urlstub.add(b.urlstub(true, true));
                            protocol.add(b.getProtocol());
                        }
                        CollectionSchema.inboundlinks_urlstub_sxt.add(surrogate, urlstub);
                        CollectionSchema.inboundlinks_protocol_sxt.add(surrogate, protocol);
                        continue jsonreader;
                    }
                    if (key.equals("outboundlinks_sxt")) {
                        // compute outboundlinks_urlstub_sxt and outboundlinks_protocol_sxt
                        final List<Object> urlstub = new ArrayList<>();
                        final List<Object> protocol = new ArrayList<>();
                        for (int i = 0; i < a.length(); i++) {
                            final AnchorURL b = new AnchorURL((String) a.opt(i));
                            urlstub.add(b.urlstub(true, true));
                            protocol.add(b.getProtocol());
                        }
                        CollectionSchema.outboundlinks_urlstub_sxt.add(surrogate, urlstub);
                        CollectionSchema.outboundlinks_protocol_sxt.add(surrogate, protocol);
                        continue jsonreader;
                    }
                    if (key.equals("images_sxt")) {
                        // compute images_urlstub_sxt and images_protocol_sxt
                        final List<Object> urlstub = new ArrayList<>();
                        final List<Object> protocol = new ArrayList<>();
                        for (int i = 0; i < a.length(); i++) {
                            final AnchorURL b = new AnchorURL((String) a.opt(i));
                            urlstub.add(b.urlstub(true, true));
                            protocol.add(b.getProtocol());
                        }
                        CollectionSchema.images_urlstub_sxt.add(surrogate, urlstub);
                        CollectionSchema.images_protocol_sxt.add(surrogate, protocol);
                        continue jsonreader;
                    }

                    // prepare to read key type
                    CollectionSchema ctype = null;
                    try {ctype = CollectionSchema.valueOf(key);} catch (final Exception e) {
                        log.warn("unknown key for CollectionSchema: " + key);
                        continue jsonreader;
                    }
                    final List<Object> list = new ArrayList<>();
                    for (int i = 0; i < a.length(); i++) list.add(a.opt(i));
                    ctype.add(surrogate, list);
                } else {
                    // first handle exceptional keys / maybe patch for other systems + other names
                    if (key.equals("url_s") || key.equals("sku")) {
                        // patch yacy grid altered schema (yacy grid does not have IDs any more, but they can be re-computed here)
                        final DigestURL durl = new DigestURL(o.toString());
                        final String id = ASCII.String(durl.hash());
                        surrogate.setField(CollectionSchema.sku.getSolrFieldName(), durl.toNormalform(true));
                        surrogate.setField(CollectionSchema.id.getSolrFieldName(), id);
                        surrogate.setField(CollectionSchema.host_s.getSolrFieldName(), durl.getHost());
                        surrogate.setField(CollectionSchema.host_id_s.getSolrFieldName(), id.substring(6));
                        continue jsonreader;
                    }
                    if (key.equals("description")) {
                        // in YaCy descriptions are full-text indexed and also multi-value fields
                        final List<Object> descriptions = new ArrayList<>();
                        descriptions.add(o.toString());
                        CollectionSchema.description_txt.add(surrogate, descriptions);
                        continue jsonreader;
                    }
                    if (key.equals("referrer_url_s")) {
                        // same patch as for urls which require re-calculation of id's; in this case we store the id only!
                        final DigestURL durl = new DigestURL(o.toString());
                        final String id = ASCII.String(durl.hash());
                        surrogate.setField(CollectionSchema.referrer_id_s.getSolrFieldName(), id);
                        continue jsonreader;
                    }

                    // prepare to read key type
                    CollectionSchema ctype = null;
                    try {ctype = CollectionSchema.valueOf(key);} catch (final Exception e) {
                        log.warn("unknown key for CollectionSchema: " + key);
                        continue jsonreader;
                    }
                    if (ctype != null && ctype.getType() == SolrType.date) {
                        // patch date into something that Solr can understand
                        final String d = o.toString(); // i.e. Wed Apr 01 02:00:00 CEST 2020
                        final Date dd = d == null || d.length() == 0 ? null : AbstractFormatter.parseAny(d);
                        if (dd != null) surrogate.setField(ctype.getSolrFieldName(), ISO8601Formatter.FORMATTER.format(dd)); // solr dateTime is ISO8601 format
                        continue jsonreader;
                    }

                    // check if required fields are still missing and compute them
                    if (!surrogate.containsKey(CollectionSchema.host_s.getSolrFieldName())) {
                        final DigestURL durl = new DigestURL((String) surrogate.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                        surrogate.setField(CollectionSchema.host_s.getSolrFieldName(), durl.getHost());
                    }

                    // regular situation, just read content of field
                    surrogate.setField(key, o.toString());
                }
            }

            try {
                sidQueue.put(surrogate);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
            this.lineCount++;
            this.consumed += line.length();
        }
        br.close();
        br = null;

        // finish indexing threads by giving them poison
        for (int t = 0; t < indexer.length; t++) {
            try {sidQueue.put(SurrogateReader.POISON_DOCUMENT);} catch (final InterruptedException e) {}
        }
        // wait until indexer threads are finished
        for (int t = 0; t < indexer.length; t++) {
            try {indexer[t].join(10000);} catch (final InterruptedException e) {}
        }

        if (this.deletewhendone) this.inputFile.delete();

        log.info("finished processing json surrogate: " + ((System.currentTimeMillis() - this.startTime) / 1000) + " seconds");
    }

    public void quit() {
        this.abort = true;
    }

    @Override
    public String source() {
        return this.name;
    }

    @Override
    public int count() {
        return (int) this.lineCount;
    }

    @Override
    public int speed() {
        if (this.lineCount == 0) return 0;
        return (int) (this.lineCount / Math.max(0L, runningTime() ));
    }

    @Override
    public long runningTime() {
        return (System.currentTimeMillis() - this.startTime) / 1000L;
    }

    @Override
    public long remainingTime() {
        if (this.consumed == 0) {
            return 0;
        }
        final long speed = this.consumed / runningTime();
        return (this.sourceSize - this.consumed) / speed;
    }

    @Override
    public String status() {
        return "";
    }

}
