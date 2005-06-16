package de.anomic.server;

import java.io.IOException;

public interface serverPortForwarding {
    public void init(
            String remoteHost, 
            int remotePort, 
            String remoteUser, 
            String remotePwd, 
            String localHost, 
            int localPort
    );
    
    public String getHost();
    public int getPort();   
    public String getUser();
    
    public void connect() throws IOException;
    public void disconnect() throws IOException;
}
