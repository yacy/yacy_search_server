/**
 *  DocumentImage
 *  SPDX-FileCopyrightText: 2014 Michael Peter Christen <mc@yacy.net)> 
 *  SPDX-License-Identifier: GPL-2.0-or-later
 *  First released 29.11.2014 at https://yacy.net
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
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
    
    private Map<String, TreeMap<Integer, TreeSet<String>>> directory; // a TreeMap for each domain (host.port) where the key is the depth and the value is a Set containing a key/urlhash id to get all files into a specific order to provide a recent view on the documents
    
    public Snapshots(final File location) {
        this.storageLocation = location;
        this.storageLocation.mkdirs();
        // scan the location to fill the directory
        this.directory = new HashMap<>();
        for (String hostport: location.list()) {
            TreeMap<Integer, TreeSet<String>> domaindepth = new TreeMap<>();
            this.directory.put(hostport, domaindepth);
            File domaindir = new File(location, hostport);
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
                    if (snapshotdir.isDirectory()) {
                        for (String snapshotfile: snapshotdir.list()) {
                            if (snapshotfile.endsWith(".xml")) {
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
                if (dateid.size() == 0) domaindepth.remove(depthi);
            }
            if (domaindepth.size() == 0) this.directory.remove(hostport);
        }
    }

    /**
     * get the number of entries in the snapshot directory
     * @return the total number of different documents
     */
    public int size() {
        int c = 0;
        for (Map<Integer, TreeSet<String>> m: directory.values()) {
            for (TreeSet<String> n: m.values()) {
                c += n.size();
            }
        }
        return c;
    }

    /**
     * get a list of <host>.<port> names in the snapshot directory
     * @return
     */
    public Set<String> listHosts() {
        return directory.keySet();
    }
    
    public final class Revisions {
        public final int depth;
        public final Date[] dates;
        public final String urlhash;
        public final String url;
        public final File[] pathtoxml;
        public Revisions(final String hostport, final int depth, final String datehash) {
            this.depth = depth;
            int p = datehash.indexOf('.');
            this.dates = new Date[1];
            String datestring = datehash.substring(0, p);
            this.dates[0] = parseDate(datestring);
            this.urlhash = datehash.substring(p + 1);
            this.pathtoxml = new File[1];
            this.pathtoxml[0] = new File(pathToShard(hostport, urlhash, depth), this.urlhash + "." + datestring + ".xml");
            String u = null;
            if (this.pathtoxml[0].exists()) {
            	BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.pathtoxml[0])));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("<str name=\"sku\">")) {
                            u = line.substring(16, line.length() - 6);
                            break;
                        }
                    }
                } catch (IOException e) {
                	ConcurrentLog.warn("SNAPSHOTS", "Error while reading file " + this.pathtoxml[0]);
                } finally {
                	if(reader != null) {
                		try {
							reader.close();
						} catch (IOException ignored) {
							ConcurrentLog.warn("SNAPSHOTS", "Could not close input stream on file " + this.pathtoxml[0]);
						}
                	}
                }
            }
            this.url = u; 
        }
    }
    
    public Revisions getRevisions(String urlhash) {
        if (urlhash == null || urlhash.length() == 0) return null;
        // search for the hash, we must iterate through all entries
        for (Map.Entry<String, TreeMap<Integer, TreeSet<String>>> hostportDomaindepth: this.directory.entrySet()) {
            String hostport = hostportDomaindepth.getKey();
            for (Map.Entry<Integer, TreeSet<String>> depthDateHash: hostportDomaindepth.getValue().entrySet()) {
                int depth = depthDateHash.getKey();
                for (String dateHash: depthDateHash.getValue()) {
                    if (dateHash.endsWith(urlhash)) {
                        return new Revisions(hostport, depth, dateHash);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * list the snapshots for a given host name
     * @param hostport the <host>.<port> identifier for the domain (with the same format as applied by the Snapshots.pathToHostPortDir() function)
     * @param depth restrict the result to the given depth or if depth == -1 do not restrict to a depth
     * @return a map with a set for each depth in the domain of the host name
     */
    public TreeMap<Integer, Collection<Revisions>> listIDs(final String hostport, final int depth) {
        TreeMap<Integer, Collection<Revisions>> result = new TreeMap<>();
        TreeMap<Integer, TreeSet<String>> list = directory.get(hostport);
        if (list != null) {
            for (Map.Entry<Integer, TreeSet<String>> entry: list.entrySet()) {
                if (depth != -1 && entry.getKey() != depth) continue;
                Collection<Revisions> r = new ArrayList<>(entry.getValue().size());
                for (String datehash: entry.getValue()) {
                    r.add(new Revisions(hostport, entry.getKey(), datehash));
                }
                result.put(entry.getKey(), r);
            }
        }
        return result;
    }

    /**
     * get the number of snapshots for the given host name
     * @param hostport the <host>.<port> identifier for the domain
     * @param depth restrict the result to the given depth or if depth == -1 do not restrict to a depth
     * @return a count, the total number of documents for the domain and depth
     */
    public int listIDsSize(final String hostport, final int depth) {
        int count = 0;
        TreeMap<Integer, TreeSet<String>> list = directory.get(hostport);
        if (list != null) {
            for (Map.Entry<Integer, TreeSet<String>> entry: list.entrySet()) {
                if (depth != -1 && entry.getKey() != depth) continue;
                count += entry.getValue().size();
            }
        }
        return count;
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
        return new File(pathToShard(url, depth), id + "." + ds + "." + ext);
    }

    /**
     * Write information about the storage of a snapshot to the Snapshot-internal index.
     * The actual writing of files to the target directory must be done elsewehre, this method does not store the snapshot files.
     * @param url
     * @param depth
     * @param date
     */
    public void announceStorage(final DigestURL url, final int depth, final Date date) {
        String id = ASCII.String(url.hash());
        String ds = GenericFormatter.SHORT_MINUTE_FORMATTER.format(date);
        String pathToHostPortDir = pathToHostPortDir(url.getHost(), url.getPort());
        TreeMap<Integer, TreeSet<String>> domaindepth = this.directory.get(pathToHostPortDir);
        if (domaindepth == null) {domaindepth = new TreeMap<Integer, TreeSet<String>>(); this.directory.put(pathToHostPortDir(url.getHost(), url.getPort()), domaindepth);}
        TreeSet<String> dateid = domaindepth.get(depth);
        if (dateid == null) {dateid = new TreeSet<String>(); domaindepth.put(depth, dateid);}
        dateid.add(ds + '.' + id);
    }

    /**
     * Delete information about the storage of a snapshot to the Snapshot-internal index.
     * The actual deletion of files in the target directory must be done elsewhere, this method does not store the snapshot files.
     * @param url
     * @param depth
     */
    public Set<Date> announceDeletion(final DigestURL url, final int depth) {
        HashSet<Date> dates = new HashSet<>();
        String id = ASCII.String(url.hash());
        String pathToHostPortDir = pathToHostPortDir(url.getHost(), url.getPort());
        TreeMap<Integer, TreeSet<String>> domaindepth = this.directory.get(pathToHostPortDir);
        if (domaindepth == null) return dates;
        TreeSet<String> dateid = domaindepth.get(depth);
        if (dateid == null) return dates;
        Iterator<String> i = dateid.iterator();
        while (i.hasNext()) {
            String dis = i.next();
            if (dis.endsWith("." + id)) {
                String d = dis.substring(0, dis.length() - id.length() - 1);
                Date date = parseDate(d);
                if (date != null) dates.add(date);
                i.remove();
            }
        }
        if (dateid.size() == 0) domaindepth.remove(depth);
        if (domaindepth.size() == 0) this.directory.remove(pathToHostPortDir);
        return dates;
    }
    
    /**
     * Order enum class for the select method
     */
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
    public LinkedHashMap<String, Revisions> select(final String host, final Integer depth, final Order order, int maxcount) {
        TreeMap<String, String[]> dateIdResult = new TreeMap<>();
        if (host == null && depth == null) {
            loop: for (Map.Entry<String, TreeMap<Integer, TreeSet<String>>> hostportDepths: this.directory.entrySet()) {
                for (Map.Entry<Integer, TreeSet<String>> depthIds: hostportDepths.getValue().entrySet()) {
                    for (String id: depthIds.getValue()) {
                        dateIdResult.put(id, new String[]{hostportDepths.getKey(), Integer.toString(depthIds.getKey())});
                        if (order == Order.ANY && dateIdResult.size() >= maxcount) break loop;
                    }
                }
            }
        }
        if (host == null && depth != null) {
            loop: for (Map.Entry<String, TreeMap<Integer, TreeSet<String>>> hostportDepths: this.directory.entrySet()) {
                TreeSet<String> ids = hostportDepths.getValue().get(depth);
                if (ids != null) for (String id: ids) {
                    dateIdResult.put(id, new String[]{hostportDepths.getKey(), Integer.toString(depth)});
                    if (order == Order.ANY && dateIdResult.size() >= maxcount) break loop;
                }
            }
        }
        if (host != null && depth == null) {
            String hostport = pathToHostPortDir(host, 80);
            TreeMap<Integer, TreeSet<String>> depthIdsMap = this.directory.get(hostport);
            if(depthIdsMap == null && isIpv6AddrHost(host)) {
            	/* If the host is a raw IPV6 address, we check also if a snapshot was recorded with the old format (without percent-encoding) */
                hostport = pathToHostPortDir(host, 80, false);
                depthIdsMap = this.directory.get(hostport);            	
            }
            if (depthIdsMap != null) {
            	loop: for (Map.Entry<Integer, TreeSet<String>> depthIds: depthIdsMap.entrySet()) {
            		for (String id: depthIds.getValue()) {
            			dateIdResult.put(id, new String[]{hostport, Integer.toString(depthIds.getKey())});
            			if (order == Order.ANY && dateIdResult.size() >= maxcount) break loop;
            		}
            	}
            }
        }
        if (host != null && depth != null) {
            String hostport = pathToHostPortDir(host, 80);
            TreeMap<Integer, TreeSet<String>> domaindepth = this.directory.get(hostport);
            if(domaindepth == null && isIpv6AddrHost(host)) {
            	/* If the host is a raw IPV6 address, we check also if a snapshot was recorded with the old format (without percent-encoding) */
                hostport = pathToHostPortDir(host, 80, false);
                domaindepth = this.directory.get(hostport);            	
            }
            if (domaindepth != null) {
                TreeSet<String> ids = domaindepth.get(depth);
                if (ids != null) loop: for (String id: ids) {
                    dateIdResult.put(id, new String[]{hostport, Integer.toString(depth)});
                    if (order == Order.ANY && dateIdResult.size() >= maxcount) break loop;
                }
            }
        }
        LinkedHashMap<String, Revisions> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, String[]>> i = order == Order.LATESTFIRST ? dateIdResult.descendingMap().entrySet().iterator() : dateIdResult.entrySet().iterator();
        while (i.hasNext() && result.size() < maxcount) {
            Map.Entry<String, String[]> entry = i.next();
            String datehash = entry.getKey();
            int p = datehash.indexOf('.');
            assert p >= 0;
            Revisions r = new Revisions(entry.getValue()[0], Integer.parseInt(entry.getValue()[1]), datehash);
            result.put(datehash.substring(p + 1), r);
        }
        return result;
    }
    
    private static Date parseDate(String d) {
        try {
            return GenericFormatter.SHORT_MINUTE_FORMATTER.parse(d, 0).getTime();
        } catch (ParseException e) {
            try {
                return GenericFormatter.SHORT_DAY_FORMATTER.parse(d, 0).getTime();
            } catch (ParseException ee) {
                return null;
            }
        }
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
    
    // pathtoxml = <storageLocation>/<host>.<port>/<depth>/<shard>/<urlhash>.<date>.xml
    
    /**
     * for a given url, get all paths for storage locations.
     * The locations are all for the single url but may represent different storage times.
     * @param url
     * @param ext required extension or null if the extension must not be checked
     * @param depth
     * @return a set of files for snapshots of the url
     */
    public Collection<File> findPaths(final DigestURL url, final int depth, final String ext) {
        String id = ASCII.String(url.hash());
        File pathToShard = pathToShard(url, depth);
        if(!pathToShard.exists() && isIpv6AddrHost(url.getHost())) {
        	/* If the host is a raw IPV6 address, we check also if a snapshot was recorded with the old format (without percent-encoding) */
        	pathToShard = pathToShard(pathToHostPortDir(url.getHost(), url.getPort(), false), ASCII.String(url.hash()), depth);
        }
        String[] list = pathToShard.exists() && pathToShard.isDirectory() ? pathToShard.list() : null; // may be null if path does not exist
        ArrayList<File> paths = new ArrayList<>();
        if (list != null) {
            for (String f: list) {
                if (f.startsWith(id) && (ext == null || f.endsWith(ext))) paths.add(new File(pathToShard, f));
            }
        }
        return paths;
    }

    private File pathToShard(final DigestURL url, final int depth) {
        return pathToShard(pathToHostPortDir(url.getHost(), url.getPort()), ASCII.String(url.hash()), depth);
    }
    
    private File pathToShard(final String hostport, final String urlhash, final int depth) {
        File pathToHostDir = new File(storageLocation, hostport);
        File pathToDepthDir = new File(pathToHostDir, pathToDepthDir(depth));
        File pathToShard = new File(pathToDepthDir, pathToShard(urlhash));
        return pathToShard;
    }
    
    /**
     * @param host a domain name or IP address
     * @return true when the host string is a raw IPV6 address (with square brackets)
     */
    private boolean isIpv6AddrHost(final String host) {
    	return (host != null && host.startsWith("[") && host.endsWith("]") && host.contains(":"));
    }
    
    /**
     * @param host a domain name or IP address
     * @param port a port number
     * @return a representation of the host and port encoding IPV6 addresses for better support accross file systems (notably FAT or NTFS)
     */
    private String pathToHostPortDir(final String host, final int port) {
    	return pathToHostPortDir(host, port, true);
    }

    /**
     * @param host a domain name or IP address
     * @param port a port number
     * @param encodeIpv6 when true, encode the host for better support accross file systems (notably FAT or NTFS)
     * @return a representation of the host and port
     */
    private String pathToHostPortDir(final String host, final int port, final boolean encodeIpv6) {
    	String encodedHost = host;
        if(encodeIpv6 && isIpv6AddrHost(host)) {
        	/* Percent-encode the host name when it is an IPV6 address, as the ':' character is illegal in a file name on MS Windows FAT32 and NTFS file systems */
        	try {
        		encodedHost = URLEncoder.encode(host, StandardCharsets.UTF_8.name());
			} catch (final UnsupportedEncodingException e) {
				/* This should not happen has UTF-8 encoding support is required for any JVM implementation */
			}
        }
        return encodedHost + "." + port;
    }
    
    private String pathToDepthDir(final int depth) {
        return depth < 10 ? "0" + depth : Integer.toString(depth);
    }
    
    private String pathToShard(final String urlhash) {
        return urlhash.substring(0, 2);
    }

}
