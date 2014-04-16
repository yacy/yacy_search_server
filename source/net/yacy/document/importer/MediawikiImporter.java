/**
 *  MediawikiImporter
 *  Copyright 2008 by Michael Peter Christen
 *  First released 20.11.2008 at http://yacy.net
 *
 *  This is a part of YaCy, a peer-to-peer based web search engine
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
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.data.wiki.WikiCode;
import net.yacy.data.wiki.WikiParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.content.SurrogateReader;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;


/*
 * this class provides data structures to read a mediawiki dump file in xml format
 * as referenced with xmlns="http://www.mediawiki.org/xml/export-0.3/"
 */

public class MediawikiImporter extends Thread implements Importer {

    private static final String textstart = "<text";
    private static final String textend = "</text>";
    private static final String pagestart = "<page>";
    private static final String pageend = "</page>";
    private static final byte[] pagestartb = UTF8.getBytes(pagestart);
    private static final byte[] pageendb = UTF8.getBytes(pageend);
    private static final int    docspermbinxmlbz2 = 800;  // documents per megabyte in a xml.bz2 mediawiki dump

    public static Importer job; // if started from a servlet, this object is used to store the thread

    public    File sourcefile;
    public    File targetdir;
    public    int count;
    private   long start;
    private   final long docsize;
    private   final int approxdocs;
    private   String hostport, urlStub;


    public MediawikiImporter(final File sourcefile, final File targetdir) {
    	this.sourcefile = sourcefile;
    	this.docsize = sourcefile.length();
    	this.approxdocs = (int) (this.docsize * docspermbinxmlbz2 / 1024L / 1024L);
    	this.targetdir = targetdir;
        this.count = 0;
        this.start = 0;
        this.hostport = null;
        this.urlStub = null;
    }

    @Override
    public int count() {
        return this.count;
    }

    @Override
    public String source() {
        return this.sourcefile.getAbsolutePath();
    }

    @Override
    public String status() {
        return "";
    }

    /**
     * return the number of articles per second
     * @return
     */
    @Override
    public int speed() {
        if (this.count == 0) return 0;
        return (int) (this.count / Math.max(1L, runningTime() ));
    }

    /**
     * return the remaining seconds for the completion of all records in milliseconds
     * @return
     */
    @Override
    public long remainingTime() {
        return Math.max(0, this.approxdocs - this.count) / Math.max(1, speed() );
    }

    @Override
    public long runningTime() {
        return (System.currentTimeMillis() - this.start) / 1000L;
    }

