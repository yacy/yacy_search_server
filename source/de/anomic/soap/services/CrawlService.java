//CrawlService.java 
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

import javax.xml.parsers.ParserConfigurationException;

import org.apache.axis.AxisFault;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;

public class CrawlService extends AbstractService {
    
    private static final String GLOBALCRAWLTRIGGER = "globalcrawltrigger";
	private static final String REMOTETRIGGEREDCRAWL = "remotetriggeredcrawl";
	private static final String LOCAL_CRAWL = "localCrawl";
	private static final String CRAWL_STATE = "crawlState";
	
	
	/**
     * Constant: template for crawling
     */    
    private static final String TEMPLATE_CRAWLING = "QuickCrawlLink_p.xml";    
    
    /**
     * Function to crawl a single link with depth <code>0</code>
     */
    public Document crawlSingleUrl(String crawlingURL) throws AxisFault {
        return this.crawling(crawlingURL, "CRAWLING-ROOT", new Integer(0), ".*", Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE, null, Boolean.TRUE);
    }
    
    public Document crawling(
            String crawlingURL, 
            String crawljobTitel,
            Integer crawlingDepth,
            String crawlingFilter,
            Boolean indexText,
            Boolean indexMedia,
            Boolean crawlingQ,
            Boolean storeHTCache,
            Boolean crawlOrder,
            String crawlOrderIntention,
            Boolean xsstopw            
    ) throws AxisFault {
        try {
            // extracting the message context
            extractMessageContext(true);  
            
            // setting the crawling properties
            serverObjects args = new serverObjects();      
            args.put("url",crawlingURL);    
            if (crawljobTitel != null && crawljobTitel.length() > 0) 
                args.put("title",crawljobTitel);
            if (crawlingFilter != null && crawlingFilter.length() > 0) 
                args.put("crawlingFilter",crawlingFilter); 
            if (crawlingDepth != null && crawlingDepth.intValue() > 0) 
                args.put("crawlingDepth",crawlingDepth.toString());   
            if (indexText != null) 
                args.put("indexText",indexText.booleanValue()?"on":"off");               
            if (indexMedia != null) 
                args.put("indexMedia",indexMedia.booleanValue()?"on":"off");               
            if (crawlingQ != null) 
                args.put("crawlingQ",crawlingQ.booleanValue()?"on":"off");              
            if (storeHTCache != null) 
                args.put("storeHTCache",storeHTCache.booleanValue()?"on":"off");             
            if (crawlOrder != null) 
                args.put("crawlOrder",crawlOrder.booleanValue()?"on":"off");      
            if (crawlOrderIntention != null) 
                args.put("intention",crawlOrderIntention);               
            if (xsstopw != null) 
                args.put("xsstopw",xsstopw.booleanValue()?"on":"off");               
            
            // triggering the crawling
            byte[] result = this.serverContext.writeTemplate(TEMPLATE_CRAWLING, args, this.requestHeader);
            
            // sending back the result to the client
            return this.convertContentToXML(result);
        } catch (Exception e) {
            throw new AxisFault(e.getMessage());
        }             
    }
    
    /**
     * Function to pause crawling of local crawl jobs, remote crawl jobs and sending of remote crawl job triggers
     * @throws AxisFault 
     */
    public void pauseCrawling() throws AxisFault {
        this.pauseResumeCrawling(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);
    }
    
    /**
     * Function to resume crawling of local crawl jobs, remote crawl jobs and sending of remote crawl job triggers
     * @throws AxisFault 
     */
    public void resumeCrawling() throws AxisFault {
        this.pauseResumeCrawling(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
    }
    
    /**
     * Function to pause or resume crawling of local crawl jobs, remote crawl jobs and sending of remote crawl job triggers
     * @param localCrawl if <code>null</code> current status is not changed. pause local crawls if <code>true</code> or
     *        resumes local crawls if <code>false</code>
     * @param remoteTriggeredCrawl if <code>null</code> current status is not changed. pause remote crawls if <code>true</code> or
     *        resumes remote crawls if <code>false</code>
     * @param globalCrawlTrigger if <code>null</code> current status is not changed. stops sending of global crawl triggers to other peers if <code>true</code> or
     *        resumes sending of global crawl triggers if <code>false</code>
     * @throws AxisFault 
     */
    public void pauseResumeCrawling(Boolean localCrawl, Boolean remoteTriggeredCrawl, Boolean globalCrawlTrigger) throws AxisFault {
        // extracting the message context
        extractMessageContext(true);         
        
        if (localCrawl != null) {
            if (localCrawl.booleanValue()) {
                ((plasmaSwitchboard)this.switchboard).pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
            } else {
                ((plasmaSwitchboard)this.switchboard).continueCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
            }
        }
        
        if (remoteTriggeredCrawl != null) {
            if (remoteTriggeredCrawl.booleanValue()) {
                ((plasmaSwitchboard)this.switchboard).pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            } else {
                ((plasmaSwitchboard)this.switchboard).continueCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            }
        }
        
        if (globalCrawlTrigger != null) {
            if (globalCrawlTrigger.booleanValue()) {
                ((plasmaSwitchboard)this.switchboard).pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_GLOBAL_CRAWL_TRIGGER);
            } else {
                ((plasmaSwitchboard)this.switchboard).continueCrawlJob(plasmaSwitchboard.CRAWLJOB_GLOBAL_CRAWL_TRIGGER);
            }
        }        
    }
    
    /**
     * Function to query the current state of the following crawling queues:
     * <ul>
     * 	<li>local crawl jobs</li>
     * 	<li>remote crawl jobs</li>
     * 	<li>of remote crawl job triggers</li>
     * </ul>
     * @return returns a XML document in the following format
     * <pre>
     * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
     * &lt;crawlState&gt;
     * 	&lt;localCrawl&gt;true&lt;/localCrawl&gt;
     * 	&lt;remotetriggeredcrawl&gt;false&lt;/remotetriggeredcrawl&gt;
     * 	&lt;globalcrawltrigger&gt;false&lt;/globalcrawltrigger&gt;
     * &lt;/crawlState&gt;
     * </pre>
     * @throws AxisFault if authentication failed
     * @throws ParserConfigurationException if xml generation failed
     */
    public Document getCrawlPauseResumeState() throws AxisFault, ParserConfigurationException {
    	
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);             
    	plasmaSwitchboard sb = (plasmaSwitchboard)this.switchboard;
        
        // creating XML document
        Element xmlElement = null;
    	Document xmlDoc = createNewXMLDocument(CRAWL_STATE);
    	Element xmlRoot = xmlDoc.getDocumentElement();        
    	
    	xmlElement = xmlDoc.createElement(LOCAL_CRAWL);
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(sb.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL))));
    	xmlRoot.appendChild(xmlElement);    	
    	
    	xmlElement = xmlDoc.createElement(REMOTETRIGGEREDCRAWL);
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(sb.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL))));
    	xmlRoot.appendChild(xmlElement);       	
    	
    	xmlElement = xmlDoc.createElement(GLOBALCRAWLTRIGGER);
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(sb.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_GLOBAL_CRAWL_TRIGGER))));
    	xmlRoot.appendChild(xmlElement);         	
    	
    	return xmlDoc;
    }    

}
