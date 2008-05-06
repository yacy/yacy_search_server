//AbstractParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2007
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

package de.anomic.plasma.dbImport;

import java.util.HashMap;

import de.anomic.crawler.CrawlProfile;
import de.anomic.data.SitemapParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.yacy.yacyURL;

public class SitemapImporter extends AbstractImporter implements dbImporter {

	private SitemapParser parser = null;
	private yacyURL sitemapURL = null;
	
	public SitemapImporter(plasmaSwitchboard switchboard) {
		super("sitemap",switchboard);
	}

	public long getEstimatedTime() {
		long t = getElapsedTime();
		int p = getProcessingStatusPercent();
		return (p==0)?0:(t/p)*(100-p);
	}

	/**
	 * @see dbImporter#getJobName()
	 */
	public String getJobName() {
		return this.sitemapURL.toString();
	}

	/**
	 * @see dbImporter#getProcessingStatusPercent()
	 */
	public int getProcessingStatusPercent() {
		if (this.parser == null) return 0;
		
		long total = this.parser.getTotalLength();
		long processed = this.parser.getProcessedLength();
		
		if (total <= 1) return 0;		
		return (int) ((processed*100)/ total);
	}

	/**
	 * @see dbImporter#getStatus()
	 */
	public String getStatus() {
        StringBuffer theStatus = new StringBuffer();
        
        theStatus.append("#URLs=").append((this.parser==null)?0:this.parser.getUrlcount());
        
        return theStatus.toString();
	}

	/**
	 * @see dbImporter#init(HashMap)
	 * @see AbstractImporter#init(HashMap)
	 */
	public void init(plasmaSwitchboard switchboard, int cacheSize) throws ImporterException {
        super.init();
	}
	
	public void initSitemap(yacyURL sitemapURL, CrawlProfile.entry profileEntry) throws ImporterException {
        try {
            // getting the sitemap URL
            this.sitemapURL = sitemapURL;
            
            // creating the sitemap parser
            this.parser = new SitemapParser(this.sb,this.sitemapURL, profileEntry);
        } catch (Exception e) {
            throw new ImporterException("Unable to initialize Importer",e);
        }
    }
	
	public void run() {
		try {
			this.parser.parse();
		} finally {
			this.globalEnd = System.currentTimeMillis();
			this.sb.dbImportManager.finishedJobs.add(this);			
		}
	}
}
