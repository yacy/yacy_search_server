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

import org.apache.axis.AxisFault;

import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;

public class CrawlService extends AbstractService {
    
    /**
     * Constant: template for crawling
     */    
    private static final String TEMPLATE_CRAWLING = "IndexCreate_p.html";    
    
    /**
     * Service used start a new crawling job using the default settings for crawling
     * 
     * @return returns the http status page containing the crawling properties to the user
     * TODO: creating an extra xml template that can be send back to the client. 
     * 
     * @throws AxisFault if the service could not be executed propery. 
     */    
    public String crawling(String crawlingURL) throws AxisFault {
        try {
            // extracting the message context
            extractMessageContext(true);  
            
            // setting the crawling properties
            serverObjects args = new serverObjects();
            args.put("crawlingQ","on");
            args.put("xsstopw","on");
            args.put("crawlOrder","on");
            args.put("crawlingstart","Start New Crawl");
            args.put("crawlingDepth","2");
            args.put("crawlingFilter",".*");
            args.put("storeHTCache","on");
            args.put("localIndexing","on");            
            args.put("crawlingURL",crawlingURL);            
            
            // triggering the crawling
            byte[] result = writeTemplate(TEMPLATE_CRAWLING, args);
            
            // sending back the crawling status page to the user
            return new String(result,"UTF-8");
        } catch (Exception e) {
            throw new AxisFault(e.getMessage());
        }        
    }
}
