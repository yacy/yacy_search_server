// TextParserTest.java
// ---------------------------
// Copyright 2017 by luccioman; https://github.com/luccioman
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

package net.yacy.document;

import static org.junit.Assert.*;

import java.util.Locale;

import org.junit.Test;

/**
 * Unit tests for the {@link TextParser} class.
 * 
 * @author luccioman
 *
 */
public class TextParserTest {

	/**
	 * Test the TextParser.supportsMime() consistency with available locales.
	 * Possible failure case : with the Turkish ("tr") language, 'I' lower cased
	 * does not becomes 'i' but '\u005Cu0131' (the latin small letter 'ı'
	 * character).
	 */
	@Test
	public void testSupportsMimeLocaleConsistency() {
		Locale initialDefaultLocale = Locale.getDefault();
		try {
			for (Locale locale : Locale.getAvailableLocales()) {
				Locale.setDefault(locale);
				for (String mimeType : TextParser.supportedMimeTypes()) {
					assertNull(locale + " " + mimeType, TextParser.supportsMime(mimeType.toUpperCase(Locale.ROOT)));
				}
			}
		} finally {
			/*
			 * Restore the initial default locale to prevent side-effects on other JUnit
			 * tests run in the same session
			 */
			Locale.setDefault(initialDefaultLocale);
		}
	}

}
