package de.anomic.server;

import java.io.IOException;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

public class serverPortForwardingSch implements serverPortForwarding{
    
    String rHost;
    int rPort;
    String rUser;
    String rPwd;
    
    String lHost;
    int lPort;
    
    Session session;
    
    public serverPortForwardingSch() {
        super();
    }
    
    public void init(
            String remoteHost, 
            int remotePort, 
            String remoteUser, 
            String remotePwd, 
            String localHost, 
            int localPort
    ) {
        this.rHost = remoteHost;
        this.rPort = remotePort;
        this.rUser = remoteUser;
        this.rPwd = remotePwd;
        
        this.lHost = localHost;
        this.lPort = localPort;        
        
        // checking if all needed libs are availalbe
        String javaClassPath = System.getProperty("java.class.path");
        if (javaClassPath.indexOf("jsch") == -1) {
            throw new IllegalStateException("Missing library.");
        }
    }
    
    public String getHost() {
        return this.rHost;
    }
    
    public int getPort() {
        return this.rPort;
    }
    
    public String getUser() {
        return this.rUser;
    }
    
    public void connect() throws IOException {
        
        try{
            JSch jsch=new JSch();
            this.session=jsch.getSession(this.rUser, this.rHost, 22);
            this.session.setPassword(this.rPwd);   

            /*
             * Setting the StrictHostKeyChecking to ignore unknown
             * hosts because of a missing known_hosts file ...
             */
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking","no");
            this.session.setConfig(config);            
            
            // username and password will be given via UserInfo interface.
            UserInfo ui= new MyUserInfo(this.rPwd);
            this.session.setUserInfo(ui);
            
            // trying to connect ...
            this.session.connect();
            
            // Channel channel=session.openChannel("shell");
            // channel.connect();
            
            this.session.setPortForwardingR(this.rPort, this.lHost, this.lPort);
        }
        catch(Exception e){
            throw new IOException(e.getMessage());
        }
    }
    
    public void disconnect() throws IOException {
        if (this.session == null) throw new IllegalStateException("No connection established.");
        
        try {
            this.session.disconnect();
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public boolean isConnected() {
        if (this.session == null) return false;
        return this.session.isConnected();
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
