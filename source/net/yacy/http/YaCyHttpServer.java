
package net.yacy.http;

import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * Isolation of HttpServer
 * 
 * Development Goal: allow for individual implementation of a HttpServer
 *                   to provide the routines and entry points required by the
 *                   YaCy servlets
 * 
 *                   currently Jetty implementation is ongoing
 * 
 * Implementation    Jetty8HttpServerImpl.java
 */
public interface YaCyHttpServer {
    
    abstract public void startupServer() throws Exception;
    abstract public void stop() throws Exception;
    abstract public void setMaxSessionCount(int cnt);
    abstract public InetSocketAddress generateSocketAddress(String port) throws SocketException;
    abstract public int getMaxSessionCount();
    abstract public int getJobCount();
    abstract public boolean withSSL();
    abstract public void reconnect(int milsec);
    abstract public String getVersion();
}
