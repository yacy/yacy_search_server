// RespourceInfoFactory.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
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



package de.anomic.plasma.cache;

import java.lang.reflect.Constructor;
import java.util.Map;

import de.anomic.yacy.yacyURL;


public class ResourceInfoFactory {
    public IResourceInfo buildResourceInfoObj(
            yacyURL resourceURL,
            Map<String, String> resourceMetadata
    ) throws UnsupportedProtocolException, IllegalAccessException {
        
        String protocString = resourceURL.getProtocol();
        
        // TODO: remove this
        if (protocString.equals("https")) protocString = "http";
        
        // the full qualified class name
        final String className = this.getClass().getPackage().getName() + "." + protocString + ".ResourceInfo";
        
        try {
            // loading class by name
            final Class<?> moduleClass = Class.forName(className);
            
            // getting the constructor
            final Constructor<?> classConstructor = moduleClass.getConstructor( new Class[] { 
                    yacyURL.class,
                    Map.class
            } );
            
            // instantiating class
            final IResourceInfo infoObject = (IResourceInfo) classConstructor.newInstance(new Object[] {
                  resourceURL,
                  resourceMetadata
            });
            
            // return the newly created object
            return infoObject;
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else if (e instanceof ClassNotFoundException) {
                throw new UnsupportedProtocolException(protocString, e);
            } else if (e instanceof IllegalAccessException) {
                throw (IllegalAccessException)e;
            } else {
                e.printStackTrace();
                return null;
            }
        }
    }
}
