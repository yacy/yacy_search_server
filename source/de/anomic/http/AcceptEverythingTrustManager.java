package de.anomic.http;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import de.anomic.server.logging.serverLog;

/**
 * trust every server
 * 
 * @author daniel
 * 
 */
class AcceptEverythingTrustManager extends EasyX509TrustManager implements X509TrustManager {

    /**
     * constructor
     * 
     * @param keystore
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    public AcceptEverythingTrustManager() throws NoSuchAlgorithmException, KeyStoreException {
        super(null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.X509Certificate[], java.lang.String)
     */
    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
        try {
            super.checkServerTrusted(chain, authType);
        } catch (final Exception e) {
            // trusted but logged
            serverLog.logWarning("HTTPC", "trusting SSL certificate with " + e.getClass() + ": " + e.getMessage());
        }
    }

}