// GenericFormatterTest.java
// Copyright 2018 by luccioman; https://github.com/luccioman
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

package net.yacy.cora.date;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the {@link GenericFormatter} class.
 */
public class GenericFormatterTest {

	/**
	 * Check that the date patterns are properly written : no error should occur
	 * when using them for formatting and parsing.
	 */
	@Test
	public void testFormats() {
		final Instant time = Instant.parse("2018-06-28T10:49:35.726Z");

		String formatted = GenericFormatter.FORMAT_SHORT_DAY.format(time);
		System.out.println("GenericFormatter.FORMAT_SHORT_DAY : " + formatted);
		SimpleDateFormat oldApiFormat = GenericFormatter.newShortDayFormat();
		Assert.assertEquals(oldApiFormat.format(Date.from(time)), formatted);
		GenericFormatter.FORMAT_SHORT_DAY.parse("20180628");

		formatted = GenericFormatter.FORMAT_SHORT_MINUTE.format(time);
		System.out.println("GenericFormatter.FORMAT_SHORT_MINUTE : " + formatted);
		oldApiFormat = GenericFormatter.newShortMinuteFormat();
		Assert.assertEquals(oldApiFormat.format(Date.from(time)), formatted);
		GenericFormatter.FORMAT_SHORT_MINUTE.parse("201806281407");

		formatted = GenericFormatter.FORMAT_SHORT_SECOND.format(time);
		System.out.println("GenericFormatter.FORMAT_SHORT_SECOND : " + formatted);
		oldApiFormat = GenericFormatter.newShortSecondFormat();
		Assert.assertEquals(oldApiFormat.format(Date.from(time)), formatted);
		GenericFormatter.FORMAT_SHORT_SECOND.parse("20180628140713");

		formatted = GenericFormatter.FORMAT_SHORT_MILSEC.format(time);
		System.out.println("GenericFormatter.FORMAT_SHORT_MILSEC : " + formatted);
		oldApiFormat = GenericFormatter.newShortMilsecFormat();
		Assert.assertEquals(oldApiFormat.format(Date.from(time)), formatted);
		GenericFormatter.FORMAT_SHORT_MILSEC.parse("20180628140713921");

		formatted = GenericFormatter.FORMAT_RFC1123_SHORT.format(time);
		System.out.println("GenericFormatter.FORMAT_RFC1123_SHORT : " + formatted);
		oldApiFormat = GenericFormatter.newRfc1123ShortFormat();
		Assert.assertEquals(oldApiFormat.format(Date.from(time)), formatted);
		GenericFormatter.FORMAT_RFC1123_SHORT.parse("Thu, 28 Jun 2018");

		formatted = GenericFormatter.FORMAT_ANSIC.format(time);
		System.out.println("GenericFormatter.FORMAT_ANSIC : " + formatted);
		oldApiFormat = GenericFormatter.newAnsicFormat();
		Assert.assertEquals(oldApiFormat.format(Date.from(time)), formatted);
		GenericFormatter.FORMAT_ANSIC.parse("Thu Jun 28 15:48:17 2018");

		formatted = GenericFormatter.FORMAT_SIMPLE.format(time);
		System.out.println("GenericFormatter.FORMAT_SIMPLE : " + formatted);
		oldApiFormat = GenericFormatter.newSimpleDateFormat();
		Assert.assertEquals(oldApiFormat.format(Date.from(time)), formatted);
		GenericFormatter.FORMAT_SIMPLE.parse("2018/06/28 15:27:45");
	}

}
