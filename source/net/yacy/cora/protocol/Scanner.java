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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.cora.protocol.http.HTTPClient;

/**
 * a protocol scanner
 * scans given ip's for existing http, https, ftp and smb services
 */
public class Scanner {

    public static enum Access {unknown, empty, granted, denied;}
    public static enum Protocol {http(80), https(443), ftp(21), smb(445);
        public int port;
        private Protocol(final int port) {this.port = port;}
    }
    public class Service implements Runnable {
        public Protocol protocol;
        public InetAddress inetAddress;
        private String hostname;
        private final long starttime;
        public Service(final Protocol protocol, final InetAddress inetAddress) {
            this.protocol = protocol;
            this.inetAddress = inetAddress;
            this.hostname = null;
            this.starttime = System.currentTimeMillis();
        }
        public Service(final String protocol, final InetAddress inetAddress) {
            this.protocol = protocol.equals("http") ? Protocol.http : protocol.equals("https") ? Protocol.https : protocol.equals("ftp") ? Protocol.ftp : Protocol.smb;
            this.inetAddress = inetAddress;
            this.hostname = null;
            this.starttime = System.currentTimeMillis();
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
        public DigestURL url() throws MalformedURLException {
            return new DigestURL(this.protocol.name() + "://" + getHostName() + "/");
        }
        @Override
        public String toString() {
            try {
                return new MultiProtocolURL(this.protocol.name() + "://" + this.inetAddress.getHostAddress() + "/").toNormalform(true);
            } catch (final MalformedURLException e) {
                return "";
            }
        }
        @Override
        public int hashCode() {
            return (this.inetAddress.toString() + ":" + protocol.port).hashCode();
        }
        @Override
        public boolean equals(final Object o) {
            return (o instanceof Service) && ((Service) o).protocol == this.protocol && ((Service) o).inetAddress.equals(this.inetAddress);
        }
        @Override
        public void run() {
            try {
                Thread.currentThread().setName("Scanner.Runner: Ping to " + this.getInetAddress().getHostAddress() + ":" + this.getProtocol().port); // good for debugging
                if (TimeoutRequest.ping(this.getInetAddress().getHostAddress(), this.getProtocol().port, Scanner.this.timeout)) {
                    Access access = this.getProtocol() == Protocol.http || this.getProtocol() == Protocol.https ? Access.granted : Access.unknown;
                    Scanner.this.services.put(this, access);
                    if (access == Access.unknown) {
                        // ask the service if it lets us in
                        if (this.getProtocol() == Protocol.ftp) {
                            final FTPClient ftpClient = new FTPClient();
                            try {
                                ftpClient.open(this.getInetAddress().getHostAddress(), this.getProtocol().port);
                                ftpClient.login(FTPClient.ANONYMOUS, "anomic@");
                                final List<String> list = ftpClient.list("/", false);
                                ftpClient.CLOSE();
                                access = list == null || list.isEmpty() ? Access.empty : Access.granted;
                            } catch (final IOException e) {
                                access = Access.denied;
                            }
                        }
                        if (this.getProtocol() == Protocol.smb) {
                            try {
                                final MultiProtocolURL uri = new MultiProtocolURL(this.toString());
                                final String[] list = uri.list();
                                access = list == null || list.length == 0 ? Access.empty : Access.granted;
                            } catch (final IOException e) {
                                access = Access.denied;
                            }
                        }
                    }
                    if (access != Access.unknown) Scanner.this.services.put(this, access);
                }
            } catch (final ExecutionException e) {
            } catch (final OutOfMemoryError e) {
            }
        }
        public long age() {
            return System.currentTimeMillis() - this.starttime;
        }
    }

    private final static Map<Service, Access> scancache = new ConcurrentHashMap<Service, Access>();

    public static int scancacheSize() {
        return scancache.size();
    }

    public static void scancacheReplace(final Scanner newScanner) {
        scancache.clear();
        scancache.putAll(newScanner.services());
    }

