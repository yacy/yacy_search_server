//httpdSoapService.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file was contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
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


package de.anomic.soap.services;

import org.apache.axis.AxisFault;
import org.w3c.dom.Document;

import de.anomic.data.wikiCode;
import de.anomic.plasma.plasmaURL;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;

/**
 * SOAP Service Class that will be invoked by the httpdSoapHandler
 * 
 * @author Martin Thelian
 */
public class SearchService extends AbstractService
{
    /* ================================================================
     * Constants needed to set the template that should be used to 
     * fullfil the request
     * ================================================================ */
    private static final String TEMPLATE_SEARCH = "yacysearch.soap";
    private static final String TEMPLATE_URLINFO = "ViewFile.soap";
    private static final String TEMPLATE_SNIPPET = "xml/snippet.xml";
    private static final String TEMPLATE_OPENSEARCH = "opensearchdescription.xml";

        
    /**
     * Constructor of this class
     */
    public SearchService() {
        super();
        // nothing special todo here at the moment
    }

    /**
     * Service for doing a simple search with the standard settings
     * 
     * @param searchString the search string that should be used
     * @param maxSearchCount the maximum amount of search result that should be returned
     * @param searchOrder can be a combination of YBR, Date and Quality, e.g. <code>YBR-Date-Quality</code> or <code>Date-Quality-YBR</code>
     * @param searchMode can be <code>global</code> or <code>local</code>
     * @param searchMode the total amount of seconds to use for the search
     * @param urlMask if the urlMaskfilter parameter should be used
     * @param urlMaskfilter e.g. <code>.*</code>
     * @param prefermaskfilter
     * @param category can be <code>image</code> or <code>href</code>
     * 
     * @return an xml document containing the search results.
     * 
     * @throws AxisFault if the service could not be executed propery. 
     */
    public Document search(
                String searchString,
                int maxSearchCount,
                String searchOrder,
                String searchMode,
                int maxSearchTime,
                boolean urlMask,
                String urlMaskfilter,
                String prefermaskfilter,
                String category
            ) 
        throws AxisFault {        
        try {
            // extracting the message context
            extractMessageContext(false);
            
            if ((searchMode == null) || !(searchMode.equalsIgnoreCase("global") || searchMode.equalsIgnoreCase("locale"))) {
                searchMode = "global";
            }
            
            if (maxSearchCount < 0) {
                maxSearchCount = 10;
            }
            
            if (searchOrder == null || searchOrder.length() == 0) {
                searchOrder = plasmaSearchPreOrder.canUseYBR() ? "YBR-Date-Quality" : "Date-Quality-YBR";
            }
            
            if (maxSearchTime < 0) {
                maxSearchTime = 10;
            }
            
            if (urlMaskfilter == null) {
                urlMaskfilter = ".*";
            }
            
            if (prefermaskfilter == null) {
                prefermaskfilter = "";
            }
            
            if (category == null || category.length() == 0) {
                category = "href";
            }
            
            // setting the searching properties
            serverObjects args = new serverObjects();
            args.put("search",searchString);
            args.put("count",Integer.toString(maxSearchCount));  
            args.put("order",searchOrder);
            args.put("resource",searchMode);
            args.put("time",Integer.toString(maxSearchTime));
            args.put("urlmask",(!urlMask)?"no":"yes");
            args.put("urlmaskfilter",urlMaskfilter);
            args.put("prefermaskfilter",prefermaskfilter);
            args.put("cat",category);                        
            
            args.put("Enter","Search");
            
            // invoke servlet
            serverObjects searchResult = invokeServlet(TEMPLATE_SEARCH, args);
            
            // Postprocess search ...
            int count = Integer.valueOf(searchResult.get("type_results","0")).intValue();
            for (int i=0; i < count; i++) {
            	searchResult.put("type_results_" + i + "_url",wikiCode.replaceHTMLonly(searchResult.get("type_results_" + i + "_url","")));
            	searchResult.put("type_results_" + i + "_description",wikiCode.replaceHTMLonly(searchResult.get("type_results_" + i + "_description","")));
            	searchResult.put("type_results_" + i + "_urlname",wikiCode.replaceHTMLonly(searchResult.get("type_results_" + i + "_urlname","")));
            }
            
            // format the result
            byte[] result = buildServletOutput(TEMPLATE_SEARCH, searchResult);
            
            // sending back the result to the client
            return this.convertContentToXML(result);
        } catch (Exception e)  {
            throw new AxisFault(e.getMessage());
        }
    }
    
    
    /**
    * @param url the url
    * @param viewMode one of (VIEW_MODE_AS_PLAIN_TEXT = 1,
    * VIEW_MODE_AS_PARSED_TEXT = 2,
    * VIEW_MODE_AS_PARSED_SENTENCES = 3) [Source: ViewFile.java]
    * @return an xml document containing the url info.
    *
    * @throws AxisFault if the service could not be executed propery.
    */    
    public Document urlInfo(String urlStr, int viewMode) throws AxisFault {
        try {
            // getting the url hash for this url
            URL url = new URL(urlStr);
            String urlHash = plasmaURL.urlHash(url);
            
            // fetch urlInfo
            return this.urlInfoByHash(urlHash, viewMode);
        } catch (Exception e) {
            throw new AxisFault(e.getMessage());
        }
    }
    
