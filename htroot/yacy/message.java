// message.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
// javac -classpath .:../../classes message.java
// if the shell's current path is HTROOT/yacy

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import de.anomic.data.messageBoard;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class message {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static String dateString(Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch ss) {
        if (post == null || ss == null) { return null; }

        // return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) ss;
        serverObjects prop = new serverObjects();
        if (prop == null || sb == null) { return null; }

//      System.out.println("yacy/message:post=" + post.toString());

        String process = (String) post.get("process", "permission");
        String key =  (String) post.get("key", "");

        int messagesize = 10240;
        int attachmentsize = 0;

        prop.put("messagesize", "0");
        prop.put("attachmentsize", "0");

//      System.out.println("DEBUG yacy/message: message post values = " + post.toString());

        String youare = (String) post.get("youare", ""); // seed hash of the target peer, needed for network stability
        // check if we are the right target and requester has correct information about this peer
        if ((yacyCore.seedDB.mySeed == null) || (!(yacyCore.seedDB.mySeed.hash.equals(youare)))) {
            // this request has a wrong target
//          System.out.println("DEBUG yacy/message: authenticate failed");
            prop.put("response", "-1"); // request rejected
            return prop;
        }

        prop.put("messagesize", Integer.toString(messagesize));
        prop.put("attachmentsize", Integer.toString(attachmentsize));

        if (process.equals("permission")) {
            // permission: respond with accceptabeale message and attachment size
//          String iam = (String) post.get("iam", "");    // seed hash of requester
            prop.put("response", "Welcome to my peer!");
            // that's it!
        }

        if (process.equals("post")) {
            // post: post message to message board
            String otherSeedString = (String) post.get("myseed", "");
            if (otherSeedString.length() == 0) {
                prop.put("response", "-1"); // request rejected
                return prop;
            }
            //Date remoteTime = yacyCore.parseUniversalDate((String) post.get("mytime")); // read remote time
            yacySeed otherSeed = yacySeed.genRemoteSeed(otherSeedString, key);

            String subject = crypt.simpleDecode((String) post.get("subject", ""), key); // message's subject
            String message = crypt.simpleDecode((String) post.get("message", ""), key); // message body

            prop.put("response", "Thank you!");

            // save message
            messageBoard.entry msgEntry = null;
            sb.messageDB.write(msgEntry = sb.messageDB.newEntry(
                    "remote",
                    otherSeed.get("Name", "anonymous"), otherSeed.hash,
                    yacyCore.seedDB.mySeed.getName(), yacyCore.seedDB.mySeed.hash,
                    subject, message.getBytes()));

            messageForwardingViaEmail(ss, msgEntry);

            // finally write notification
            File notifierSource = new File(sb.getRootPath(), sb.getConfig("htRootPath","htroot") + "/env/grafics/message.gif");
            File notifierDest   = new File(sb.getRootPath(), sb.getConfig("htRootPath","htroot") + "/env/grafics/notifier.gif");
            try {
                serverFileUtils.copy(notifierSource, notifierDest);
            } catch (IOException e) {
                System.out.println("NEW MESSAGE ARRIVED! (error: " + e.getMessage() + ")");
            };
        }
//      System.out.println("respond = " + prop.toString());

        // return rewrite properties
        return prop;
    }

    /*
     * To: #[to]#
     From: #[from]#
     Subject: #[subject]#
     Date: #[date]#

     #[message]#
     */
    private static void messageForwardingViaEmail(serverSwitch env, messageBoard.entry msgEntry) {
        try {
            if (!Boolean.valueOf(env.getConfig("msgForwardingEnabled","false")).booleanValue()) return;

            // getting the sendmail configuration
            String sendMailStr = env.getConfig("msgForwardingCmd","/usr/bin/sendmail -t");
            String[] sendMail = sendMailStr.trim().split(" ");

            // getting the recipient address
            String sendMailTo = env.getConfig("msgForwardingTo","root@localhost").trim();

            // building the message text
            StringBuffer emailTxt = new StringBuffer();
            emailTxt.append("To: ")
            .append(sendMailTo)
            .append("\nFrom: ")
            .append("yacy@")
            .append(yacyCore.seedDB.mySeed.getName())
            .append("\nSubject: [YaCy] ")
            .append(msgEntry.subject())
            .append("\nDate: ")
            .append(msgEntry.date())
            .append("\n")
            .append("\nMessage from: ")
            .append(msgEntry.author())
            .append("/")
            .append(msgEntry.authorHash())
            .append("\nMessage to:   ")
            .append(msgEntry.recipient()) 
            .append("/")
            .append(msgEntry.recipientHash())
            .append("\nCategory:     ")
            .append(msgEntry.category())
            .append("\n===================================================================\n")
            .append(new String(msgEntry.message()));

            Process process=Runtime.getRuntime().exec(sendMail);
            PrintWriter email = new PrintWriter(process.getOutputStream());
            email.print(emailTxt.toString());
            email.close();                        
        } catch (Exception e) {
            yacyCore.log.logWarning("message: message forwarding via email failed. ",e);
        }

    }

    /*
     on 83 
     DEBUG: message post values = {youare=Ty2F86ekSWM5, key=pPQSZaXD, iam=WSjicAx1hRio, process=permission}
     von 93 wurde gesendet:
     DEBUG: PUT BODY=------------1090394265522
     Content-Disposition: form-data; name="youare"

     Ty2F86ekSWM5
     ------------1090394265522
     Content-Disposition: form-data; name="key"

     pPQSZaXD
     ------------1090394265522
     Content-Disposition: form-data; name="iam"

     WSjicAx1hRio
     ------------1090394265522
     Content-Disposition: form-data; name="process"

     permission
     ------------1090394265522


     on 93
     DEBUG: message post values = {youare=WSjicAx1hRio, key=YJZLwaNS, iam=Ty2F86ekSWM5, process=permission}

     */
}