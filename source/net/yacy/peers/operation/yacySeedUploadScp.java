//yacySeedUploadScp.java
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.peers.operation;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.server.serverSwitch;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;


public class yacySeedUploadScp implements yacySeedUploader {

    public static final String CONFIG_SCP_SERVER = "seedScpServer";
    public static final String CONFIG_SCP_SERVER_PORT = "seedScpServerPort";
    public static final String CONFIG_SCP_ACCOUNT = "seedScpAccount";
    public static final String CONFIG_SCP_PASSWORD = "seedScpPassword";
    public static final String CONFIG_SCP_PATH = "seedScpPath";

    @Override
    public String uploadSeedFile(final serverSwitch sb, final File seedFile) throws Exception {
        try {
            if (sb == null) throw new NullPointerException("Reference to serverSwitch nut not be null.");
            if ((seedFile == null)||(!seedFile.exists())) throw new Exception("Seed file does not exist.");

            final String  seedScpServer   = sb.getConfig(CONFIG_SCP_SERVER,null);
            final String  seedScpServerPort =  sb.getConfig(CONFIG_SCP_SERVER_PORT,"22");
            final String  seedScpAccount  = sb.getConfig(CONFIG_SCP_ACCOUNT,null);
            final String  seedScpPassword = sb.getConfig(CONFIG_SCP_PASSWORD,null);
            final String  seedScpPath = sb.getConfig(CONFIG_SCP_PATH,null);

            if (seedScpServer == null || seedScpServer.isEmpty())
                throw new Exception("Seed SCP upload settings not configured properly. Servername must not be null or empty.");
            else if (seedScpAccount == null || seedScpAccount.isEmpty())
                throw new Exception("Seed SCP upload settings not configured properly. Username must not be null or empty.");
            else if (seedScpPassword == null || seedScpPassword.isEmpty())
                throw new Exception("Seed SCP upload settings not configured properly. Password must not be null or empty.");
            else if (seedScpPath == null || seedScpPath.isEmpty())
                throw new Exception("Seed SCP upload settings not configured properly. File path must not be null or empty.");
            else if (seedScpServerPort == null || seedScpServerPort.isEmpty())
            throw new Exception("Seed SCP upload settings not configured properly. Server port must not be null or empty.");
            int port = 22;
            try {
                port = Integer.parseInt(seedScpServerPort);
            } catch (final NumberFormatException ex) {
                throw new Exception("Seed SCP upload settings not configured properly. Server port is not a vaild integer.");
            }

            return sshc.put(seedScpServer, port, seedFile, seedScpPath, seedScpAccount, seedScpPassword);
        } catch (final Exception e) {
            throw e;
        }
    }

    @Override
    public String[] getConfigurationOptions() {
        return new String[] {CONFIG_SCP_SERVER,CONFIG_SCP_SERVER_PORT,CONFIG_SCP_ACCOUNT,CONFIG_SCP_PASSWORD,CONFIG_SCP_PATH};
    }

}

class sshc {
    public static String put(
            final String host,
            final int port,
            final File localFile,
            final String remoteName,
            final String account,
            final String password
    ) throws Exception {

        Session session = null;
        try {
            // Creating a new secure channel object
            final JSch jsch=new JSch();

            // setting hostname, username, userpassword
            session = jsch.getSession(account, host, port);
            session.setPassword(password);

            /*
             * Setting the StrictHostKeyChecking to ignore unknown
             * hosts because of a missing known_hosts file ...
             */
            final java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking","no");
            session.setConfig(config);

            /*
             * we need this user interaction interface to support
             * the interactive-keyboard mode
             */
            final UserInfo ui=new SchUserInfo(password);
            session.setUserInfo(ui);

            // trying to connect ...
            session.connect();

            String command="scp -p -t " + remoteName;
            final Channel channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);

            // get I/O streams for remote scp
            final OutputStream out=channel.getOutputStream();
            final InputStream in=channel.getInputStream();

            channel.connect();

            checkAck(in);

            // send "C0644 filesize filename", where filename should not include '/'
            final int filesize=(int)(localFile).length();
            command="C0644 "+filesize+" ";
            if(localFile.toString().lastIndexOf('/')>0){
                command+=localFile.toString().substring(localFile.toString().lastIndexOf('/')+1);
            }
            else{
                command+=localFile.toString();
            }
            command+="\n";
            out.write(UTF8.getBytes(command)); out.flush();

            checkAck(in);

            // send a content of lfile
            final byte[] buf=new byte[1024];
            BufferedInputStream bufferedIn = null;
            try {
                bufferedIn=new BufferedInputStream(new FileInputStream(localFile));
                while(true){
                    final int len=bufferedIn.read(buf, 0, buf.length);
                    if(len<=0) break;
                    out.write(buf, 0, len); out.flush();
                }
            } finally {
                if (bufferedIn != null) try{bufferedIn.close();}catch(final Exception e){}
            }

            // send '\0'
            buf[0]=0; out.write(buf, 0, 1); out.flush();

            checkAck(in);

            return "SCP: File uploaded successfully.";
        } catch (final Exception e) {
            throw new Exception("SCP: File uploading failed: " + e.getMessage());
        } finally {
            if ((session != null) && (session.isConnected())) session.disconnect();
        }
    }

    static int checkAck(final InputStream in) throws IOException{
        final int b=in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if(b==0) return b;
        if(b==-1) return b;

        if(b==1 || b==2){
            final StringBuilder sb=new StringBuilder();
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

    public SchUserInfo(final String password) {
        this.passwd = password;
    }

    @Override
    public String getPassword() {
        return this.passwd;
    }

    @Override
    public boolean promptYesNo(final String str){
        System.err.println("User was prompted from: " + str);
        return true;
    }

    @Override
    public String getPassphrase() {
        return null;
    }

    @Override
    public boolean promptPassphrase(final String message) {
        System.out.println("promptPassphrase : " + message);
        return false;
    }

    @Override
    public boolean promptPassword(final String message) {
        System.out.println("promptPassword : " + message);
        return true;
    }

    /**
     * @see com.jcraft.jsch.UserInfo#showMessage(java.lang.String)
     */
    @Override
    public void showMessage(final String message) {
        System.out.println("Sch has tried to show the following message to the user: " + message);
    }

    @Override
    public String[] promptKeyboardInteractive(final String destination,
            final String name,
            final String instruction,
            final String[] prompt,
            final boolean[] echo) {
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

    static String arrayToString2(final String[] a, final String separator) {
        final StringBuilder result = new StringBuilder();// start with first element
        if (a.length > 0) {
            result.append(a[0]);
            for (int i=1; i<a.length; i++) {
                result.append(separator);
                result.append(a[i]);
            }
        }
        return result.toString();
    }

    static String arrayToString2(final boolean[] a, final String separator) {
        final StringBuilder result = new StringBuilder();// start with first element
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

