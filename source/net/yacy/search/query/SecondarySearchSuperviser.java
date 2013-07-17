package net.yacy.search.query;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.SetTools;
import net.yacy.peers.RemoteSearch;
import net.yacy.search.Switchboard;

public class SecondarySearchSuperviser extends Thread {

    // cache for index abstracts; word:TreeMap mapping where the embedded TreeMap is a urlhash:peerlist relation
    // this relation contains the information where specific urls can be found in specific peers
    private final SortedMap<String, SortedMap<String, Set<String>>> abstractsCache;
    private final SortedSet<String> checkedPeers;
    private final Semaphore trigger;
    private final SearchEvent searchEvent;

    protected SecondarySearchSuperviser(SearchEvent searchEvent) {
        this.abstractsCache = Collections.synchronizedSortedMap(new TreeMap<String, SortedMap<String, Set<String>>>());
        this.checkedPeers = Collections.synchronizedSortedSet(new TreeSet<String>());
        this.trigger = new Semaphore(0);
        this.searchEvent = searchEvent;
    }

    /**
     * add a single abstract to the existing set of abstracts
     *
     * @param wordhash
     * @param singleAbstract // a mapping from url-hashes to a string of peer-hashes
     */
    public void addAbstract(final String wordhash, final SortedMap<String, Set<String>> singleAbstract) {
        final SortedMap<String, Set<String>> oldAbstract = this.abstractsCache.get(wordhash);
        if ( oldAbstract == null ) {
            // new abstracts in the cache
            this.abstractsCache.put(wordhash, singleAbstract);
            return;
        }
        // extend the abstracts in the cache: join the single abstracts
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("SearchEvent.addAbstract:" + wordhash);
                for ( final Map.Entry<String, Set<String>> oneref : singleAbstract.entrySet() ) {
                    final String urlhash = oneref.getKey();
                    final Set<String> peerlistNew = oneref.getValue();
                    final Set<String> peerlistOld = oldAbstract.put(urlhash, peerlistNew);
                    if ( peerlistOld != null ) {
                        peerlistOld.addAll(peerlistNew);
                    }
                }
            }
        }.start();
        // abstractsCache.put(wordhash, oldAbstract); // put not necessary since it is sufficient to just change the value content (it stays assigned)
    }

    public void commitAbstract() {
        this.trigger.release();
    }

    private Set<String> wordsFromPeer(final String peerhash, final Set<String> urls) {
        Set<String> wordlist = new HashSet<String>();
        String word;
        Set<String> peerlist;
        SortedMap<String, Set<String>> urlPeerlist; // urlhash:peerlist
        for ( Map.Entry<String, SortedMap<String, Set<String>>> entry: this.abstractsCache.entrySet()) {
            word = entry.getKey();
            urlPeerlist = entry.getValue();
            for (String url: urls) {
                peerlist = urlPeerlist.get(url);
                if (peerlist != null && peerlist.contains(peerhash)) {
                    wordlist.add(word);
                    break;
                }
            }
        }
        return wordlist;
    }

    @Override
    public void run() {
        try {
            boolean aquired;
            while ( (aquired = this.trigger.tryAcquire(3000, TimeUnit.MILLISECONDS)) == true ) { // compare to true to remove warning: "Possible accidental assignement"
                if ( !aquired || MemoryControl.shortStatus()) {
                    break;
                }
                // a trigger was released
                prepareSecondarySearch();
            }
        } catch (final InterruptedException e ) {
            // the thread was interrupted
            // do nothing
        }
        // the time-out was reached:
        // as we will never again prepare another secondary search, we can flush all cached data
        this.abstractsCache.clear();
        this.checkedPeers.clear();
    }

    private void prepareSecondarySearch() {
        if ( this.abstractsCache == null || this.abstractsCache.size() != this.searchEvent.query.getQueryGoal().getIncludeHashes().size() ) {
            return; // secondary search not possible (yet)
        }

        // catch up index abstracts and join them; then call peers again to submit their urls
        /*
        System.out.println("DEBUG-INDEXABSTRACT: " + this.abstractsCache.size() + " word references caught, " + SearchEvent.this.query.queryHashes.size() + " needed");
        for (final Map.Entry<String, SortedMap<String, Set<String>>> entry: this.abstractsCache.entrySet()) {
            System.out.println("DEBUG-INDEXABSTRACT: hash " + entry.getKey() + ": " + ((SearchEvent.this.query.queryHashes.has(entry.getKey().getBytes()) ? "NEEDED" : "NOT NEEDED") + "; " + entry.getValue().size() + " entries"));
        }
        */

        // find out if there are enough references for all words that are searched
        if ( this.abstractsCache.size() != this.searchEvent.query.getQueryGoal().getIncludeHashes().size() ) {
            return;
        }

        // join all the urlhash:peerlist relations: the resulting map has values with a combined peer-list list
        final SortedMap<String, Set<String>> abstractJoin = SetTools.joinConstructive(this.abstractsCache.values(), true);
        if ( abstractJoin.isEmpty() ) {
            return;
            // the join result is now a urlhash: peer-list relation
        }

        // generate a list of peers that have the urls for the joined search result
        final SortedMap<String, Set<String>> secondarySearchURLs = new TreeMap<String, Set<String>>(); // a (peerhash:urlhash-liststring) mapping
        String url;
        Set<String> urls;
        Set<String> peerlist;
        final String mypeerhash = this.searchEvent.peers.mySeed().hash;
        boolean mypeerinvolved = false;
        int mypeercount;
        for ( final Map.Entry<String, Set<String>> entry : abstractJoin.entrySet() ) {
            url = entry.getKey();
            peerlist = entry.getValue();
            //System.out.println("DEBUG-INDEXABSTRACT: url " + url + ": from peers " + peerlist);
            mypeercount = 0;
            for (String peer: peerlist) {
                if ( (peer.equals(mypeerhash)) && (mypeercount++ > 1) ) {
                    continue;
                }
                //if (peers.indexOf(peer) < j) continue; // avoid doubles that may appear in the abstractJoin
                urls = secondarySearchURLs.get(peer);
                if ( urls == null ) {
                    urls = new HashSet<String>();
                    urls.add(url);
                    secondarySearchURLs.put(peer, urls);
                } else {
                    urls.add(url);
                }
                secondarySearchURLs.put(peer, urls);
            }
            if ( mypeercount == 1 ) {
                mypeerinvolved = true;
            }
        }

        // compute words for secondary search and start the secondary searches
        Set<String> words;
        this.searchEvent.secondarySearchThreads = new Thread[(mypeerinvolved) ? secondarySearchURLs.size() - 1 : secondarySearchURLs.size()];
        int c = 0;
        for ( final Map.Entry<String, Set<String>> entry : secondarySearchURLs.entrySet() ) {
            String peer = entry.getKey();
            if ( peer.equals(mypeerhash) ) {
                continue; // we don't need to ask ourself
            }
            if ( this.checkedPeers.contains(peer) ) {
                continue; // do not ask a peer again
            }
            urls = entry.getValue();
            words = wordsFromPeer(peer, urls);
            if ( words.isEmpty() ) {
                continue; // ???
            }
            ConcurrentLog.info("SearchEvent.SecondarySearchSuperviser", "asking peer " + peer + " for urls: " + urls + " from words: " + words);
            this.checkedPeers.add(peer);
            this.searchEvent.secondarySearchThreads[c++] =
                RemoteSearch.secondaryRemoteSearch(
                    this.searchEvent,
                    words,
                    urls.toString(),
                    6000,
                    peer,
                    Switchboard.urlBlacklist);
        }
    }
}
