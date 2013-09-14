// message.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
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
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.MessageBoard;
import net.yacy.peers.Network;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;

import com.google.common.io.Files;


public final class message {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
    public static String dateString(final Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        if (post == null || env == null) { return null; }

        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        if (!Protocol.authentifyRequest(post, env)) return prop;

        final String process = post.get("process", "permission");
        final String clientip = header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "<unknown>"); // read an artificial header addendum
        final InetAddress ias = Domains.dnsResolve(clientip);

        final int messagesize = 10240;
        final int attachmentsize = 0;

        prop.put("messagesize", "0");
        prop.put("attachmentsize", "0");

        final String youare = post.get("youare", ""); // seed hash of the target peer, needed for network stability
        // check if we are the right target and requester has correct information about this peer
        if ((sb.peers.mySeed() == null) || (!(sb.peers.mySeed().hash.equals(youare)))) {
            // this request has a wrong target
            prop.put("response", "-1"); // request rejected
            return prop;
        }

        if ((sb.isRobinsonMode()) &&
        	 (!((sb.isPublicRobinson()) ||
        	    (sb.isInMyCluster(header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP)))))) {
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
            final String otherSeedString = post.get("myseed", "");
            if (otherSeedString.isEmpty()) {
                prop.put("response", "-1"); // request rejected
                return prop;
            }
            //Date remoteTime = yacyCore.parseUniversalDate((String) post.get(yacySeed.MYTIME)); // read remote time
            Seed otherSeed;
            try {
                otherSeed = Seed.genRemoteSeed(otherSeedString, false, ias.getHostAddress());
            } catch (final IOException e) {
                prop.put("response", "-1"); // don't accept messages for bad seeds
                return prop;
            }

            String subject = crypt.simpleDecode(post.get("subject", "")); // message's subject
            String message = crypt.simpleDecode(post.get("message", "")); // message body
            if (subject == null || message == null) {
                prop.put("response", "-1"); // don't accept empty messages
                return prop;
            }
            message = message.trim();
            subject = subject.trim();
            if (subject.isEmpty() || message.isEmpty()) {
                prop.put("response", "-1"); // don't accept empty messages
                return prop;
            }

            prop.put("response", "Thank you!");

            // save message
            MessageBoard.entry msgEntry = null;
            byte[] mb;
            mb = UTF8.getBytes(message);
            sb.messageDB.write(msgEntry = sb.messageDB.newEntry(
                    "remote",
                    otherSeed.get(Seed.NAME, "anonymous"), otherSeed.hash,
                    sb.peers.mySeed().getName(), sb.peers.mySeed().hash,
                    subject, mb));

            messageForwardingViaEmail(sb, msgEntry);

            // finally write notification
            final File notifierSource = new File(sb.getAppPath(), sb.getConfig("htRootPath","htroot") + "/env/grafics/message.gif");
            final File notifierDest   = new File(sb.getDataPath("htDocsPath", "DATA/HTDOCS"), "notifier.gif");
            try {
                Files.copy(notifierSource, notifierDest);
            } catch (final IOException e) {
            	ConcurrentLog.severe("MESSAGE", "NEW MESSAGE ARRIVED! (error: " + e.getMessage() + ")");

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
    private static void messageForwardingViaEmail(final Switchboard sb, final MessageBoard.entry msgEntry) {
        try {
            if (!sb.getConfigBool("msgForwardingEnabled", false)) return;

            // get the recipient address
            final String sendMailTo = sb.getConfig("msgForwardingTo","root@localhost").trim();

            // get the sendmail configuration
            final String sendMailStr = sb.getConfig("msgForwardingCmd","/usr/bin/sendmail")+" "+sendMailTo;
            final String[] sendMail = sendMailStr.trim().split(" ");

            // building the message text
            final StringBuilder emailTxt = new StringBuilder();
            emailTxt.append("To: ")
            .append(sendMailTo)
            .append("\nFrom: ")
            .append("yacy@")
            .append(sb.peers.mySeed().getName())
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
            .append(UTF8.String(msgEntry.message()));

            final Process process=Runtime.getRuntime().exec(sendMail);
            final PrintWriter email = new PrintWriter(process.getOutputStream());
            email.print(emailTxt.toString());
            email.close();
        } catch (final Exception e) {
            Network.log.warn("message: message forwarding via email failed. ",e);
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
