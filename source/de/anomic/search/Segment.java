// Segment.java
// (C) 2005-2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2005 on http://yacy.net; full redesign for segments 28.5.2009
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

package de.anomic.search;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.navigation.NavigationReference;
import net.yacy.kelondro.data.navigation.NavigationReferenceFactory;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.rwi.IODispatcher;
import net.yacy.kelondro.rwi.IndexCell;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceFactory;
import net.yacy.kelondro.util.ISO639;
import net.yacy.repository.Blacklist;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;

public class Segment {

    // environment constants
    public static final long wCacheMaxAge    = 1000 * 60 * 30; // milliseconds; 30 minutes
    public static final int  wCacheMaxChunk  =  800;           // maximum number of references for each urlhash
    public static final int  lowcachedivisor =  900;
    public static final long targetFileSize  = 256 * 1024 * 1024; // 256 MB
    public static final int  writeBufferSize = 4 * 1024 * 1024;
    
    // the reference factory
    public static final ReferenceFactory<WordReference> wordReferenceFactory = new WordReferenceFactory();
    public static final ReferenceFactory<NavigationReference> navigationReferenceFactory = new NavigationReferenceFactory();
    public static final ByteOrder wordOrder = Base64Order.enhancedCoder;
    
    private   final Log                            log;
    protected final IndexCell<WordReference>       termIndex;
    //private   final IndexCell<NavigationReference> authorNavIndex;
    protected final MetadataRepository             urlMetadata;
    private   final File                           segmentPath;
    private   final IODispatcher                   merger;
    
    public Segment(
            final Log log,
            final File segmentPath,
            final int entityCacheMaxSize,
            final long maxFileSize,
            final boolean useTailCache,
            final boolean exceed134217727) throws IOException {
        
        migrateTextIndex(segmentPath, segmentPath);
        migrateTextMetadata(segmentPath, segmentPath);
        
        log.logInfo("Initializing Segment '" + segmentPath + ".");

        this.log = log;
        this.segmentPath = segmentPath;
        
        this.merger = new IODispatcher(1, 1, writeBufferSize);
        this.merger.start();
        
        this.termIndex = new IndexCell<WordReference>(
                segmentPath,
                "text.index",
                wordReferenceFactory,
                wordOrder,
                WordReferenceRow.urlEntryRow,
                entityCacheMaxSize,
                targetFileSize,
                maxFileSize,
                this.merger,
                writeBufferSize);
        /*
        this.authorNavIndex = new IndexCell<NavigationReference>(
                new File(new File(segmentPath, "nav_author"), "idx"),
                navigationReferenceFactory,
                wordOrder,
                NavigationReferenceRow.navEntryRow,
                entityCacheMaxSize,
                targetFileSize,
                maxFileSize,
                this.merger,
                writeBufferSize);
        */

        // create LURL-db
        urlMetadata = new MetadataRepository(segmentPath, "text.urlmd", useTailCache, exceed134217727);
    }
    
    public static void migrateTextIndex(File oldSegmentPath, File newSegmentPath) {
        File oldCellPath = new File(oldSegmentPath, "RICELL");
        if (!oldCellPath.exists()) return;
        String[] oldIndexFiles = oldCellPath.list();
        for (String oldIndexFile: oldIndexFiles) {
            if (oldIndexFile.startsWith("index.")) {
                File newFile = new File(newSegmentPath, "text.index." + oldIndexFile.substring(6));
                new File(oldCellPath, oldIndexFile).renameTo(newFile);
            }
        }
        oldCellPath.delete();
    }
    
    public static void migrateTextMetadata(File oldSegmentPath, File newSegmentPath) {
        File oldMetadataPath = new File(oldSegmentPath, "METADATA");
        if (!oldMetadataPath.exists()) return;
        String[] oldMetadataFiles = oldMetadataPath.list();
        for (String oldMetadataFile: oldMetadataFiles) {
            if (oldMetadataFile.startsWith("urls.")) {
                File newFile = new File(newSegmentPath, "text.urlmd." + oldMetadataFile.substring(5));
                new File(oldMetadataPath, oldMetadataFile).renameTo(newFile);
            }
        }
        oldMetadataPath.delete();
    }
    
