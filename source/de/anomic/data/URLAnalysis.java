// URLAnalysis.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.02.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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


package de.anomic.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.HandleMap;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.rwi.ReferenceContainerArray;
import net.yacy.kelondro.util.MemoryControl;

import de.anomic.search.MetadataRepository;
import de.anomic.search.Segment;
import de.anomic.search.MetadataRepository.Export;

public class URLAnalysis {

    /**
     * processes to analyse URL lists
     */

    public static DigestURI poison = null;
    static {
        try {
            poison = new DigestURI("http://poison.org/poison", null);
        } catch (MalformedURLException e) {
            poison = null;
        }
    }
    
    public static class splitter extends Thread {
        
        ArrayBlockingQueue<DigestURI> in;
        ConcurrentHashMap<String, Integer> out;

        public splitter(ArrayBlockingQueue<DigestURI> in, ConcurrentHashMap<String, Integer> out) {
            this.in = in;
            this.out = out;
        }
        
        public void run() {
            try {
                DigestURI url;
                Pattern p = Pattern.compile("~|\\(|\\)|\\+|-|@|:|%|\\.|;|_");
                while (true) {
                    try {
                        url = in.take();
                        if (url == poison) break;
                        update(url.getHost().replaceAll("-", "\\.").split("\\."));
                        update(p.matcher(url.getPath()).replaceAll("/").split("/"));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        private void update(String[] s) {
            Integer c;
            for (String t: s) {
                if (t.length() == 0) continue;
                c = out.get(t);
                out.put(t, (c == null) ? 1 : c.intValue() + 1);
            }
        }
    }
    
    public static void cleanup(ConcurrentHashMap<String, Integer> stat) {
    	Map.Entry<String, Integer> entry;
    	int c, low = Integer.MAX_VALUE;
    	Iterator<Map.Entry<String, Integer>> i = stat.entrySet().iterator();
    	while (i.hasNext()) {
    		entry = i.next();
    		c = entry.getValue().intValue();
    		if (c == 1) {
    			i.remove();
    		} else {
    			if (c < low) low = c;
    		}
    	}
    	i = stat.entrySet().iterator();
    	while (i.hasNext()) {
    		entry = i.next();
    		c = entry.getValue().intValue();
    		if (c == low) {
    			i.remove();
    		}
    	}
    	Runtime.getRuntime().gc();
    }
    
    public static void genstat(String urlfile) {

        boolean gz = urlfile.endsWith(".gz");
        String analysis = (gz) ? urlfile.substring(0, urlfile.length() - 3) + ".stats.gz" : urlfile  + ".stats";
        long cleanuplimit = Math.max(50 * 1024 * 1024, MemoryControl.available() / 8);

        // start threads
        ArrayBlockingQueue<DigestURI> in = new ArrayBlockingQueue<DigestURI>(1000);
        ConcurrentHashMap<String, Integer> out = new ConcurrentHashMap<String, Integer>();
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) new splitter(in, out).start();
        splitter spl = new splitter(in, out);
        spl.start();

        // put urls in queue
        File infile = new File(urlfile);
        File outfile = new File(analysis);
        BufferedReader reader = null;
        long time = System.currentTimeMillis();
        long start = time;
        int count = 0;

        System.out.println("start processing");
        try {
            InputStream is = new BufferedInputStream(new FileInputStream(infile));
            if (gz) is = new GZIPInputStream(is);
            reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0) {
                    try {
                        DigestURI url = new DigestURI(line, null);
                        in.put(url);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (MalformedURLException e) {
                        continue;
                    }
                }
                count++;
                if (System.currentTimeMillis() - time > 1000) {
                    time = System.currentTimeMillis();
                    System.out.println("processed " + count + " urls, " + (MemoryControl.available() / 1024 / 1024) + " mb left, " + count * 1000L / (time - start) + " url/second");
                    if (MemoryControl.available() < cleanuplimit) {
                    	System.out.println("starting cleanup, " + out.size() + " entries in statistic");
                    	cleanup(out);
                    	System.out.println("finished cleanup, " + out.size() + " entries in statistic left, " + (MemoryControl.available() / 1024 / 1024) + " mb left");
                    }
                }
            }
            reader.close();
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {}
        }

        // stop threads
        System.out.println("stopping threads");
        for (int i = 0; i < Runtime.getRuntime().availableProcessors() + 1; i++) try {
            in.put(poison);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            spl.join();
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        
        // generate statistics
        System.out.println("start processing results");
        TreeMap<String, Integer> results = new TreeMap<String, Integer>();
        count = 0;
        Map.Entry<String, Integer> entry;
        Iterator<Map.Entry<String, Integer>> i = out.entrySet().iterator();
        while (i.hasNext()) {
            entry = i.next();
            results.put(num(entry.getValue().intValue() * (entry.getKey().length() - 1)) + " - " + entry.getKey(), entry.getValue());
            count++;
            i.remove(); // free memory
            if (System.currentTimeMillis() - time > 10000) {
                time = System.currentTimeMillis();
                System.out.println("processed " + count + " results, " + (MemoryControl.available() / 1024 / 1024) + " mb left");
            }
        }
        
        // write statistics
        System.out.println("start writing results");
        try {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(outfile));
            if (gz) os = new GZIPOutputStream(os);
            count = 0;
            for (Map.Entry<String, Integer> e: results.entrySet()) {
                os.write(e.getKey().getBytes());
                os.write(new byte[]{'\t'});
                os.write(("" + e.getValue()).getBytes());
                os.write(new byte[]{'\n'});
                count++;
                if (System.currentTimeMillis() - time > 10000) {
                    time = System.currentTimeMillis();
                    System.out.println("wrote " + count + " lines.");
                }
            }
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        System.out.println("finished");
    }
    
    public static void genhost(String urlfile) {

        boolean gz = urlfile.endsWith(".gz");
        String trunk = (gz) ? urlfile.substring(0, urlfile.length() - 3) + ".host" : urlfile  + ".host";
        HashSet<String> hosts = new HashSet<String>();
        File infile = new File(urlfile);
        BufferedReader reader = null;
        long time = System.currentTimeMillis();
        long start = time;
        int count = 0;

        System.out.println("start processing");
        try {
            InputStream is = new BufferedInputStream(new FileInputStream(infile));
            if (gz) is = new GZIPInputStream(is);
            reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0) {
                    try {
                        DigestURI url = new DigestURI(line, null);
                        hosts.add(url.getHost());
                    } catch (MalformedURLException e) {
                        continue;
                    }
                }
                count++;
                if (System.currentTimeMillis() - time > 1000) {
                    time = System.currentTimeMillis();
                    System.out.println("processed " + count + " urls, " + (MemoryControl.available() / 1024 / 1024) + " mb left, " + count * 1000L / (time - start) + " url/second");
                }
            }
            reader.close();
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {}
        }
        
        // copy everything into a TreeSet to order it
        System.out.println("start processing results");
        TreeSet<String> results = new TreeSet<String>();
        count = 0;
        Iterator<String> i = hosts.iterator();
        while (i.hasNext()) {
            results.add(i.next());
            count++;
            i.remove(); // free memory
            if (System.currentTimeMillis() - time > 10000) {
                time = System.currentTimeMillis();
                System.out.println("processed " + count + " results, " + (MemoryControl.available() / 1024 / 1024) + " mb left");
            }
        }
        
        // write hosts
        writeSet(trunk, gz, results);
        
        System.out.println("finished");
    }
    
