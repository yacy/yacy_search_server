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

// You must compile this file with
// javac -classpath .:../../classes message.java
// if the shell's current path is HTROOT/yacy

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.anomic.data.messageBoard;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.crypt;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNetwork;
import de.anomic.yacy.yacySeed;

public final class message {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static String dateString(Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        if (post == null || env == null) { return null; }

        // return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        if (!yacyNetwork.authentifyRequest(post, env)) return prop;

        String process = post.get("process", "permission");
        String key =  post.get("key", "");

        int messagesize = 10240;
        int attachmentsize = 0;

        prop.put("messagesize", "0");
        prop.put("attachmentsize", "0");

        String youare = post.get("youare", ""); // seed hash of the target peer, needed for network stability
        // check if we are the right target and requester has correct information about this peer
        if ((sb.webIndex.seedDB.mySeed() == null) || (!(sb.webIndex.seedDB.mySeed().hash.equals(youare)))) {
            // this request has a wrong target
            prop.put("response", "-1"); // request rejected
            return prop;
        }

        if ((sb.isRobinsonMode()) &&
        	 (!((sb.isPublicRobinson()) ||
        	    (sb.isInMyCluster(header.get(httpHeader.CONNECTION_PROP_CLIENTIP)))))) {
            // if we are a robinson cluster, answer only if this client is known by our network definition
        	prop.put("response", "-1"); // request rejected
            return prop;
        }
        
        prop.put("messagesize", Integer.toString(messagesize));
        prop.put("attachmentsize", Integer.toString(attachmentsize));

        if (process.equals("permission")) {
            // permission: respond with acceptable message and attachment size
//          String iam = (String) post.get("iam", "");    // seed hash of requester
            prop.put("response", "Welcome to my peer!");
            // that's it!
        }

        if (process.equals("post")) {
            // post: post message to message board
            String otherSeedString = post.get("myseed", "");
            if (otherSeedString.length() == 0) {
                prop.put("response", "-1"); // request rejected
                return prop;
            }
            //Date remoteTime = yacyCore.parseUniversalDate((String) post.get(yacySeed.MYTIME)); // read remote time
            yacySeed otherSeed = yacySeed.genRemoteSeed(otherSeedString, key, false);

            String subject = crypt.simpleDecode(post.get("subject", ""), key); // message's subject
            String message = crypt.simpleDecode(post.get("message", ""), key); // message body
            if (subject == null || message == null) {
                prop.put("response", "-1"); // don't accept empty messages
                return prop;
            }
            message = message.trim();
            subject = subject.trim();
            if (subject.length() == 0 || message.length() == 0) {
                prop.put("response", "-1"); // don't accept empty messages
                return prop;
            }
            
            prop.put("response", "Thank you!");

            // save message
            messageBoard.entry msgEntry = null;
            byte[] mb;
            try {
                mb = message.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                mb = message.getBytes();
            }
            sb.messageDB.write(msgEntry = sb.messageDB.newEntry(
                    "remote",
                    otherSeed.get(yacySeed.NAME, "anonymous"), otherSeed.hash,
                    sb.webIndex.seedDB.mySeed().getName(), sb.webIndex.seedDB.mySeed().hash,
                    subject, mb));

            messageForwardingViaEmail(sb, msgEntry);

            // finally write notification
            File notifierSource = new File(sb.getRootPath(), sb.getConfig("htRootPath","htroot") + "/env/grafics/message.gif");
            File notifierDest   = new File(sb.getConfigPath("htDocsPath", "DATA/HTDOCS"), "notifier.gif");
            try {
                serverFileUtils.copy(notifierSource, notifierDest);
            } catch (IOException e) {
            	serverLog.logSevere("MESSAGE", "NEW MESSAGE ARRIVED! (error: " + e.getMessage() + ")");
              
            }
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
    private static void messageForwardingViaEmail(plasmaSwitchboard sb, messageBoard.entry msgEntry) {
        try {
            if (!Boolean.valueOf(sb.getConfig("msgForwardingEnabled","false")).booleanValue()) return;

            // getting the recipient address
            String sendMailTo = sb.getConfig("msgForwardingTo","root@localhost").trim();
			
            // getting the sendmail configuration
            String sendMailStr = sb.getConfig("msgForwardingCmd","/usr/bin/sendmail")+" "+sendMailTo;
            String[] sendMail = sendMailStr.trim().split(" ");

            // building the message text
            StringBuffer emailTxt = new StringBuffer();
            emailTxt.append("To: ")
            .append(sendMailTo)
            .append("\nFrom: ")
            .append("yacy@")
            .append(sb.webIndex.seedDB.mySeed().getName())
            .append("\nSubject: [YaCy] ")
            .append(msgEntry.subject().replace('\n', ' '))
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
            email.print(new String(emailTxt));
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
