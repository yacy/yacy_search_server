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

import net.yacy.cora.document.UTF8;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.content.SurrogateReader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;

import org.apache.tools.bzip2.CBZip2InputStream;

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

import de.anomic.data.wiki.WikiCode;
import de.anomic.data.wiki.WikiParser;

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
    private static final int    docspermbinxmlbz2 = 800;  // documents per megabyte in a xml.bz2 wikimedia dump
    
    public static Importer job; // if started from a servlet, this object is used to store the thread
    
    public    File sourcefile;
    public    File targetdir;
    public    int count;
    private   long start;
    private   final long docsize;
    private   final int approxdocs;
    private   String hostport, urlStub;
    
    
    public MediawikiImporter(File sourcefile, File targetdir) {
    	this.sourcefile = sourcefile;
    	this.docsize = sourcefile.length();
    	this.approxdocs = (int) (this.docsize * (long) docspermbinxmlbz2 / 1024L / 1024L);
    	this.targetdir = targetdir;
        this.count = 0;
        this.start = 0;
        this.hostport = null;
        this.urlStub = null;
    }
    
    public int count() {
        return this.count;
    }
    
    public String source() {
        return this.sourcefile.getAbsolutePath();
    }
    
    public String status() {
        return "";
    }
    
    /**
     * return the number of articles per second
     * @return
     */
    public int speed() {
        if (count == 0) return 0;
        return (int) ((long) count / Math.max(1L, runningTime() ));
    }
    
    /**
     * return the remaining seconds for the completion of all records in milliseconds
     * @return
     */
    public long remainingTime() {
        return Math.max(0, this.approxdocs - count) / Math.max(1, speed() );
    }
    
    public long runningTime() {
        return (System.currentTimeMillis() - start) / 1000L;
    }
    
    public void run() {
        this.start = System.currentTimeMillis();
        try {
            String targetstub = sourcefile.getName();
            int p = targetstub.lastIndexOf("\\.");
            if (p > 0) targetstub = targetstub.substring(0, p);
            InputStream is = new BufferedInputStream(new FileInputStream(sourcefile), 1024 * 1024);
            if (sourcefile.getName().endsWith(".bz2")) {
                int b = is.read();
                if (b != 'B') throw new IOException("Invalid bz2 content.");
                b = is.read();
                if (b != 'Z') throw new IOException("Invalid bz2 content.");
                is = new CBZip2InputStream(is);
            } else if (sourcefile.getName().endsWith(".gz")) {
                is = new GZIPInputStream(is);
            }
            BufferedReader r = new BufferedReader(new java.io.InputStreamReader(is, "UTF-8"), 4 * 1024 * 1024);
            String t;
            StringBuilder sb = new StringBuilder();
            boolean page = false, text = false;
            String title = null;
            wikiparserrecord poison = newRecord();
            int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            BlockingQueue<wikiparserrecord> in = new ArrayBlockingQueue<wikiparserrecord>(threads * 10);
            BlockingQueue<wikiparserrecord> out = new ArrayBlockingQueue<wikiparserrecord>(threads * 10);
            ExecutorService service = Executors.newFixedThreadPool(threads + 1);
            convertConsumer[] consumers = new convertConsumer[threads];
            Future<?>[] consumerResults = new Future[threads];
            for (int i = 0; i < threads; i++) {
                consumers[i] = new convertConsumer(in, out, poison);
                consumerResults[i] = service.submit(consumers[i]);
            }
            convertWriter   writer = new convertWriter(out, poison, targetdir, targetstub);
            Future<Integer> writerResult = service.submit(writer);
            
            wikiparserrecord record;
            int q;
            while ((t = r.readLine()) != null) {
                if ((p = t.indexOf("<base>")) >= 0 && (q = t.indexOf("</base>", p)) > 0) {
                    //urlStub = "http://" + lang + ".wikipedia.org/wiki/";
                    urlStub = t.substring(p + 6, q);
                    if (!urlStub.endsWith("/")) {
                        q = urlStub.lastIndexOf('/');
                        if (q > 0) urlStub = urlStub.substring(0, q + 1);
                    }
                    DigestURI uri = new DigestURI(urlStub);
                    hostport = uri.getHost();
                    if (uri.getPort() != 80) hostport += ":" + uri.getPort();
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
                        int u = t.indexOf(textend, q + 1);
                        if (u > q) {
                            sb.append(t.substring(q + 1, u));
                            Log.logInfo("WIKITRANSLATION", "[INJECT] Title: " + title);
                            if (sb.length() == 0) {
                                Log.logInfo("WIKITRANSLATION", "ERROR: " + title + " has empty content");
                                continue;
                            }
                            record = newRecord(hostport, urlStub, title, sb);
                            try {
                                in.put(record);
                                this.count++;
                            } catch (InterruptedException e1) {
                                Log.logException(e1);
                            }
                            sb = new StringBuilder(200);
                            continue;
                        } else {
                            sb.append(t.substring(q + 1));
                        }
                    }
                    continue;
                }
                if (t.indexOf(textend) >= 0) {
                    text = false;
                    Log.logInfo("WIKITRANSLATION", "[INJECT] Title: " + title);
                    if (sb.length() == 0) {
                        Log.logInfo("WIKITRANSLATION", "ERROR: " + title + " has empty content");
                        continue;
                    }
                    record = newRecord(hostport, urlStub, title, sb);
                    try {
                        in.put(record);
                        this.count++;
                    } catch (InterruptedException e1) {
                        Log.logException(e1);
                    }
                    sb = new StringBuilder(200);
                    continue;
                }
                if (t.indexOf(pageend) >= 0) {
                    page = false;
                    continue;
                }
                if ((p = t.indexOf("<title>")) >= 0) {
                    title = t.substring(p + 7);
                    q = title.indexOf("</title>");
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
            } catch (InterruptedException e) {
                Log.logException(e);
            } catch (ExecutionException e) {
                Log.logException(e);
            } catch (TimeoutException e) {
                Log.logException(e);
            } catch (Exception e) {
                Log.logException(e);
            }
        } catch (IOException e) {
            Log.logException(e);
        } catch (Exception e) {
            Log.logException(e);
        }
    }
    
    public static void checkIndex(File wikimediaxml) {
        File idx = idxFromWikimediaXML(wikimediaxml);
        if (idx.exists()) return;
        new indexMaker(wikimediaxml).start();
    }
    
    public static class indexMaker extends Thread {
        
        File wikimediaxml;
        public indexMaker(File wikimediaxml) {
            this.wikimediaxml = wikimediaxml;
        }
        
        public void run() {
            try {
                createIndex(this.wikimediaxml);
            } catch (final IOException e) {
            } catch (final Exception e) {
                Log.logException(e);
            }
        }
    }
    
    public static File idxFromWikimediaXML(File wikimediaxml) {
        return new File(wikimediaxml.getAbsolutePath() + ".idx.xml");
    }
    
    public static void createIndex(File dumpFile) throws IOException {
        // calculate md5
        //String md5 = serverCodings.encodeMD5Hex(dumpFile);
        
        // init reader, producer and consumer
        PositionAwareReader in = new PositionAwareReader(dumpFile);
        indexProducer producer = new indexProducer(100, idxFromWikimediaXML(dumpFile));
        wikiConsumer consumer = new wikiConsumer(100, producer);
        ExecutorService service = Executors.newFixedThreadPool(2);
        Future<Integer> producerResult = service.submit(consumer);
        Future<Integer> consumerResult = service.submit(producer);
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
            try {consumerResult.get(5000, TimeUnit.MILLISECONDS);} catch (TimeoutException e) {}
            producer.consume(indexProducer.poison);
            if (!consumerResult.isDone()) consumerResult.get();
            producerResult.get();
        } catch (InterruptedException e) {
            Log.logException(e);
            return;
        } catch (ExecutionException e) {
            Log.logException(e);
            return;
        }
        in.close();
    }

    private static class indexProducer implements Callable<Integer> {

        private final BlockingQueue<wikisourcerecord> entries;
        PrintWriter out;
        protected static wikisourcerecord poison = new wikisourcerecord("", 0, 0);
        int count;
        
        public indexProducer(int bufferCount, File indexFile) throws IOException {
            entries = new ArrayBlockingQueue<wikisourcerecord>(bufferCount);
            out = new PrintWriter(new BufferedWriter(new FileWriter(indexFile)));
            count = 0;
            out.println("<index>");
            
        }
        
        public void consume(wikisourcerecord b) {
            try {
                entries.put(b);
            } catch (InterruptedException e) {
                Log.logException(e);
            }
        }
        
        public Integer call() {
            wikisourcerecord r;
            try {
                while(true) {
                    r = entries.take();
                    if (r == poison) {
                        Log.logInfo("WIKITRANSLATION", "producer / got poison");
                        break;
                    }
                    out.println("  <page start=\"" + r.start + "\" length=\"" + (r.end - r.start) + "\">");
                    out.println("    <title>" + r.title + "</title>");
                    out.println("  </page>");
                    Log.logInfo("WIKITRANSLATION", "producer / record start: " + r.start + ", title : " + r.title);
                    count++;
                }
            } catch (InterruptedException e) {
                Log.logException(e);
            }
            entries.clear();
            out.println("</index>");
            out.close();
            return Integer.valueOf(count);
        }
        
    }
    
    private static class wikiConsumer implements Callable<Integer> {

        private   final BlockingQueue<wikiraw> entries;
        protected static wikiraw poison = new wikiraw(new byte[0], 0, 0);
        private   final indexProducer producer;
        private   int count;
        
        public wikiConsumer(int bufferCount, indexProducer producer) {
            entries = new ArrayBlockingQueue<wikiraw>(bufferCount);
            this.producer = producer;
            count = 0;
        }
        
        public void consume(wikiraw b) {
            try {
                entries.put(b);
            } catch (InterruptedException e) {
                Log.logException(e);
            }
        }
        
        public Integer call() {
            wikisourcerecord r;
            wikiraw c;
            try {
                while(true) {
                    c = entries.take();
                    if (c == poison) {
                        Log.logInfo("WIKITRANSLATION", "consumer / got poison");
                        break;
                    }
                    try {
                        r = new wikisourcerecord(c.b, c.start, c.end);
                        producer.consume(r);
                        Log.logInfo("WIKITRANSLATION", "consumer / record start: " + r.start + ", title : " + r.title);
                        count++;
                    } catch (RuntimeException e) {}
                }
            } catch (InterruptedException e) {
                Log.logException(e);
            }
            entries.clear();
            return Integer.valueOf(count);
        }
        
    }

    private static class wikiraw {
        public long start, end;
        public byte[] b;
        public wikiraw(byte[] b, long start, long end) {
            this.b = b;
            this.start = start;
            this.end = end;
        }
    }
    
    public static class wikisourcerecord {
        public long start, end;
        public String title;
        public wikisourcerecord(String title, long start, long end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
        public wikisourcerecord(byte[] chunk, long start, long end) {
            String s;
            s = UTF8.String(chunk);
            int t0 = s.indexOf("<title>");
            if (t0 >= 0) {
                int t1 = s.indexOf("</title>", t0);
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
    public wikiparserrecord newRecord(String hostport, String urlStub, String title, StringBuilder sb) {
        return new wikiparserrecord(hostport, urlStub, title, sb);
    }
    
    public class wikiparserrecord {
        public String title;
        String source, html, hostport, urlStub;
        DigestURI url;
        Document document;
        public wikiparserrecord(String hostport, String urlStub, String title, StringBuilder sb) {
            this.title = title;
            this.hostport = hostport;
            this.urlStub = urlStub;
            this.source = (sb == null) ? null : sb.toString();
        }
        public void genHTML() throws IOException {
            try {
                WikiParser wparser = new WikiCode();
                html = wparser.transform(hostport, source);
            } catch (Exception e) {
                Log.logException(e);
                throw new IOException(e.getMessage());
            }
        }
        public void genDocument() throws Parser.Failure {
            try {
				url = new DigestURI(urlStub + title);
				Document[] parsed = TextParser.parseSource(url, "text/html", "UTF-8", UTF8.getBytes(html));
				document = Document.mergeDocuments(url, "text/html", parsed);
				// the wiki parser is not able to find the proper title in the source text, so it must be set here
				document.setTitle(title);
			} catch (MalformedURLException e1) {
			    Log.logException(e1);
			}
        }
        public void writeXML(OutputStreamWriter os) throws IOException {
            document.writeXML(os, new Date());
        }
    }
    
    private static class PositionAwareReader {
    
        private final InputStream is;
        private long seekpos;
        private ByteBuffer bb;
        
        public PositionAwareReader(File dumpFile) throws FileNotFoundException {
            this.is = new BufferedInputStream(new FileInputStream(dumpFile), 64 *1024);
            this.seekpos = 0;
            this.bb = new ByteBuffer();
        }
        
        public void resetBuffer() {
            if (bb.length() > 10 * 1024) bb = new ByteBuffer(); else bb.clear();
        }
        
        public boolean seek(byte[] pattern) throws IOException {
            int pp = 0;
            int c;
            while ((c = is.read()) >= 0) {
                seekpos++;
                bb.append(c);
                if (pattern[pp] == c) pp++; else pp = 0;
                if (pp == pattern.length) return true;
            }
            return false;
        }
        
        public long pos() {
            return seekpos;
        }
        
        public byte[] bytes() {
            return bb.getBytes();
        }
        
        public void close() {
            try {
                is.close();
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }

    public static byte[] read(File f, long start, int len) {
        byte[] b = new byte[len];
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            raf.seek(start);
            raf.read(b);
        } catch (IOException e) {
            Log.logException(e);
            return null;
        } finally {
            if (raf != null) try {
                raf.close();
                try{raf.getChannel().close();} catch (IOException e) {}
            } catch (IOException e) { }
        }
        return b;
    }
    
    public static wikisourcerecord find(String title, File f) throws IOException {
        PositionAwareReader in = new PositionAwareReader(f);
        long start;
        String m = "<title>" + title + "</title>";
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
                int p = s.indexOf("start=\"");
                if (p < 0) return null;
                p += 7;
                int q = s.indexOf('"', p + 1);
                if (q < 0) return null;
                start = Long.parseLong(s.substring(p, q));
                p = s.indexOf("length=\"", q);
                if (p < 0) return null;
                p += 8;
                q = s.indexOf('"', p + 1);
                if (q < 0) return null;
                int length = Integer.parseInt(s.substring(p, q));
                //Log.logInfo("WIKITRANSLATION", "start = " + start + ", length = " + length);
                return new wikisourcerecord(title, start, start + length);
            }
        }
        return null;
    }
    
    private static class convertConsumer implements Callable<Integer> {

        private final BlockingQueue<wikiparserrecord> in, out;
        private final wikiparserrecord poison;
        
        public convertConsumer(BlockingQueue<wikiparserrecord> in, BlockingQueue<wikiparserrecord> out, wikiparserrecord poison) {
            this.poison = poison;
            this.in = in;
            this.out = out;
        }
        
        public Integer call() {
            wikiparserrecord record;
            try {
                while(true) {
                    record = in.take();
                    if (record == poison) {
                        Log.logInfo("WIKITRANSLATION", "convertConsumer / got poison");
                        break;
                    }
                    try {
                        record.genHTML();
                        record.genDocument();
                        out.put(record);
                    } catch (RuntimeException e) {
                        Log.logException(e);
                    } catch (Parser.Failure e) {
                        Log.logException(e);
                    } catch (IOException e) {
						// TODO Auto-generated catch block
                        Log.logException(e);
					}
                }
            } catch (InterruptedException e) {
                Log.logException(e);
            }
            Log.logInfo("WIKITRANSLATION", "*** convertConsumer has terminated");
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
                BlockingQueue<wikiparserrecord> in,
                wikiparserrecord poison,
                File targetdir,
                String targetstub) {
            this.poison = poison;
            this.in = in;
            this.osw = null;
            this.targetdir = targetdir;
            this.targetstub = targetstub;
            this.fc = 0;
            this.rc = 0;
            this.outputfilename = null;
        }
        
        public Integer call() {
            wikiparserrecord record;
            try {
                while(true) {
                    record = in.take();
                    if (record == poison) {
                        Log.logInfo("WIKITRANSLATION", "convertConsumer / got poison");
                        break;
                    }
                    
                    if (osw == null) {
                        // start writing a new file
                        this.outputfilename = targetstub + "." + fc + ".xml.prt";
                        this.osw = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(new File(targetdir, outputfilename))), "UTF-8");
                        osw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + SurrogateReader.SURROGATES_MAIN_ELEMENT_OPEN + "\n");
                    }
                    Log.logInfo("WIKITRANSLATION", "[CONSUME] Title: " + record.title);
                    record.document.writeXML(osw, new Date());
                    rc++;
                    if (rc >= 10000) {
                        osw.write("</surrogates>\n");
                        osw.close();
                        String finalfilename = targetstub + "." + fc + ".xml";
                        new File(targetdir, outputfilename).renameTo(new File(targetdir, finalfilename));
                        rc = 0;
                        fc++;
                        outputfilename = targetstub + "." + fc + ".xml.prt";
                        osw = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(new File(targetdir, outputfilename))), "UTF-8");
                        osw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + SurrogateReader.SURROGATES_MAIN_ELEMENT_OPEN + "\n");
                    }
                }
            } catch (InterruptedException e) {
                Log.logException(e);
            } catch (UnsupportedEncodingException e) {
                Log.logException(e);
            } catch (FileNotFoundException e) {
                Log.logException(e);
            } catch (IOException e) {
                Log.logException(e);
            } finally {
	            try {
					osw.write(SurrogateReader.SURROGATES_MAIN_ELEMENT_CLOSE + "\n");
		            osw.close();
		            String finalfilename = targetstub + "." + fc + ".xml";
		            new File(targetdir, outputfilename).renameTo(new File(targetdir, finalfilename));
				} catch (IOException e) {
				    Log.logException(e);
				}
            }
            Log.logInfo("WIKITRANSLATION", "*** convertWriter has terminated");
            return Integer.valueOf(0);
        }
        
    }
    
    public static void main(String[] s) {
        if (s.length == 0) {
            Log.logInfo("WIKITRANSLATION", "usage:");
            Log.logInfo("WIKITRANSLATION", " -index <wikipedia-dump>");
            Log.logInfo("WIKITRANSLATION", " -read  <start> <len> <idx-file>");
            Log.logInfo("WIKITRANSLATION", " -find  <title> <wikipedia-dump>");
            Log.logInfo("WIKITRANSLATION", " -convert <wikipedia-dump-xml.bz2> <convert-target-dir> <url-stub>");
            System.exit(0);
        }

        // example:
        // java -Xmx2000m -cp classes:lib/bzip2.jar de.anomic.tools.mediawikiIndex -convert DATA/HTCACHE/dewiki-20090311-pages-articles.xml.bz2 DATA/SURROGATES/in/ http://de.wikipedia.org/wiki/

        if (s[0].equals("-convert") && s.length > 2) {
            File sourcefile = new File(s[1]);
            File targetdir = new File(s[2]);
            //String urlStub = s[3]; // i.e. http://de.wikipedia.org/wiki/
            //String language = urlStub.substring(7,9);
            try {
                MediawikiImporter mi = new MediawikiImporter(sourcefile, targetdir);
                mi.start();
                mi.join();
            } catch (InterruptedException e) {
                Log.logException(e);
            }
        }
        
        if (s[0].equals("-index")) {   
            try {
                createIndex(new File(s[1]));
            } catch (IOException e) {
                Log.logException(e);
            }
        }
        
        if (s[0].equals("-read")) {
            long start = Integer.parseInt(s[1]);
            int  len   = Integer.parseInt(s[2]);
            System.out.println(UTF8.String(read(new File(s[3]), start, len)));
        }
        
        if (s[0].equals("-find")) {
            try {
                wikisourcerecord w = find(s[1], new File(s[2] + ".idx.xml"));
                if (w == null) {
                    Log.logInfo("WIKITRANSLATION", "not found");
                } else {
                    System.out.println(UTF8.String(read(new File(s[2]), w.start, (int) (w.end - w.start))));
                }
            } catch (IOException e) {
                Log.logException(e);
            }
            
        }
        System.exit(0);
    }
    
}
