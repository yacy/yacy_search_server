/**
 *  IconEntryTest
 *  part of YaCy
 *  Copyright 2016 by luccioman; https://github.com/luccioman
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.parser.html;

import java.awt.Dimension;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;

/**
 * Unit tests for IconEntry class.
 * @author luc
 *
 */
public class IconEntryTest {

	@Test
	public final void testGetDistance() {
		/* Normal case : one size has both width and height greater */
		Dimension size1 = new Dimension(5, 8);
		Dimension size2 = new Dimension(7, 12);
		Assert.assertEquals(3.0, IconEntry.getDistance(size1, size2), 0.0);
		/* Check inverted parameters should produces same result */
		Assert.assertEquals(3.0, IconEntry.getDistance(size2, size1), 0.0);
		/* Equal sizes */
		size2 = new Dimension(5, 8);
		Assert.assertEquals(0.0, IconEntry.getDistance(size1, size2), 0.0);
		/* Equal sizes */
		size2 = new Dimension(5, 8);
		Assert.assertEquals(0.0, IconEntry.getDistance(size1, size2), 0.0);
		/* Only one dimension differs */
		size2 = new Dimension(5, 12);
		Assert.assertEquals(2.0, IconEntry.getDistance(size1, size2), 0.0);
		size2 = new Dimension(10, 8);
		Assert.assertEquals(2.5, IconEntry.getDistance(size1, size2), 0.0);
		/* width lower, height upper */
		size2 = new Dimension(3, 12);
		Assert.assertEquals(3.0, IconEntry.getDistance(size1, size2), 0.0);
		/* negative values */
		size1 = new Dimension(-5, -8);
		size2 = new Dimension(-7, -12);
		Assert.assertEquals(3.0, IconEntry.getDistance(size1, size2), 0.0);
		/* one null */
		size1 = null;
		size2 = new Dimension(-7, -12);
		Assert.assertEquals(Double.MAX_VALUE, IconEntry.getDistance(size1, size2), 0.0);
	}

	@Test
	public final void testGetClosestSize() throws MalformedURLException {
		/* Preferred size in sizes set */
		Set<String> rels = new HashSet<>();
		rels.add(IconLinkRelations.ICON.getRelValue());
		
		Set<Dimension> sizes = new HashSet<>();
		sizes.add(new Dimension(128,128));
		sizes.add(new Dimension(256,512));
		sizes.add(new Dimension(16,16));
		
		Dimension preferredSize = new Dimension(16, 16);
		IconEntry icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		Dimension result = icon.getClosestSize(preferredSize);
		Assert.assertEquals(preferredSize, result);
		
		/* Preferred size lower than all sizes in set */
		preferredSize = new Dimension(12, 12);
		result = icon.getClosestSize(preferredSize);
		Assert.assertEquals(new Dimension(16,16), result);
		
		/* Preferred size over than all sizes in set */
		preferredSize = new Dimension(1992, 1024);
		result = icon.getClosestSize(preferredSize);
		Assert.assertEquals(new Dimension(256, 512), result);
		
		/* Preferred size between sizes in set */
		preferredSize = new Dimension(17, 18);
		result = icon.getClosestSize(preferredSize);
		Assert.assertEquals(new Dimension(16, 16), result);
		
		/* Sizes set contains only one item */
		sizes = new HashSet<>();
		sizes.add(new Dimension(128,128));
		icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		preferredSize = new Dimension(1992, 1024);
		result = icon.getClosestSize(preferredSize);
		Assert.assertEquals(new Dimension(128, 128), result);
		
		/* Empty sizes set */
		sizes = new HashSet<>();
		icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		preferredSize = new Dimension(16, 16);
		result = icon.getClosestSize(preferredSize);
		Assert.assertNull(result);
		
		/* Null preferred size */
		sizes = new HashSet<>();
		sizes.add(new Dimension(128,128));
		sizes.add(new Dimension(256,512));
		sizes.add(new Dimension(16,16));
		icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		preferredSize = null;
		result = icon.getClosestSize(preferredSize);
		Assert.assertNull(result);
	}

	@Test
	public final void testSizesToString() throws MalformedURLException {
		/* Multiple values in sizes set */
		Set<String> rels = new HashSet<>();
		rels.add(IconLinkRelations.ICON.getRelValue());
		
		Set<Dimension> sizes = new HashSet<>();
		sizes.add(new Dimension(128,128));
		sizes.add(new Dimension(256,512));
		sizes.add(new Dimension(16,16));
		
		IconEntry icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		String sizesStr = icon.sizesToString();
		/* The set is not ordered, only check result contains what we expect */
		Assert.assertTrue(sizesStr.contains("128x128"));
		Assert.assertTrue(sizesStr.contains("256x512"));
		Assert.assertTrue(sizesStr.contains("16x16"));
		Assert.assertTrue(sizesStr.contains(" "));
		
		/* One value in sizes set */
		sizes = new HashSet<>();
		sizes.add(new Dimension(128,128));
		
		icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		sizesStr = icon.sizesToString();
		Assert.assertEquals("128x128", sizesStr);
		
		/* Empty sizes set */
		sizes = new HashSet<>();
		
		icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		sizesStr = icon.sizesToString();
		Assert.assertTrue(sizesStr.isEmpty());
	}

	@Test
	public final void testRelToString() throws MalformedURLException {
		/* Multiple values in rel set */
		Set<String> rels = new HashSet<>();
		rels.add(IconLinkRelations.ICON.getRelValue());
		rels.add(IconLinkRelations.APPLE_TOUCH_ICON.getRelValue());
		rels.add(IconLinkRelations.MASK_ICON.getRelValue());
		
		Set<Dimension> sizes = new HashSet<>();
		sizes.add(new Dimension(128,128));
		
		IconEntry icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		String relStr = icon.relToString();
		/* The set is not ordered, only check result contains what we expect */
		Assert.assertTrue(relStr.contains(IconLinkRelations.ICON.getRelValue()));
		Assert.assertTrue(relStr.contains(IconLinkRelations.APPLE_TOUCH_ICON.getRelValue()));
		Assert.assertTrue(relStr.contains(IconLinkRelations.MASK_ICON.getRelValue()));
		Assert.assertTrue(relStr.contains(" "));
		
		/* One value in rel set */
		rels = new HashSet<>();
		rels.add(IconLinkRelations.ICON.getRelValue());
		
		icon = new IconEntry(new DigestURL("https://yacy.net"), rels, sizes);
		relStr = icon.relToString();
		Assert.assertEquals(IconLinkRelations.ICON.getRelValue(), relStr);
	}

}
