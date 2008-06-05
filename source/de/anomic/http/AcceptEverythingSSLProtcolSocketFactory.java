package de.anomic.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;

/**
 * accepts every Certificate
 * 
 * @author danielr
 * @since 12.05.2008
 */
class AcceptEverythingSSLProtcolSocketFactory implements SecureProtocolSocketFactory {
    private SSLContext sslContext = null;

    /**
     * constructor
     */
    public AcceptEverythingSSLProtcolSocketFactory() {
        super();
        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[] { new AcceptEverythingTrustManager() }, null);
        } catch (final NoSuchAlgorithmException e) {
            // SSL should be supported
            e.printStackTrace();
        } catch (final KeyManagementException e) {
            e.printStackTrace();
        } catch (final KeyStoreException e) {
            // should never happen, because we don't use a keystore
            e.printStackTrace();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory#createSocket(java.net.Socket,
     *      java.lang.String, int, boolean)
     */
    public Socket createSocket(final Socket socket, final String host, final int port, final boolean autoClose)
            throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.httpclient.protocol.ProtocolSocketFactory#createSocket(java.lang.String, int)
     */
    public Socket createSocket(final String host, final int port) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(host, port);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.httpclient.protocol.ProtocolSocketFactory#createSocket(java.lang.String, int,
     *      java.net.InetAddress, int)
     */
    public Socket createSocket(final String host, final int port, final InetAddress localAddress, final int localPort)
            throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(host, port, localAddress, localPort);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.httpclient.protocol.ProtocolSocketFactory#createSocket(java.lang.String, int,
     *      java.net.InetAddress, int, org.apache.commons.httpclient.params.HttpConnectionParams)
     */
    public Socket createSocket(final String host, final int port, final InetAddress localAddress, final int localPort,
            final HttpConnectionParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        final Socket socket = sslContext.getSocketFactory().createSocket();
        // apply params
        if (params.getLinger() > -1) {
            socket.setSoLinger((params.getLinger() != 0), params.getLinger());
        }
        if (params.getReceiveBufferSize() > 0) {
            socket.setReceiveBufferSize(params.getReceiveBufferSize());
        }
        if (params.getSendBufferSize() > 0) {
            socket.setSendBufferSize(params.getSendBufferSize());
        }
        socket.setSoTimeout(params.getSoTimeout());
        socket.setTcpNoDelay(params.getTcpNoDelay());

        socket.bind(new InetSocketAddress(localAddress, localPort));
        socket.connect(new InetSocketAddress(host, port), params.getConnectionTimeout());
        return socket;
    }
}