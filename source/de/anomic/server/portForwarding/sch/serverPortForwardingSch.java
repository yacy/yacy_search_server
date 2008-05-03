// serverPortForwardingSch.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: borg-0300 $
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

package de.anomic.server.portForwarding.sch;

import java.io.IOException;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import de.anomic.server.serverInstantBusyThread;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.server.portForwarding.serverPortForwarding;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;

public class serverPortForwardingSch implements serverPortForwarding{

    /* ========================================================================
     * Constants needed to read properties from the configuration file
     * ======================================================================== */
    public static final String FORWARDING_HOST = "portForwarding.sch.Host";
    public static final String FORWARDING_HOST_PORT = "portForwarding.sch.HostPort";
    public static final String FORWARDING_HOST_USER = "portForwarding.sch.HostUser";
    public static final String FORWARDING_HOST_PWD = "portForwarding.sch.HostPwd";

    public static final String FORWARDING_PORT = "portForwarding.sch.Port";
    public static final String FORWARDING_USE_PROXY = "portForwarding.sch.UseProxy";


    /* ========================================================================
     * Other object fields
     * ======================================================================== */
    private serverSwitch<?> switchboard;

    private String forwardingHost;
    private int forwardingHostPort;
    private String forwardingHostUser;
    private String forwardingHostPwd;

    private int forwardingPort;
    private boolean useProxy;

    private String remoteProxyHost;
    private int remoteProxyPort;    

    private String localHost;
    private int localHostPort;

    private static Session session = null;
    private static serverInstantBusyThread sessionWatcher = null;

    private final serverLog log;

    public serverPortForwardingSch() {
        super();
        this.log = new serverLog("PORT_FORWARDING_SCH");
    }

    public void init(
            serverSwitch<?> switchboard,
            String localHost,
            int localPort
    ) throws Exception {
        try {
            if (this.log.isFine()) this.log.logFine("Initializing port forwarding via sch ...");

            this.switchboard = switchboard;

            this.forwardingHost     = switchboard.getConfig(FORWARDING_HOST,"localhost");
            this.forwardingHostPort = Integer.valueOf(switchboard.getConfig(FORWARDING_HOST_PORT,"8080")).intValue();
            this.forwardingHostUser = switchboard.getConfig(FORWARDING_HOST_USER,"xxx");
            this.forwardingHostPwd  = switchboard.getConfig(FORWARDING_HOST_PWD,"xxx");

            this.forwardingPort = Integer.valueOf(switchboard.getConfig(FORWARDING_PORT,"8080")).intValue();
            this.useProxy = Boolean.valueOf(switchboard.getConfig(FORWARDING_USE_PROXY,"false")).booleanValue();

            this.localHost = localHost;
            this.localHostPort = localPort;

            // load remote proxy data
            this.remoteProxyHost    = switchboard.getConfig("remoteProxyHost","");
            try {
                this.remoteProxyPort    = Integer.parseInt(switchboard.getConfig("remoteProxyPort","3128"));
            } catch (NumberFormatException e) {
                remoteProxyPort = 3128;
            }

            // checking if all needed libs are availalbe
            String javaClassPath = System.getProperty("java.class.path");
            if (javaClassPath.indexOf("jsch") == -1) {
                throw new IllegalStateException("Missing library.");
            }
        } catch (Exception e) {
            this.log.logSevere("Unable to initialize port forwarding.",e);
            throw e;
        }
    }

    public String getHost() {
        return this.forwardingHost;
    }

    public int getPort() {
        return this.forwardingPort;
    }