    public MetadataRepository urlMetadata() {
        return this.urlMetadata;
    }

    public IndexCell<WordReference> termIndex() {
        return this.termIndex;
    }
    
    public void clear() {
        try {
            termIndex.clear();
            urlMetadata.clear();
        } catch (final IOException e) {
            Log.logException(e);
        }
        if (Switchboard.getSwitchboard() != null &&
            Switchboard.getSwitchboard().peers != null &&
            Switchboard.getSwitchboard().peers.mySeed() != null) Switchboard.getSwitchboard().peers.mySeed().resetCounters();
    }
    
    public File getLocation() {
        return this.segmentPath;
    }

    /**
     * this is called by the switchboard to put in a new page into the index
     * use all the words in one condenser object to simultanous create index entries
     * 
     * @param url
     * @param urlModified
     * @param document
     * @param condenser
     * @param language
     * @param doctype
     * @param outlinksSame
     * @param outlinksOther
     * @return
     */
    private int addPageIndex(
            final DigestURI url,
            final Date urlModified,
            final Document document,
            final Condenser condenser,
            final String language,
            final char doctype,
            final int outlinksSame,
            final int outlinksOther,
            final SearchEvent searchEvent,
            final String sourceName) {
        RankingProcess rankingProcess = (searchEvent == null) ? null : searchEvent.getRankingResult();
        if (rankingProcess != null) rankingProcess.moreFeeders(1);
        int wordCount = 0;
        final int urlLength = url.toNormalform(true, true).length();
        final int urlComps = MultiProtocolURI.urlComps(url.toString()).length;
        
        // iterate over all words of context text
        final Iterator<Map.Entry<String, Word>> i = condenser.words().entrySet().iterator();
        Map.Entry<String, Word> wentry;
        String word;
        int len = (document == null) ? urlLength : document.dc_title().length();
        WordReferenceRow ientry = new WordReferenceRow(url.hash(),
                                urlLength, urlComps, len,
                                condenser.RESULT_NUMB_WORDS,
                                condenser.RESULT_NUMB_SENTENCES,
                                urlModified.getTime(),
                                System.currentTimeMillis(),
                                UTF8.getBytes(language),
                                doctype,
                                outlinksSame, outlinksOther);
        Word wprop;
        byte[] wordhash;
        while (i.hasNext()) {
            wentry = i.next();
            word = wentry.getKey();
            wprop = wentry.getValue();
            assert (wprop.flags != null);
            ientry.setWord(wprop);
            wordhash = Word.word2hash(word);
            try {
                this.termIndex.add(wordhash, ientry);
            } catch (Exception e) {
                Log.logException(e);
            }
            wordCount++;
            if (searchEvent != null && !searchEvent.getQuery().excludeHashes.has(wordhash) && searchEvent.getQuery().queryHashes.has(wordhash)) {
                ReferenceContainer<WordReference> container;
                try {
                    container = ReferenceContainer.emptyContainer(Segment.wordReferenceFactory, wordhash, 1);
                    container.add(ientry);
                    rankingProcess.add(container, true, sourceName, -1, !i.hasNext());
                } catch (RowSpaceExceededException e) {
                    continue;
                }
            }
        }
        if (rankingProcess != null) rankingProcess.oneFeederTerminated();
        return wordCount;
    }

    public void close() {
        if (this.merger != null) this.merger.terminate();
        termIndex.close();
        urlMetadata.close();
    }

