package de.anomic.soap.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.rmi.RemoteException;
import java.util.Properties;

import junit.framework.TestCase;

import org.apache.axis.client.Stub;
import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import yacy.soap.status.StatusService;
import yacy.soap.status.StatusServiceServiceLocator;

public class StatusServiceTest extends TestCase {
	private static String authString;
	private static String peerPort;
	private static StatusService service;
	
	
	protected void setUp() throws Exception {
		if (peerPort == null) this.loadConfigProperties();
		super.setUp();
	}
	
	private void loadConfigProperties() throws Exception {
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
			authString = peerProperties.getProperty("adminAccountBase64MD5");
			if (authString == null) throw new Exception("Unable to find authentication information.");
			
			peerPort = peerProperties.getProperty("port");
			if (authString == null) throw new Exception("Unable to find peer port information.");
			
			// construct Soap object
			StatusServiceServiceLocator locator = new StatusServiceServiceLocator();
			locator.setstatusEndpointAddress("http://localhost:" + peerPort + "/soap/status");
			
			service = locator.getstatus();
			((Stub)service).setHeader("http://http.anomic.de/header","Authorization",authString);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (fileInput != null) try { fileInput.close(); } catch (Exception e){/* ignore this */}
		}
	}
	
	public void testNetwork() throws RemoteException {
		Document xml = service.network();
		System.out.println(XMLUtils.DocumentToString(xml));	
	}
	
	public void testGetQueueStatus() throws RemoteException {
		Document xml = service.getQueueStatus(null,null,null,null);
		System.out.println(XMLUtils.DocumentToString(xml));
	}
}