    /**
    * @param urlHash the url hash
    * @param viewMode one of (VIEW_MODE_AS_PLAIN_TEXT = 1,
    * VIEW_MODE_AS_PARSED_TEXT = 2,
    * VIEW_MODE_AS_PARSED_SENTENCES = 3) [Source: ViewFile.java]
    * @return an xml document containing the url info.
    *
    * @throws AxisFault if the service could not be executed propery.
    */
   public Document urlInfoByHash(String urlHash, int viewMode) throws AxisFault {       
       try {
           // extracting the message context
           extractMessageContext(true);

           if (urlHash == null || urlHash.trim().equals("")) {
               throw new AxisFault("No Url-hash provided.");
           }
          
           if (viewMode < 1 || viewMode > 3) {
               viewMode = 2;
           }
           
           String viewModeStr = "sentences";
           if (viewMode == 1) viewModeStr = "plain";
           else if (viewMode == 2) viewModeStr = "parsed";
           else if (viewMode == 3) viewModeStr = "sentences";

          
           // setting the properties
           final serverObjects args = new serverObjects();
           args.put("urlHash",urlHash);
           args.put("viewMode",viewModeStr);
          
           // generating the template containing the url info
           byte[] result = writeTemplate(TEMPLATE_URLINFO, args);
          
           // sending back the result to the client
           return this.convertContentToXML(result);
       } catch (Exception e)  {
           throw new AxisFault(e.getMessage());
       }
   }    
   
   public Document snippet(String url, String searchString) throws AxisFault {
       try {
           if (url == null || url.trim().equals("")) throw new AxisFault("No url provided.");
           if (searchString == null || searchString.trim().equals("")) throw new AxisFault("No search string provided.");
           
           // extracting the message context
           extractMessageContext(false);
           
           // setting the properties
           final serverObjects args = new serverObjects();
           args.put("url",url);
           args.put("search",searchString);      
           
           // generating the template containing the url info
           byte[] result = writeTemplate(TEMPLATE_SNIPPET, args);     
           
           // sending back the result to the client
           return this.convertContentToXML(result);           
           
       } catch (Exception e)  {
           throw new AxisFault(e.getMessage());
       }           
   }

   /**
    * Returns the OpenSearch-Description of this peer
    * @return a XML document of the following format:
    * <pre>
	* &lt;?xml version="1.0" encoding="UTF-8"?&gt;
	* &lt;OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/"&gt;
	*   &lt;ShortName&gt;YaCy/peerName&lt;/ShortName&gt;
	*   &lt;LongName&gt;YaCy.net - P2P WEB SEARCH&lt;/LongName&gt;
	*   &lt;Image type="image/gif"&gt;http://ip-address:port/env/grafics/yacy.gif&lt;/Image&gt;
	*   &lt;Image type="image/vnd.microsoft.icon"&gt;http://ip-address:port/env/grafics/yacy.ico&lt;/Image&gt;
	*   &lt;Language&gt;en-us&lt;/Language&gt;
	*   &lt;OutputEncoding&gt;UTF-8&lt;/OutputEncoding&gt;
	*   &lt;InputEncoding&gt;UTF-8&lt;/InputEncoding&gt;
	*   &lt;AdultContent&gt;true&lt;/AdultContent&gt;
	*   &lt;Description&gt;YaCy is a open-source GPL-licensed software that can be used for stand-alone search engine installations or as a client for a multi-user P2P-based web indexing cluster. This is the access to peer 'peername'.&lt;/Description&gt;
	*   &lt;Url type="application/rss+xml" template="http://ip-address:port/yacysearch.rss?search={searchTerms}&amp;Enter=Search" /&gt;
	*   &lt;Developer&gt;See http://developer.berlios.de/projects/yacy/&lt;/Developer&gt;
	*   &lt;Query role="example" searchTerms="yacy" /&gt;
	*   &lt;Tags&gt;YaCy P2P Web Search&lt;/Tags&gt;
	*   &lt;Contact&gt;See http://ip-address:port/ViewProfile.html?hash=localhash&lt;/Contact&gt;
	*   &lt;Attribution&gt;YaCy Software &amp;copy; 2004-2006 by Michael Christen et al., YaCy.net; Content: ask peer owner&lt;/Attribution&gt;
	*   &lt;SyndicationRight&gt;open&lt;/SyndicationRight&gt;
	* &lt;/OpenSearchDescription&gt;
	* </pre>
    * @throws Exception
    */
   public Document getOpenSearchDescription() throws Exception {
       // extracting the message context
       extractMessageContext(false);          	
       
       // generating the template containing the network status information
       byte[] result = writeTemplate(TEMPLATE_OPENSEARCH, new serverObjects());
       
       // sending back the result to the client
       return this.convertContentToXML(result);   	   
   }

}
