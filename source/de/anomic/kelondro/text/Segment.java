// Segment.java
// (C) 2005-2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2005 on http://yacy.net; full redesign for segments 28.5.2009
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-05-28 01:51:34 +0200 (Do, 28 Mai 2009) $
// $LastChangedRevision: 5988 $
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

package de.anomic.kelondro.text;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import net.yacy.kelondro.logging.Log;

import de.anomic.crawler.retrieval.Response;
import de.anomic.data.Blacklist;
import de.anomic.document.Condenser;
import de.anomic.document.Word;
import de.anomic.document.Document;
import de.anomic.document.parser.html.ContentScraper;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.text.navigationPrototype.NavigationReference;
import de.anomic.kelondro.text.navigationPrototype.NavigationReferenceFactory;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.kelondro.text.referencePrototype.WordReferenceFactory;
import de.anomic.kelondro.text.referencePrototype.WordReferenceRow;
import de.anomic.search.Switchboard;
import de.anomic.tools.iso639;
import de.anomic.yacy.yacyURL;

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
        
        log.logInfo("Initializing Segment '" + segmentPath + "', word hash cache size is " + Word.hashCacheSize + ".");

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
            e.printStackTrace();
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
    private int addPageIndex(final yacyURL url, final Date urlModified, final Document document, final Condenser condenser, final String language, final char doctype, final int outlinksSame, final int outlinksOther) {
        int wordCount = 0;
        final int urlLength = url.toNormalform(true, true).length();
        final int urlComps = ContentScraper.urlComps(url.toString()).length;
        
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
                                language,
                                doctype,
                                outlinksSame, outlinksOther);
        Word wprop;
        while (i.hasNext()) {
            wentry = i.next();
            word = wentry.getKey();
            wprop = wentry.getValue();
            assert (wprop.flags != null);
            ientry.setWord(wprop);
            try {
                this.termIndex.add(Word.word2hash(word), ientry);
            } catch (IOException e) {
                e.printStackTrace();
            }
            wordCount++;
        }
        
        return wordCount;
    }

    public void close() {
        if (this.merger != null) this.merger.terminate();
        termIndex.close();
        urlMetadata.close();
    }

    public URLMetadataRow storeDocument(
            final yacyURL url,
            final yacyURL referrerURL,
            final Date docDate,
            final long sourcesize,
            final Document document,
            final Condenser condenser
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
                    if (!u.contains("/" + language + "/") && !u.contains("/" + iso639.country(language).toLowerCase() + "/")) {
                        // no confirmation using the url, use the TLD
                        language = url.language();
                        System.out.println(error + ", corrected using the TLD");
                    } else {
                        // this is a strong hint that the statistics was in fact correct
                        System.out.println(error + ", but the url proves that the statistic is correct");
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
        final long ldate = System.currentTimeMillis();
        final URLMetadataRow newEntry = new URLMetadataRow(
                url,                                       // URL
                dc_title,                                  // document description
                document.dc_creator(),                     // author
                document.dc_subject(' '),                  // tags
                "",                                        // ETag
                docDate,                                   // modification date
                new Date(),                                // loaded date
                new Date(ldate + Math.max(0, ldate - docDate.getTime()) / 2), // freshdate, computed with Proxy-TTL formula 
                (referrerURL == null) ? null : referrerURL.hash(),            // referer hash
                new byte[0],                               // md5
                (int) sourcesize,                          // size
                condenser.RESULT_NUMB_WORDS,               // word count
                Response.docType(document.dc_format()), // doctype
                condenser.RESULT_FLAGS,                    // flags
                language,                                  // language
                document.inboundLinks(),                   // inbound links
                document.outboundLinks(),                  // outbound links
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
                docDate,                                      // document mod date
                document,                                     // document content
                condenser,                                    // document condenser
                language,                                     // document language
                Response.docType(document.dc_format()),  // document type
                document.inboundLinks(),                      // inbound links
                document.outboundLinks()                      // outbound links
        );
            
        final long indexingEndTime = System.currentTimeMillis();
        
        if (log.isInfo()) {
            // TODO: UTF-8 docDescription seems not to be displayed correctly because
            // of string concatenation
            log.logInfo("*Indexed " + words + " words in URL " + url +
                    " [" + url.hash() + "]" +
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
            WordReference entry = null;
            yacyURL url = null;
            final HashSet<String> urlHashs = new HashSet<String>();
            try {
                Iterator<ReferenceContainer<WordReference>> indexContainerIterator = termIndex.references(startHash, false, 100, false).iterator();
                while (indexContainerIterator.hasNext() && run) {
                    waiter();
                    container = indexContainerIterator.next();
                    final Iterator<WordReference> containerIterator = container.entries();
                    wordHashNow = container.getTermHash();
                    while (containerIterator.hasNext() && run) {
                        waiter();
                        entry = containerIterator.next();
                        // System.out.println("Wordhash: "+wordHash+" UrlHash:
                        // "+entry.getUrlHash());
                        final URLMetadataRow ue = urlMetadata.load(entry.metadataHash(), entry, 0);
                        if (ue == null) {
                            urlHashs.add(entry.metadataHash());
                        } else {
                            url = ue.metadata().url();
                            if ((url == null) || (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, url) == true)) {
                                urlHashs.add(entry.metadataHash());
                            }
                        }
                    }
                    if (urlHashs.size() > 0) try {
                        final int removed = termIndex.remove(container.getTermHash(), urlHashs);
                        Log.logFine("INDEXCLEANER", container.getTermHashAsString() + ": " + removed + " of " + container.size() + " URL-entries deleted");
                        lastWordHash = container.getTermHash();
                        lastDeletionCounter = urlHashs.size();
                        urlHashs.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
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
                e.printStackTrace();
            } catch (final Exception e) {
                e.printStackTrace();
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
