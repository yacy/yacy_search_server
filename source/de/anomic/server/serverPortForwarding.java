package de.anomic.server;

import java.io.IOException;

public interface serverPortForwarding {
    public void init(serverSwitch switchboard, String localHost, int localPort) throws Exception;
    
    public String getHost();
    public int getPort();   
    
    public void connect() throws IOException;
    public void disconnect() throws IOException;
    public boolean reconnect() throws IOException;
    public boolean isConnected();
}
