/**
 *  Transactions
 *  SPDX-FileCopyrightText: 2014 Michael Peter Christen <mc@yacy.net)> 
 *  SPDX-License-Identifier: GPL-2.0-or-later
 *  First released 08.12.2014 at https://yacy.net
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

package net.yacy.crawler.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.responsewriter.EnhancedXMLResponseWriter;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Html2Image;
import net.yacy.crawler.data.Snapshots.Order;
import net.yacy.crawler.data.Snapshots.Revisions;
import net.yacy.search.schema.CollectionSchema;

/**
 * This is a static class holding one or several Snapshot directories
 * Transacted snapshots are moved from the inventory snapshot directory to the archive snapshot directory.
 *
 */
public class Transactions {
    
    private final static String XML_PREFIX = "<response>\n<!--\n";
    private final static char[] WHITESPACE = new char[132];
    //private final static int WHITESPACE_START = XML_PREFIX.length();
    //private final static int WHITESPACE_LENGTH = WHITESPACE.length;
    private static File transactionDir = null, inventoryDir = null, archiveDir = null;
    private static Snapshots inventory = null, archive = null;
    private static ExecutorService executor = Executors.newCachedThreadPool();
    private static AtomicInteger executorRunning = new AtomicInteger(0);
    
    /** the maximum to wait for each wkhtmltopdf call when rendering PDF snapshots */
    private static long wkhtmltopdfTimeout = 30;
    
    static {
        for (int i = 0; i < WHITESPACE.length; i++) WHITESPACE[i] = 32;
    }
    
    public static enum State {
        INVENTORY("inventory"), ARCHIVE("archive"), ANY(null);
        public String dirname;
        State(String dirname) {
            this.dirname = dirname;
        }
    }
    
    /**
     * @param dir the parent directory of inventory and archive snapshots.
     * @param wkhtmltopdfSecondsTimeout the maximum to wait for each wkhtmltopdf call when rendering PDF snapshots
     */
    public static void init(final File dir, final long wkhtmltopdfSecondsTimeout) {
        transactionDir = dir;
        transactionDir.mkdirs();
        inventoryDir = new File(transactionDir, State.INVENTORY.dirname);
        inventory = new Snapshots(inventoryDir);
        archiveDir = new File(transactionDir, State.ARCHIVE.dirname);
        archive = new Snapshots(archiveDir);
        wkhtmltopdfTimeout = wkhtmltopdfSecondsTimeout;
    }
    
