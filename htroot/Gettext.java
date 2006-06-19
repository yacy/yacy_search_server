import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.data.gettext;
import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

//Gettext.java
//------------
// part of YACY
//
// (C) 2006 Alexander Schier
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

public class Gettext{
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        String htRootPath = env.getConfig("htRootPath", "htroot");
        File sourceDir = new File(env.getRootPath(), htRootPath);
        ArrayList list = gettext.createGettextRecursive(sourceDir, "html,template,inc", "locale");
        Iterator it=list.iterator();
        String out="";
        while(it.hasNext()){
            out+=(String)it.next()+"\n";
        }
        //this does not work
        /*httpHeader outheader=new httpHeader();
        outheader.put("Content-Type", "text/plain");
        prop.setOutgoingHeader(outheader);*/
        prop.put("gettext", out);
        
        return prop;
    }
}