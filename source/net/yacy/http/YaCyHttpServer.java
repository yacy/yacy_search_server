
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
    
    abstract void startupServer() throws Exception;
    abstract void stop() throws Exception;
    abstract void setMaxSessionCount(int cnt);
    abstract InetSocketAddress generateSocketAddress(String port) throws SocketException;
    abstract int getMaxSessionCount();
    abstract int getJobCount();
    abstract int getSslPort();
    abstract boolean withSSL();
    abstract void reconnect(int milsec);
    abstract String getVersion();
}
