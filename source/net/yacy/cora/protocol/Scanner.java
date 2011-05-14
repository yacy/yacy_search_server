/**
 *  Scanner
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 28.10.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.cora.protocol.http.HTTPClient;

/**
 * a protocol scanner
 * scans given ip's for existing http, https, ftp and smb services
 */
public class Scanner extends Thread {

    private static final Service POISONSERVICE = new Service(Protocol.http, null);
    private static final Object PRESENT = new Object();
    
    public static enum Access {unknown, empty, granted, denied;}
    public static enum Protocol {http(80), https(443), ftp(21), smb(445);
        public int port;
        private Protocol(int port) {this.port = port;}
    }
    public static class Service {
        public Protocol protocol;
        public InetAddress inetAddress;
        private String hostname;
        public Service(Protocol protocol, InetAddress inetAddress) {
            this.protocol = protocol;
            this.inetAddress = inetAddress;
            this.hostname = null;
        }
        public Service(String protocol, InetAddress inetAddress) {
            this.protocol = protocol.equals("http") ? Protocol.http : protocol.equals("https") ? Protocol.https : protocol.equals("ftp") ? Protocol.ftp : Protocol.smb;
            this.inetAddress = inetAddress;
            this.hostname = null;
        }
        public Protocol getProtocol() {
            return this.protocol;
        }
        public InetAddress getInetAddress() {
            return this.inetAddress;
        }
        public String getHostName() {
            if (this.hostname != null) return this.hostname;
            this.hostname = Domains.getHostName(this.inetAddress);
            return this.hostname;
        }
        public MultiProtocolURI url() throws MalformedURLException {
            return new MultiProtocolURI(this.protocol.name() + "://" + getHostName() + "/");
        }
        @Override
        public String toString() {
            try {
                return new MultiProtocolURI(this.protocol.name() + "://" + this.inetAddress.getHostAddress() + "/").toNormalform(true, false);
            } catch (MalformedURLException e) {
                return "";
            }
        }
        @Override
        public int hashCode() {
            return this.inetAddress.hashCode();
        }
        @Override
        public boolean equals(Object o) {
            return (o instanceof Service) && ((Service) o).protocol == this.protocol && ((Service) o).inetAddress.equals(this.inetAddress);
        }
    }
    
    private final static Map<Service, Access> scancache = new HashMap<Service, Access>();
    //private       static long scancacheUpdateTime = 0;
    //private       static long scancacheValidUntilTime = Long.MAX_VALUE;
    private       static Set<InetAddress> scancacheScanrange = new HashSet<InetAddress>();

    public static int scancacheSize() {
        return scancache.size();
    }
    
    public static void scancacheReplace(Scanner newScanner, long validTime) {
        scancache.clear();
        scancache.putAll(newScanner.services());
        //scancacheUpdateTime = System.currentTimeMillis();
        //scancacheValidUntilTime = validTime == Long.MAX_VALUE ? Long.MAX_VALUE : scancacheUpdateTime + validTime;
        scancacheScanrange = newScanner.scanrange;
    }
    
    public static void scancacheExtend(Scanner newScanner, long validTime) {
        Iterator<Map.Entry<Service, Access>> i = Scanner.scancache.entrySet().iterator();
        Map.Entry<Service, Access> entry;
        while (i.hasNext()) {
            entry = i.next();
            if (entry.getValue() != Access.granted) i.remove();
        }
        scancache.putAll(newScanner.services());
        //scancacheUpdateTime = System.currentTimeMillis();
        //scancacheValidUntilTime = validTime == Long.MAX_VALUE ? Long.MAX_VALUE : scancacheUpdateTime + validTime;
        scancacheScanrange = newScanner.scanrange;
    }
    
    public static Iterator<Map.Entry<Service, Scanner.Access>> scancacheEntries() {
        return scancache.entrySet().iterator();
    }
    
