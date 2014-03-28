/**
 *  Peers
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 21.09.2012 at http://yacy.net
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

package net.yacy.cora.federate.yacy;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.federate.yacy.api.Network;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;

public class Peers extends TreeMap<byte[], Peer> implements Serializable {

    public final static String[] bootstrapPeers = new String[]{
        "search.yacy.net", "yacy.dyndns.org:8000", "yacy-websuche.mxchange.org:8090",
        "sokrates.homeunix.net:6070", "sokrates.homeunix.net:9090",
        "141.52.175.27:8080", "62.75.214.113:8080", "141.52.175.30:8080"};

    private final static ConcurrentLog log = new ConcurrentLog(Peers.class.getName());
    private static final long serialVersionUID = -2939656606305545080L;
    private long lastBootstrap;

    
    public Peers() {
        super(Base64Order.enhancedCoder);
        this.lastBootstrap = 0;
    }
    
    /**
     * refresh() gets a new network list from one random remote peer once every
     * minute. This method will load a remote list not more then every one minute
     * and if it does, it is done concurrently. Therefore this method can be called
     * every time when a process needs specific remote peers.
     */
    public void refresh() {
        if (System.currentTimeMillis() - this.lastBootstrap < 60000) return;
        lastBootstrap = System.currentTimeMillis();
        new Thread() {
            @Override
            public void run() {
                String[] peers = bootstrapList(select(false, false));
                bootstrap(peers, 1);
            }
        }.start();
    }
    
    /**
     * this method must be called once to bootstrap a list of network peers.
     * To do this, a default list of peers must be given.
     * @param peers a list of known peers
     * @param selection number of peers which are taken from the given list of peers for bootstraping
     */
    public void bootstrap(final String[] peers, int selection) {
        int loops = 0;
        while (this.size() == 0 || loops++ == 0) {
            if (selection > peers.length) selection = peers.length;
            Set<Integer> s = new HashSet<Integer>();
            Random r = new Random(System.currentTimeMillis());
            while (s.size() < selection) s.add(r.nextInt(peers.length));
            List<Thread> t = new ArrayList<Thread>();
            for (Integer pn: s) {
                final String bp = peers[pn.intValue()];
                Thread t0 = new Thread() {
                    @Override
                    public void run() {
                        Peers ps;
                        try {
                            ps = Network.getNetwork(bp);
                            int c0 = Peers.this.size();
                            for (Peer p: ps.values()) Peers.this.add(p);
                            int c1 = Peers.this.size();
                            log.info("bootstrap with peer " + bp + ": added " + (c1 - c0) + " peers");
                        } catch (final IOException e) {
                            log.info("bootstrap with peer " + bp + ": FAILED - " + e.getMessage());
                        }
                    }
                };
                t0.start();
                t.add(t0);
            }
            for (Thread t0: t) try {t0.join(10000);} catch (final InterruptedException e) {}
        }
        lastBootstrap = System.currentTimeMillis();
        log.info("bootstrap finished: " + this.size() + " peers");
    }
    
    /**
     * add a new peer to the list of peers
     * @param peer
     */
    public synchronized void add(Peer peer) {
        String hash = peer.get(Peer.Schema.hash);
        if (hash == null) return;
        Peer p = this.put(ASCII.getBytes(hash), peer);
        if (p == null) return;
        if (p.lastseenTime() < peer.lastseenTime()) this.put(ASCII.getBytes(hash), p);
    }
    
    /**
     * get a peer using the peer hash
     * @param hash
     * @return
     */
    public synchronized Peer get(String hash) {
        return super.get(ASCII.getBytes(hash));
    }

    /**
     * select a list of peers according to special needs. The require parameters are combined as conjunction
     * @param requireNode must be true to select only peers which are node peers
     * @param requireSolr must be true to select only peers which support the solr interface
     * @return
     */
    public synchronized List<Peer> select(final boolean requireNode, final boolean requireSolr) {
        List<Peer> l = new ArrayList<Peer>();
        for (Peer p: this.values()) {
            if (requireNode && !p.get(Peer.Schema.nodestate).equals("1")) continue;
            if (requireSolr && !p.supportsSolr()) continue;
            l.add(p);
        }
        return l;
    }
    
    /**
     * convenient method to produce a list of bootstrap peer addresses from given peer lists
     * @param peers
     * @return
     */
    public static synchronized String[] bootstrapList(List<Peer> peers) {
        List<String> l = new ArrayList<String>();
        for (Peer p: peers) l.add(p.get(Peer.Schema.address));
        return l.toArray(new String[l.size()]);
    }

    public static void main(String[] args) {
        Peers peers = new Peers();
        peers.bootstrap(Peers.bootstrapPeers, 4);
        //Peers peers = Network.getNetwork("sokrates.homeunix.net:9090");
        /*
        for (Peer p: peers.values()) {
            log.info(p.get(Peer.Schema.fullname) + " - " + p.get(Peer.Schema.address));
        }
        */
        List<Peer> nodes = peers.select(false, true);
        for (Peer p: nodes) {
            log.info(p.get(Peer.Schema.fullname) + " - " + p.get(Peer.Schema.address));
        }
        try {HTTPClient.closeConnectionManager();} catch (final InterruptedException e) {}
    }
    
}
