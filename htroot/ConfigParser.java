// ConfigParser.p.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 13.07.2009
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

// You must compile this file with
// javac -classpath .:../Classes Settings_p.java
// if the shell's current path is HTROOT

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class ConfigParser {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        
        if (post != null) {
            if (!sb.verifyAuthentication(header, false)) {
                // force log-in
                prop.put("AUTHENTICATE", "admin log-in");
                return prop;
            }
            
            if (post.containsKey("parserSettings")) {
                post.remove("parserSettings");
                
                for (final Parser parser: TextParser.parsers()) {
                    for (final String mimeType: parser.supportedMimeTypes()) {
                        TextParser.grantMime(mimeType, "on".equals(post.get("mimename_" + mimeType, "")));
                    }
                }
                env.setConfig(SwitchboardConstants.PARSER_MIME_DENY, TextParser.getDenyMime());
            }
        }
        
        int i = 0;        
        for (final Parser parser: TextParser.parsers()) {
            prop.put("parser_" + i + "_name", parser.getName());
            
            int mimeIdx = 0;
            for (final String mimeType: parser.supportedMimeTypes()) {
                prop.put("parser_" + i + "_mime_" + mimeIdx + "_mimetype", mimeType);
                prop.put("parser_" + i + "_mime_" + mimeIdx + "_status", (TextParser.supportsMime(mimeType) == null) ? 1 : 0);
                mimeIdx++;
            }
            prop.put("parser_" + i + "_mime", mimeIdx);
            i++;
        }
        
        prop.put("parser", i);
        
        // return rewrite properties
        return prop;
    }
}