    /**
     * check if the url can be accepted by the scanner. the scanner accepts the url if:
     * - the host of the url is not supervised (it is not in the scan range), or
     * - the host is supervised (it is in the scan range) and the host is in the scan cache
     * @param url
     * @return true if the url shall be part of a search result
     */
    public static boolean acceptURL(MultiProtocolURI url) {
        // if the scan range is empty, then all urls are accepted
        if (scancacheScanrange == null || scancacheScanrange.isEmpty()) return true;
        
        //if (System.currentTimeMillis() > scancacheValidUntilTime) return true;
        InetAddress a = Domains.dnsResolve(url.getHost()); // try to avoid that!
        if (a == null) return true;
        InetAddress n = normalize(a);
        if (!scancacheScanrange.contains(n)) return true;
        Access access = scancache.get(new Service(url.getProtocol(), a));
        if (access == null) return false;
        return access == Access.granted;
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
    private BlockingQueue<Service> scanqueue;
    private Map<Service, Access> services;
    private Map<Runner, Object> runner;
    private int timeout;

    public Scanner(Set<InetAddress> scanrange, int concurrentRunner, int timeout) {
        this.runnerCount = concurrentRunner;
        this.scanrange = new HashSet<InetAddress>();
        for (InetAddress a: scanrange) this.scanrange.add(normalize(a));
        this.scanqueue = new LinkedBlockingQueue<Service>();
        this.services = Collections.synchronizedMap(new HashMap<Service, Access>());
        this.runner = new ConcurrentHashMap<Runner, Object>();
        this.timeout = timeout;
    }
    
    public Scanner(int concurrentRunner, int timeout) {
        this(Domains.myIntranetIPs(), concurrentRunner, timeout);
    }
    
    @Override
    public void run() {
        Service uri;
        try {
            while ((uri = scanqueue.take()) != POISONSERVICE) {
                while (runner.size() >= this.runnerCount) {
                    /*for (Runner r: runner.keySet()) {
                        if (r.age() > 3000) synchronized(r) { r.interrupt(); }
                    }*/
                    if (runner.size() >= this.runnerCount) Thread.sleep(20);
                }
                Runner runner = new Runner(uri);
                this.runner.put(runner, PRESENT);
                runner.start();
            }
        } catch (InterruptedException e) {
        }
    }

    public int pending() {
        return this.scanqueue.size();
    }
    
    public void terminate() {
        for (int i = 0; i < runnerCount; i++) try {
            this.scanqueue.put(POISONSERVICE);
        } catch (InterruptedException e) {
        }
        try {
            this.join();
        } catch (InterruptedException e) {
        }
    }
    
    public class Runner extends Thread {
        private Service service;
        private long starttime;
        public Runner(Service service) {
            this.service = service;
            this.starttime = System.currentTimeMillis();
        }
        @Override
        public void run() {
            try {
                if (TimeoutRequest.ping(this.service.getInetAddress().getHostAddress(), this.service.getProtocol().port, timeout)) {
                    Access access = this.service.getProtocol() == Protocol.http || this.service.getProtocol() == Protocol.https ? Access.granted : Access.unknown;
                    services.put(service, access);
                    if (access == Access.unknown) {
                        // ask the service if it lets us in
                        if (this.service.getProtocol() == Protocol.ftp) {
                            final FTPClient ftpClient = new FTPClient();
                            try {
                                ftpClient.open(this.service.getInetAddress().getHostAddress(), this.service.getProtocol().port);
                                ftpClient.login("anonymous", "anomic@");
                                List<String> list = ftpClient.list("/", false);
                                ftpClient.CLOSE();
                                access = list == null || list.isEmpty() ? Access.empty : Access.granted;
                            } catch (IOException e) {
                                access = Access.denied;
                            }
                        }
                        if (this.service.getProtocol() == Protocol.smb) {
                            try {
                                MultiProtocolURI uri = new MultiProtocolURI(this.service.toString());
                                String[] list = uri.list();
                                access = list == null || list.length == 0 ? Access.empty : Access.granted;
                            } catch (IOException e) {
                                access = Access.denied;
                            }
                        }
                    }
                    if (access != Access.unknown) services.put(this.service, access);
                }
            } catch (ExecutionException e) {
            }
            Object r = runner.remove(this);
            assert r != null;
        }
        public long age() {
            return System.currentTimeMillis() - this.starttime;
        }
        @Override
        public boolean equals(Object o) {
            return (o instanceof Runner) && this.service.equals(((Runner) o).service);
        }
        @Override
        public int hashCode() {
            return this.service.hashCode();
        }
    }
    
    public void addHTTP(boolean bigrange) {
        addProtocol(Protocol.http, bigrange);
    }

    public void addHTTPS(boolean bigrange) {
        addProtocol(Protocol.https, bigrange);
    }

    public void addSMB(boolean bigrange) {
        addProtocol(Protocol.smb, bigrange);
    }
    
    public void addFTP(boolean bigrange) {
        addProtocol(Protocol.ftp, bigrange);
    }
    
    private void addProtocol(Protocol protocol, boolean bigrange) {
        for (InetAddress i: genlist(bigrange)) {
            try {
                this.scanqueue.put(new Service(protocol, i));
            } catch (InterruptedException e) {
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
    
    public Map<Service, Access> services() {
        return this.services;
    }
    
    public static byte[] inIndex(Map<byte[], String> commentCache, String url) {
        for (Map.Entry<byte[], String> comment: commentCache.entrySet()) {
            if (comment.getValue().contains(url)) return comment.getKey();
        }
        return null;
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
        for (Service service: scanner.services().keySet()) {
            System.out.println(service.toString());
        }
        try {
            HTTPClient.closeConnectionManager();
        } catch (InterruptedException e) {
        }
    }
}
