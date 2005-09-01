package de.anomic.server;

import java.io.IOException;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;

public class serverPortForwardingSch implements serverPortForwarding{
    
    /* ========================================================================
     * Constants needed to read properties from the configuration file
     * ======================================================================== */
    public static final String FORWARDING_HOST = "portForwardingHost";
    public static final String FORWARDING_HOST_PORT = "portForwardingHostPort";
    public static final String FORWARDING_HOST_USER = "portForwardingHostUser";
    public static final String FORWARDING_HOST_PWD = "portForwardingHostPwd";
    
    public static final String FORWARDING_PORT = "portForwardingPort";
    public static final String FORWARDING_USE_PROXY = "portForwardingUseProxy";
    
    
    /* ========================================================================
     * Other object fields
     * ======================================================================== */    
    private serverSwitch switchboard;
        
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
    
    private static Session session;
    private static serverInstantThread sessionWatcher;
    
    private serverLog log;
    
    public serverPortForwardingSch() {
        super();
        this.log = new serverLog("PORT_FORWARDING_SCH");
    }
    
    public void init(
            serverSwitch switchboard,
            String localHost, 
            int localPort
    ) throws Exception {
        try {
            this.log.logFine("Initializing port forwarding via sch ...");
            
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
                        sessionWatcher = new serverInstantThread(this, "reconnect", null), 30000,30000,30000,1000);
                sessionWatcher.setSyncObject(new Object());
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
            this.log.logFine("Trying to reconnect to port forwarding host.");
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
        int urls = yacyClient.queryUrlCount(yacyCore.seedDB.mySeed);
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
            StringBuffer result = new StringBuffer();// start with first element
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
            StringBuffer result = new StringBuffer();// start with first element
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
