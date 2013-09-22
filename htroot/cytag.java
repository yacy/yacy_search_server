// cytag.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 06.02.2009 on http://www.yacy.net
//
// This is a part of YaCy.
// The Software shall be used for Good, not Evil.
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

import java.awt.Image;
import java.io.File;
import java.io.IOException;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.ImageParser;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class cytag {
    
    public static Image respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        
        final Switchboard sb = (Switchboard)env;
        final MultiProtocolURL referer = header.referer();
        
        // harvest request information
        StringBuilder connect = new StringBuilder();
        connect.append('{');
        appendJSON(connect, "time", GenericFormatter.SHORT_MILSEC_FORMATTER.format());
        appendJSON(connect, "trail", (referer == null) ? "" : referer.toNormalform(false));
        appendJSON(connect, "nick",  (post == null) ? "" : post.get("nick", ""));
        appendJSON(connect, "tag",   (post == null) ? "" : post.get("tag", ""));
        appendJSON(connect, "icon",  (post == null) ? "" : post.get("icon", ""));
        appendJSON(connect, "ip",    header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, ""));
        appendJSON(connect, "agent", header.get("User-Agent", ""));
        connect.append('}');
        
        if (sb.trail.size() >= 100) sb.trail.remove();
        sb.trail.add(connect.toString());
        //Log.logInfo("CYTAG", "catched trail - " + connect.toString());
        
        final String defaultimage;
        if (post != null && post.get("icon", "").equals("invisible")) {
            defaultimage = "invisible.png";
        } else {
            defaultimage = "redpillmini.png";
        }
        final File iconfile = new File(sb.getAppPath(), "/htroot/env/grafics/" + defaultimage);
        
        byte[] imgb = null;
        try {
            imgb = FileUtils.read(iconfile);
        } catch (final IOException e) {
             return null;
        }
        if (imgb == null) return null;
        
        // read image
        final Image image = ImageParser.parse("cytag.png", imgb);

        return image;
    }
    
    private static final void appendJSON(final StringBuilder sb, final String key, final String value) {
        if (sb.length() > 2) sb.append(',');
        sb.append('\"');
        sb.append(key);
        sb.append("\":\"");
        sb.append(CharacterCoding.unicode2xml(value, true));
        sb.append('\"');
    }
}
