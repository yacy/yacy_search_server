// ServiceList.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Martin Thelian
//
// $LastChangedDate: 2007-02-24 13:56:32 +0000 (Sa, 24 Feb 2007) $
// $LastChangedRevision: 3391 $
// $LastChangedBy: karlchenofhell $
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

// You must compile this file with
// javac -classpath .:../classes Blacklist_p.java
// if the shell's current path is HTROOT


package soap;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.axis.AxisEngine;
import org.apache.axis.ConfigurationException;
import org.apache.axis.description.OperationDesc;
import org.apache.axis.description.ServiceDesc;

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ServiceList {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) throws ConfigurationException {
        
        serverObjects prop = new serverObjects();
        
        // getting the SOAP engine
        AxisEngine engine = (AxisEngine) post.get("SOAP.engine");
        
        // loop through the deployed services
        int i = 0;
        boolean dark = true;
        Iterator serviceIter = engine.getConfig().getDeployedServices();
        while (serviceIter.hasNext()) {
        	// getting the service description
            ServiceDesc serviceDescription = (ServiceDesc)serviceIter.next();
            prop.put("services_" + i + "_name",serviceDescription.getName());
            prop.put("services_" + i + "_style",serviceDescription.getStyle());
            prop.put("services_" + i + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
            
            // loop through the methods of this service
            int j = 0;
            ArrayList operations = serviceDescription.getOperations();
            while (j < operations.size()) {
                OperationDesc op = (OperationDesc)operations.get(j);
                
                prop.put("services_" + i + "_methods_" + j + "_name",op.getName());
                prop.put("services_" + i + "_methods_" + j + "_method",op.getMethod());
                j++;
            }
            prop.put("services_" + i + "_methods",j);
            
            i++;
        }
        prop.put("services",i);
        
        return prop;
    }
    
}
