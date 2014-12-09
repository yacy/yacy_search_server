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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
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
    
    private Map<String, TreeMap<Integer, TreeSet<String>>> directory; // a TreeMap for each domain where the key is the depth and the value is a Set containing a key/urlhash id to get all files into a specific order to provide a recent view on the documents
    
    public Snapshots(File location) {
        this.storageLocation = location;
        this.storageLocation.mkdirs();
        // scan the location to fill the directory
        this.directory = new HashMap<>();
        for (String domain: location.list()) {
            TreeMap<Integer, TreeSet<String>> domaindepth = new TreeMap<>();
            this.directory.put(domain, domaindepth);
            File domaindir = new File(location, domain);
            if (domaindir.isDirectory()) domainscan: for (String depth: domaindir.list()) {
                TreeSet<String> dateid = new TreeSet<>();
                Integer depthi = -1;
                try {
                    depthi = Integer.parseInt(depth);
                } catch (NumberFormatException e) {
                    continue domainscan;
                }
                domaindepth.put(depthi, dateid);
                File sharddir = new File(domaindir, depth);
                if (sharddir.isDirectory()) for (String shard: sharddir.list()) {
                    File snapshotdir = new File(sharddir, shard);
                    if (snapshotdir.isDirectory()) for (String snapshotfile: snapshotdir.list()) {
                        if (snapshotfile.endsWith(".pdf")) {
                            String s = snapshotfile.substring(0, snapshotfile.length() - 4);
                            int p = s.indexOf('.');
                            assert p == 12;
                            if (p > 0) {
                                String key = s.substring(p + 1) + '.' + s.substring(0, p);
                                dateid.add(key);
                            }
                        }
                    }
                }
            }
        }
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
    public File definePath(final DigestURL url, final int depth, final Date date, final String ext) {
        String id = ASCII.String(url.hash());
        String ds = GenericFormatter.SHORT_MINUTE_FORMATTER.format(date);
        File path = new File(pathToShard(url, depth), id + "." + ds + "." + ext);
        return path;
    }
    
    public void announceStorage(final DigestURL url, final int depth, final Date date) {
        String id = ASCII.String(url.hash());
        String ds = GenericFormatter.SHORT_MINUTE_FORMATTER.format(date);
        TreeMap<Integer, TreeSet<String>> domaindepth = this.directory.get(pathToHostDir(url));
        if (domaindepth == null) {domaindepth = new TreeMap<Integer, TreeSet<String>>(); this.directory.put(pathToHostDir(url), domaindepth);}
        TreeSet<String> dateid = domaindepth.get(depth);
        if (dateid == null) {dateid = new TreeSet<String>(); domaindepth.put(depth, dateid);}
        dateid.add(ds + '.' + id);        
    }
    
    public static enum Order {
        ANY, OLDESTFIRST, LATESTFIRST;
    }

    /**
     * select a set of urlhashes from the snapshot directory. The selection either ordered
     * by generation date (upwards == OLDESTFIRST or downwards == LATESTFIRST) or with any
     * order. The result set can be selected either with a given host or a depth
     * @param host selected host or null for all hosts
     * @param depth selected depth or null for all depths
     * @param order Order.ANY, Order.OLDESTFIRST or Order.LATESTFIRST
     * @param maxcount the maximum number of hosthashes. If unlimited, submit Integer.MAX_VALUE
     * @return a map of hosthashes with the associated creation date
     */
    public Map<String, Date> select(String host, Integer depth, final Order order, int maxcount) {
        TreeSet<String> dateIdResult = new TreeSet<>();
        if (host == null && depth == null) {
            loop: for (TreeMap<Integer, TreeSet<String>> domaindepth: this.directory.values()) {
                for (TreeSet<String> keys: domaindepth.values()) {
                    dateIdResult.addAll(keys);
                    if (order == Order.ANY && dateIdResult.size() >= maxcount) break loop; 
                }
            }
        }
        if (host == null && depth != null) {
            loop: for (TreeMap<Integer, TreeSet<String>> domaindepth: this.directory.values()) {
                TreeSet<String> keys = domaindepth.get(depth);
                if (keys != null) dateIdResult.addAll(keys);
                if (order == Order.ANY && dateIdResult.size() >= maxcount) break loop;
            }
        }
        if (host != null && depth == null) {
            TreeMap<Integer, TreeSet<String>> domaindepth = this.directory.get(pathToHostDir(host,80));
            if (domaindepth != null) loop: for (TreeSet<String> keys: domaindepth.values()) {
                dateIdResult.addAll(keys);
                if (order == Order.ANY && dateIdResult.size() >= maxcount) break loop;
            }
        }
        if (host != null && depth != null) {
            TreeMap<Integer, TreeSet<String>> domaindepth = this.directory.get(pathToHostDir(host,80));
            if (domaindepth != null) {
                TreeSet<String> keys = domaindepth.get(depth);
                if (keys != null) dateIdResult.addAll(keys);
            }
        }
        Map<String, Date> result = new HashMap<>();
        Iterator<String> i = order == Order.LATESTFIRST ? dateIdResult.descendingIterator() : dateIdResult.iterator();
        while (i.hasNext() && result.size() < maxcount) {
            String di = i.next();
            int p = di.indexOf('.');
            assert p >= 0;
            String d = di.substring(0, p);
            Date date;
            try {
                date = GenericFormatter.SHORT_MINUTE_FORMATTER.parse(d);
            } catch (ParseException e) {
                try {
                    date = GenericFormatter.SHORT_DAY_FORMATTER.parse(d);
                } catch (ParseException ee) {
                    date = new Date();
                }
            }
            result.put(di.substring(p + 1), date);
        }
        return result;
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
        File pathToHostDir = new File(storageLocation, pathToHostDir(url));
        File pathToDepthDir = new File(pathToHostDir, pathToDepthDir(depth));
        File pathToShard = new File(pathToDepthDir, pathToShard(id));
        return pathToShard;
    }

    private String pathToHostDir(final DigestURL url) {
        return pathToHostDir(url.getHost(), url.getPort());
    }
    
    private String pathToHostDir(final String host, final int port) {
        return host + "." + port;
    }
    
    private String pathToDepthDir(final int depth) {
        return depth < 10 ? "0" + depth : Integer.toString(depth);
    }
    
    private String pathToShard(final String urlhash) {
        return urlhash.substring(0, 2);
    }

}