    public static void scancacheExtend(final Scanner newScanner) {
        final Iterator<Map.Entry<Service, Access>> i = Scanner.scancache.entrySet().iterator();
        Map.Entry<Service, Access> entry;
        while (i.hasNext()) {
            entry = i.next();
            if (entry.getValue() != Access.granted) i.remove();
        }
        scancache.putAll(newScanner.services());
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
    public static boolean acceptURL(final MultiProtocolURL url) {
        // if the scan range is empty, then all urls are accepted
        if (scancache == null || scancache.isEmpty()) return true;

        //if (System.currentTimeMillis() > scancacheValidUntilTime) return true;
        final InetAddress a = url.getInetAddress(); // try to avoid that!
        if (a == null) return true;
        for (Map.Entry<Service, Access> entry: scancache.entrySet()) {
            Service service = entry.getKey();
            if (service.inetAddress.equals(a) && service.protocol.toString().equals(url.getProtocol())) {
                Access access = entry.getValue();
                if (access == null) return false;
                return access == Access.granted;
            }
        }
        return true;
    }
    
    private final Map<Service, Access> services;
    private final ThreadPoolExecutor threadPool;
    private final int timeout;

    public Scanner(final int concurrentRunner, final int timeout) {
        this.services = Collections.synchronizedMap(new HashMap<Service, Access>());
        this.threadPool = new ThreadPoolExecutor(concurrentRunner, concurrentRunner, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        this.timeout = timeout;
    }

    public int pending() {
        return this.threadPool.getQueue().size() + this.threadPool.getActiveCount();
    }

    public void terminate() {
        this.threadPool.shutdown();
    }

    public void addProtocols(final List<InetAddress> addresses, boolean http, boolean https, boolean ftp, boolean smb) {
        if (http) addProtocol(Protocol.http, addresses);
        if (https) addProtocol(Protocol.https, addresses);
        if (ftp) addProtocol(Protocol.ftp, addresses);
        if (smb) addProtocol(Protocol.smb, addresses);
    }
    
    private void addProtocol(final Protocol protocol, final List<InetAddress> addresses) {
        for (final InetAddress i: addresses) {
            threadPool.execute(new Service(protocol, i));
        }
    }

    /**
     * generate a list of internetaddresses
     * @param subnet the subnet: 24 will generate 254 addresses, 16 will generate 256 * 254; must be >= 16 and <= 24
     * @return
     */
    public static final List<InetAddress> genlist(Collection<InetAddress> base, final int subnet) {
        final ArrayList<InetAddress> c = new ArrayList<InetAddress>(1);
        for (final InetAddress i: base) {
            genlist(c, i, subnet);
        }
        return c;
    }
    public static final List<InetAddress> genlist(InetAddress base, final int subnet) {
        final ArrayList<InetAddress> c = new ArrayList<InetAddress>(1);
        genlist(c, base, subnet);
        return c;
    }
    private static final void genlist(ArrayList<InetAddress> c, InetAddress base, final int subnet) {
            if (subnet == 31) {
                try {
                    c.add(InetAddress.getByAddress(base.getAddress()));
                } catch (final UnknownHostException e) {}
            } else {
                int ul = subnet >= 24 ? base.getAddress()[2] : (1 << (24 - subnet)) - 1;
                for (int br = subnet >= 24 ? base.getAddress()[2] : 0; br <= ul; br++) {
                    for (int j = 1; j < 255; j++) {
                        final byte[] address = base.getAddress();
                        address[2] = (byte) br;
                        address[3] = (byte) j;
                        try {
                            c.add(InetAddress.getByAddress(address));
                        } catch (final UnknownHostException e) {
                        }
                    }
                }
            }
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
        //try {System.out.println("192.168.1.91: " + ping(new MultiProtocolURI("smb://192.168.1.91/"), 1000));} catch (final MalformedURLException e) {}
        final Scanner scanner = new Scanner(100, 10);
        List<InetAddress> addresses = genlist(Domains.myIntranetIPs(), 20);
        scanner.addProtocols(addresses, true, true, true, true);
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
