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
import java.util.Date;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.ImageParser;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class cytag {
    
    public static Image respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        
        final Switchboard sb = (Switchboard)env;

        // harvest request information
        StringBuilder connect = new StringBuilder();
        connect.append('{');
        addJSON(connect, "time", DateFormatter.formatShortMilliSecond(new Date()));
        addJSON(connect, "trail", header.referer().toNormalform(false, false));
        addJSON(connect, "nick",  (post == null) ? "" : post.get("nick", ""));
        addJSON(connect, "tag",   (post == null) ? "" : post.get("tag", ""));
        addJSON(connect, "icon",  (post == null) ? "" : post.get("icon", ""));
        addJSON(connect, "ip",    header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, ""));
        addJSON(connect, "agent", header.get("User-Agent", ""));
        connect.append('}');
        
        sb.trail.add(connect.toString());
        //Log.logInfo("CYTAG", "catched trail - " + connect.toString());
        
        String defaultimage = "redpillmini.png";
        if (post != null && post.get("icon", "").equals("invisible")) defaultimage = "invisible.png";
        File iconfile = new File(sb.getRootPath(), "/htroot/env/grafics/" + defaultimage);
        
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
    
    private static final void addJSON(StringBuilder sb, String k, String v) {
        if (sb.length() > 2) sb.append(',');
        sb.append('\"');
        sb.append(k);
        sb.append("\":\"");
        sb.append(CharacterCoding.unicode2xml(v, true));
        sb.append('\"');
    }
}
