//serverCoreSocket.java 
//-------------------------------------
//part of YACY
//
//(C) 2006 by Martin Thelian
//
//last change: $LastChangedDate: 2006-05-12 16:35:56 +0200 (Fr, 12 Mai 2006) $ by $LastChangedBy: theli $
//Revision: $LastChangedRevision: 2086 $
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

package de.anomic.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

public class serverCoreSocket extends Socket {
    
    private PushbackInputStream input = null;
    private Socket sock = null;
    private boolean isSSL = false;
    private String sslType = null;
    
    public serverCoreSocket(Socket sock) throws IOException {
        this.sock = sock;
        
        // determine the socket type
        detectSSL();
    }
    
    public boolean isSSL() {
        return this.isSSL;
    }

    public String getProtocol() {
        return this.sslType;
    }
    
    private void detectSSL() throws IOException {
        InputStream in = getInputStream();    
        
        // read the first 5 bytes to determine the protocol type
        byte[] preRead = new byte[5];
        int read, count = 0;
        while ((count < preRead.length) && ((read = in.read()) != -1)) {
        	preRead[count] = (byte) read;
        	count++;
        }
        if (count < preRead.length) {
        	((PushbackInputStream) in).unread(preRead,0,count);
        	return;
        }
        
        int idx = 0;
        if ((preRead[0] & 0xFF) == 22) {
            // we have detected the ContentType field.
            // 22 means "handshake"
            idx = 1;
        } else {
            // SSL messages have two preceding bytes
            // byte nr 3 specifies the handshake type
            // 3 means "ClientHello"
            int handshakeType = preRead[2] & 0x00FF;
            if (handshakeType == 1) this.isSSL = true;
            idx = 3;
        }

        // determine the protocol version
        if (preRead[idx] == 3 && (preRead[idx+1] >= 0 && preRead[idx+1] <= 2)) {
            switch (preRead[idx+1]) {
            case 0:
                this.sslType = "SSL_3";
                break;
            case 1:
                this.sslType = "TLS_1";
                break;
            case 2:
                this.sslType = "TLS_1_1";
                break;
            }
            this.isSSL = true;
        //} else {
            // maybe SSL_2, but we can not be sure
        }

        // unread pre read bytes
        ((PushbackInputStream) in).unread(preRead);    
    }
    
    public InetAddress getInetAddress() {
        return this.sock.getInetAddress();
    }
    
    public InetAddress getLocalAddress() {
        return this.sock.getLocalAddress();
    }
    
    public int getPort() {
        return this.sock.getPort();
    }
    
    public int getLocalPort() {
        return this.sock.getLocalPort();
    }
    
    public SocketAddress getRemoteSocketAddress() {
        return this.sock.getRemoteSocketAddress();
    }
    
    public SocketAddress getLocalSocketAddress() {
        return this.sock.getLocalSocketAddress();
    }
    
    public SocketChannel getChannel() {
        return this.sock.getChannel();
    }
    
    
    public InputStream getInputStream() throws IOException {
        if (this.input == null) {
            this.input = new PushbackInputStream(this.sock.getInputStream(),100);
        }
        return this.input;
    }
    
    public OutputStream getOutputStream() throws IOException {
        return this.sock.getOutputStream();
    }
    
    
    public synchronized void close() throws IOException {
        this.sock.close();
    }
    
    public void shutdownInput() throws IOException {
        this.sock.shutdownInput();
    }
    
    public void shutdownOutput() throws IOException {
        this.sock.shutdownOutput();
    }
    
    public String toString() {
        return this.sock.toString();
    }
    
    public boolean isConnected() {
        return this.sock.isConnected();
    }
    
    public boolean isBound() {
        return this.sock.isBound();
    }
    
    public boolean isClosed() {
        return this.sock.isClosed();
    }
    
    public boolean isInputShutdown() {
        return this.sock.isInputShutdown();
    }
    
    public boolean isOutputShutdown() {
        return this.sock.isOutputShutdown();
    }    
    
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        this.sock.setSoTimeout(timeout);
    }
    
}
