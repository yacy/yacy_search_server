package de.anomic.soap.services;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ServiceTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("Test for de.anomic.soap.services");
		//$JUnit-BEGIN$
		suite.addTestSuite(AdminServiceTest.class);
		suite.addTestSuite(ShareServiceTest.class);
		suite.addTestSuite(StatusServiceTest.class);
		suite.addTestSuite(BlacklistServiceTest.class);
		suite.addTestSuite(BookmarkServiceTest.class);
		//$JUnit-END$
		return suite;
	}

}
