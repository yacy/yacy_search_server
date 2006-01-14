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
        wikiCode wikiTransformer = new wikiCode(switchboard);

        String action = ((post == null) ? "list" : post.get("action", "list"));
        StringBuffer messages = new StringBuffer();
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
            messages.append("<table border=\"0\" cellpadding=\"2\" cellspacing=\"1\">");
            messages.append("<tr class=\"MenuHeader\"><td>Date</td><td>From</td><td>To</td><td>Subject</td><td>Action</td></tr>");
            try {
                Iterator i = switchboard.messageDB.keys("remote", true);
                String key;

                boolean dark = true;
                while (i.hasNext()) {
                    key = (String) i.next();
                    message = switchboard.messageDB.read(key);
                    messages.append("<tr class=\"TableCell").append((dark) ? "Dark" : "Light").append("\">");
                    messages.append("<td>").append(dateString(message.date())).append("</td>");
                    messages.append("<td>").append(message.author()).append("</td>");
                    messages.append("<td>").append(message.recipient()).append("</td>");
                    messages.append("<td>").append(wikiTransformer.transform(message.subject())).append("</td>");
                    messages.append("<td>");
                    messages.append("<a href=\"Messages_p.html?action=view&object=").append(key).append("\">view</a>&nbsp;/&nbsp;");
                    messages.append("<a href=\"MessageSend_p.html?hash=").append(message.authorHash()).append("&subject=Re: ").append(message.subject()).append("\">reply</a>&nbsp;/&nbsp;");
                    messages.append("<a href=\"Messages_p.html?action=delete&object=").append(key).append("\">delete</a></td></tr>");
                    dark = !dark;
                }
                messages.append("</table>");
            } catch (IOException e) {
                messages.append("I/O error reading message table: ").append(e.getMessage());
            }
        }

        if (action.equals("view")) {
            String key = post.get("object", "");
            message = switchboard.messageDB.read(key);
            messages.append("<table border=\"0\" cellpadding=\"2\" cellspacing=\"1\">");
            messages.append("<tr><td class=\"MenuHeader\">From:</td><td class=\"MessageBackground\">").append(message.author()).append("</td></tr>");
            messages.append("<tr><td class=\"MenuHeader\">To:</td><td class=\"MessageBackground\">").append(message.recipient()).append("</td></tr>");
            messages.append("<tr><td class=\"MenuHeader\">Send Date:</td><td class=\"MessageBackground\">").append(dateString(message.date())).append("</td></tr>");
            messages.append("<tr><td class=\"MenuHeader\">Subject:</td><td class=\"MessageBackground\">").append(wikiTransformer.transform(message.subject())).append("</td></tr>");
            String theMessage = null;
            try {
                theMessage = new String(message.message(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // can not happen, because UTF-8 must be supported by every JVM
            }
            messages.append("<tr><td class=\"MessageBackground\" colspan=\"2\">").append(wikiTransformer.transform(theMessage)).append("</td></tr>");
            messages.append("<tr><td class=\"MenuHeader\">Action:</td>");
            messages.append("<td class=\"MessageBackground\">");
            messages.append("<a href=\"Messages_p.html\">inbox</a>&nbsp;/&nbsp;");
            messages.append("<a href=\"MessageSend_p.html?hash=").append(message.authorHash());
            messages.append("&subject=Re: ").append(message.subject()).append("\">reply</a>&nbsp;/&nbsp;");
            messages.append("<a href=\"Messages_p.html?action=delete&object=").append(key).append("\">delete</a>");
            messages.append("</td></tr></table>");
        }

        prop.put("messages", messages.toString());

        // return rewrite properties
        return prop;
    }
}