    @Override
    public void run() {
        this.start = System.currentTimeMillis();
        try {
            String targetstub = this.sourcefile.getName();
            int p = targetstub.lastIndexOf("\\.");
            if (p > 0) targetstub = targetstub.substring(0, p);
            InputStream is = new BufferedInputStream(new FileInputStream(this.sourcefile), 1024 * 1024);
            if (this.sourcefile.getName().endsWith(".bz2")) {
                int b = is.read();
                if (b != 'B') {
                    try {is.close();} catch (final IOException e) {}
                    throw new IOException("Invalid bz2 content.");
                }
                b = is.read();
                if (b != 'Z') {
                    try {is.close();} catch (final IOException e) {}
                    throw new IOException("Invalid bz2 content.");
                }
                is = new BZip2CompressorInputStream(is);
            } else if (this.sourcefile.getName().endsWith(".gz")) {
                is = new GZIPInputStream(is);
            }
            final BufferedReader r = new BufferedReader(new java.io.InputStreamReader(is, "UTF-8"), 4 * 1024 * 1024);
            String t;
            StringBuilder sb = new StringBuilder();
            boolean page = false, text = false;
            String title = null;
            final wikiparserrecord poison = newRecord();
            final int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            final BlockingQueue<wikiparserrecord> in = new ArrayBlockingQueue<wikiparserrecord>(threads * 10);
            final BlockingQueue<wikiparserrecord> out = new ArrayBlockingQueue<wikiparserrecord>(threads * 10);
            final ExecutorService service = Executors.newFixedThreadPool(threads + 1);
            final convertConsumer[] consumers = new convertConsumer[threads];
            final Future<?>[] consumerResults = new Future[threads];
            for (int i = 0; i < threads; i++) {
                consumers[i] = new convertConsumer(in, out, poison);
                consumerResults[i] = service.submit(consumers[i]);
            }
            final convertWriter   writer = new convertWriter(out, poison, this.targetdir, targetstub);
            final Future<Integer> writerResult = service.submit(writer);

            wikiparserrecord record;
            int q;
            while ((t = r.readLine()) != null) {
                if ((p = t.indexOf("<base>",0)) >= 0 && (q = t.indexOf("</base>", p)) > 0) {
                    //urlStub = "http://" + lang + ".wikipedia.org/wiki/";
                    this.urlStub = t.substring(p + 6, q);
                    if (!this.urlStub.endsWith("/")) {
                        q = this.urlStub.lastIndexOf('/');
                        if (q > 0) this.urlStub = this.urlStub.substring(0, q + 1);
                    }
                    final DigestURL uri = new DigestURL(this.urlStub);
                    this.hostport = uri.getHost();
                    if (uri.getPort() != 80) this.hostport += ":" + uri.getPort();
                    continue;
                }
                if (t.indexOf(pagestart) >= 0) {
                    page = true;
                    continue;
                }
                if ((p = t.indexOf(textstart)) >= 0) {
                    text = page;
                    q = t.indexOf('>', p + textstart.length());
                    if (q > 0) {
                        final int u = t.indexOf(textend, q + 1);
                        if (u > q) {
                            sb.append(t.substring(q + 1, u));
                            ConcurrentLog.info("WIKITRANSLATION", "[INJECT] Title: " + title);
                            if (sb.length() == 0) {
                                ConcurrentLog.info("WIKITRANSLATION", "ERROR: " + title + " has empty content");
                                continue;
                            }
                            record = newRecord(this.hostport, this.urlStub, title, sb);
                            try {
                                in.put(record);
                                this.count++;
                            } catch (final InterruptedException e1) {
                                ConcurrentLog.logException(e1);
                            }
                            sb = new StringBuilder(200);
                            continue;
                        }
                        sb.append(t.substring(q + 1));
                    }
                    continue;
                }
                if (t.indexOf(textend) >= 0) {
                    text = false;
                    ConcurrentLog.info("WIKITRANSLATION", "[INJECT] Title: " + title);
                    if (sb.length() == 0) {
                        ConcurrentLog.info("WIKITRANSLATION", "ERROR: " + title + " has empty content");
                        continue;
                    }
                    record = newRecord(this.hostport, this.urlStub, title, sb);
                    try {
                        in.put(record);
                        this.count++;
                    } catch (final InterruptedException e1) {
                        ConcurrentLog.logException(e1);
                    }
                    sb = new StringBuilder(200);
                    continue;
                }
                if (t.indexOf(pageend) >= 0) {
                    page = false;
                    continue;
                }
                if ((p = t.indexOf("<title>",0)) >= 0) {
                    title = t.substring(p + 7);
                    q = title.indexOf("</title>",0);
                    if (q >= 0) title = title.substring(0, q);
                    continue;
                }
                if (text) {
                    sb.append(t);
                    sb.append('\n');
                }
            }
            r.close();

            try {
                for (int i = 0; i < threads; i++) {
                    in.put(poison);
                }
                for (int i = 0; i < threads; i++) {
                    consumerResults[i].get(10000, TimeUnit.MILLISECONDS);
                }
                out.put(poison);
                writerResult.get(10000, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            } catch (final ExecutionException e) {
                ConcurrentLog.logException(e);
            } catch (final TimeoutException e) {
                ConcurrentLog.logException(e);
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }

    public static void checkIndex(final File mediawikixml) {
        final File idx = idxFromMediawikiXML(mediawikixml);
        if (idx.exists()) return;
        new indexMaker(mediawikixml).start();
    }

    public static class indexMaker extends Thread {

        File mediawikixml;
        public indexMaker(final File mediawikixml) {
            this.mediawikixml = mediawikixml;
        }

        @Override
        public void run() {
            try {
                createIndex(this.mediawikixml);
            } catch (final IOException e) {
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    public static File idxFromMediawikiXML(final File mediawikixml) {
        return new File(mediawikixml.getAbsolutePath() + ".idx.xml");
    }

    public static void createIndex(final File dumpFile) throws IOException {
        // calculate md5
        //String md5 = serverCodings.encodeMD5Hex(dumpFile);

        // init reader, producer and consumer
        final PositionAwareReader in = new PositionAwareReader(dumpFile);
        final indexProducer producer = new indexProducer(100, idxFromMediawikiXML(dumpFile));
        final wikiConsumer consumer = new wikiConsumer(100, producer);
        final ExecutorService service = Executors.newFixedThreadPool(2);
        final Future<Integer> producerResult = service.submit(consumer);
        final Future<Integer> consumerResult = service.submit(producer);
        service.shutdown();

        // read the wiki dump
        long start, stop;
        while (in.seek(pagestartb)) {
            start = in.pos() - 6;
            in.resetBuffer();
            if (!in.seek(pageendb)) break;
            stop = in.pos();
            consumer.consume(new wikiraw(in.bytes(), start, stop));
            in.resetBuffer();
        }

        // shut down the services
        try {
            consumer.consume(wikiConsumer.poison);
            try {consumerResult.get(5000, TimeUnit.MILLISECONDS);} catch (final TimeoutException e) {}
            producer.consume(indexProducer.poison);
            if (!consumerResult.isDone()) consumerResult.get();
            producerResult.get();
        } catch (final InterruptedException e) {
            ConcurrentLog.logException(e);
            return;
        } catch (final ExecutionException e) {
            ConcurrentLog.logException(e);
            return;
        }
        in.close();
    }

    private static class indexProducer implements Callable<Integer> {

        private final BlockingQueue<wikisourcerecord> entries;
        PrintWriter out;
        protected static wikisourcerecord poison = new wikisourcerecord("", 0, 0);
        int count;

        public indexProducer(final int bufferCount, final File indexFile) throws IOException {
            this.entries = new ArrayBlockingQueue<wikisourcerecord>(bufferCount);
            this.out = new PrintWriter(new BufferedWriter(new FileWriter(indexFile)));
            this.count = 0;
            this.out.println("<index>");

        }

        public void consume(final wikisourcerecord b) {
            try {
                this.entries.put(b);
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
        }

        @Override
        public Integer call() {
            wikisourcerecord r;
            try {
                while(true) {
                    r = this.entries.take();
                    if (r == poison) {
                        ConcurrentLog.info("WIKITRANSLATION", "producer / got poison");
                        break;
                    }
                    this.out.println("  <page start=\"" + r.start + "\" length=\"" + (r.end - r.start) + "\">");
                    this.out.println("    <title>" + r.title + "</title>");
                    this.out.println("  </page>");
                    ConcurrentLog.info("WIKITRANSLATION", "producer / record start: " + r.start + ", title : " + r.title);
                    this.count++;
                }
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
            this.entries.clear();
            this.out.println("</index>");
            this.out.close();
            return Integer.valueOf(this.count);
        }

    }

    private static class wikiConsumer implements Callable<Integer> {

        private   final BlockingQueue<wikiraw> entries;
        protected static wikiraw poison = new wikiraw(new byte[0], 0, 0);
        private   final indexProducer producer;
        private   int count;

        public wikiConsumer(final int bufferCount, final indexProducer producer) {
            this.entries = new ArrayBlockingQueue<wikiraw>(bufferCount);
            this.producer = producer;
            this.count = 0;
        }

        public void consume(final wikiraw b) {
            try {
                this.entries.put(b);
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
        }

        @Override
        public Integer call() {
            wikisourcerecord r;
            wikiraw c;
            try {
                while(true) {
                    c = this.entries.take();
                    if (c == poison) {
                        ConcurrentLog.info("WIKITRANSLATION", "consumer / got poison");
                        break;
                    }
                    try {
                        r = new wikisourcerecord(c.b, c.start, c.end);
                        this.producer.consume(r);
                        ConcurrentLog.info("WIKITRANSLATION", "consumer / record start: " + r.start + ", title : " + r.title);
                        this.count++;
                    } catch (final RuntimeException e) {}
                }
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
            this.entries.clear();
            return Integer.valueOf(this.count);
        }

    }

    private static class wikiraw {
        public long start, end;
        public byte[] b;
        public wikiraw(final byte[] b, final long start, final long end) {
            this.b = b;
            this.start = start;
            this.end = end;
        }
    }

    public static class wikisourcerecord {
        public long start, end;
        public String title;
        public wikisourcerecord(final String title, final long start, final long end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
        public wikisourcerecord(final byte[] chunk, final long start, final long end) {
            String s;
            s = UTF8.String(chunk);
            final int t0 = s.indexOf("<title>",0);
            if (t0 >= 0) {
                final int t1 = s.indexOf("</title>", t0);
                if (t1 >= 0) {
                    this.title = s.substring(t0 + 7, t1);
                } else {
                    throw new RuntimeException("no title end in record");
                }
            } else {
                throw new RuntimeException("no title start in record");
            }

            this.start = start;
            this.end = end;
        }
    }
    public wikiparserrecord newRecord() {
        return new wikiparserrecord(null, null, null, null);
    }
    public wikiparserrecord newRecord(final String hostport, final String urlStub, final String title, final StringBuilder sb) {
        return new wikiparserrecord(hostport, urlStub, title, sb);
    }

    public class wikiparserrecord {
        public String title;
        String source, html, hostport, urlStub;
        AnchorURL url;
        Document document;
        public wikiparserrecord(final String hostport, final String urlStub, final String title, final StringBuilder sb) {
            this.title = title;
            this.hostport = hostport;
            this.urlStub = urlStub;
            this.source = (sb == null) ? null : sb.toString();
        }
        public void genHTML() throws IOException {
            try {
                final WikiParser wparser = new WikiCode();
                this.html = wparser.transform(this.hostport, this.source);
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
                throw new IOException(e.getMessage());
            }
        }
        public void genDocument() throws Parser.Failure {
            try {
				this.url = new AnchorURL(this.urlStub + this.title);
				final Document[] parsed = TextParser.parseSource(this.url, "text/html", "UTF-8", 1, UTF8.getBytes(this.html));
				this.document = Document.mergeDocuments(this.url, "text/html", parsed);
				// the wiki parser is not able to find the proper title in the source text, so it must be set here
				this.document.setTitle(this.title);
			} catch (final MalformedURLException e1) {
			    ConcurrentLog.logException(e1);
			}
        }
        public void writeXML(final OutputStreamWriter os) throws IOException {
            this.document.writeXML(os, new Date());
        }
    }

    private static class PositionAwareReader {

        private final InputStream is;
        private long seekpos;
        private ByteBuffer bb;

        public PositionAwareReader(final File dumpFile) throws FileNotFoundException {
            this.is = new BufferedInputStream(new FileInputStream(dumpFile), 64 *1024);
            this.seekpos = 0;
            this.bb = new ByteBuffer();
        }

        public void resetBuffer() {
            if (this.bb.length() > 10 * 1024) this.bb = new ByteBuffer(); else this.bb.clear();
        }

        public boolean seek(final byte[] pattern) throws IOException {
            int pp = 0;
            int c;
            while ((c = this.is.read()) >= 0) {
                this.seekpos++;
                this.bb.append(c);
                if (pattern[pp] == c) pp++; else pp = 0;
                if (pp == pattern.length) return true;
            }
            return false;
        }

        public long pos() {
            return this.seekpos;
        }

        public byte[] bytes() {
            return this.bb.getBytes();
        }

        public synchronized void close() {
            try {
                this.is.close();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    public static byte[] read(final File f, final long start, final int len) {
        final byte[] b = new byte[len];
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            raf.seek(start);
            raf.read(b);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return null;
        } finally {
            if (raf != null) try {
                raf.close();
                try{raf.getChannel().close();} catch (final IOException e) {}
            } catch (final IOException e) { }
        }
        return b;
    }

    public static wikisourcerecord find(final String title, final File f) throws IOException {
        final PositionAwareReader in = new PositionAwareReader(f);
        long start;
        final String m = "<title>" + title + "</title>";
        String s;
        while (in.seek(UTF8.getBytes("<page "))) {
            start = in.pos() - 6;
            in.resetBuffer();
            if (!in.seek(pageendb)) break;
            s = UTF8.String(in.bytes());
            in.resetBuffer();
            if (s.indexOf(m) >= 0) {
                // we found the record
                //Log.logInfo("WIKITRANSLATION", "s = " + s);
                int p = s.indexOf("start=\"",0);
                if (p < 0) return null;
                p += 7;
                int q = s.indexOf('"', p + 1);
                if (q < 0) return null;
                start = NumberTools.parseLongDecSubstring(s, p, q);
                p = s.indexOf("length=\"", q);
                if (p < 0) return null;
                p += 8;
                q = s.indexOf('"', p + 1);
                if (q < 0) return null;
                final int length = NumberTools.parseIntDecSubstring(s, p, q);
                //Log.logInfo("WIKITRANSLATION", "start = " + start + ", length = " + length);
                return new wikisourcerecord(title, start, start + length);
            }
        }
        return null;
    }

    private static class convertConsumer implements Callable<Integer> {

        private final BlockingQueue<wikiparserrecord> in, out;
        private final wikiparserrecord poison;

        public convertConsumer(final BlockingQueue<wikiparserrecord> in, final BlockingQueue<wikiparserrecord> out, final wikiparserrecord poison) {
            this.poison = poison;
            this.in = in;
            this.out = out;
        }

        @Override
        public Integer call() {
            wikiparserrecord record;
            try {
                while(true) {
                    record = this.in.take();
                    if (record == this.poison) {
                        ConcurrentLog.info("WIKITRANSLATION", "convertConsumer / got poison");
                        break;
                    }
                    try {
                        record.genHTML();
                        record.genDocument();
                        this.out.put(record);
                    } catch (final RuntimeException e) {
                        ConcurrentLog.logException(e);
                    } catch (final Parser.Failure e) {
                        ConcurrentLog.logException(e);
                    } catch (final IOException e) {
						// TODO Auto-generated catch block
                        ConcurrentLog.logException(e);
					}
                }
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
            ConcurrentLog.info("WIKITRANSLATION", "*** convertConsumer has terminated");
            return Integer.valueOf(0);
        }

    }

    private static class convertWriter implements Callable<Integer> {

        private final BlockingQueue<wikiparserrecord> in;
        private final wikiparserrecord poison;
        private OutputStreamWriter osw;
        private final String targetstub;
        private final File targetdir;
        private int fc, rc;
        private String outputfilename;

        public convertWriter(
                final BlockingQueue<wikiparserrecord> in,
                final wikiparserrecord poison,
                final File targetdir,
                final String targetstub) {
            this.poison = poison;
            this.in = in;
            this.osw = null;
            this.targetdir = targetdir;
            this.targetstub = targetstub;
            this.fc = 0;
            this.rc = 0;
            this.outputfilename = null;
        }

        @Override
        public Integer call() {
            wikiparserrecord record;
            try {
                while(true) {
                    record = this.in.take();
                    if (record == this.poison) {
                        ConcurrentLog.info("WIKITRANSLATION", "convertConsumer / got poison");
                        break;
                    }

                    if (this.osw == null) {
                        // start writing a new file
                        this.outputfilename = this.targetstub + "." + this.fc + ".xml.prt";
                        this.osw = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(new File(this.targetdir, this.outputfilename))), "UTF-8");
                        this.osw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + SurrogateReader.SURROGATES_MAIN_ELEMENT_OPEN + "\n");
                    }
                    ConcurrentLog.info("WIKITRANSLATION", "[CONSUME] Title: " + record.title);
                    record.document.writeXML(this.osw, new Date());
                    this.rc++;
                    if (this.rc >= 10000) {
                        this.osw.write("</surrogates>\n");
                        this.osw.close();
                        final String finalfilename = this.targetstub + "." + this.fc + ".xml";
                        new File(this.targetdir, this.outputfilename).renameTo(new File(this.targetdir, finalfilename));
                        this.rc = 0;
                        this.fc++;
                        this.outputfilename = this.targetstub + "." + this.fc + ".xml.prt";
                        this.osw = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(new File(this.targetdir, this.outputfilename))), "UTF-8");
                        this.osw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + SurrogateReader.SURROGATES_MAIN_ELEMENT_OPEN + "\n");
                    }
                }
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            } catch (final UnsupportedEncodingException e) {
                ConcurrentLog.logException(e);
            } catch (final FileNotFoundException e) {
                ConcurrentLog.logException(e);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } finally {
	            try {
					this.osw.write(SurrogateReader.SURROGATES_MAIN_ELEMENT_CLOSE + "\n");
		            this.osw.close();
		            final String finalfilename = this.targetstub + "." + this.fc + ".xml";
		            new File(this.targetdir, this.outputfilename).renameTo(new File(this.targetdir, finalfilename));
				} catch (final IOException e) {
				    ConcurrentLog.logException(e);
				}
            }
            ConcurrentLog.info("WIKITRANSLATION", "*** convertWriter has terminated");
            return Integer.valueOf(0);
        }

    }

    public static void main(final String[] s) {
        if (s.length == 0) {
            ConcurrentLog.info("WIKITRANSLATION", "usage:");
            ConcurrentLog.info("WIKITRANSLATION", " -index <wikipedia-dump>");
            ConcurrentLog.info("WIKITRANSLATION", " -read  <start> <len> <idx-file>");
            ConcurrentLog.info("WIKITRANSLATION", " -find  <title> <wikipedia-dump>");
            ConcurrentLog.info("WIKITRANSLATION", " -convert <wikipedia-dump-xml.bz2> <convert-target-dir> <url-stub>");
            System.exit(0);
        }

        // example:
        // java -Xmx2000m -cp classes:lib/bzip2.jar de.anomic.tools.mediawikiIndex -convert DATA/HTCACHE/dewiki-20090311-pages-articles.xml.bz2 DATA/SURROGATES/in/ http://de.wikipedia.org/wiki/

        if (s[0].equals("-convert") && s.length > 2) {
            final File sourcefile = new File(s[1]);
            final File targetdir = new File(s[2]);
            //String urlStub = s[3]; // i.e. http://de.wikipedia.org/wiki/
            //String language = urlStub.substring(7,9);
            try {
                final MediawikiImporter mi = new MediawikiImporter(sourcefile, targetdir);
                mi.start();
                mi.join();
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
        }

        if (s[0].equals("-index")) {
            try {
                createIndex(new File(s[1]));
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }

        if (s[0].equals("-read")) {
            final long start = Integer.parseInt(s[1]);
            final int  len   = Integer.parseInt(s[2]);
            System.out.println(UTF8.String(read(new File(s[3]), start, len)));
        }

        if (s[0].equals("-find")) {
            try {
                final wikisourcerecord w = find(s[1], new File(s[2] + ".idx.xml"));
                if (w == null) {
                    ConcurrentLog.info("WIKITRANSLATION", "not found");
                } else {
                    System.out.println(UTF8.String(read(new File(s[2]), w.start, (int) (w.end - w.start))));
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }

        }
        System.exit(0);
    }

}
