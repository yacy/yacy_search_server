// mediawikiIndex.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.11.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.tools;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.anomic.kelondro.util.ByteBuffer;

/*
 * this class provides data structures to read a mediawiki dump file in xml format
 * as referenced with xmlns="http://www.mediawiki.org/xml/export-0.3/"
 */

public class mediawikiIndex {

    private static final byte[] pagestart = "<page>".getBytes();
    private static final byte[] pageend = "</page>".getBytes();
    
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
            } catch (IOException e) {
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
        while (in.seek(pagestart)) {
            start = in.pos() - 6;
            in.resetBuffer();
            if (!in.seek(pageend)) break;
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
            e.printStackTrace();
            return;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return;
        }
        in.close();
    }

    private static class indexProducer implements Callable<Integer> {

        private BlockingQueue<wikirecord> entries;
        PrintWriter out;
        private static wikirecord poison = new wikirecord("", 0, 0);
        int count;
        
        public indexProducer(int bufferCount, File indexFile) throws IOException {
            entries = new ArrayBlockingQueue<wikirecord>(bufferCount);
            out = new PrintWriter(new BufferedWriter(new FileWriter(indexFile)));
            count = 0;
            out.println("<index>");
            
        }
        
        public void consume(wikirecord b) {
            try {
                entries.put(b);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        public Integer call() {
            wikirecord r;
            try {
                while(true) {
                    r = entries.take();
                    if (r == poison) {
                        System.out.println("producer / got poison");
                        break;
                    }
                    out.println("  <page start=\"" + r.start + "\" length=\"" + (r.end - r.start) + "\">");
                    out.println("    <title>" + r.title + "</title>");
                    out.println("  </page>");
                    System.out.println("producer / record start: " + r.start + ", title : " + r.title);
                    count++;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            entries.clear();
            out.println("</index>");
            out.close();
            return Integer.valueOf(count);
        }
        
    }
    
    private static class wikiConsumer implements Callable<Integer> {

        private BlockingQueue<wikiraw> entries;
        private static wikiraw poison = new wikiraw(new byte[0], 0, 0);
        private indexProducer producer;
        private int count;
        
        public wikiConsumer(int bufferCount, indexProducer producer) {
            entries = new ArrayBlockingQueue<wikiraw>(bufferCount);
            this.producer = producer;
            count = 0;
        }
        
        public void consume(wikiraw b) {
            try {
                entries.put(b);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        public Integer call() {
            wikirecord r;
            wikiraw c;
            try {
                while(true) {
                    c = entries.take();
                    if (c == poison) {
                        System.out.println("consumer / got poison");
                        break;
                    }
                    try {
                        r = new wikirecord(c.b, c.start, c.end);
                        producer.consume(r);
                        System.out.println("consumer / record start: " + r.start + ", title : " + r.title);
                        count++;
                    } catch (RuntimeException e) {}
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
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
    
    public static class wikirecord {
        public long start, end;
        public String title;
        public wikirecord(String title, long start, long end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
        public wikirecord(byte[] chunk, long start, long end) {
            String s;
            try {
                s = new String(chunk, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e.getMessage());
            }
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
    
    private static class PositionAwareReader {
    
        private InputStream is;
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
                e.printStackTrace();
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
            e.printStackTrace();
            return null;
        } finally {
            if (raf != null) try {
                raf.close();
                try{raf.getChannel().close();} catch (IOException e) {}
            } catch (IOException e) { }
        }
        return b;
    }
    
    public static wikirecord find(String title, File f) throws IOException {
        PositionAwareReader in = new PositionAwareReader(f);
        long start;
        String m = "<title>" + title + "</title>";
        String s;
        while (in.seek("<page ".getBytes())) {
            start = in.pos() - 6;
            in.resetBuffer();
            if (!in.seek(pageend)) break;
            s = new String(in.bytes(), "UTF-8");
            in.resetBuffer();
            if (s.indexOf(m) >= 0) {
                // we found the record
                //System.out.println("s = " + s);
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
                //System.out.println("start = " + start + ", length = " + length);
                return new wikirecord(title, start, start + length);
            }
        }
        return null;
    }
    
    public static void main(String[] s) {
        if (s.length == 0) {
            System.out.println("usage:");
            System.out.println(" -index <wikipedia-dump>");
            System.out.println(" -read  <start> <len> <idx-file>");
            System.out.println(" -find  <title> <wikipedia-dump>");
            System.exit(0);
        }

        if (s[0].equals("-index")) {   
            try {
                createIndex(new File(s[1]));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        if (s[0].equals("-read")) {
            long start = Integer.parseInt(s[1]);
            int  len   = Integer.parseInt(s[2]);
            try {
                System.out.println(new String(read(new File(s[3]), start, len), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        
        if (s[0].equals("-find")) {
            try {
                wikirecord w = find(s[1], new File(s[2] + ".idx.xml"));
                if (w == null) {
                    System.out.println("not found");
                } else {
                    System.out.println(new String(read(new File(s[2]), w.start, (int) (w.end - w.start)), "UTF-8"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        }
    }
    
}
