// URLAnalysis.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.02.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-01-02 12:38:20 +0100 (Fr, 02 Jan 2009) $
// $LastChangedRevision: 5432 $
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


package de.anomic.data;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import de.anomic.kelondro.util.MemoryControl;
import de.anomic.yacy.yacyURL;

public class URLAnalysis {

    /**
     * processes to analyse URL lists
     */
    
    public static yacyURL poison = null;
    static {
        try {
            poison = new yacyURL("http://poison.org/poison", null);
        } catch (MalformedURLException e) {
            poison = null;
        }
    }
    
    public static class splitter extends Thread {
        
        ArrayBlockingQueue<yacyURL> in;
        ConcurrentHashMap<String, Integer> out;

        public splitter(ArrayBlockingQueue<yacyURL> in, ConcurrentHashMap<String, Integer> out) {
            this.in = in;
            this.out = out;
        }
        
        public void run() {
            yacyURL url;
            Pattern p = Pattern.compile("~|\\(|\\)|\\+|-|@|:|%|\\.|;|_");
            while (true) {
                try {
                    url = in.take();
                    if (url == poison) break;
                    //System.out.println(url);
                    update(url.getHost().replaceAll("-", "\\.").split("\\."));
                    update(p.matcher(url.getPath()).replaceAll("/").split("/"));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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
    
    public static void main(String[] args) {
        String filename = args[0];
        String analysis = filename + ".stats";

        // start threads
        ArrayBlockingQueue<yacyURL> in = new ArrayBlockingQueue<yacyURL>(1000);
        ConcurrentHashMap<String, Integer> out = new ConcurrentHashMap<String, Integer>();
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) new splitter(in, out).start();
        splitter spl = new splitter(in, out);
        spl.start();

        // put urls in queue
        File infile = new File(filename);
        File outfile = new File(analysis);
        BufferedReader reader = null;
        long time = System.currentTimeMillis();
        long start = time;
        int count = 0;

        System.out.println("start processing");
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(infile)));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0) {
                    yacyURL url = new yacyURL(line, null);
                    try {
                        in.put(url);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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
            BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outfile));
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
    
    private static final String num(int i) {
        String s = Integer.toString(i);
        while (s.length() < 9) s = "0" + s;
        return s;
    }
}
