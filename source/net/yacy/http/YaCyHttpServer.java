
package net.yacy.http;

/**
 * Isolation of HttpServer
 * 
 * Development Goal: allow for individual implementation of a HttpServer
 *                   to provide the routines and entry points required by the
 *                   YaCy servlets
 * 
 * Implementation    Jetty9HttpServerImpl.java
 */
public interface YaCyHttpServer {
    
    abstract void startupServer() throws Exception;
    abstract void stop() throws Exception;
    abstract int getSslPort();
    abstract boolean withSSL();
    abstract void reconnect(int milsec);
    abstract String getVersion();
    abstract int getServerThreads();
}