    public URIMetadataRow storeDocument(
            final DigestURI url,
            final DigestURI referrerURL,
            Date modDate,
            final Date loadDate,
            final long sourcesize,
            final Document document,
            final Condenser condenser,
            final SearchEvent searchEvent,
            final String sourceName
            ) throws IOException {
        final long startTime = System.currentTimeMillis();

        // CREATE INDEX
        
        // load some document metadata
        final String dc_title = document.dc_title();
        
        // do a identification of the language
        String language = condenser.language(); // this is a statistical analysation of the content: will be compared with other attributes
        String bymetadata = document.dc_language(); // the languageByMetadata may return null if there was no declaration
        if (language == null) {
            // no statistics available, we take either the metadata (if given) or the TLD
            language = (bymetadata == null) ? url.language() : bymetadata;
            if (log.isFine()) log.logFine("LANGUAGE-BY-STATISTICS: " + url + " FAILED, taking " + ((bymetadata == null) ? "TLD" : "metadata") + ": " + language);
        } else {
            if (bymetadata == null) {
                // two possible results: compare and report conflicts
                if (language.equals(url.language()))
                    if (log.isFine()) log.logFine("LANGUAGE-BY-STATISTICS: " + url + " CONFIRMED - TLD IDENTICAL: " + language);
                else {
                    String error = "LANGUAGE-BY-STATISTICS: " + url + " CONFLICTING: " + language + " (the language given by the TLD is " + url.language() + ")";
                    // see if we have a hint in the url that the statistic was right
                    String u = url.toNormalform(true, false).toLowerCase();
                    if (!u.contains("/" + language + "/") && !u.contains("/" + ISO639.country(language).toLowerCase() + "/")) {
                        // no confirmation using the url, use the TLD
                        language = url.language();
                        if (log.isFine()) log.logFine(error + ", corrected using the TLD");
                    } else {
                        // this is a strong hint that the statistics was in fact correct
                        if (log.isFine()) log.logFine(error + ", but the url proves that the statistic is correct");
                    }
                }
            } else {
                // here we have three results: we can do a voting
                if (language.equals(bymetadata)) {
                    //if (log.isFine()) log.logFine("LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFIRMED - METADATA IDENTICAL: " + language);
                } else if (language.equals(url.language())) {
                    //if (log.isFine()) log.logFine("LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFIRMED - TLD IS IDENTICAL: " + language);
                } else if (bymetadata.equals(url.language())) {
                    //if (log.isFine()) log.logFine("LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFLICTING: " + language + " BUT METADATA AND TLD ARE IDENTICAL: " + bymetadata + ")");
                    language = bymetadata;
                } else {
                    //if (log.isFine()) log.logFine("LANGUAGE-BY-STATISTICS: " + entry.url() + " CONFLICTING: ALL DIFFERENT! statistic: " + language + ", metadata: " + bymetadata + ", TLD: + " + entry.url().language() + ". taking metadata.");
                    language = bymetadata;
                }
            }
        }
        
        // create a new loaded URL db entry
        if (modDate.getTime() > loadDate.getTime()) modDate = loadDate;
        final URIMetadataRow newEntry = new URIMetadataRow(
                url,                                       // URL
                dc_title,                                  // document description
                document.dc_creator(),                     // author
                document.dc_subject(' '),                  // tags
                document.dc_publisher(),                   // publisher (may be important to get location data)
                document.lon(),                            // decimal degrees as in WGS84;  
                document.lat(),                            // if unknown both values may be 0.0f;
                modDate,                                   // modification date
                loadDate,                                  // loaded date
                new Date(loadDate.getTime() + Math.max(0, loadDate.getTime() - modDate.getTime()) / 2), // freshdate, computed with Proxy-TTL formula 
                (referrerURL == null) ? null : UTF8.String(referrerURL.hash()),            // referer hash
                new byte[0],                               // md5
                (int) sourcesize,                          // size
                condenser.RESULT_NUMB_WORDS,               // word count
                Response.docType(document.dc_format()), // doctype
                condenser.RESULT_FLAGS,                    // flags
                UTF8.getBytes(language),                   // language
                document.inboundLinkCount(),                   // inbound links
                document.outboundLinkCount(),                  // outbound links
                document.getAudiolinks().size(),           // laudio
                document.getImages().size(),               // limage
                document.getVideolinks().size(),           // lvideo
                document.getApplinks().size()              // lapp
        );
        
        // STORE URL TO LOADED-URL-DB
        urlMetadata.store(newEntry); // TODO: should be serialized; integrated in IODispatcher
        
        final long storageEndTime = System.currentTimeMillis();
        
        // STORE PAGE INDEX INTO WORD INDEX DB
        final int words = addPageIndex(
                url,                                          // document url
                modDate,                                      // document mod date
                document,                                     // document content
                condenser,                                    // document condenser
                language,                                     // document language
                Response.docType(document.dc_format()),       // document type
                document.inboundLinkCount(),                      // inbound links
                document.outboundLinkCount(),                     // outbound links
                searchEvent,                                  // a search event that can have results directly
                sourceName                                    // the name of the source where the index was created
        );
        
        final long indexingEndTime = System.currentTimeMillis();
        
        if (log.isInfo()) {
            // TODO: UTF-8 docDescription seems not to be displayed correctly because
            // of string concatenation
            log.logInfo("*Indexed " + words + " words in URL " + url +
                    " [" + UTF8.String(url.hash()) + "]" +
                    "\n\tDescription:  " + dc_title +
                    "\n\tMimeType: "  + document.dc_format() + " | Charset: " + document.getCharset() + " | " +
                    "Size: " + document.getTextLength() + " bytes | " +
                    "Anchors: " + ((document.getAnchors() == null) ? 0 : document.getAnchors().size()) +
                    "\n\tLinkStorageTime: " + (storageEndTime - startTime) + " ms | " +
                    "indexStorageTime: " + (indexingEndTime - storageEndTime) + " ms");
        }
        
        // finished
        return newEntry;
    }
    

