/**
 *  Scanner
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 28.10.2010 at http://yacy.net
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

package net.yacy.cora.protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.logging.Log;

/**
 * a protocol scanner
 * scans given ip's for existing http, https, ftp and smb services
 */
public class Scanner extends Thread {

    private static final MultiProtocolURI POISONURI = new MultiProtocolURI();
    private static final Object PRESENT = new Object();
    
    public static enum Access {unknown, empty, granted, denied;}
    
    private final static Map<MultiProtocolURI, Access> scancache = new TreeMap<MultiProtocolURI, Access>();
    private       static long scancacheUpdateTime = 0;
    private       static long scancacheValidUntilTime = Long.MAX_VALUE;
    private       static Set<InetAddress> scancacheScanrange = new HashSet<InetAddress>();

    public static int scancacheSize() {
        return scancache.size();
    }
    
    public static void scancacheReplace(Scanner newScanner, long validTime) {
        scancache.clear();
        scancache.putAll(newScanner.services());
        scancacheUpdateTime = System.currentTimeMillis();
        scancacheValidUntilTime = validTime == Long.MAX_VALUE ? Long.MAX_VALUE : scancacheUpdateTime + validTime;
        scancacheScanrange = newScanner.scanrange;
    }
    
    public static void scancacheExtend(Scanner newScanner, long validTime) {
        Iterator<Map.Entry<MultiProtocolURI, Access>> i = Scanner.scancache.entrySet().iterator();
        Map.Entry<MultiProtocolURI, Access> entry;
        while (i.hasNext()) {
            entry = i.next();
            if (entry.getValue() != Access.granted) i.remove();
        }
        scancache.putAll(newScanner.services());
        scancacheUpdateTime = System.currentTimeMillis();
        scancacheValidUntilTime = validTime == Long.MAX_VALUE ? Long.MAX_VALUE : scancacheUpdateTime + validTime;
        scancacheScanrange = newScanner.scanrange;
    }
    
    public static Iterator<Map.Entry<MultiProtocolURI, Scanner.Access>> scancacheEntries() {
        return scancache.entrySet().iterator();
    }
    
