// RespourceInfoFactory.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
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
