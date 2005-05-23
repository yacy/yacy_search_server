package de.anomic.yacy.seedUpload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacySeedUploader;

public class yacySeedUploadScp implements yacySeedUploader {
    
    public static final String CONFIG_SCP_SERVER = "seedScpServer";
    public static final String CONFIG_SCP_ACCOUNT = "seedScpAccount";
    public static final String CONFIG_SCP_PASSWORD = "seedScpPassword";
    public static final String CONFIG_SCP_PATH = "seedScpPath";
    
    public String uploadSeedFile(serverSwitch sb, yacySeedDB seedDB, File seedFile) throws Exception {
        try {        
            if (sb == null) throw new NullPointerException("Reference to serverSwitch nut not be null.");
            if (seedDB == null) throw new NullPointerException("Reference to seedDB must not be null.");
            if ((seedFile == null)||(!seedFile.exists())) throw new Exception("Seed file does not exist.");
            
            String  seedScpServer   = sb.getConfig(CONFIG_SCP_SERVER,null);
            String  seedScpAccount  = sb.getConfig(CONFIG_SCP_ACCOUNT,null);
            String  seedScpPassword = sb.getConfig(CONFIG_SCP_PASSWORD,null);
            String  seedScpPath = sb.getConfig(CONFIG_SCP_PATH,null);       
            
            if ((seedScpServer != null) && (seedScpAccount != null) && (seedScpPassword != null) && (seedScpPath != null)) {
                return sshc.put(seedScpServer, seedFile, seedScpPath, seedScpAccount, seedScpPassword);
            } 
            return "Seed upload settings not configured properly. password-len=" +
            seedScpPassword.length() + ", filePath=" +
            seedScpPath;
        } catch (Exception e) {
            throw e;
        }
    }
    
    public String[] getConfigurationOptions() {
        return new String[] {CONFIG_SCP_SERVER,CONFIG_SCP_ACCOUNT,CONFIG_SCP_PASSWORD,CONFIG_SCP_PATH};
    }
    
    public String[] getLibxDependences() {
        return new String[]{"jsch-0.1.19.jar"};
    }
    
}

class sshc {
    public static String put(String host,
            File localFile, String remoteName,
            String account, String password) throws Exception {
        
        Session session = null;
        try {    
            // Creating a new secure channel object
            JSch jsch=new JSch();
            
            // setting hostname, username, userpassword
            session = jsch.getSession(account, host, 22);
            session.setPassword(password);        
            
            /*
             * Setting the StrictHostKeyChecking to ignore unknown
             * hosts because of a missing known_hosts file ...
             */
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking","no");
            session.setConfig(config);
            
            /* 
             * we need this user interaction interface to support
             * the interactive-keyboard mode
             */             
            UserInfo ui=new SchUserInfo(password);
            session.setUserInfo(ui);        
            
            // trying to connect ...
            session.connect();        
            
            String command="scp -p -t " + remoteName;
            Channel channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);        
            
            // get I/O streams for remote scp
            OutputStream out=channel.getOutputStream();
            InputStream in=channel.getInputStream();
            
            channel.connect();
            
            byte[] tmp=new byte[1];
            checkAck(in);
            
            // send "C0644 filesize filename", where filename should not include '/'
            int filesize=(int)(localFile).length();
            command="C0644 "+filesize+" ";
            if(localFile.toString().lastIndexOf('/')>0){
                command+=localFile.toString().substring(localFile.toString().lastIndexOf('/')+1);
            }
            else{
                command+=localFile.toString();
            }
            command+="\n";
            out.write(command.getBytes()); out.flush();
            
            checkAck(in);
            
            // send a content of lfile
            FileInputStream fis=new FileInputStream(localFile);
            byte[] buf=new byte[1024];
            while(true){
                int len=fis.read(buf, 0, buf.length);
                if(len<=0) break;
                out.write(buf, 0, len); out.flush();
            }
            
            // send '\0'
            buf[0]=0; out.write(buf, 0, 1); out.flush();
            
            checkAck(in);       
            
            return "SCP: File uploaded successfully.";
        } catch (Exception e) {
            throw new Exception("SCP: File uploading failed: " + e.getMessage());
        } finally {
            if ((session != null) && (session.isConnected())) session.disconnect();
        }
    }
    
    static int checkAck(InputStream in) throws IOException{
        int b=in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if(b==0) return b;
        if(b==-1) return b;
        
        if(b==1 || b==2){
            StringBuffer sb=new StringBuffer();
            int c;
            do {
                c=in.read();
                sb.append((char)c);
            }
            while(c!='\n');
            
            if(b==1){ // error
                throw new IOException(sb.toString());
            }
            if(b==2){ // fatal error
                throw new IOException(sb.toString());
            }
        }
        return b;
    }
}


class SchUserInfo 
implements UserInfo, UIKeyboardInteractive {
    String passwd;
    
    public SchUserInfo(String password) {
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

