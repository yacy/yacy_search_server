/**
 *  DocumentImage
 *  Copyright 2014 by Michael Peter Christen
 *  First released 29.11.2014 at http://yacy.net
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.Html2Image;
import net.yacy.search.index.Fulltext;
import net.yacy.search.schema.CollectionSchema;

/**
 * This class hosts document snapshots.
 * 
 * The storage is organized in the following hierarchy:
 * - in the root path are subpaths for each host:port
 * - in the host:port path are subpaths for the crawl depth, two digits length
 * - in the crawl depth path are subpaths for the first two charaters of the url-hash, called shard
 * - in the shard path are files, named with <urlhash>'.'<date>.<ext>
 * .. where the <date> has the form "yyyyMMdd" and ext may be one of {pdf,jpg,png,xml,json}.
 * The pdf is created with wxhtmltopdf, jpg/png is created with convert
 * and the xml/json is an extract from solr.
 * 
 * The construction of the file name with the date allows to make several copies of the same document
 * for different snapshot-times. The usage of the crawl depth makes it easier to extract a specific part
 * of the domain.
 */
public class Snapshots {

    private File storageLocation;
    
    public Snapshots(File location) {
        this.storageLocation = location;
    }

    /**
     * Load a pdf snapshot of a document.
     * A proxy must be given to ensure that multiple loads containing i.e. image are cached
     * Use http://localhost:<thisport> as proxy.
     * @param url
     * @param depth
     * @param date
     * @param proxy - a string of the form 'http://<host>:<port>
     * @return
     */
    public File downloadPDFSnapshot(final DigestURL url, final int depth, final Date date, boolean replaceOld, String proxy, String userAgent) {
        Collection<File> oldPaths = findPaths(url, depth, "pdf");
        if (replaceOld) {
            for (File oldPath: oldPaths) oldPath.delete();
        }
        File path = definePath(url, "pdf", depth, date);
        path.getParentFile().mkdirs();
        boolean success = Html2Image.writeWkhtmltopdf(url.toNormalform(true), proxy, userAgent, path);
        return success ? path : null;
    }
    
    /**
     * Compute the path of a snapshot. This does not create the snapshot, only gives a path.
     * Also, the path to the storage location is not created.
     * @param url
     * @param ext
     * @param depth
     * @param date
     * @return a file to the snapshot
     */
    public File definePath(final DigestURL url, final String ext, final int depth, final Date date) {
        String id = ASCII.String(url.hash());
        String ds = GenericFormatter.SHORT_DAY_FORMATTER.format(date);
        File path = new File(pathToShard(url, depth), id + "." + ds + "." + ext);
        return path;
    }

    /**
     * get the depth to a document, helper method for definePath to determine the depth value
     * @param url
     * @param fulltext
     * @return the crawldepth of the document
     */
    public int getDepth(final DigestURL url, final Fulltext fulltext) {
        Integer depth = null;
        if (fulltext.getDefaultConfiguration().contains(CollectionSchema.crawldepth_i)) {
            try {
                SolrDocument doc = fulltext.getDefaultConnector().getDocumentById(ASCII.String(url.hash()), CollectionSchema.crawldepth_i.getSolrFieldName());
                if (doc != null) {
                    depth = (Integer) doc.getFieldValue(CollectionSchema.crawldepth_i.getSolrFieldName());
                }
            } catch (IOException e) {
            }
        }
        return depth == null ? 0 : depth;
    }

    /**
     * for a given url, get all paths for storage locations.
     * The locations are all for the single url but may represent different storage times.
     * This method is inefficient because it tests all different depths, it would be better to use
     * findPaths/3 with a given depth.
     * @param url
     * @param ext
     * @return a set of files for snapshots of the url
     */
    public Collection<File> findPaths(final DigestURL url, final String ext) {
        for (int i = 0; i < 100; i++) {
            Collection<File> paths = findPaths(url, i, ext);
            if (paths.size() > 0) return paths;
        }
        return new ArrayList<>(0);
    }
    
    /**
     * for a given url, get all paths for storage locations.
     * The locations are all for the single url but may represent different storage times.
     * @param url
     * @param ext
     * @param depth
     * @return a set of files for snapshots of the url
     */
    public Collection<File> findPaths(final DigestURL url, final int depth, final String ext) {
        String id = ASCII.String(url.hash());
        File pathToShard = pathToShard(url, depth);
        String[] list = pathToShard.exists() && pathToShard.isDirectory() ? pathToShard.list() : null; // may be null if path does not exist
        ArrayList<File> paths = new ArrayList<>();
        if (list != null) {
            for (String f: list) {
                if (f.startsWith(id) && f.endsWith(ext)) paths.add(new File(pathToShard, f));
            }
        }
        return paths;
    }
    
    private File pathToShard(final DigestURL url, final int depth) {
        String id = ASCII.String(url.hash());
        File pathToHostDir = new File(storageLocation, url.getHost() + "." + url.getPort());
        File pathToDepthDir = new File(pathToHostDir, depth < 10 ? "0" + depth : Integer.toString(depth));
        File pathToShard = new File(pathToDepthDir, id.substring(0, 2));
        return pathToShard;
    }

}
