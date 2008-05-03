//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@anomic.de
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

import de.anomic.http.httpHeader;
import de.anomic.index.indexRepositoryReference;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexCleaner_p {
    private static indexRepositoryReference.BlacklistCleaner urldbCleanerThread = null;
    private static plasmaWordIndex.ReferenceCleaner indexCleanerThread = null;

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        prop.put("title", "DbCleanup_p");
        if (post!=null) {
            //prop.putHTML("bla", "post!=null");
            if (post.get("action").equals("ustart")) {
                if (urldbCleanerThread==null || !urldbCleanerThread.isAlive()) {
                    urldbCleanerThread = sb.wordIndex.getURLCleaner(plasmaSwitchboard.urlBlacklist);
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
                    indexCleanerThread = sb.wordIndex.getReferenceCleaner(post.get("wordHash","AAAAAAAAAAAA"));
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
        //prop.put("bla", "post==null");
        if (urldbCleanerThread!=null) {
            prop.put("urldb", "1");
            prop.putNum("urldb_percentUrls", ((double)urldbCleanerThread.totalSearchedUrls/sb.wordIndex.countURL())*100);
            prop.putNum("urldb_blacklisted", urldbCleanerThread.blacklistedUrls);
            prop.putNum("urldb_total", urldbCleanerThread.totalSearchedUrls);
            prop.putHTML("urldb_lastBlacklistedUrl", urldbCleanerThread.lastBlacklistedUrl);
            prop.put("urldb_lastBlacklistedHash", urldbCleanerThread.lastBlacklistedHash);
            prop.putHTML("urldb_lastUrl", urldbCleanerThread.lastUrl);
            prop.put("urldb_lastHash", urldbCleanerThread.lastHash);
            prop.put("urldb_threadAlive", urldbCleanerThread.isAlive() + "");
            prop.put("urldb_threadToString", urldbCleanerThread.toString());
            double percent = ((double)urldbCleanerThread.blacklistedUrls/urldbCleanerThread.totalSearchedUrls)*100;
            prop.putNum("urldb_percent", percent);
        }
        if (indexCleanerThread!=null) {
            prop.put("rwidb", "1");
            prop.put("rwidb_threadAlive", indexCleanerThread.isAlive() + "");
            prop.put("rwidb_threadToString", indexCleanerThread.toString());
            prop.putNum("rwidb_RWIcountstart", indexCleanerThread.rwiCountAtStart);
            prop.putNum("rwidb_RWIcountnow", sb.wordIndex.size());
            prop.put("rwidb_wordHashNow", indexCleanerThread.wordHashNow);
            prop.put("rwidb_lastWordHash", indexCleanerThread.lastWordHash);
            prop.putNum("rwidb_lastDeletionCounter", indexCleanerThread.lastDeletionCounter);

        }
        return prop;
    }
}
