// index.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate: 2005-12-07 01:36:05 +0100 $
// $LastChangedRevision: 1177 $
// $LastChangedBy: orbiter $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.
//
// You must compile this file with
// javac -classpath .:../classes index.java
// if the shell's current path is HTROOT

import java.util.Iterator;
import java.util.Map.Entry;

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class CookieTest {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
      

        // case if no values are requested
        if (post == null || env == null) {

            // we create empty entries for template strings
            final serverObjects prop = new serverObjects();
            return prop;
        }
        
        final servletProperties prop = new servletProperties();
        if(post.containsKey("act")&&post.get("act").equals("clear_cookie"))
        {
         httpHeader outgoingHeader=new httpHeader();
        	Iterator it = header.entrySet().iterator();
        	while(it.hasNext())
        	{
        		java.util.Map.Entry e = (Entry) it.next();
        		if(e.getKey().equals("Cookie"))
        		{
        			String coockie[]=e.getValue().toString().split(";");
        			for(int i=0;i<coockie.length;i++)
        			{
        				String nameValue[]=coockie[i].split("=");
        				outgoingHeader.setCookie(nameValue[0].trim(),nameValue.length>1?(nameValue[1].trim()):"","Thu, 01-Jan-99 00:00:01 GMT");	
        			}
        		}

        		
        		
        	}
        	
         prop.setOutgoingHeader(outgoingHeader);
         prop.put("coockiesout",0);
         //header.
         
        }
        else if(post.containsKey("act")&&post.get("act").equals("set_cookie"))
       {
        String cookieName = post.get("cookie_name").toString().trim();
        String cookieValue = post.get("cookie_value").toString().trim();
        httpHeader outgoingHeader=new httpHeader();
        
        outgoingHeader.setCookie(cookieName,cookieValue);
        prop.setOutgoingHeader(outgoingHeader);
        prop.put("cookiesin",1);
        prop.put("cookiesin_0_name",cookieName);
        prop.put("cookiesin_0_value",cookieValue);
        //header.
        
       }

        prop.put("cookiesout",1);
        prop.put("cookiesout_0_string", header.getHeaderCookies().replaceAll(";",";<br />"));
        return prop;
    }



}
