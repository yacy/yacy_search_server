// httpClientGetMethod.java
// (C) 2009 by David Wieditz; lotus@users.berlios.de
// first published 13.7.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package de.anomic.http.client;

import java.io.IOException;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;

/**
 * this class implements the ability for a maxfilesize
 * @author lotus
 *
 */
public class ClientGetMethod extends GetMethod {
    
	private long maxfilesize = Long.MAX_VALUE;
	
	public ClientGetMethod(String uri, long maxfilesize) {
		super(uri);
		if (maxfilesize > 0) this.maxfilesize = maxfilesize;
	}
	
	@Override
    protected void readResponseHeaders(HttpState state, HttpConnection conn) throws IOException, HttpException {
		super.readResponseHeaders(state, conn);
		
		// already processing the header to be able to throw an exception
        Header contentlengthHeader = getResponseHeader("content-length");
        long contentlength = 0;
        if (contentlengthHeader != null) {
        	try { contentlength = Long.parseLong(contentlengthHeader.getValue()); } catch (NumberFormatException e) {	}
        }
        if (contentlength > maxfilesize) {
            throw new IOException("Content-Length " + contentlength + " larger than maxfilesize " + maxfilesize);
		}
    }	
}
