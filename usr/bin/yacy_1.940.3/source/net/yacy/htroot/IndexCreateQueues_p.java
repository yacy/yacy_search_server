
package net.yacy.htroot;

import java.net.MalformedURLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.apache.http.conn.util.InetAddressUtils;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.retrieval.Request;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexCreateQueues_p {

	private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter
			.ofPattern("uuuu/MM/dd", Locale.US).withZone(ZoneId.systemDefault());

	/**
	 * @param date a date to render as a String
	 * @return the date formatted using the DAY_FORMATTER pattern.
	 */
    private static String daydate(final Date date) {
        if (date == null) {
            return "";
        }
        return GenericFormatter.formatSafely(date.toInstant(), DAY_FORMATTER);
    }

    private static final int INVALID    = 0;
    private static final int URL        = 1;
    private static final int ANCHOR     = 2;
    private static final int PROFILE    = 3;
    private static final int DEPTH      = 4;
    private static final int INITIATOR  = 5;
    private static final int MODIFIED   = 6;

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        StackType stackType = StackType.LOCAL;
        int urlsPerHost = 3;
        int sortByCount = 0;
        int sortByHost = 0;
        boolean embed = false;
        String deletepattern = ".*";

        if (post != null) {
            stackType = StackType.valueOf(post.get("stack", stackType.name()).toUpperCase());
            urlsPerHost = post.getInt("urlsPerHost", urlsPerHost);
            if (post.containsKey("embed")) embed = true;

            if (post.containsKey("sort")) {
                final String countSort = post.get("sort");
                if (countSort.equals("count"))  sortByCount = +1;
                if (countSort.equals("-count")) sortByCount = -1;
                if (countSort.equals("host"))  sortByHost = +1;
                if (countSort.equals("-host")) sortByHost = -1;
            }

            if (post.containsKey("delete")) {
                deletepattern = post.get("pattern", deletepattern).trim();
                final int option  = post.getInt("option", INVALID);
                if (".*".equals(deletepattern)) {
                    sb.crawlQueues.noticeURL.clear(stackType);
                    try { sb.cleanProfiles(); } catch (final InterruptedException e) {/* ignore this */}
                } else if (option > INVALID) {
                    try {
                        // compiling the regular expression
                        final Pattern compiledPattern = Pattern.compile(deletepattern);

                        if (option == PROFILE) {
                            // search and delete the crawl profile (_much_ faster, independent of queue size)
                            CrawlProfile entry;
                            for (final byte[] handle: sb.crawler.getActive()) {
                                entry = sb.crawler.getActive(handle);
                                final String name = entry.name();
                                if (CrawlSwitchboard.DEFAULT_PROFILES.contains(name)) continue;
                                if (compiledPattern.matcher(name).find()) {
                                    sb.crawler.removeActive(entry.handle().getBytes());
                                    sb.crawler.removePassive(entry.handle().getBytes());
                                }
                            }
                        } else {
                            int removedByHosts = 0;
                            if (option == URL && deletepattern.startsWith(".*") && deletepattern.endsWith(".*")) {
                                // try to delete that using the host name
                                final Set<String> hosthashes = new HashSet<String>();
                                final String hn = deletepattern.substring(2, deletepattern.length() - 2);
                                try {
                                    hosthashes.add(DigestURL.hosthash(hn, hn.startsWith("ftp") ? 21 : 80));
                                    hosthashes.add(DigestURL.hosthash(hn, 443));
                                    removedByHosts = sb.crawlQueues.removeHosts(hosthashes);
                                } catch (final MalformedURLException e) {
                                }
                            }

                            if (removedByHosts == 0) {
                                // iterating through the list of URLs
                                final Iterator<Request> iter = sb.crawlQueues.noticeURL.iterator(stackType);
                                Request entry;
                                final List<byte[]> removehashes = new ArrayList<byte[]>();
                                while (iter.hasNext()) {
                                    if ((entry = iter.next()) == null) continue;
                                    String value = null;

                                    location: switch (option) {
                                        case URL:       value = (entry.url() == null) ? null : entry.url().toString(); break location;
                                        case ANCHOR:    value = entry.name(); break location;
                                        case DEPTH:     value = Integer.toString(entry.depth()); break location;
                                        case INITIATOR:
                                            value = (entry.initiator() == null || entry.initiator().length == 0) ? "proxy" : ASCII.String(entry.initiator());
                                            break location;
                                        case MODIFIED:  value = daydate(entry.appdate()); break location;
                                        default: value = null; break location;
                                    }

                                    if (value != null && compiledPattern.matcher(value).matches()) removehashes.add(entry.url().hash());
                                }
                                ConcurrentLog.info("IndexCreateQueues_p", "created a remove list with " + removehashes.size() + " entries for pattern '" + deletepattern + "'");
                                for (final byte[] b: removehashes) {
                                    sb.crawlQueues.noticeURL.removeByURLHash(b);
                                }
                            }
                        }
                    } catch (final PatternSyntaxException e) {
                        ConcurrentLog.logException(e);
                    }
                }
            }
        }

        final int stackSize = sb.crawlQueues.noticeURL.stackSize(stackType);
        if (stackSize == 0) {
            prop.put("crawler", "0");
        } else {
            prop.put("crawler", "1");
            prop.put("crawler_embed", embed ? 1 : 0);
            prop.put("crawler_embed_deletepattern", deletepattern);
            prop.put("crawler_embed_queuename", stackType.name());

            Map<String, Integer[]> hosts = sb.crawlQueues.noticeURL.getDomainStackHosts(stackType, sb.robots);

            prop.put("crawler_showtable_queuename", stackType.name());

            if ( sortByCount==0 && sortByHost==0 ) {
                prop.put("crawler_showtable_sortedByCount",false);
                prop.put("crawler_showtable_sortedByCount_asc",true); // first click sorts descending
                prop.put("crawler_showtable_sortedByHost",false);
                prop.put("crawler_showtable_sortedByHost_asc",false);
            }
            else {

                if ( sortByCount!=0 ) {
                    prop.put("crawler_showtable_sortedByHost",false);
                    prop.put("crawler_showtable_sortedByHost_asc",false);
                    prop.put("crawler_showtable_sortedByCount",true);
                    if (sortByCount < 0) {
                        prop.put("crawler_showtable_sortedByCount_asc", false);
                        final Map<String, Integer[]> hosts_sorted = hosts.entrySet().stream()
                            .sorted( (e1, e2)->e2.getValue()[0].compareTo(e1.getValue()[0]) )
                            .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
                        hosts = hosts_sorted;
                    }
                    else {
                        prop.put("crawler_showtable_sortedByCount_asc", true);
                        final Map<String, Integer[]> hosts_sorted = hosts.entrySet().stream()
                            .sorted( (e1, e2)->e1.getValue()[0].compareTo(e2.getValue()[0]) )
                            .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
                        hosts = hosts_sorted;
                    }
                }

                if ( sortByHost!=0 ) {
                    prop.put("crawler_showtable_sortedByCount",false);
                    prop.put("crawler_showtable_sortedByCount_asc",true);
                    prop.put("crawler_showtable_sortedByHost",true);
                    if (sortByHost < 0) {
                        prop.put("crawler_showtable_sortedByHost_asc", false);
                        final Map<String, Integer[]> hosts_sorted = new TreeMap<String, Integer[]>(Collections.reverseOrder());
                        hosts_sorted.putAll(hosts);
                        hosts = hosts_sorted;
                    }
                    else {
                        prop.put("crawler_showtable_sortedByHost_asc", true);
                        final Map<String, Integer[]> hosts_sorted = new TreeMap<String, Integer[]>(hosts);
                        hosts = hosts_sorted;
                    }
                }
            }

            int hc = 0;
            for (final Map.Entry<String, Integer[]> host: hosts.entrySet()) {
                final String hostnameport = host.getKey();
                String hostname = Domains.stripToHostName(hostnameport);
                if(InetAddressUtils.isIPv6Address(hostname)) {
                	hostname = "[" + hostname + "]"; // HostBalancer.getDomainStackReferences() function requires square brackets around IPV6 addresses
                }
                prop.putHTML("crawler_host_" + hc + "_hostnameport", hostnameport);
                prop.putHTML("crawler_host_" + hc + "_hostname", hostname);
                prop.put("crawler_host_" + hc + "_embed", embed ? 1 : 0);
                prop.put("crawler_host_" + hc + "_urlsPerHost", urlsPerHost);
                prop.putHTML("crawler_host_" + hc + "_queuename", stackType.name());
                prop.put("crawler_host_" + hc + "_hostcount", host.getValue()[0]);
                prop.put("crawler_host_" + hc + "_hostdelta", host.getValue()[1] == Integer.MIN_VALUE ? "not accessed" : Integer.toString(host.getValue()[1]));
                final List<Request> domainStackReferences = sb.crawlQueues.noticeURL.getDomainStackReferences(stackType, hostname, urlsPerHost, 10000);

                Seed initiator;
                String profileHandle;
                CrawlProfile profileEntry;
                int count = 0;
                for (final Request request: domainStackReferences) {
                    if (request == null) continue;
                    initiator = sb.peers.getConnected(request.initiator() == null ? "" : ASCII.String(request.initiator()));
                    profileHandle = request.profileHandle();
                    profileEntry = profileHandle == null ? null : sb.crawler.getActive(profileHandle.getBytes());
                    String depthString = Integer.toString(request.depth());
                    while (depthString.length() < 4) depthString = "0" + depthString;
                    prop.putHTML("crawler_host_" + hc + "_list_" + count + "_initiator", ((initiator == null) ? "proxy" : initiator.getName()) );
                    prop.put("crawler_host_" + hc + "_list_" + count + "_profile", ((profileEntry == null) ? "unknown" : profileEntry.collectionName()));
                    prop.putHTML("crawler_host_" + hc + "_list_" + count + "_depth", depthString);
                    prop.put("crawler_host_" + hc + "_list_" + count + "_modified", daydate(request.appdate()) );
                    prop.putHTML("crawler_host_" + hc + "_list_" + count + "_anchor", request.name());
                    prop.putHTML("crawler_host_" + hc + "_list_" + count + "_url", request.url().toNormalform(true));
                    prop.put("crawler_host_" + hc + "_list_" + count + "_hash", request.url().hash());
                    count++;
                }
                prop.putNum("crawler_host_" + hc + "_list", count);
                hc++;
            }
            prop.put("crawler_host", hc);
        }

        prop.put("embed", embed ? 1 : 0);
        prop.put("queuename", stackType.name().charAt(0) + stackType.name().substring(1).toLowerCase());
        prop.put("embed_queuename", stackType.name().charAt(0) + stackType.name().substring(1).toLowerCase());

        // return rewrite properties
        return prop;
    }
}
