//Thumbnail.java
//------------
// part of YACY
//
// (C) 2007 Alexander Schier
//
// last change: $LastChangedDate:  $ by $LastChangedBy: $
// $LastChangedRevision: $
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class Thumbnail{
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        servletProperties prop = new servletProperties();
        String command=env.getConfig("thumbnailProgram", "");
        if(command.equals("")||post==null||!post.containsKey("url")){
            prop.put("image", "thumbnail cannot be generated"); //TODO: put a "thumbnail not possible" image.
            return prop;
        }
        String[] cmdline=new String[3];
        cmdline[0]=env.getConfig("thumbnailProgram", "");
        cmdline[1]=post.get("url", "");
        plasmaSwitchboard sb=plasmaSwitchboard.getSwitchboard();
        File path=new File(sb.workPath, plasmaURL.urlHash(cmdline[1])+".png");
        cmdline[2]=path.getAbsolutePath();//does not contain an extension!
        try {
            Runtime.getRuntime().exec(cmdline);
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            String line;
            StringBuffer image=new StringBuffer();
            while((line=br.readLine())!=null){
                image.append(line);
            }
            //path.delete(); //we do not cache, yet.
            prop.put("image", image.toString());
        } catch (IOException e) {
            prop.put("image", "error creating thumbnail");//TODO: put a "thumbnail error" image.
        }
		httpHeader out_header=new httpHeader();
		out_header.put(httpHeader.CONTENT_TYPE, "image/png");
		prop.setOutgoingHeader(out_header);
        return prop;
    }
}
