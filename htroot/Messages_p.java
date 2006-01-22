// Messages_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.06.2003
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

// You must compile this file with
// javac -classpath .:../Classes Message.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import de.anomic.data.messageBoard;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.data.wikiCode;

public class Messages_p {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    public static String dateString(Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        prop.put("mode", 0);
        prop.put("mode_error", 0);
        wikiCode wikiTransformer = new wikiCode(switchboard);

        String action = ((post == null) ? "list" : post.get("action", "list"));
        messageBoard.entry message;

        // first reset notification
        File notifierSource = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath", "htroot") + "/env/grafics/empty.gif");
        File notifierDest = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath", "htroot") + "/env/grafics/notifier.gif");
        try {
            serverFileUtils.copy(notifierSource, notifierDest);
        } catch (IOException e) {
        }

        if (action.equals("delete")) {
            String key = post.get("object", "");
            switchboard.messageDB.remove(key);
            action = "list";
        }

        if (action.equals("list")) {
            prop.put("mode", 0); //list
            try {
                Iterator i = switchboard.messageDB.keys("remote", true);
                String key;

                boolean dark = true;
                int count=0;
                while (i.hasNext()) {
                    key = (String) i.next();
                    message = switchboard.messageDB.read(key);
                    prop.put("mode_messages_"+count+"_dark", ((dark) ? 1 : 0) );
                    prop.put("mode_messages_"+count+"_date", dateString(message.date()));
                    prop.put("mode_messages_"+count+"_from", message.author());
                    prop.put("mode_messages_"+count+"_to", message.recipient());
                    //prop.put("mode_messages_"+count+"_subject", wikiTransformer.transform(message.subject()));
                    //TODO: not needed, when all templates will be cleaned via replaceHTML
                    prop.put("mode_messages_"+count+"_subject", wikiCode.replaceHTML(message.subject()));
                    prop.put("mode_messages_"+count+"_key", key);
                    prop.put("mode_messages_"+count+"_hash", message.authorHash());
                    dark = !dark;
                    count++;
                }
                prop.put("mode_messages", count);
            } catch (IOException e) {
                prop.put("mode_error", 1);//I/O error reading message table
                prop.put("mode_error_message", e.getMessage());
            }
        }

        if (action.equals("view")) {
            prop.put("mode", 1); //view
            String key = post.get("object", "");
            message = switchboard.messageDB.read(key);
            
            prop.put("mode_from", message.author());
            prop.put("mode_to", message.recipient());
            prop.put("mode_date", dateString(message.date()));
            //prop.put("mode_messages_subject", wikiTransformer.transform(message.subject()));
            //TODO: not needed, when all templates will be cleaned via replaceHTML
            prop.put("mode_subject", wikiCode.replaceHTML(message.subject()));
            String theMessage = null;
            try {
                theMessage = new String(message.message(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // can not happen, because UTF-8 must be supported by every JVM
            }
            prop.put("mode_message", wikiTransformer.transform(theMessage));
            prop.put("mode_hash", message.authorHash());
            prop.put("mode_key", key);
        }

        // return rewrite properties
        return prop;
    }
}