    private static void writeSet(String trunk, boolean gz, Set<String> set) {

        // write hosts
        System.out.println("start writing results");
        File outfile = new File(trunk + ((gz) ? ".gz" : ""));
        long time = System.currentTimeMillis();
        try {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(outfile));
            if (gz) os = new GZIPOutputStream(os);
            int count = 0;
            for (String h: set) {
                os.write(h.getBytes());
                os.write(new byte[]{'\n'});
                count++;
                if (System.currentTimeMillis() - time > 10000) {
                    time = System.currentTimeMillis();
                    System.out.println("wrote " + count + " lines.");
                }
            }
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("finished writing results");
    }
    
    public static void sortsplit(String urlfile) {

        boolean gz = urlfile.endsWith(".gz");
        String trunk = ((gz) ? urlfile.substring(0, urlfile.length() - 3) : urlfile) + ".sort";
        File infile = new File(urlfile);
        TreeSet<String> urls = new TreeSet<String>();
        BufferedReader reader = null;
        long time = System.currentTimeMillis();
        long start = time;
        int count = 0;
        int filecount = 0;
        long cleanuplimit = Math.max(50 * 1024 * 1024, MemoryControl.available() / 8);

        System.out.println("start processing");
        try {
            InputStream is = new BufferedInputStream(new FileInputStream(infile));
            if (gz) is = new GZIPInputStream(is);
            reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0) {
                    try {
                        DigestURI url = new DigestURI(line, null);
                        urls.add(url.toNormalform(true, true));
                    } catch (MalformedURLException e) {
                        continue;
                    }
                }
                count++;
                if (System.currentTimeMillis() - time > 1000) {
                    time = System.currentTimeMillis();
                    System.out.println("processed " + count + " urls, " + (MemoryControl.available() / 1024 / 1024) + " mb left, " + count * 1000L / (time - start) + " url/second");
                }
                if (MemoryControl.available() < cleanuplimit) {
                    writeSet(trunk + "." + filecount, gz, urls);
                    filecount++;
                    urls.clear();
                    Runtime.getRuntime().gc();
                }
            }
            reader.close();
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {}
        }
        