    public static synchronized void migrateIPV6Snapshots() {
    	executor.shutdown();
    	try {
			executor.awaitTermination(10, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {
			return;
		}
    }

    /**
     * get the number of entries for each of the transaction states
     * @return the total number of different documents for each transaction state
     */
    public static Map<String, Integer> sizes() {
        HashMap<String, Integer> m = new HashMap<>();
        m.put(State.INVENTORY.name(), inventory.size());
        m.put(State.ARCHIVE.name(), archive.size());
        return m;
    }

    public static Revisions getRevisions(final State state, final String urlhash) {
        switch (state) {
            case INVENTORY : return inventory.getRevisions(urlhash);
            case ARCHIVE : return archive.getRevisions(urlhash);
            default : Revisions a = inventory.getRevisions(urlhash); return a == null ? archive.getRevisions(urlhash) : a;
        }
    }
    
    /**
     * get a list of <host>.<port> names in the snapshot directory
     * @return the list of the given state. if the state is ALL or unknown, all lists are combined
     */
    public static Set<String> listHosts(final State state) {
        switch (state) {
            case INVENTORY : return inventory.listHosts();
            case ARCHIVE : return archive.listHosts();
            default : Set<String> a = inventory.listHosts(); a.addAll(archive.listHosts()); return a;
        }
    }
    
    /**
     * list the snapshots for a given host name
     * @param hostport the <host>.<port> identifier for the domain (with the same format as applied by the Snapshots.pathToHostPortDir() function).
     * @param depth restrict the result to the given depth or if depth == -1 do not restrict to a depth
     * @param state the wanted transaction state, State.INVENTORY, State.ARCHIVE or State.ANY 
     * @return a map with a set for each depth in the domain of the host name
     */
    public static TreeMap<Integer, Collection<Revisions>> listIDs(final String hostport, final int depth, final State state) {
        switch (state) {
            case INVENTORY : return inventory.listIDs(hostport, depth);
            case ARCHIVE : return archive.listIDs(hostport, depth);
            default : TreeMap<Integer, Collection<Revisions>> a = inventory.listIDs(hostport, depth); a.putAll(archive.listIDs(hostport, depth)); return a;
        }
    }
    
    /**
     * get the number of snapshots for the given host name
     * @param hostport the <host>.<port> identifier for the domain
     * @param depth restrict the result to the given depth or if depth == -1 do not restrict to a depth
     * @param state the wanted transaction state, State.INVENTORY, State.ARCHIVE or State.ANY 
     * @return a count, the total number of documents for the domain and depth
     */
    public static int listIDsSize(final String hostport, final int depth, final State state) {
        switch (state) {
            case INVENTORY : return inventory.listIDsSize(hostport, depth);
            case ARCHIVE : return archive.listIDsSize(hostport, depth);
            default : return inventory.listIDsSize(hostport, depth) + archive.listIDsSize(hostport, depth);
        }
    }
    
    public static boolean store(final SolrInputDocument doc, final boolean concurrency, final boolean loadImage, final boolean replaceOld, final String proxy, final String acceptLanguage) {

        // GET METADATA FROM DOC
        final String urls = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
        final Date date = (Date) doc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
        final Integer o_depth = (Integer) doc.getFieldValue(CollectionSchema.crawldepth_i.getSolrFieldName()); // may return null
        final int depth = o_depth == null ? 0 : o_depth.intValue();

        DigestURL url;
        try {
            url = new DigestURL(urls);
        } catch (MalformedURLException e) {
            ConcurrentLog.logException(e);
            return false;
        }
        
        boolean success = loadImage ? store(url, date, depth, concurrency, replaceOld, proxy, acceptLanguage) : true;
        if (success) {
            // STORE METADATA FOR THE IMAGE
            File metadataPath = Transactions.definePath(url, depth, date, "xml", Transactions.State.INVENTORY);
            metadataPath.getParentFile().mkdirs();
            if (doc != null) {
            	try (
            		/* Resources automatically closed by this try-with-resources statement */
                    final FileOutputStream fos = new FileOutputStream(metadataPath);
                    final OutputStreamWriter osw = new OutputStreamWriter(fos);
            	) {
                    osw.write(XML_PREFIX);
                    osw.write(WHITESPACE); osw.write("\n-->\n"); // placeholder for transaction information properties (a hack to attach metadata to metadata)
                    osw.write("<result name=\"response\" numFound=\"1\" start=\"0\">\n");
                    EnhancedXMLResponseWriter.writeDoc(osw, doc);
                    osw.write("</result>\n");
                    osw.write("</response>\n");
            	} catch (IOException e) {
            		ConcurrentLog.logException(e);
            		success = false;
            	}
            	if(success) {
            		Transactions.announceStorage(url, depth, date, State.INVENTORY);            		
            	}
            }
            
        }
        
        return success;
    }
    

    public static boolean store(final DigestURL url, final Date date, final int depth, final boolean concurrency, final boolean replaceOld, final String proxy, final String acceptLanguage) {

        // CLEAN UP OLD DATA (if wanted)
        Collection<File> oldPaths = Transactions.findPaths(url, depth, null, Transactions.State.INVENTORY);
        if (replaceOld && oldPaths != null) {
            for (File oldPath: oldPaths) {
            	oldPath.delete();
            }
        }
        
        // STORE METADATA FOR THE IMAGE
        File metadataPath = Transactions.definePath(url, depth, date, "xml", Transactions.State.INVENTORY);
        metadataPath.getParentFile().mkdirs();
        boolean success = true;
        
        // STORE AN IMAGE
        final String urls = url.toNormalform(true);
        final File pdfPath = Transactions.definePath(url, depth, date, "pdf", Transactions.State.INVENTORY);
        if (concurrency && executorRunning.intValue() < Runtime.getRuntime().availableProcessors()) {
            Thread t = new Thread("Transactions.store"){
                @Override
                public void run() {
                    executorRunning.incrementAndGet();
                    try {
                        Html2Image.writeWkhtmltopdf(urls, proxy, ClientIdentification.browserAgent.userAgent, acceptLanguage, pdfPath, wkhtmltopdfTimeout);
                    } catch (Throwable e) {} finally {
                    executorRunning.decrementAndGet();
                    }
                }
            };
            executor.execute(t);
        } else {
            success = Html2Image.writeWkhtmltopdf(urls, proxy, ClientIdentification.browserAgent.userAgent, acceptLanguage, pdfPath, wkhtmltopdfTimeout);
        }
        
        return success;
    }

    /**
     * Announce the commit of a snapshot: this will move all data for the given urlhash from the inventory to the archive
     * The index within the snapshot management will update also.
     * @param urlhash
     * @return a revision object from the moved document if the commit has succeeded, null if something went wrong
     */
    public static Revisions commit(String urlhash) {
        return transact(urlhash, State.INVENTORY, State.ARCHIVE);
    }

    /**
     * Announce the rollback of a snapshot: this will move all data for the given urlhash from the archive to the inventory
     * The index within the snapshot management will update also.
     * @param urlhash
     * @return a revision object from the moved document if the commit has succeeded, null if something went wrong
     */
    public static Revisions rollback(String urlhash) {
        return transact(urlhash, State.ARCHIVE, State.INVENTORY);
    }
    
    private static Revisions transact(final String urlhash, final State from, final State to) {
        Revisions r = Transactions.getRevisions(from, urlhash);
        if (r == null) return null;
        // we take all pathtoxml and move that to archive
        for (File f: r.pathtoxml) {
            String name = f.getName();
            String nameStub = name.substring(0, name.length() - 4);
            File sourceParent = f.getParentFile();
            File targetParent = new File(sourceParent.getAbsolutePath().replace("/" + from.dirname + "/", "/" + to.dirname + "/"));
            targetParent.mkdirs();
            // list all files in the parent directory
            for (String a: sourceParent.list()) {
                if (a.startsWith(nameStub)) {
                    new File(sourceParent, a).renameTo(new File(targetParent, a));
                }
            }
            // delete empty directories
            while (sourceParent.list().length == 0) {
                sourceParent.delete();
                sourceParent = sourceParent.getParentFile();
            }
        }
        // announce the movement
        DigestURL durl;
        try {
            durl = new DigestURL(r.url);
            Transactions.announceDeletion(durl, r.depth, from);
            Transactions.announceStorage(durl, r.depth, r.dates[0], to);
            return r;
        } catch (MalformedURLException e) {
            ConcurrentLog.logException(e);
        }
    
        return null;
    }
    
    /**
     * select a set of urlhashes from the snapshot directory. The selection either ordered
     * by generation date (upwards == OLDESTFIRST or downwards == LATESTFIRST) or with any
     * order. The result set can be selected either with a given host or a depth
     * @param host selected host or null for all hosts
     * @param depth selected depth or null for all depths
     * @param order Order.ANY, Order.OLDESTFIRST or Order.LATESTFIRST
     * @param maxcount the maximum number of hosthashes. If unlimited, submit Integer.MAX_VALUE
     * @param state the wanted transaction state, State.INVENTORY, State.ARCHIVE or State.ANY 
     * @return a map of hosthashes with the associated creation date
     */
    public static LinkedHashMap<String, Revisions> select(String host, Integer depth, final Order order, int maxcount, State state) {
        LinkedHashMap<String, Revisions> result = new LinkedHashMap<>();
        if (state == State.INVENTORY || state == State.ANY) result.putAll(inventory.select(host, depth, order, maxcount));
        if (state == State.ARCHIVE || state == State.ANY) result.putAll(archive.select(host, depth, order, maxcount));
        return result;
    }

    /**
     * Compute the path of a snapshot. This does not create the snapshot, only gives a path.
     * Also, the path to the storage location is not created.
     * @param url
     * @param depth
     * @param date
     * @param ext
     * @param state the wanted transaction state, State.INVENTORY, State.ARCHIVE or State.ANY 
     * @return a file to the snapshot
     */
    public static File definePath(final DigestURL url, final int depth, final Date date, final String ext, State state) {
        if (state == State.ANY) throw new RuntimeException("definePath must be selected with INVENTORY or ARCHIVE state");
        if (state == State.INVENTORY) return inventory.definePath(url, depth, date, ext);
        if (state == State.ARCHIVE) return archive.definePath(url, depth, date, ext);
        return null;
    }

    /**
     * Write information about the storage of a snapshot to the Snapshot-internal index.
     * The actual writing of files to the target directory must be done elsewehre, this method does not store the snapshot files.
     * @param state the wanted transaction state, State.INVENTORY, State.ARCHIVE or State.ANY 
     * @param url
     * @param depth
     * @param date
     */
    public static void announceStorage(final DigestURL url, final int depth, final Date date, State state) {
        if (state == State.INVENTORY || state == State.ANY) inventory.announceStorage(url, depth, date); 
        if (state == State.ARCHIVE || state == State.ANY) archive.announceStorage(url, depth, date);
    }

    /**
     * Delete information about the storage of a snapshot to the Snapshot-internal index.
     * The actual deletion of files in the target directory must be done elsewehre, this method does not store the snapshot files.
     * @param state the wanted transaction state, State.INVENTORY, State.ARCHIVE or State.ANY 
     * @param url
     * @param depth
     */
    public static void announceDeletion(final DigestURL url, final int depth, final State state) {
        if (state == State.INVENTORY || state == State.ANY) inventory.announceDeletion(url, depth); 
        if (state == State.ARCHIVE || state == State.ANY) archive.announceDeletion(url, depth);
    }

    /**
     * for a given url, get all paths for storage locations.
     * The locations are all for the single url but may represent different storage times.
     * This method is inefficient because it tests all different depths, it would be better to use
     * findPaths/3 with a given depth.
     * @param url
     * @param ext required extension or null if the extension must not be checked
     * @param state the wanted transaction state, State.INVENTORY, State.ARCHIVE or State.ANY 
     * @return a set of files for snapshots of the url
     */
    public static Collection<File> findPaths(final DigestURL url, final String ext, State state) {
        Collection<File> result = new ArrayList<>();
        if (state == State.INVENTORY || state == State.ANY) result.addAll(inventory.findPaths(url, ext));
        if (state == State.ARCHIVE || state == State.ANY) result.addAll(archive.findPaths(url, ext));
        return result;
    }
    
    /**
     * for a given url, get all paths for storage locations.
     * The locations are all for the single url but may represent different storage times.
     * @param url
     * @param ext required extension or null if the extension must not be checked
     * @param depth
     * @param state the wanted transaction state, State.INVENTORY, State.ARCHIVE or State.ANY 
     * @return a set of files for snapshots of the url
     */
    public static Collection<File> findPaths(final DigestURL url, final int depth, final String ext, State state) {
        Collection<File> result = new ArrayList<>();
        if (state == State.INVENTORY || state == State.ANY) result.addAll(inventory.findPaths(url, depth, ext));
        if (state == State.ARCHIVE || state == State.ANY) result.addAll(archive.findPaths(url, depth, ext));
        return result;
    }
    
}