    // method for index deletion
    public int removeAllUrlReferences(final DigestURI url, LoaderDispatcher loader, final CrawlProfile.CacheStrategy cacheStrategy) {
        return removeAllUrlReferences(url.hash(), loader, cacheStrategy);
    }
    
    public void removeAllUrlReferences(final HandleSet urls, LoaderDispatcher loader, final CrawlProfile.CacheStrategy cacheStrategy) {
        for (byte[] urlhash: urls) removeAllUrlReferences(urlhash, loader, cacheStrategy);
    }
    
    /**
     * find all the words in a specific resource and remove the url reference from every word index
     * finally, delete the url entry
     * @param urlhash the hash of the url that shall be removed
     * @param loader
     * @param cacheStrategy
     * @return number of removed words
     */
    public int removeAllUrlReferences(final byte[] urlhash, LoaderDispatcher loader, final CrawlProfile.CacheStrategy cacheStrategy) {

        if (urlhash == null) return 0;
        // determine the url string
        final URIMetadataRow entry = urlMetadata().load(urlhash);
        if (entry == null) return 0;
        final URIMetadataRow.Components metadata = entry.metadata();
        if (metadata == null || metadata.url() == null) return 0;
        
        try {
            // parse the resource
            final Document document = Document.mergeDocuments(metadata.url(), null, loader.loadDocuments(loader.request(metadata.url(), true, false), cacheStrategy, 10000, Long.MAX_VALUE));
            if (document == null) {
                // delete just the url entry
                urlMetadata().remove(urlhash);
                return 0;
            }
            // get the word set
            Set<String> words = null;
            words = new Condenser(document, true, true, null).words().keySet();
            
            // delete all word references
            int count = 0;
            if (words != null) count = termIndex().remove(Word.words2hashesHandles(words), urlhash);
            
            // finally delete the url entry itself
            urlMetadata().remove(urlhash);
            return count;
        } catch (final Parser.Failure e) {
            return 0;
        } catch (IOException e) {
            Log.logException(e);
            return 0;
        }
    }

    
    //  The Cleaner class was provided as "UrldbCleaner" by Hydrox
    public synchronized ReferenceCleaner getReferenceCleaner(final byte[] startHash) {
        return new ReferenceCleaner(startHash);
    }
    
