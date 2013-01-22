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
        private Protocol(final int port) {this.port = port;}
    }
    public static class Service {
        public Protocol protocol;
        public InetAddress inetAddress;
        private String hostname;
        public Service(final Protocol protocol, final InetAddress inetAddress) {
            this.protocol = protocol;
            this.inetAddress = inetAddress;
            this.hostname = null;
        }
        public Service(final String protocol, final InetAddress inetAddress) {
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
            if (this.hostname != null) {
                if (this.hostname.equals(this.inetAddress.getHostAddress())) {
                    // if the hostname was created in case of a time-out from TimoutRequest
                    // then in rare cases we try to get that name again
                    if ( (System.currentTimeMillis() / 1000) % 10 != 1) return this.hostname;
                } else {
                    return this.hostname;
                }
            }
            try {
                this.hostname = TimeoutRequest.getHostName(this.inetAddress, 100);
                Domains.setHostName(this.inetAddress, this.hostname);
            } catch (final ExecutionException e) {
                this.hostname = this.inetAddress.getHostAddress();
            }
            //this.hostname = Domains.getHostName(this.inetAddress);
            return this.hostname;
        }
        public MultiProtocolURI url() throws MalformedURLException {
            return new MultiProtocolURI(this.protocol.name() + "://" + getHostName() + "/");
        }
        @Override
        public String toString() {
            try {
                return new MultiProtocolURI(this.protocol.name() + "://" + this.inetAddress.getHostAddress() + "/").toNormalform(true);
            } catch (final MalformedURLException e) {
                return "";
            }
        }
        @Override
        public int hashCode() {
            return this.inetAddress.hashCode();
        }
        @Override
        public boolean equals(final Object o) {
            return (o instanceof Service) && ((Service) o).protocol == this.protocol && ((Service) o).inetAddress.equals(this.inetAddress);
        }
    }

    private final static Map<Service, Access> scancache = new ConcurrentHashMap<Service, Access>();
    //private       static long scancacheUpdateTime = 0;
    //private       static long scancacheValidUntilTime = Long.MAX_VALUE;
    private       static Set<InetAddress> scancacheScanrange = new HashSet<InetAddress>();

    public static int scancacheSize() {
        return scancache.size();
    }

    public static void scancacheReplace(final Scanner newScanner) {
        scancache.clear();
        scancache.putAll(newScanner.services());
        //scancacheUpdateTime = System.currentTimeMillis();
        //scancacheValidUntilTime = validTime == Long.MAX_VALUE ? Long.MAX_VALUE : scancacheUpdateTime + validTime;
        scancacheScanrange = newScanner.scanrange;
    }

    public static void scancacheExtend(final Scanner newScanner) {
        final Iterator<Map.Entry<Service, Access>> i = Scanner.scancache.entrySet().iterator();
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
    public static boolean acceptURL(final MultiProtocolURI url) {
        // if the scan range is empty, then all urls are accepted
        if (scancacheScanrange == null || scancacheScanrange.isEmpty()) return true;

        //if (System.currentTimeMillis() > scancacheValidUntilTime) return true;
        final InetAddress a = url.getInetAddress(); // try to avoid that!
        if (a == null) return true;
        final InetAddress n = normalize(a);
        if (!scancacheScanrange.contains(n)) return true;
        final Access access = scancache.get(new Service(url.getProtocol(), a));
        if (access == null) return false;
        return access == Access.granted;
    }

    private static InetAddress normalize(final InetAddress a) {
        if (a == null) return null;
        final byte[] b = a.getAddress();
        if (b[3] == 1) return a;
        b[3] = 1;
        try {
            return InetAddress.getByAddress(b);
        } catch (final UnknownHostException e) {
            return a;
        }
    }

    private final int runnerCount;
    private final Set<InetAddress> scanrange;
    private final BlockingQueue<Service> scanqueue;
    private final Map<Service, Access> services;
    private final Map<Runner, Object> runner;
    private final int timeout;

    public Scanner(final Set<InetAddress> scanrange, final int concurrentRunner, final int timeout) {
        this.runnerCount = concurrentRunner;
        this.scanrange = new HashSet<InetAddress>();
        for (final InetAddress a: scanrange) this.scanrange.add(normalize(a));
        this.scanqueue = new LinkedBlockingQueue<Service>();
        this.services = Collections.synchronizedMap(new HashMap<Service, Access>());
        this.runner = new ConcurrentHashMap<Runner, Object>();
        this.timeout = timeout;
    }

    public Scanner(final int concurrentRunner, final int timeout) {
        this(Domains.myIntranetIPs(), concurrentRunner, timeout);
    }

    @Override
    public void run() {
        Service uri;
        try {
            while ((uri = this.scanqueue.take()) != POISONSERVICE) {
                Thread.currentThread().setName("Scanner Start Loop; now: " + uri.getHostName()); // good for debugging
                while (this.runner.size() >= this.runnerCount) {
                    /*for (Runner r: runner.keySet()) {
                        if (r.age() > 3000) synchronized(r) { r.interrupt(); }
                    }*/
                    if (this.runner.size() >= this.runnerCount) Thread.sleep(20);
                }
                final Runner runner = new Runner(uri);
                this.runner.put(runner, PRESENT);
                runner.start();
            }
        } catch (final InterruptedException e) {
        }
    }

    public int pending() {
        return this.scanqueue.size();
    }

    public void terminate() {
        for (int i = 0; i < this.runnerCount; i++) try {
            this.scanqueue.put(POISONSERVICE);
        } catch (final InterruptedException e) {
        }
        try {
            this.join();
        } catch (final InterruptedException e) {
        }
    }

    public class Runner extends Thread {
        private final Service service;
        private final long starttime;
        public Runner(final Service service) {
            this.service = service;
            this.starttime = System.currentTimeMillis();
        }
        @Override
        public void run() {
            try {
                Thread.currentThread().setName("Scanner.Runner: Ping to " + this.service.getInetAddress().getHostAddress() + ":" + this.service.getProtocol().port); // good for debugging
                if (TimeoutRequest.ping(this.service.getInetAddress().getHostAddress(), this.service.getProtocol().port, Scanner.this.timeout)) {
                    Access access = this.service.getProtocol() == Protocol.http || this.service.getProtocol() == Protocol.https ? Access.granted : Access.unknown;
                    Scanner.this.services.put(this.service, access);
                    if (access == Access.unknown) {
                        // ask the service if it lets us in
                        if (this.service.getProtocol() == Protocol.ftp) {
                            final FTPClient ftpClient = new FTPClient();
                            try {
                                ftpClient.open(this.service.getInetAddress().getHostAddress(), this.service.getProtocol().port);
                                ftpClient.login("anonymous", "anomic@");
                                final List<String> list = ftpClient.list("/", false);
                                ftpClient.CLOSE();
                                access = list == null || list.isEmpty() ? Access.empty : Access.granted;
                            } catch (final IOException e) {
                                access = Access.denied;
                            }
                        }
                        if (this.service.getProtocol() == Protocol.smb) {
                            try {
                                final MultiProtocolURI uri = new MultiProtocolURI(this.service.toString());
                                final String[] list = uri.list();
                                access = list == null || list.length == 0 ? Access.empty : Access.granted;
                            } catch (final IOException e) {
                                access = Access.denied;
                            }
                        }
                    }
                    if (access != Access.unknown) Scanner.this.services.put(this.service, access);
                }
            } catch (final ExecutionException e) {
            } catch (final OutOfMemoryError e) {
            }
            final Object r = Scanner.this.runner.remove(this);
            assert r != null;
        }
        public long age() {
            return System.currentTimeMillis() - this.starttime;
        }
        @Override
        public boolean equals(final Object o) {
            return (o instanceof Runner) && this.service.equals(((Runner) o).service);
        }
        @Override
        public int hashCode() {
            return this.service.hashCode();
        }
    }

    public void addHTTP(final boolean bigrange) {
        addProtocol(Protocol.http, bigrange);
    }

    public void addHTTPS(final boolean bigrange) {
        addProtocol(Protocol.https, bigrange);
    }

    public void addSMB(final boolean bigrange) {
        addProtocol(Protocol.smb, bigrange);
    }

    public void addFTP(final boolean bigrange) {
        addProtocol(Protocol.ftp, bigrange);
    }

    private void addProtocol(final Protocol protocol, final boolean bigrange) {
        for (final InetAddress i: genlist(bigrange)) {
            try {
                this.scanqueue.put(new Service(protocol, i));
            } catch (final InterruptedException e) {
            }
        }
    }

    private final List<InetAddress> genlist(final boolean bigrange) {
        final ArrayList<InetAddress> c = new ArrayList<InetAddress>(10);
        for (final InetAddress i: this.scanrange) {
            for (int br = bigrange ? 1 : i.getAddress()[2]; br < (bigrange ? 255 : i.getAddress()[2] + 1); br++) {
                for (int j = 1; j < 255; j++) {
                    final byte[] address = i.getAddress();
                    address[2] = (byte) br;
                    address[3] = (byte) j;
                    try {
                        c.add(InetAddress.getByAddress(address));
                    } catch (final UnknownHostException e) {
                    }
                }
            }
        }
        return c;
    }

    public Map<Service, Access> services() {
        return this.services;
    }

    public static byte[] inIndex(final Map<byte[], String> commentCache, final String url) {
        for (final Map.Entry<byte[], String> comment: commentCache.entrySet()) {
            if (comment.getValue().contains(url)) return comment.getKey();
        }
        return null;
    }

    public static void main(final String[] args) {
        //try {System.out.println("192.168.1.91: " + ping(new MultiProtocolURI("smb://192.168.1.91/"), 1000));} catch (MalformedURLException e) {}
        final Scanner scanner = new Scanner(100, 10);
        scanner.addFTP(false);
        scanner.addHTTP(false);
        scanner.addHTTPS(false);
        scanner.addSMB(false);
        scanner.start();
        scanner.terminate();
        for (final Service service: scanner.services().keySet()) {
            System.out.println(service.toString());
        }
        try {
            HTTPClient.closeConnectionManager();
        } catch (final InterruptedException e) {
        }
    }
}
