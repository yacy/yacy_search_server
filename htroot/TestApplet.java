// TestApplet.java 
// -----------------------
// (C) 2006 by Alexander Schier
// part of YACY
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
// Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.http.httpTemplate;
import de.anomic.http.httpdFileHandler;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class TestApplet {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
    serverObjects prop = new serverObjects();
    plasmaSwitchboard sb = (plasmaSwitchboard) env;
    httpdFileHandler filehandler=new httpdFileHandler(sb);
    
    if(post== null || !post.containsKey("url")){
        prop.put("mode", "0");
        return prop;
    }
    serverObjects args=new serverObjects();
    
    String[] lines=((String)post.get("arguments")).split("\n");
    String[] pair;
    for(int i=0;i<lines.length;i++){
        pair=lines[i].split("=");
        if(pair.length==2){
            args.put(pair[0], pair[1]);
        }
    }
    
    prop.put("mode", "1");
    File templatefile=filehandler.getOverlayedFile((String)post.get("url"));
    File classfile=filehandler.getOverlayedClass((String)post.get("url"));
    httpHeader header2=new httpHeader();
    header2.put("CLIENTIP", "127.0.0.1");
    header2.put("PATH", (String)post.get("url"));
    serverObjects tp=null;
    try {
        if(classfile==null || !classfile.exists()){
            prop.put("mode_templates", "classfile does not exist");
            return prop;
        }else{
            tp=(serverObjects)filehandler.invokeServlet(classfile, header2, args);
        }
    }
    catch (IllegalArgumentException e) {}
    catch (IllegalAccessException e) {}
    catch (InvocationTargetException e) {}
    if(tp==null){
        prop.put("templates", "some error occured.");
        prop.put("structure", "");
        prop.put("text", "");
        return prop;
    }
    Iterator it=tp.keySet().iterator();
    StringBuffer tmp=new StringBuffer();
    String key="";
    while(it.hasNext()){
        key=(String)it.next();
        tmp.append(key).append("=").append(tp.get(key)).append("\n");
    }
    prop.put("mode_templates", tmp.toString());
    FileInputStream fis=null;
    try {
        fis=new FileInputStream(templatefile);

        serverByteBuffer o=new serverByteBuffer();
        byte[] structure=httpTemplate.writeTemplate(fis, (OutputStream)o, tp, "-UNRESOLVED_PATTERN-".getBytes("UTF-8"));
        prop.put("mode_structure", structure);
        prop.put("mode_text", o.toString());
        return prop;
    }
    catch (FileNotFoundException e) {}
    catch (UnsupportedEncodingException e) {}
    catch (IOException e) {}
    
    prop.put("mode_text", "could not finish correctly"); //very informative errormessage
    return prop;

    }

}
