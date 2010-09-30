
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.kelondro.data.meta.DigestURI;

import de.anomic.crawler.CrawlProfile;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class getpageinfo_p {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        // avoid UNRESOLVED PATTERN        
        prop.put("title", "");        
        prop.put("desc", "");
        prop.put("lang", "");
        prop.put("robots-allowed", "3"); //unknown
        prop.put("sitemap", "");
        prop.put("favicon","");        
        prop.put("sitelist", "");
        prop.put("filter", ".*");
        
        // default actions
        String actions="title,robots";
        
        if (post != null && post.containsKey("url")) {
            if(post.containsKey("actions"))
                actions=post.get("actions");
            String url=post.get("url");
			if(url.toLowerCase().startsWith("ftp://")){
				prop.put("robots-allowed", "1");
				prop.putXML("title", "FTP: "+url);
                return prop;
			} else if (!url.startsWith("http://") &&
		               !url.startsWith("https://") &&
		               !url.startsWith("ftp://") &&
		               !url.startsWith("smb://") &&
		              !url.startsWith("file://")) {
                url = "http://" + url;
            }
            if (actions.indexOf("title")>=0) {
                DigestURI u = null;
                try {
                    u = new DigestURI(url, null);
                } catch (final MalformedURLException e) {
                    // fail, do nothing
                }
                ContentScraper scraper = null;
                if (u != null) try {
                    scraper = sb.loader.parseResource(u, CrawlProfile.CacheStrategy.IFFRESH);
                } catch (final IOException e) {
                    // now thats a fail, do nothing      
                }  
                if (scraper != null) {
                    // put the document title 
                    prop.putXML("title", scraper.getTitle());
                    
                    // put the favicon that belongs to the document
                    prop.put("favicon", (scraper.getFavicon()==null) ? "" : scraper.getFavicon().toString());
                    
                    // put keywords
                    final String list[]=scraper.getKeywords();
                    int count = 0;
                    for(int i=0;i<list.length;i++){
                        String tag = list[i];
                        if (!tag.equals("")) {                                          
                            prop.putXML("tags_"+count+"_tag", tag);
                            count++;
                        }
                    }
                    prop.put("tags", count);
                    // put description                    
                    prop.putXML("desc", scraper.getDescription());
                    // put language
                    Set<String> languages = scraper.getContentLanguages();
                    prop.putXML("lang", (languages == null) ? "unknown" : languages.iterator().next());
                    
                    // get links and put them into a semicolon-separated list
                    StringBuilder links = new StringBuilder();
                    StringBuilder filter = new StringBuilder();
                    count = 0;
                    for (MultiProtocolURI uri: scraper.getAnchors().keySet()) {
                        links.append(';').append(uri.toNormalform(true, false));
                        filter.append('|').append(uri.getProtocol()).append("://").append(uri.getHost()).append(".*");
                        prop.putXML("links_" + count + "_link", uri.toNormalform(true, false));
                        count++;
                    }
                    prop.put("links", count);
                    prop.putXML("sitelist", links.length() > 0 ? links.substring(1) : "");
                    prop.putXML("filter", filter.length() > 0 ? filter.substring(1) : ".*");
                }
            }
            if (actions.indexOf("robots")>=0) {
                try {
                    final DigestURI theURL = new DigestURI(url, null);
                    
                	// determine if crawling of the current URL is allowed
                	prop.put("robots-allowed", sb.robots.isDisallowed(theURL) ? "0" : "1");
                    
                    // get the sitemap URL of the domain
                    final MultiProtocolURI sitemapURL = sb.robots.getSitemapURL(theURL);
                    prop.putXML("sitemap", (sitemapURL==null) ? "" : sitemapURL.toString());
                } catch (final MalformedURLException e) {}
            }
            
        }
        // return rewrite properties
        return prop;
    }
    
}
