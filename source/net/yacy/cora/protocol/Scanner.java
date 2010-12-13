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

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;

/**
 * a protocol scanner
 * scans given ip's for existing http, https, ftp and smb services
 */
public class Scanner extends Thread {

    private static final MultiProtocolURI POISONURI = new MultiProtocolURI();
    private static final Object PRESENT = new Object();
    
    public static Map<byte[], DigestURI> intranetURLs = new TreeMap<byte[], DigestURI>(Base64Order.enhancedCoder); // deprecated
    public static Collection<MultiProtocolURI> scancache = new ArrayList<MultiProtocolURI>(1);
    
    private int runnerCount;
    private List<InetAddress> scanrange;
    private BlockingQueue<MultiProtocolURI> scanqueue;
    private Map<MultiProtocolURI, String> services;
    private Map<Runner, Object> runner;
    private int timeout;

    public Scanner(InetAddress scanrange, int concurrentRunner, int timeout) {
        this.runnerCount = concurrentRunner;
        this.scanrange = new ArrayList<InetAddress>();
        this.scanrange.add(scanrange);
        this.scanqueue = new LinkedBlockingQueue<MultiProtocolURI>();
        this.services = Collections.synchronizedMap(new TreeMap<MultiProtocolURI, String>());
        this.runner = new ConcurrentHashMap<Runner, Object>();
        this.timeout = timeout;
    }

    public Scanner(List<InetAddress> scanrange, int concurrentRunner, int timeout) {
        this.runnerCount = concurrentRunner;
        this.scanrange = scanrange;
        this.scanqueue = new LinkedBlockingQueue<MultiProtocolURI>();
        this.services = Collections.synchronizedMap(new TreeMap<MultiProtocolURI, String>());
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
                        services.put(new MultiProtocolURI(this.uri.getProtocol() + "://" + Domains.getHostName(InetAddress.getByName(this.uri.getHost())) + "/"), "");
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (UnknownHostException e) {
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
    
    public Collection<MultiProtocolURI> services() {
        return this.services.keySet();
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
        for (MultiProtocolURI service: scanner.services()) {
            System.out.println(service.toNormalform(true, false));
        }
        try {
            HTTPClient.closeConnectionManager();
        } catch (InterruptedException e) {
        }
    }
}
