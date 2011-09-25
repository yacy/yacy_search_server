/**
 *  ContentDomain
 *  Copyright 2011 by Michael Christen
 *  First released 18.05.2011 at http://yacy.net
 *  
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.search.snippet;

public enum ContentDomain {
    
    ALL(-1),
    TEXT(0),
    IMAGE(1),
    AUDIO(2),
    VIDEO(3),
    APP(4);
    
    private int code;
    
    ContentDomain(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return this.code;
    }
    
    public static ContentDomain contentdomParser(final String dom) {
        if ("text".equals(dom)) return TEXT;
        else if ("image".equals(dom)) return IMAGE;
        else if ("audio".equals(dom)) return AUDIO;
        else if ("video".equals(dom)) return VIDEO;
        else if ("app".equals(dom)) return APP;
        return TEXT;
    }
    
    @Override
    public String toString() {
        if (this == TEXT) return "text";
        else if (this == IMAGE) return "image";
        else if (this == AUDIO) return "audio";
        else if (this == VIDEO) return "video";
        else if (this == APP) return "app";
        return "text";
    }
}
