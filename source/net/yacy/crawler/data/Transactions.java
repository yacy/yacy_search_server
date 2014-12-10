/**
 *  Transactions
 *  Copyright 2014 by Michael Peter Christen
 *  First released 08.12.2014 at http://yacy.net
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.responsewriter.EnhancedXMLResponseWriter;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Html2Image;
import net.yacy.crawler.data.Snapshots.Order;
import net.yacy.search.schema.CollectionSchema;

/**
 * This is a static class holding one or several Snapshot directories
 * Transacted snapshots are moved from the inventory snapshot directory to the archive snapshot directory.
 *
 */
public class Transactions {
    
    private final static String XML_PREFIX = "<response>\n<!--\n";
    private final static char[] WHITESPACE = new char[132];
    private final static int WHITESPACE_START = XML_PREFIX.length();
    private final static int WHITESPACE_LENGTH = WHITESPACE.length;
    private final static String SNAPSHOT_INVENTORY_DIR = "inventory";
    private final static String SNAPSHOT_ARCHIVE_DIR = "archive";
    private static File transactionDir = null, inventoryDir = null, archiveDir = null;
    private static Snapshots inventory = null, archive = null;
    private static ExecutorService executor = Executors.newCachedThreadPool();
    private static AtomicInteger executorRunning = new AtomicInteger(0);
    
    static {
        for (int i = 0; i < WHITESPACE.length; i++) WHITESPACE[i] = 32;
    }
    
    public static enum State {
        INVENTORY, ARCHIVE, ANY;
    }
    
    public static void init(File dir) {
        transactionDir = dir;
        transactionDir.mkdirs();
        inventoryDir = new File(transactionDir, SNAPSHOT_INVENTORY_DIR);
        inventory = new Snapshots(inventoryDir);
        archiveDir = new File(transactionDir, SNAPSHOT_ARCHIVE_DIR);
        archive = new Snapshots(archiveDir);
    }

    public static boolean store(final SolrInputDocument doc, final boolean loadImage, final boolean replaceOld, final String proxy, final ClientIdentification.Agent agent) {

        // GET METADATA FROM DOC
        final String urls = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
        final Date date = (Date) doc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
        final int depth = (Integer) doc.getFieldValue(CollectionSchema.crawldepth_i.getSolrFieldName());
        DigestURL url;
        try {
            url = new DigestURL(urls);
        } catch (MalformedURLException e) {
            ConcurrentLog.logException(e);
            return false;
        }
        
        // CLEAN UP OLD DATA (if wanted)
        Collection<File> oldPaths = Transactions.findPaths(url, depth, null, Transactions.State.INVENTORY);
        if (replaceOld) {
            for (File oldPath: oldPaths) oldPath.delete();
        }
        
        
        // STORE METADATA FOR THE IMAGE
        File metadataPath = Transactions.definePath(url, depth, date, "xml", Transactions.State.INVENTORY);
        metadataPath.getParentFile().mkdirs();
        boolean success = true;
        try {
            if (doc != null) {
                FileOutputStream fos = new FileOutputStream(metadataPath);
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(XML_PREFIX);
                osw.write(WHITESPACE); osw.write("\n-->\n"); // placeholder for transaction information properties (a hack to attach metadata to metadata)
                osw.write("<result name=\"response\" numFound=\"1\" start=\"0\">\n");
                EnhancedXMLResponseWriter.writeDoc(osw, doc);
                osw.write("</result>\n");
                osw.write("</response>\n");
                osw.close();
                fos.close();
                Transactions.announceStorage(url, depth, date);
            }
        } catch (IOException e) {
            ConcurrentLog.logException(e);
            success = false;
        }
        
        // STORE AN IMAGE
        if (success && loadImage) {
            final File pdfPath = Transactions.definePath(url, depth, date, "pdf", Transactions.State.INVENTORY);
            if (executorRunning.intValue() < Runtime.getRuntime().availableProcessors()) {
                Thread t = new Thread(){
                    @Override
                    public void run() {
                        executorRunning.incrementAndGet();
                        try {
                            Html2Image.writeWkhtmltopdf(urls, proxy, agent.userAgent, pdfPath);
                        } catch (Throwable e) {} finally {
                        executorRunning.decrementAndGet();
                        }
                    }
                };
                executor.execute(t);
            } else {
                success = Html2Image.writeWkhtmltopdf(urls, proxy, agent.userAgent, pdfPath);
            }
        }
        
        return success;
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
    public static Map<String, Date> select(String host, Integer depth, final Order order, int maxcount, State state) {
        Map<String, Date> result = new HashMap<>();
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
    
    public static void announceStorage(final DigestURL url, final int depth, final Date date) {
        inventory.announceStorage(url, depth, date); 
    }

    /**
     * for a given url, get all paths for storage locations.
     * The locations are all for the single url but may represent different storage times.
     * This method is inefficient because it tests all different depths, it would be better to use
     * findPaths/3 with a given depth.
     * @param url
     * @param ext
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
