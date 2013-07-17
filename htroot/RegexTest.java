/**
 *  RegexTest
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.09.2011 at http://yacy.net
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

import java.util.regex.PatternSyntaxException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class RegexTest {
    
    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        String text = (post == null) ? "" : post.get("text", "");
        String regex = (post == null) ? ".*" : post.get("regex", ".*");
        String error = "";
        Boolean match = null;
        try {
            match = text.matches(regex);
        } catch (final PatternSyntaxException e) {
            error = e.getMessage();
        }
        
        final serverObjects prop = new serverObjects();
        
        prop.put("text", text);
        prop.put("regex", regex);
        prop.put("match", match == null ? 2 : (match.booleanValue() ? 1 : 0));
        prop.put("match_error", error);

        return prop;
    }

}