    public class ReferenceCleaner extends Thread {
        
        private final byte[] startHash;
        private boolean run = true;
        private boolean pause = false;
        public int rwiCountAtStart = 0;
        public byte[] wordHashNow = null;
        public byte[] lastWordHash = null;
        public int lastDeletionCounter = 0;
        
        public ReferenceCleaner(final byte[] startHash) {
            this.startHash = startHash;
            this.rwiCountAtStart = termIndex().sizesMax();
        }
        
        public void run() {
            Log.logInfo("INDEXCLEANER", "IndexCleaner-Thread started");
            ReferenceContainer<WordReference> container = null;
            WordReferenceVars entry = null;
            DigestURI url = null;
            final HandleSet urlHashs = new HandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
            try {
                Iterator<ReferenceContainer<WordReference>> indexContainerIterator = termIndex.references(startHash, false, 100, false).iterator();
                while (indexContainerIterator.hasNext() && run) {
                    waiter();
                    container = indexContainerIterator.next();
                    final Iterator<WordReference> containerIterator = container.entries();
                    wordHashNow = container.getTermHash();
                    while (containerIterator.hasNext() && run) {
                        waiter();
                        entry = new WordReferenceVars(containerIterator.next());
                        // System.out.println("Wordhash: "+wordHash+" UrlHash:
                        // "+entry.getUrlHash());
                        final URIMetadataRow ue = urlMetadata.load(entry.metadataHash());
                        if (ue == null) {
                            urlHashs.put(entry.metadataHash());
                        } else {
                            url = ue.metadata().url();
                            if (url == null || Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, url)) {
                                urlHashs.put(entry.metadataHash());
                            }
                        }
                    }
                    if (!urlHashs.isEmpty()) try {
                        final int removed = termIndex.remove(container.getTermHash(), urlHashs);
                        Log.logFine("INDEXCLEANER", UTF8.String(container.getTermHash()) + ": " + removed + " of " + container.size() + " URL-entries deleted");
                        lastWordHash = container.getTermHash();
                        lastDeletionCounter = urlHashs.size();
                        urlHashs.clear();
                    } catch (IOException e) {
                        Log.logException(e);
                    }
                    
                    if (!containerIterator.hasNext()) {
                        // We may not be finished yet, try to get the next chunk of wordHashes
                        final TreeSet<ReferenceContainer<WordReference>> containers = termIndex.references(container.getTermHash(), false, 100, false);
                        indexContainerIterator = containers.iterator();
                        // Make sure we don't get the same wordhash twice, but don't skip a word
                        if ((indexContainerIterator.hasNext()) && (!container.getTermHash().equals(indexContainerIterator.next().getTermHash()))) {
                            indexContainerIterator = containers.iterator();
                        }
                    }
                }
            } catch (final IOException e) {
                Log.logException(e);
            } catch (final Exception e) {
                Log.logException(e);
            }
            Log.logInfo("INDEXCLEANER", "IndexCleaner-Thread stopped");
        }
        
        public void abort() {
            synchronized(this) {
                run = false;
                this.notifyAll();
            }
        }

        public void pause() {
            synchronized (this) {
                if (!pause) {
                    pause = true;
                    Log.logInfo("INDEXCLEANER", "IndexCleaner-Thread paused");
                }
            }
        }

        public void endPause() {
            synchronized (this) {
                if (pause) {
                    pause = false;
                    this.notifyAll();
                    Log.logInfo("INDEXCLEANER", "IndexCleaner-Thread resumed");
                }
            }
        }

        public void waiter() {
            synchronized (this) {
                if (this.pause) {
                    try {
                        this.wait();
                    } catch (final InterruptedException e) {
                        this.run = false;
                        return;
                    }
                }
            }
        }
        
        public int rwisize() {
            return termIndex().sizesMax();
        }
        
        public int urlsize() {
            return urlMetadata().size();
        }
    }
}
