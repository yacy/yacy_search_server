package de.anomic.soap.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.rmi.Remote;
import java.util.Hashtable;
import java.util.Properties;

import javax.xml.rpc.ServiceException;

import junit.framework.TestCase;

import org.apache.axis.MessageContext;
import org.apache.axis.client.Stub;
import org.apache.axis.transport.http.HTTPConstants;

import de.anomic.http.httpd;

public abstract class AbstractServiceTest extends TestCase {
	protected static final String SOAP_HEADER_NAMESPACE = "http://http.anomic.de/header";
	protected static final String SOAP_HEADER_AUTHORIZATION = "Authorization";
	
	protected static String authString;
	protected static String peerPort;
	protected static Remote service;
	
	protected void setUp() throws Exception {
		this.loadConfigProperties();
		super.setUp();
	}
	
	protected abstract void createServiceClass() throws ServiceException;
	
	protected String getBaseServiceURL() {
		return "http://localhost:" + peerPort + "/soap/";
	}
	
	protected void loadConfigProperties() throws Exception {
		BufferedInputStream fileInput = null;
		try {
			File configFile = new File("DATA/SETTINGS/httpProxy.conf"); 
			System.out.println("Reading config file: " + configFile.getAbsoluteFile().toString());
			fileInput = new BufferedInputStream(new FileInputStream(configFile));
			
			// load property list
			Properties peerProperties = new Properties();
			peerProperties.load(fileInput);
			fileInput.close();  
			
			// getting admin account auth string
			authString = peerProperties.getProperty(httpd.ADMIN_ACCOUNT_B64MD5);
			if (authString == null) throw new Exception("Unable to find authentication information.");
			
			peerPort = peerProperties.getProperty("port");
			if (authString == null) throw new Exception("Unable to find peer port information.");
			
			// creating the service class
			createServiceClass();
			
			// setting the authentication header
			((Stub)service).setHeader(SOAP_HEADER_NAMESPACE,SOAP_HEADER_AUTHORIZATION,authString);
			
			// configure axis to use HTTP 1.1
			((Stub)service)._setProperty(MessageContext.HTTP_TRANSPORT_VERSION,HTTPConstants.HEADER_PROTOCOL_V11);
			
			// configure axis to use chunked transfer encoding
			Hashtable userHeaderTable = new Hashtable();
			userHeaderTable.put(HTTPConstants.HEADER_TRANSFER_ENCODING, HTTPConstants.HEADER_TRANSFER_ENCODING_CHUNKED);
			((Stub)service)._setProperty(HTTPConstants.REQUEST_HEADERS,userHeaderTable);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (fileInput != null) try { fileInput.close(); } catch (Exception e){/* ignore this */}
		}
	}
}