    public static boolean acceptURL(MultiProtocolURI url) {
        if (scancacheScanrange == null || scancacheScanrange.size() == 0) return true;
        
        //if (System.currentTimeMillis() > scancacheValidUntilTime) return true;
        InetAddress a = Domains.dnsResolve(url.getHost());
        if (a == null) return true;
        InetAddress n = normalize(a);
        if (!scancacheScanrange.contains(n)) return true;
        MultiProtocolURI uri;
        try {
            uri = produceURI(url.getProtocol(), a);
            return scancache.containsKey(uri);
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private static InetAddress normalize(InetAddress a) {
        if (a == null) return null;
        byte[] b = a.getAddress();
        if (b[3] == 1) return a;
        b[3] = 1;
        try {
            return InetAddress.getByAddress(b);
        } catch (UnknownHostException e) {
            return a;
        }
    }
    
    private int runnerCount;
    private Set<InetAddress> scanrange;
    private BlockingQueue<MultiProtocolURI> scanqueue;
    private Map<MultiProtocolURI, Access> services;
    private Map<Runner, Object> runner;
    private int timeout;

    public Scanner(InetAddress scanrange, int concurrentRunner, int timeout) {
        this.runnerCount = concurrentRunner;
        this.scanrange = new HashSet<InetAddress>();
        this.scanrange.add(normalize(scanrange));
        this.scanqueue = new LinkedBlockingQueue<MultiProtocolURI>();
        this.services = Collections.synchronizedMap(new TreeMap<MultiProtocolURI, Access>());
        this.runner = new ConcurrentHashMap<Runner, Object>();
        this.timeout = timeout;
    }

    public Scanner(Set<InetAddress> scanrange, int concurrentRunner, int timeout) {
        this.runnerCount = concurrentRunner;
        this.scanrange = new HashSet<InetAddress>();
        for (InetAddress a: scanrange) this.scanrange.add(normalize(a));
        this.scanqueue = new LinkedBlockingQueue<MultiProtocolURI>();
        this.services = Collections.synchronizedMap(new TreeMap<MultiProtocolURI, Access>());
        this.runner = new ConcurrentHashMap<Runner, Object>();
        this.timeout = timeout;
    }
    
    public Scanner(int concurrentRunner, int timeout) {
        this(Domains.myIntranetIPs(), concurrentRunner, timeout);
    }
    
    public void run() {
        MultiProtocolURI uri;
        try {
            while ((uri = scanqueue.take()) != POISONURI) {
                while (runner.size() >= this.runnerCount) {
                    /*for (Runner r: runner.keySet()) {
                        if (r.age() > 3000) synchronized(r) { r.interrupt(); }
                    }*/
                    if (runner.size() >= this.runnerCount) Thread.sleep(1000);
                }
                Runner runner = new Runner(uri);
                this.runner.put(runner, PRESENT);
                runner.start();
            }
        } catch (InterruptedException e) {
            Log.logException(e);
        }
    }

    public int pending() {
        return this.scanqueue.size();
    }
    
    public void terminate() {
        for (int i = 0; i < runnerCount; i++) try {
            this.scanqueue.put(POISONURI);
        } catch (InterruptedException e) {
        }
        try {
            this.join();
        } catch (InterruptedException e) {
        }
    }
    
    private static MultiProtocolURI produceURI(String protocol, InetAddress a) throws MalformedURLException {
        return new MultiProtocolURI(protocol + "://" + Domains.getHostName(a) + "/");
    }
    
    public class Runner extends Thread {
        private MultiProtocolURI uri;
        private long starttime;
        public Runner(MultiProtocolURI uri) {
            this.uri = uri;
            this.starttime = System.currentTimeMillis();
        }
        public void run() {
            try {
                if (TimeoutRequest.ping(this.uri, timeout)) {
                    try {
                        MultiProtocolURI uri = produceURI(this.uri.getProtocol(), Domains.dnsResolve(this.uri.getHost()));
                        String protocol = uri.getProtocol();
                        Access access = protocol.equals("http") || protocol.equals("https") ? Access.granted : Access.unknown;
                        services.put(uri, access);
                        if (access == Access.unknown) {
                            // ask the service if it lets us in
                            if (protocol.equals("ftp")) {
                                final FTPClient ftpClient = new FTPClient();
                                try {
                                    ftpClient.open(uri.getHost(), uri.getPort());
                                    ftpClient.login("anonymous", "anomic@");
                                    List<String> list = ftpClient.list("/", false);
                                    ftpClient.CLOSE();
                                    access = list == null || list.size() == 0 ? Access.empty : Access.granted;
                                } catch (IOException e) {
                                    access = Access.denied;
                                }
                            }
                            if (protocol.equals("smb")) {
                                try {
                                    String[] list = uri.list();
                                    access = list == null || list.length == 0 ? Access.empty : Access.granted;
                                } catch (IOException e) {
                                    access = Access.denied;
                                }
                            }
                        }
                        if (access != Access.unknown) services.put(uri, access);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                }
            } catch (ExecutionException e) {
            }
            Object r = runner.remove(this);
            assert r != null;
        }
        public long age() {
            return System.currentTimeMillis() - this.starttime;
        }
        public boolean equals(Object o) {
            return (o instanceof Runner) && this.uri.toNormalform(true, false).equals(((Runner) o).uri.toNormalform(true, false));
        }
        public int hashCode() {
            return this.uri.hashCode();
        }
    }
    
    public void addHTTP(boolean bigrange) {
        addProtocol("http", bigrange);
    }

    public void addHTTPS(boolean bigrange) {
        addProtocol("https", bigrange);
    }

    public void addSMB(boolean bigrange) {
        addProtocol("smb", bigrange);
    }
    
    public void addFTP(boolean bigrange) {
        addProtocol("ftp", bigrange);
    }
    
    private void addProtocol(String protocol, boolean bigrange) {
        for (InetAddress i: genlist(bigrange)) {
            try {
                this.scanqueue.put(new MultiProtocolURI(protocol + "://" + i.getHostAddress() + "/"));
            } catch (MalformedURLException e) {
                Log.logException(e);
            } catch (InterruptedException e) {
                Log.logException(e);
            }
        }
    }
    
    private final List<InetAddress> genlist(boolean bigrange) {
        ArrayList<InetAddress> c = new ArrayList<InetAddress>(10);
        for (InetAddress i: scanrange) {
            for (int br = bigrange ? 1 : i.getAddress()[2]; br < (bigrange ? 255 : i.getAddress()[2] + 1); br++) {
                for (int j = 1; j < 255; j++) {
                    byte[] address = i.getAddress();
                    address[2] = (byte) br;
                    address[3] = (byte) j;
                    try {
                        c.add(InetAddress.getByAddress(address));
                    } catch (UnknownHostException e) {
                    }
                }
            }
        }
        return c;
    }
    
    public Map<MultiProtocolURI, Access> services() {
        return this.services;
    }
    
    public static void main(String[] args) {
        //try {System.out.println("192.168.1.91: " + ping(new MultiProtocolURI("smb://192.168.1.91/"), 1000));} catch (MalformedURLException e) {}
        Scanner scanner = new Scanner(100, 10);
        scanner.addFTP(false);
        scanner.addHTTP(false);
        scanner.addHTTPS(false);
        scanner.addSMB(false);
        scanner.start();
        scanner.terminate();
        for (MultiProtocolURI service: scanner.services().keySet()) {
            System.out.println(service.toNormalform(true, false));
        }
        try {
            HTTPClient.closeConnectionManager();
        } catch (InterruptedException e) {
        }
    }
}