    public synchronized void connect() throws IOException {
        try{
            if ((session != null) && (session.isConnected()))
                throw new IOException("Session already connected");

            this.log.logInfo("Trying to connect to remote port forwarding host " + this.forwardingHostUser + "@" + this.forwardingHost + ":" + this.forwardingHostPort);

            JSch jsch=new JSch();
            session=jsch.getSession(this.forwardingHostUser, this.forwardingHost, this.forwardingHostPort);
            session.setPassword(this.forwardingHostPwd);   

            /*
             * Setting the StrictHostKeyChecking to ignore unknown
             * hosts because of a missing known_hosts file ...
             */
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking","no");
            session.setConfig(config);            

            // setting the proxy that should be used
            if (this.useProxy) {
                session.setProxy(new ProxyHTTP(this.remoteProxyHost, this.remoteProxyPort));                
            }

            // username and password will be given via UserInfo interface.
            UserInfo ui= new MyUserInfo(this.forwardingHostPwd);
            session.setUserInfo(ui);

            // trying to connect ...
            session.connect();            

            // activating remote port forwarding 
            session.setPortForwardingR(this.forwardingPort, this.localHost, this.localHostPort);

            // using a timer task to control if the session remains open
            if (sessionWatcher == null) {
                this.log.logFine("Deploying port forwarding session watcher thread.");
                this.switchboard.deployThread("portForwardingWatcher", "Remote Port Forwarding Watcher", "this thread is used to detect broken connections and to re-establish it if necessary.", null,
                sessionWatcher = new serverInstantBusyThread(this, "reconnect", null, null), 30000,30000,30000,1000);
            }

            this.log.logInfo("Remote port forwarding connection established: " + 
                             this.forwardingHost+ ":" + this.forwardingPort + " -> " + 
                             this.localHost + ":" + this.localHostPort);
        }
        catch(Exception e){
            this.log.logSevere("Unable to connect to remote port forwarding host.",e);
            throw new IOException(e.getMessage());
        }
    }

    public synchronized boolean reconnect() throws IOException {
        if ((!this.isConnected()) && (!Thread.currentThread().isInterrupted())) {
            if (this.log.isFine()) this.log.logFine("Trying to reconnect to port forwarding host.");
            this.disconnect();
            this.connect();
            return this.isConnected();
        }
        return false;
    }

    public synchronized void disconnect() throws IOException {
        if (session == null) throw new IOException("No connection established.");

        // terminating port watcher thread
        this.log.logFine("Terminating port forwarding session watcher thread.");
        this.switchboard.terminateThread("portForwardingWatcher",true);     
        sessionWatcher = null;

        // disconnection the session
        try {
            session.disconnect();
            this.log.logFine("Successfully disconnected from port forwarding host.");
        } catch (Exception e) {
            this.log.logSevere("Error while trying to disconnect from port forwarding host.",e);
            throw new IOException(e.getMessage());
        }
    }

    public synchronized boolean isConnected() {
        if (session == null) return false;
        if (!session.isConnected()) return false;        
        int urls = yacyClient.queryUrlCount(yacyCore.seedDB.mySeed());
        return !(urls < 0); 
    }

    class MyUserInfo 
    implements UserInfo, UIKeyboardInteractive {
        String passwd;

        public MyUserInfo(String password) {
            this.passwd = password;
        }

        public String getPassword() { 
            return this.passwd; 
        }

        public boolean promptYesNo(String str){   
            System.err.println("User was prompted from: " + str);
            return true;
        }

        public String getPassphrase() { 
            return null; 
        }

        public boolean promptPassphrase(String message) {
            System.out.println("promptPassphrase : " + message);            
            return false;
        }

        public boolean promptPassword(String message) {
            System.out.println("promptPassword : " + message);      
            return true;
        }

        /**
         * @see com.jcraft.jsch.UserInfo#showMessage(java.lang.String)
         */
        public void showMessage(String message) {
            System.out.println("Sch has tried to show the following message to the user: " + message);
        }

        public String[] promptKeyboardInteractive(String destination,
                String name,
                String instruction,
                String[] prompt,
                boolean[] echo) {
            System.out.println("User was prompted using interactive-keyboard: "  +
                    "\n\tDestination: " + destination +
                    "\n\tName:        " + name +
                    "\n\tInstruction: " + instruction +
                    "\n\tPrompt:      " + arrayToString2(prompt,"|") + 
                    "\n\techo:        " + arrayToString2(echo,"|"));        

            if ((prompt.length >= 1) && (prompt[0].startsWith("Password")))
                return new String[]{this.passwd};
            return new String[]{};
        }

        String arrayToString2(String[] a, String separator) {
            StringBuffer result = new StringBuffer(); // start with first element
            if (a.length > 0) {
                result.append(a[0]);
                for (int i=1; i<a.length; i++) {
                    result.append(separator);
                    result.append(a[i]);
                }
            }
            return result.toString();
        }

        String arrayToString2(boolean[] a, String separator) {
            StringBuffer result = new StringBuffer(); // start with first element
            if (a.length > 0) {
                result.append(a[0]);
                for (int i=1; i<a.length; i++) {
                    result.append(separator);
                    result.append(a[i]);
                }
            }
            return result.toString();
        }
    }

}