        // write hosts
        writeSet(trunk + "." + filecount, gz, urls);

        System.out.println("finished");
    }
    
    public static void incell(File cellPath, String statisticPath) {
        try {
        	HandleMap idx = ReferenceContainerArray.referenceHashes(
                cellPath,
                Segment.wordReferenceFactory,
                Base64Order.enhancedCoder,
                WordReferenceRow.urlEntryRow);
            System.out.println("INDEX REFERENCE COLLECTION starting dump of statistics");
            idx.dump(new File(statisticPath));
            System.out.println("INDEX REFERENCE COLLECTION finished dump, wrote " + idx.size() + " entries to " + statisticPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int diffurlcol(String metadataPath, String statisticFile, String diffFile) throws IOException {
        System.out.println("INDEX DIFF URL-COL startup");
        HandleMap idx = new HandleMap(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 4, new File(statisticFile), 0);
        MetadataRepository mr = new MetadataRepository(new File(metadataPath), "text.urlmd", false, false);
        HandleSet hs = new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0, 1000000);
        System.out.println("INDEX DIFF URL-COL loaded dump, starting diff");
        long start = System.currentTimeMillis();
        long update = start - 7000;
        int c = 0;
        for (byte[] refhash: mr) {
            if (idx.get(refhash) == -1) {
                // the key exists as urlhash in the URL database, but not in the collection as referenced urlhash
                hs.put(refhash);
            }
            c++;
            if (System.currentTimeMillis() - update > 10000) {
                System.out.println("INDEX DIFF URL-COL running, checked " + c + ", found " + hs.size() + " missing references so far, " + (((System.currentTimeMillis() - start) * (mr.size() - c) / c) / 60000) + " minutes remaining");
                update = System.currentTimeMillis();
            }
        }
        mr.close();
        System.out.println("INDEX DIFF URL-COL finished diff, starting dump to " + diffFile);
        c = hs.dump(new File(diffFile));
        System.out.println("INDEX DIFF URL-COL finished dump, wrote " + c + " references that occur in the URL-DB, but not in the collection-dump");
        return c;
    }
    
    public static void export(String metadataPath, int format, String export, String diffFile) throws IOException {
        // format: 0=text, 1=html, 2=rss/xml
        System.out.println("URL EXPORT startup");
        MetadataRepository mr = new MetadataRepository(new File(metadataPath), "text.urlmd", false, false);
        HandleSet hs = (diffFile == null) ? null : new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, new File(diffFile), 0);
        System.out.println("URL EXPORT loaded dump, starting export");
        Export e = mr.export(new File(export), ".*", hs, format, false);
        try {
            e.join();
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        System.out.println("URL EXPORT finished export, wrote " + ((hs == null) ? mr.size() : hs.size()) + " entries");
    }
    
    public static void delete(String metadataPath, String diffFile) throws IOException {
        System.out.println("URL DELETE startup");
        MetadataRepository mr = new MetadataRepository(new File(metadataPath), "text.urlmd", false, false);
        int mrSize = mr.size();
        HandleSet hs = new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, new File(diffFile), 0);
        System.out.println("URL DELETE loaded dump, starting deletion of " + hs.size() + " entries from " + mrSize);
        for (byte[] refhash: hs) {
            mr.remove(new String(refhash));
        }
        System.out.println("URL DELETE finished deletions, " + mr.size() + " entries left in URL database");
    }
    
    public static void main(String[] args) {
        if (args[0].equals("-stat") && args.length >= 2) {
            // generate a statistics about common words in file, store to <file>.stat
            // example:
            // java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -stat DATA/EXPORT/urls1.txt.gz 
            for (int i = 1; i < args.length; i++) genstat(args[i]);
    	} else if (args[0].equals("-host") && args.length >= 2) {
    	    // generate a file <file>.host containing only the hosts of the urls
    		for (int i = 1; i < args.length; i++) genhost(args[i]);
        } else if (args[0].equals("-sort") && args.length >= 2) {
            // generate file <file>.x.sort with sorted lists and split the file in smaller pieces
            for (int i = 1; i < args.length; i++) sortsplit(args[i]);
        } else if (args[0].equals("-incell") && args.length >= 2) {
            // generate a dump of all referenced URL hashes from a given RICELL
            // example:
            // java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -incell DATA/INDEX/freeworld/TEXT/RICELL used.dump
            incell(new File(args[1]), args[2]);
        } else if (args[0].equals("-diffurlcol") && args.length >= 3) {
            // make a diff-file that contains hashes from the url database that do not occur in the collection reference dump
            // example:
            // java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -diffurlcol DATA/INDEX/freeworld/TEXT/METADATA used.dump diffurlcol.dump
            try {
                diffurlcol(args[1], args[2], args[3]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (args[0].equals("-export") && args.length >= 4) {
            // export a url-list file
            // example:
            // java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -export DATA/INDEX/freeworld/TEXT xml urls.xml diffurlcol.dump
            // instead of 'xml' (which is in fact a rss), the format can also be 'text' and 'html'
            int format = (args[2].equals("xml")) ? 2 : (args[2].equals("html")) ? 1 : 0;
            try {
                export(args[1], format, args[3], (args.length >= 5) ? args[4] : null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (args[0].equals("-delete") && args.length >= 3) {
            // delete from URLs as given by urlreference diff dump
            // example:
            // java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -delete DATA/INDEX/freeworld/TEXT diffurlcol.dump
            // instead of 'xml' (which is in fact a rss), the format can also be 'text' and 'html'
            try {
                delete(args[1], args[2]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
    		System.out.println("usage:");
    		System.out.println();
    		System.out.println("-stat <file> ");
    		System.out.println(" generate a statistics about common words in file, store to <file>.stat");
            System.out.println();
            System.out.println("-host <file>");
            System.out.println(" generate a file <file>.host containing only the hosts of the urls");
            System.out.println();
            System.out.println("-sort <file>");
            System.out.println(" generate file <file>.x.sort with sorted lists and split the file in smaller pieces");
            System.out.println();
            System.out.println("-incollection <path-to-RICOLLECTION> <file>");
            System.out.println(" generate a dump of all referenced URL hashes");
            System.out.println();
            System.out.println("-diffurlcol <path-to-URL-DB> <dump-from-incollection> <diff-dump>");
            System.out.println(" find URLs that occur in url-db but not in collections");
            System.out.println();
            System.out.println("-export <path-to-URL-DB> <format text|html|xml> <export-file> <diff-dump>");
            System.out.println(" export urls to file. the last argument can be omitted, then all urls are exported");
            System.out.println();
            System.out.println("-delete <path-to-URL-DB> <diff-dump>");
            System.out.println(" delete all urls that are listed in the diff-dump from the url-db");
            System.out.println();
            System.out.println("to do a complete clean-up of the url database, start the following:");
            System.out.println();
            System.out.println("java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -incollection DATA/INDEX/freeworld/TEXT/RICOLLECTION used.dump");
            System.out.println("java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -diffurlcol DATA/INDEX/freeworld/TEXT used.dump diffurlcol.dump");
            System.out.println("java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -export DATA/INDEX/freeworld/TEXT xml urls.xml diffurlcol.dump");
            System.out.println("java -Xmx1000m -cp classes de.anomic.data.URLAnalysis -delete DATA/INDEX/freeworld/TEXT diffurlcol.dump");
            System.out.println();
    	}
        System.exit(0); // kill remaining threads
    }
    
    private static final String num(int i) {
        String s = Integer.toString(i);
        while (s.length() < 9) s = "0" + s;
        return s;
    }
}
