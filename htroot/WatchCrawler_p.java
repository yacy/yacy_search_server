import java.io.File;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.data.wikiCode;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsRecord;

// WatchCrawler_p.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 18.12.2006 on http://www.anomic.de
// this file was created using the an implementation from IndexCreate_p.java, published 02.12.2004
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

public class WatchCrawler_p {

    // this servlet does NOT create the WatchCrawler page content!
    // this servlet starts a web crawl. The interface for entering the web crawl parameters is in IndexCreate_p.html
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        if (post == null) {
            // not a crawl start, only monitoring
            prop.put("info", 0);
        } else {
            prop.put("info", 0);
            
            if (post.containsKey("deleteprofile")) {
                // deletion of a crawl
                String handle = (String) post.get("handle");
                if (handle != null) switchboard.profiles.removeEntry(handle);
            }
            
            if (post.containsKey("continue")) {
                // continue queue
                String queue = post.get("continue", "");
                if (queue.equals("localcrawler")) {
                    switchboard.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                } else if (queue.equals("remotecrawler")) {
                    switchboard.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_GLOBAL_CRAWL_TRIGGER);
                }
            }

            if (post.containsKey("pause")) {
                // pause queue
                String queue = post.get("pause", "");
                if (queue.equals("localcrawler")) {
                    switchboard.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                } else if (queue.equals("remotecrawler")) {
                    switchboard.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_GLOBAL_CRAWL_TRIGGER);
                }
            }
            
            if (post.containsKey("crawlingstart")) {
                // init crawl
                if (yacyCore.seedDB == null) {
                    prop.put("info", 3);
                } else {
                    // set new properties
                    String newcrawlingfilter = post.get("crawlingFilter", ".*");
                    env.setConfig("crawlingFilter", newcrawlingfilter);
                    
                    int newcrawlingdepth = Integer.parseInt(post.get("crawlingDepth", "0"));
                    env.setConfig("crawlingDepth", Integer.toString(newcrawlingdepth));
                    
                    boolean crawlingIfOlderCheck = post.get("crawlingIfOlderCheck", "off").equals("on");
                    int crawlingIfOlderNumber = Integer.parseInt(post.get("crawlingIfOlderNumber", "-1"));
                    String crawlingIfOlderUnit = post.get("crawlingIfOlderUnit","year");
                    int crawlingIfOlder = recrawlIfOlderC(crawlingIfOlderCheck, crawlingIfOlderNumber, crawlingIfOlderUnit);                    
                    env.setConfig("crawlingIfOlder", crawlingIfOlder);
                    
                    boolean crawlingDomFilterCheck = post.get("crawlingDomFilterCheck", "off").equals("on");
                    int crawlingDomFilterDepth = (crawlingDomFilterCheck) ? Integer.parseInt(post.get("crawlingDomFilterDepth", "-1")) : -1;
                    env.setConfig("crawlingDomFilterDepth", Integer.toString(crawlingDomFilterDepth));
                    
                    boolean crawlingDomMaxCheck = post.get("crawlingDomMaxCheck", "off").equals("on");
                    int crawlingDomMaxPages = (crawlingDomMaxCheck) ? Integer.parseInt(post.get("crawlingDomMaxPages", "-1")) : -1;
                    env.setConfig("crawlingDomMaxPages", Integer.toString(crawlingDomMaxPages));
                    
                    boolean crawlingQ = post.get("crawlingQ", "off").equals("on");
                    env.setConfig("crawlingQ", (crawlingQ) ? "true" : "false");
                    
                    boolean indexText = post.get("indexText", "off").equals("on");
                    env.setConfig("indexText", (indexText) ? "true" : "false");
                    
                    boolean indexMedia = post.get("indexMedia", "off").equals("on");
                    env.setConfig("indexMedia", (indexMedia) ? "true" : "false");
                    
                    boolean storeHTCache = post.get("storeHTCache", "off").equals("on");
                    env.setConfig("storeHTCache", (storeHTCache) ? "true" : "false");
                    
                    boolean crawlOrder = post.get("crawlOrder", "off").equals("on");
                    env.setConfig("crawlOrder", (crawlOrder) ? "true" : "false");
                    
                    boolean xsstopw = post.get("xsstopw", "off").equals("on");
                    env.setConfig("xsstopw", (xsstopw) ? "true" : "false");
                    
                    boolean xdstopw = post.get("xdstopw", "off").equals("on");
                    env.setConfig("xdstopw", (xdstopw) ? "true" : "false");
                    
                    boolean xpstopw = post.get("xpstopw", "off").equals("on");
                    env.setConfig("xpstopw", (xpstopw) ? "true" : "false");
                    
                    String crawlingMode = post.get("crawlingMode","url");
                    if (crawlingMode.equals("url")) {
                        // getting the crawljob start url
                        String crawlingStart = post.get("crawlingURL","");
                        crawlingStart = crawlingStart.trim();
                        
                        // adding the prefix http:// if necessary
                        int pos = crawlingStart.indexOf("://");
                        if (pos == -1) crawlingStart = "http://" + crawlingStart;

                        // normalizing URL
                        try {crawlingStart = new URL(crawlingStart).toNormalform();} catch (MalformedURLException e1) {}
                        
                        // check if url is proper
                        URL crawlingStartURL = null;
                        try {
                            crawlingStartURL = new URL(crawlingStart);
                        } catch (MalformedURLException e) {
                            crawlingStartURL = null;
                        }
                        
                        // check if pattern matches
                        if ((crawlingStartURL == null) /* || (!(crawlingStart.matches(newcrawlingfilter))) */) {
                            // print error message
                            prop.put("info", 4); //crawlfilter does not match url
                            prop.put("info_newcrawlingfilter", newcrawlingfilter);
                            prop.put("info_crawlingStart", crawlingStart);
                        } else try {
                            
                            // check if the crawl filter works correctly
                            Pattern.compile(newcrawlingfilter);
                            
                            // stack request
                            // first delete old entry, if exists
                            String urlhash = plasmaURL.urlHash(crawlingStart);
                            switchboard.wordIndex.loadedURL.remove(urlhash);
                            switchboard.noticeURL.remove(urlhash);
                            switchboard.errorURL.remove(urlhash);
                            
                            // stack url
                            plasmaCrawlProfile.entry pe = switchboard.profiles.newEntry(
                                    crawlingStartURL.getHost(), crawlingStart, newcrawlingfilter, newcrawlingfilter,
                                    newcrawlingdepth, newcrawlingdepth,
                                    crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages,
                                    crawlingQ,
                                    indexText, indexMedia,
                                    storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw);
                            String reasonString = switchboard.sbStackCrawlThread.stackCrawl(crawlingStart, null, yacyCore.seedDB.mySeed.hash, "CRAWLING-ROOT", new Date(), 0, pe);
                            
                            if (reasonString == null) {
                                // liftoff!
                                prop.put("info", 8);//start msg
                                prop.put("info_crawlingURL", ((String) post.get("crawlingURL")));
                                
                                // generate a YaCyNews if the global flag was set
                                if (crawlOrder) {
                                    Map m = new HashMap(pe.map()); // must be cloned
                                    m.remove("specificDepth");
                                    m.remove("indexText");
                                    m.remove("indexMedia");
                                    m.remove("remoteIndexing");
                                    m.remove("xsstopw");
                                    m.remove("xpstopw");
                                    m.remove("xdstopw");
                                    m.remove("storeTXCache");
                                    m.remove("storeHTCache");
                                    m.remove("generalFilter");
                                    m.remove("specificFilter");
                                    m.put("intention", post.get("intention", "").replace(',', '/'));
                                    yacyCore.newsPool.publishMyNews(new yacyNewsRecord("crwlstrt", m));
                                }
                                
                            } else {
                                prop.put("info", 5); //Crawling failed
                                prop.put("info_crawlingURL", wikiCode.replaceHTML(((String) post.get("crawlingURL"))));
                                prop.put("info_reasonString", reasonString);
                                
                                plasmaCrawlEURL.Entry ee = switchboard.errorURL.newEntry(crawlingStartURL, null, yacyCore.seedDB.mySeed.hash, yacyCore.seedDB.mySeed.hash,
                                                                                                 crawlingStartURL.getHost(), reasonString, new kelondroBitfield());
                                ee.store();
                                switchboard.errorURL.stackPushEntry(ee);
                            }
                        } catch (PatternSyntaxException e) {
                            prop.put("info", 4); //crawlfilter does not match url
                            prop.put("info_newcrawlingfilter", newcrawlingfilter);
                            prop.put("info_error", e.getMessage());                                 
                        } catch (Exception e) {
                            // mist
                            prop.put("info", 6);//Error with url
                            prop.put("info_crawlingStart", crawlingStart);
                            prop.put("info_error", e.getMessage());
                            e.printStackTrace();
                        }                        
                        
                    } else if (crawlingMode.equals("file")) {                        
                        if (post.containsKey("crawlingFile")) {
                            // getting the name of the uploaded file
                            String fileName = (String) post.get("crawlingFile");  
                            try {                     
                                // check if the crawl filter works correctly
                                Pattern.compile(newcrawlingfilter);                              
                                
                                // loading the file content
                                File file = new File(fileName);
                                
                                // getting the content of the bookmark file
                                byte[] fileContent = (byte[]) post.get("crawlingFile$file");
                                
                                // TODO: determine the real charset here ....
                                String fileString = new String(fileContent,"UTF-8");
                                
                                // parsing the bookmark file and fetching the headline and contained links
                                htmlFilterContentScraper scraper = new htmlFilterContentScraper(new URL(file));
                                //OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                                Writer writer = new htmlFilterWriter(null,null,scraper,null,false);
                                serverFileUtils.write(fileString,writer);
                                writer.close();
                                
                                //String headline = scraper.getHeadline();
                                HashMap hyperlinks = (HashMap) scraper.getAnchors();
                                
                                // creating a crawler profile
                                plasmaCrawlProfile.entry profile = switchboard.profiles.newEntry(fileName, file.toURL().toString(), newcrawlingfilter, newcrawlingfilter, newcrawlingdepth, newcrawlingdepth, crawlingIfOlder, crawlingDomFilterDepth, crawlingDomMaxPages, crawlingQ, indexText, indexMedia, storeHTCache, true, crawlOrder, xsstopw, xdstopw, xpstopw);
                                
                                // loop through the contained links
                                Iterator interator = hyperlinks.entrySet().iterator();
                                int c = 0;
                                while (interator.hasNext()) {
                                    Map.Entry e = (Map.Entry) interator.next();
                                    String nexturlstring = (String) e.getKey();
                                    
                                    if (nexturlstring == null) continue;
                                    
                                    nexturlstring = nexturlstring.trim();
                                    
                                    // normalizing URL
                                    nexturlstring = new URL(nexturlstring).toNormalform();                                    
                                    
                                    // generating an url object
                                    URL nexturlURL = null;
                                    try {
                                        nexturlURL = new URL(nexturlstring);
                                    } catch (MalformedURLException ex) {
                                        nexturlURL = null;
                                        c++;
                                        continue;
                                    }                                    
                                    
                                    // enqueuing the url for crawling
                                    String rejectReason = switchboard.sbStackCrawlThread.stackCrawl(nexturlstring, null, yacyCore.seedDB.mySeed.hash, (String)e.getValue(), new Date(), 1, profile);                                    
                                    
                                    // if something failed add the url into the errorURL list
                                    if (rejectReason == null) {
                                        c++;
                                    } else {
                                        plasmaCrawlEURL.Entry ee = switchboard.errorURL.newEntry(nexturlURL, null, yacyCore.seedDB.mySeed.hash, yacyCore.seedDB.mySeed.hash,
                                                                                                         (String) e.getValue(), rejectReason, new kelondroBitfield());
                                        ee.store();
                                        switchboard.errorURL.stackPushEntry(ee);
                                    }
                                }                             
                               
                            } catch (PatternSyntaxException e) {
                                // print error message
                                prop.put("info", 4); //crawlfilter does not match url
                                prop.put("info_newcrawlingfilter", newcrawlingfilter);
                                prop.put("info_error", e.getMessage());                            
                            } catch (Exception e) {
                                // mist
                                prop.put("info", 7);//Error with file
                                prop.put("info_crawlingStart", fileName);
                                prop.put("info_error", e.getMessage());
                                e.printStackTrace();                                
                            }
                        }                        
                    }
                }
            }
        }
        
        // crawl profiles
        int count = 0;
        int domlistlength = (post == null) ? 160 : post.getInt("domlistlength", 160);
        Iterator it = switchboard.profiles.profiles(true);
        plasmaCrawlProfile.entry profile;
        boolean dark = true;
        while (it.hasNext()) {
            profile = (plasmaCrawlProfile.entry) it.next();
            prop.put("crawlProfiles_"+count+"_dark", ((dark) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_name", wikiCode.replaceHTML(profile.name()));
            prop.put("crawlProfiles_"+count+"_startURL", wikiCode.replaceHTML(profile.startURL()));
            prop.put("crawlProfiles_"+count+"_handle", wikiCode.replaceHTML(profile.handle()));
            prop.put("crawlProfiles_"+count+"_depth", profile.generalDepth());
            prop.put("crawlProfiles_"+count+"_filter", profile.generalFilter());
            prop.put("crawlProfiles_"+count+"_crawlingIfOlder", (profile.recrawlIfOlder() == Long.MAX_VALUE) ? "no re-crawl" : ""+profile.recrawlIfOlder());
            prop.put("crawlProfiles_"+count+"_crawlingDomFilterDepth", (profile.domFilterDepth() == Integer.MAX_VALUE) ? "inactive" : Integer.toString(profile.domFilterDepth()));
            prop.put("crawlProfiles_"+count+"_crawlingDomFilterContent", profile.domNames(true, domlistlength));
            prop.put("crawlProfiles_"+count+"_crawlingDomMaxPages", (profile.domMaxPages() == Integer.MAX_VALUE) ? "unlimited" : ""+profile.domMaxPages());
            prop.put("crawlProfiles_"+count+"_withQuery", ((profile.crawlingQ()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_storeCache", ((profile.storeHTCache()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_indexText", ((profile.indexText()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_indexMedia", ((profile.indexMedia()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_remoteIndexing", ((profile.remoteIndexing()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_deleteButton", (((profile.name().equals("remote")) ||
                                                               (profile.name().equals("proxy")) ||
                                                               (profile.name().equals("snippetText")) ||
                                                               (profile.name().equals("snippetMedia")) ? 0 : 1)));
            prop.put("crawlProfiles_"+count+"_deleteButton_handle", profile.handle());
            
            dark = !dark;
            count++;
        }
        prop.put("crawlProfiles", count);
        
        // return rewrite properties
        return prop;
    }

    private static int recrawlIfOlderC(boolean recrawlIfOlderCheck, int recrawlIfOlderNumber, String crawlingIfOlderUnit) {
        if (!recrawlIfOlderCheck) return -1;
        if (crawlingIfOlderUnit.equals("year")) return recrawlIfOlderNumber * 60 * 24 * 356;
        if (crawlingIfOlderUnit.equals("month")) return recrawlIfOlderNumber * 60 * 24 * 30;
        if (crawlingIfOlderUnit.equals("day")) return recrawlIfOlderNumber * 60 * 24;
        if (crawlingIfOlderUnit.equals("hour")) return recrawlIfOlderNumber * 60;
        if (crawlingIfOlderUnit.equals("minute")) return recrawlIfOlderNumber;
        return -1;
    }
    
}
