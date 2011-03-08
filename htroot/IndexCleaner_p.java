//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//This file is contributed by Matthias Soehnholz
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import de.anomic.search.MetadataRepository;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexCleaner_p {
    private static MetadataRepository.BlacklistCleaner urldbCleanerThread = null;
    private static Segment.ReferenceCleaner indexCleanerThread = null;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        prop.put("title", "DbCleanup_p");
        
        // get segment
        Segment indexSegment = null;
        if (post != null && post.containsKey("segment")) {
            String segmentName = post.get("segment");
            if (sb.indexSegments.segmentExist(segmentName)) {
                indexSegment = sb.indexSegments.segment(segmentName);
            }
        } else {
            // take default segment
            indexSegment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }
        
        if (post!=null) {
            if (post.get("action").equals("ustart")) {
                if (urldbCleanerThread==null || !urldbCleanerThread.isAlive()) {
                    urldbCleanerThread = indexSegment.urlMetadata().getBlacklistCleaner(Switchboard.urlBlacklist, sb.crawlStacker);
                    urldbCleanerThread.start();
                }
                else {
                    urldbCleanerThread.endPause();
                }
            }
            else if (post.get("action").equals("ustop") && (urldbCleanerThread!=null)) {
                urldbCleanerThread.abort();
            }
            else if (post.get("action").equals("upause") && (urldbCleanerThread!=null)) {
                urldbCleanerThread.pause();
            }
            else if (post.get("action").equals("rstart")) {
                if (indexCleanerThread==null || !indexCleanerThread.isAlive()) {
                    indexCleanerThread = indexSegment.getReferenceCleaner(post.get("wordHash","AAAAAAAAAAAA").getBytes());
                    indexCleanerThread.start();
                }
                else {
                    indexCleanerThread.endPause();
                }
            }
            else if (post.get("action").equals("rstop") && (indexCleanerThread!=null)) {
                indexCleanerThread.abort();
            }
            else if (post.get("action").equals("rpause") && (indexCleanerThread!=null)) {
                indexCleanerThread.pause();
            }
            prop.put("LOCATION","");
            return prop;
        }
        if (urldbCleanerThread!=null) {
            prop.put("urldb", "1");
            prop.putNum("urldb_percentUrls", ((double)urldbCleanerThread.totalSearchedUrls/indexSegment.urlMetadata().size())*100);
            prop.putNum("urldb_blacklisted", urldbCleanerThread.blacklistedUrls);
            prop.putNum("urldb_total", urldbCleanerThread.totalSearchedUrls);
            prop.putHTML("urldb_lastBlacklistedUrl", urldbCleanerThread.lastBlacklistedUrl);
            prop.put("urldb_lastBlacklistedHash", urldbCleanerThread.lastBlacklistedHash);
            prop.putHTML("urldb_lastUrl", urldbCleanerThread.lastUrl);
            prop.put("urldb_lastHash", urldbCleanerThread.lastHash);
            prop.put("urldb_threadAlive", Boolean.toString(urldbCleanerThread.isAlive()));
            prop.put("urldb_threadToString", urldbCleanerThread.toString());
            final double percent = ((double)urldbCleanerThread.blacklistedUrls/urldbCleanerThread.totalSearchedUrls)*100;
            prop.putNum("urldb_percent", percent);
        }
        if (indexCleanerThread!=null) {
            prop.put("rwidb", "1");
            prop.put("rwidb_threadAlive", Boolean.toString(indexCleanerThread.isAlive()));
            prop.put("rwidb_threadToString", indexCleanerThread.toString());
            prop.putNum("rwidb_RWIcountstart", indexCleanerThread.rwiCountAtStart);
            prop.putNum("rwidb_RWIcountnow", indexCleanerThread.rwisize());
            prop.put("rwidb_wordHashNow", (indexCleanerThread.wordHashNow == null) ? "NULL" : UTF8.String(indexCleanerThread.wordHashNow));
            prop.put("rwidb_lastWordHash", (indexCleanerThread.lastWordHash == null) ? "null" : UTF8.String(indexCleanerThread.lastWordHash));
            prop.putNum("rwidb_lastDeletionCounter", indexCleanerThread.lastDeletionCounter);

        }
        return prop;
    }
}
