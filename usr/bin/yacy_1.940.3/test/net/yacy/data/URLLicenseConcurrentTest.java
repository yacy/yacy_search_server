// URLLicenseConcurrentTest.java
// Copyright 2016 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.data;

import java.net.MalformedURLException;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;

/**
 * Test URLLicence reliability when used by concurrent threads
 * 
 * @author luc
 *
 */
public class URLLicenseConcurrentTest {

	/**
	 * Thread emulating a client who tries to fetch some url content.
	 * @author luc
	 *
	 */
	private static class ClientThread extends Thread {

		private String testURL = "https://yacy.net";

		private int steps = 100000;

		@Override
		public void run() {
			System.out.println(this.getName() + " started...");
			DigestURL url = null;
			try {
				url = new DigestURL(this.testURL);
			} catch (MalformedURLException e1) {
				e1.printStackTrace();
			}
			String normalizedURL = url.toNormalform(true);
			for (int step = 0; step < this.steps; step++) {
					String license = URLLicense.aquireLicense(url);
					// You can eventually call here Thread.sleep()
					String retrievedURL = URLLicense.releaseLicense(license);
					if (!normalizedURL.equals(retrievedURL)) {
						System.err.println("Licence lost! license : " + license + ", step : " + step + ", Thread : " + this.getName());
					}
			}
			System.out.println(this.getName() + " finished!");
		}
	}

	/**
	 * Runs clients concurrently : until the end, no error message should be displayed in console.
	 * @param args
	 */
	public static void main(String args[]) {
		long beginTime = System.nanoTime();
		try {
			ClientThread[] threads = new ClientThread[10];
			for (int i = 0; i < threads.length; i++) {
				threads[i] = new URLLicenseConcurrentTest.ClientThread();
				threads[i].setName("ClientThread" + i);
				threads[i].start();
			}
			for (int i = 0; i < threads.length; i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
				}
			}
		} finally {
			long time = System.nanoTime() - beginTime;
			System.out.println("Test run in " + time / 1000000 + "ms");
			ConcurrentLog.shutdown();
		}
	}

}
