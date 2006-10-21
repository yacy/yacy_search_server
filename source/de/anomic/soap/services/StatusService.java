//StatusService.java 
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

import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;

public class StatusService extends AbstractService {
    
	/* =====================================================================
	 * Used XML Templates
	 * ===================================================================== */	
    /**
     * Constant: template for the network status page
     */    
    private static final String TEMPLATE_NETWORK_XML = "Network.xml";    
    private static final String TEMPLATE_QUEUES_XML = "xml/queues_p.xml";
    
    
    /**
     * Service used to query the network properties
     * @throws AxisFault if the service could not be executed propery. 
     */
    public Document network() throws AxisFault {
        try {
            // extracting the message context
            extractMessageContext(false);  
            
            // generating the template containing the network status information
            byte[] result = writeTemplate(TEMPLATE_NETWORK_XML, new serverObjects());
            
            // sending back the result to the client
            return this.convertContentToXML(result);
        } catch (Exception e) {
            throw new AxisFault(e.getMessage());
        }
    }
    
    
	/**
	 * Returns the current status of the following queues
	 * <ul>
	 * 	<li>Indexing Queue</li>
	 * 	<li>Loader Queue</li> 
	 * 	<li>Local Crawling Queue </li>
	 *  <li>Remote Triggered Crawling Queue</li> 
	 * </ul>
	 * @param localqueueCount the amount of items that should be returned. If this is <code>null</code> 10 items will be returned
	 * @param loaderqueueCount the amount of items that should be returned. This parameter will be ignored at the moment
	 * @param localcrawlerqueueCount the amount of items that should be returned. This parameter will be ignored at the moment
	 * @param remotecrawlerqueueCount the amount of items that should be returned. This parameter will be ignored at the moment
	 * @return a XML document containing the status information. For the detailed format, take a look into the template file
	 *         <code>htroot/xml/queues_p.xml</code>
	 *         
	 * @throws AxisFault if authentication failed
	 * @throws Exception on other unexpected errors
	 * 
	 * @since 2835
	 */
    public Document getQueueStatus(
    		Integer localqueueCount,
    		Integer loaderqueueCount,
    		Integer localcrawlerqueueCount,
    		Integer remotecrawlerqueueCount    		
    ) throws Exception {
        // extracting the message context
        extractMessageContext(true);      
        
        // passing parameters to servlet
        serverObjects input = new serverObjects();        
        if (localqueueCount != null) input.put("num",localqueueCount.toString());
        //if (loaderqueueCount != null) input.put("num",loaderqueueCount.toString());
        //if (localcrawlerqueueCount != null) input.put("num",localcrawlerqueueCount.toString());
        //if (remotecrawlerqueueCount != null) input.put("num",remotecrawlerqueueCount.toString());
        
        
        // generating the template containing the network status information
        byte[] result = writeTemplate(TEMPLATE_QUEUES_XML, input);
        
        // sending back the result to the client
        return this.convertContentToXML(result);        
    }
}
